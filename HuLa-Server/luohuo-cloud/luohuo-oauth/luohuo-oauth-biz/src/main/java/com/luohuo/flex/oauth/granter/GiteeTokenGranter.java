package com.luohuo.flex.oauth.granter;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.luohuo.basic.boot.utils.WebUtils;
import com.luohuo.flex.im.api.vo.UserRegisterVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luohuo.basic.base.R;
import com.luohuo.basic.exception.BizException;
import com.luohuo.flex.base.entity.tenant.DefUser;
import com.luohuo.flex.base.service.system.DefClientService;
import com.luohuo.flex.base.service.tenant.DefUserService;
import com.luohuo.flex.base.service.user.BaseEmployeeService;
import com.luohuo.flex.base.service.user.BaseOrgService;
import com.luohuo.flex.common.properties.SystemProperties;
import com.luohuo.flex.im.api.ImUserApi;
import com.luohuo.flex.im.enums.UserTypeEnum;
import com.luohuo.flex.oauth.biz.StpInterfaceBiz;
import com.luohuo.flex.oauth.service.GiteeAuthService;
import com.luohuo.flex.oauth.vo.param.LoginParamVO;
import com.luohuo.flex.oauth.vo.result.LoginResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gitee TokenGranter
 *
 * @author 乾乾
 */
@Component("GITEE")
@Slf4j
public class GiteeTokenGranter extends AbstractTokenGranter {

	private final GiteeAuthService giteeAuthService;

	public GiteeTokenGranter(SystemProperties systemProperties, DefClientService defClientService, DefUserService defUserService, BaseEmployeeService baseEmployeeService, BaseOrgService baseOrgService, SaTokenConfig saTokenConfig, ImUserApi imUserApi, StpInterfaceBiz stpInterfaceBiz, GiteeAuthService giteeAuthService) {
		super(systemProperties, defClientService, defUserService, baseEmployeeService, baseOrgService, saTokenConfig, imUserApi, stpInterfaceBiz);
		this.giteeAuthService = giteeAuthService;
	}

	@Override
	public R<LoginResultVO> checkParam(LoginParamVO loginParam) {
		if (StrUtil.isBlank(loginParam.getCode())) {
			return R.fail("授权码不能为空");
		}
		return R.success(null);
	}

	@Override
	protected R<LoginResultVO> checkAuthorization() {
		Object flag = WebUtils.request().getAttribute("OAUTH_INTERNAL_CALLBACK");
		if (Boolean.TRUE.equals(flag)) {
			return R.success(null);
		}
		return super.checkAuthorization();
	}

	@Override
	protected DefUser getUser(LoginParamVO loginParam) {
		String code = loginParam.getCode();

		JSONObject userJson = giteeAuthService.getGiteeUserInfo(code);

		String giteeOpenId = userJson.getStr("id");
		if (giteeOpenId == null) {
			throw new BizException("获取 Gitee 用户 ID 失败");
		}

		DefUser defUser = defUserService.getSuperManager().getOne(Wrappers.<DefUser>lambdaQuery().eq(DefUser::getGiteeOpenId, giteeOpenId));

		if (defUser == null) {
			String login = userJson.getStr("login");
			String name = userJson.getStr("name");
			String avatar = userJson.getStr("avatar_url");
			String email = userJson.getStr("email");

			if (StrUtil.isNotBlank(email)) {
				DefUser byEmail = defUserService.getSuperManager().getOne(Wrappers.<DefUser>lambdaQuery().eq(DefUser::getEmail, email));
				if (byEmail != null) {
					DefUser update = new DefUser();
					update.setId(byEmail.getId());
					update.setGiteeOpenId(giteeOpenId);
					if (StrUtil.isNotBlank(avatar)) {
						update.setAvatar(avatar);
					}
					if (StrUtil.isNotBlank(name)) {
						update.setNickName(name);
					}
					defUserService.getSuperManager().updateById(update);
					DefUser existed = defUserService.getSuperManager().getById(byEmail.getId());
					UserRegisterVo userRegisterVo = new UserRegisterVo();
					userRegisterVo.setEmail(existed.getEmail());
					userRegisterVo.setGiteeId(giteeOpenId);
					imUserApi.bindOAuth(userRegisterVo);
					return existed;
				}
			}

			String username = login;
			if (defUserService.checkUsername(username, null)) {
				username = login + "_" + giteeOpenId;
			}

			if (StrUtil.isBlank(email)) {
				email = giteeOpenId + "@gitee.com";
			}

			defUser = DefUser.builder()
					.username(username)
					.nickName(StrUtil.isBlank(name) ? username : name)
					.avatar(avatar)
					.email(email)
					.giteeOpenId(giteeOpenId)
					.state(true)
					.systemType(2) // 默认注册 IM 系统登录
					.sex(1) // 默认性别
					.build();

			// 生成随机盐和默认密码: 账号
			defUser.setSalt(username);
			defUser.setPassword(username);

			defUserService.registerByEmail(defUser);
			log.info("Gitee 用户自动注册成功: username={}, giteeOpenId={}", username, giteeOpenId);

			// 注册 IM 用户
			UserRegisterVo userRegisterVo = new UserRegisterVo();
			userRegisterVo.setAccount(defUser.getUsername());
			userRegisterVo.setEmail(defUser.getEmail());
			userRegisterVo.setUserId(defUser.getId());
			userRegisterVo.setName(defUser.getNickName());
			userRegisterVo.setSex(defUser.getSex());
			userRegisterVo.setAvatar(defUser.getAvatar());
			userRegisterVo.setTenantId(defUser.getTenantId());
			userRegisterVo.setUserType(UserTypeEnum.NORMAL.getValue());
			userRegisterVo.setGiteeId(giteeOpenId);

			// 调用 IM 远程注册接口
			if (!imUserApi.register(userRegisterVo).getData()) {
				// 如果 IM 注册失败（例如邮箱已存在），可能需要回滚 defUser 的创建，或者抛出异常
				// 这里简单处理：记录错误并抛出异常
				log.error("IM 用户注册失败，Gitee OpenID: {}", giteeOpenId);
				// 实际业务中可能需要手动删除已创建的 defUser，或者依赖事务回滚（如果配置了分布式事务）
				// 这里我们手动删除 defUser 以保持数据一致性（如果不在同一个事务中）
				defUserService.getSuperManager().removeById(defUser.getId());
				throw new BizException("IM 用户注册失败，可能是邮箱已被绑定");
			}
			log.info("IM 用户注册成功: userId={}", defUser.getId());
		}

		return defUser;
	}
}
