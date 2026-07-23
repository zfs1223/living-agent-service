package com.luohuo.flex.oauth.granter;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luohuo.basic.base.R;
import com.luohuo.basic.boot.utils.WebUtils;
import com.luohuo.basic.exception.BizException;
import com.luohuo.flex.base.entity.tenant.DefUser;
import com.luohuo.flex.base.service.system.DefClientService;
import com.luohuo.flex.base.service.tenant.DefUserService;
import com.luohuo.flex.base.service.user.BaseEmployeeService;
import com.luohuo.flex.base.service.user.BaseOrgService;
import com.luohuo.flex.common.properties.SystemProperties;
import com.luohuo.flex.im.api.ImUserApi;
import com.luohuo.flex.im.api.vo.UserRegisterVo;
import com.luohuo.flex.im.enums.UserTypeEnum;
import com.luohuo.flex.oauth.biz.StpInterfaceBiz;
import com.luohuo.flex.oauth.service.GithubAuthService;
import com.luohuo.flex.oauth.vo.param.LoginParamVO;
import com.luohuo.flex.oauth.vo.result.LoginResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * GitHub TokenGranter
 */
@Component("GITHUB")
@Slf4j
public class GithubTokenGranter extends AbstractTokenGranter {

	private final GithubAuthService githubAuthService;

	public GithubTokenGranter(SystemProperties systemProperties,
							  DefClientService defClientService,
							  DefUserService defUserService,
							  BaseEmployeeService baseEmployeeService,
							  BaseOrgService baseOrgService,
							  SaTokenConfig saTokenConfig,
							  ImUserApi imUserApi,
							  StpInterfaceBiz stpInterfaceBiz,
							  GithubAuthService githubAuthService) {
		super(systemProperties, defClientService, defUserService, baseEmployeeService, baseOrgService, saTokenConfig, imUserApi, stpInterfaceBiz);
		this.githubAuthService = githubAuthService;
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

		JSONObject userJson = githubAuthService.getGithubUserInfo(code);

		String id = userJson.getStr("id");
		if (id == null) {
			throw new BizException("获取 GitHub 用户 ID 失败");
		}

		String login = userJson.getStr("login");
		String name = userJson.getStr("name");
		String avatar = userJson.getStr("avatar_url");
		String email = userJson.getStr("email");
		String bio = userJson.getStr("bio");
		if (StrUtil.isBlank(email)) {
			email = id + "@github.com";
		}

		// 优先通过 GitHub OpenId 查找用户
		DefUser defUser = defUserService.getSuperManager().getOne(
				Wrappers.<DefUser>lambdaQuery()
						.eq(DefUser::getSystemType, 2)
						.eq(DefUser::getGithubOpenId, id),
				false
		);

		if (defUser == null) {
			// 自动注册逻辑
			String username = login;
			if (defUserService.checkUsername(username, null)) {
				username = login + "_" + id;
			}

			defUser = DefUser.builder()
					.username(username)
					.nickName(StrUtil.isBlank(name) ? username : name)
					.avatar(avatar)
					.email(email)
					.workDescribe(bio)
					.state(true)
					.systemType(2)
					.sex(1)
					.githubOpenId(id)
					.build();

			// 生成随机盐和默认密码: 账号
			defUser.setSalt(username);
			defUser.setPassword(username);

			boolean registeredNew = false;
			try {
				defUserService.registerByEmail(defUser);
				registeredNew = true;
				log.info("GitHub 用户自动注册成功: username={}, email={}", username, email);
			} catch (DuplicateKeyException e) {
				DefUser exist = defUserService.getUserByEmail(2, email);
				if (exist != null) {
					DefUser update = new DefUser();
					update.setId(exist.getId());
					update.setGithubOpenId(id);
					if (StrUtil.isNotBlank(avatar) && !StrUtil.equals(avatar, exist.getAvatar())) {
						update.setAvatar(avatar);
					}
					String targetNick = StrUtil.isBlank(name) ? exist.getNickName() : name;
					if (StrUtil.isNotBlank(targetNick) && !StrUtil.equals(targetNick, exist.getNickName())) {
						update.setNickName(targetNick);
					}
					if (StrUtil.isNotBlank(bio) && !StrUtil.equals(bio, exist.getWorkDescribe())) {
						update.setWorkDescribe(bio);
					}
					defUserService.getSuperManager().updateById(update);
					defUser = defUserService.getSuperManager().getById(exist.getId());
				} else {
					throw e;
				}
			}

			if (registeredNew) {
				UserRegisterVo userRegisterVo = new UserRegisterVo();
				userRegisterVo.setAccount(defUser.getUsername());
				userRegisterVo.setEmail(defUser.getEmail());
				userRegisterVo.setUserId(defUser.getId());
				userRegisterVo.setName(defUser.getNickName());
				userRegisterVo.setSex(defUser.getSex());
				userRegisterVo.setAvatar(defUser.getAvatar());
				userRegisterVo.setTenantId(defUser.getTenantId());
				userRegisterVo.setUserType(UserTypeEnum.NORMAL.getValue());
				userRegisterVo.setGithubId(id);

				if (!imUserApi.register(userRegisterVo).getData()) {
					log.error("IM 用户注册失败，GitHub Email: {}", email);
					defUserService.getSuperManager().removeById(defUser.getId());
					throw new BizException("IM 用户注册失败，可能是邮箱已被绑定");
				}
				log.info("IM 用户注册成功: userId={}", defUser.getId());
			}
		} else {
			// 更新头像、昵称、签名、邮箱等信息
			boolean needUpdate = false;
			DefUser update = new DefUser();
			update.setId(defUser.getId());
			if (StrUtil.isNotBlank(avatar) && !StrUtil.equals(avatar, defUser.getAvatar())) {
				update.setAvatar(avatar);
				needUpdate = true;
			}
			String targetNick = StrUtil.isBlank(name) ? defUser.getNickName() : name;
			if (StrUtil.isNotBlank(targetNick) && !StrUtil.equals(targetNick, defUser.getNickName())) {
				update.setNickName(targetNick);
				needUpdate = true;
			}
			if (StrUtil.isNotBlank(bio) && !StrUtil.equals(bio, defUser.getWorkDescribe())) {
				update.setWorkDescribe(bio);
				needUpdate = true;
			}
			if (StrUtil.isNotBlank(email) && !StrUtil.equals(email, defUser.getEmail())) {
				update.setEmail(email);
				needUpdate = true;
			}
			if (defUser.getGithubOpenId() == null || !StrUtil.equals(defUser.getGithubOpenId(), id)) {
				update.setGithubOpenId(id);
				needUpdate = true;

				// 绑定 IM 账号
				UserRegisterVo userRegisterVo = new UserRegisterVo();
				userRegisterVo.setEmail(defUser.getEmail());
				userRegisterVo.setGithubId(id);
				imUserApi.bindOAuth(userRegisterVo);
			}
			if (needUpdate) {
				defUserService.getSuperManager().updateById(update);
			}
		}

		return defUser;
	}
}
