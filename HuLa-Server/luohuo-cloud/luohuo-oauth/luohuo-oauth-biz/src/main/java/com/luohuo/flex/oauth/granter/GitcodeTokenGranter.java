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
import com.luohuo.flex.oauth.service.GitcodeAuthService;
import com.luohuo.flex.oauth.vo.param.LoginParamVO;
import com.luohuo.flex.oauth.vo.result.LoginResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component("GITCODE")
@Slf4j
public class GitcodeTokenGranter extends AbstractTokenGranter {

	private final GitcodeAuthService gitcodeAuthService;

	public GitcodeTokenGranter(SystemProperties systemProperties,
							   DefClientService defClientService,
							   DefUserService defUserService,
							   BaseEmployeeService baseEmployeeService,
							   BaseOrgService baseOrgService,
							   SaTokenConfig saTokenConfig,
							   ImUserApi imUserApi,
							   StpInterfaceBiz stpInterfaceBiz,
							   GitcodeAuthService gitcodeAuthService) {
		super(systemProperties, defClientService, defUserService, baseEmployeeService, baseOrgService, saTokenConfig, imUserApi, stpInterfaceBiz);
		this.gitcodeAuthService = gitcodeAuthService;
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
		JSONObject userJson = gitcodeAuthService.getGitcodeUserInfo(code);

		String gitcodeOpenId = userJson.getStr("id");
		if (gitcodeOpenId == null) {
			throw new BizException("获取 GitCode 用户 ID 失败");
		}

		DefUser defUser = defUserService.getSuperManager().getOne(Wrappers.<DefUser>lambdaQuery().eq(DefUser::getGitcodeOpenId, gitcodeOpenId));

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
					update.setGitcodeOpenId(gitcodeOpenId);
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
					userRegisterVo.setGitcodeId(gitcodeOpenId);
					imUserApi.bindOAuth(userRegisterVo);
					return existed;
				}
			}

			String username = login;
			if (defUserService.checkUsername(username, null)) {
				username = login + "_" + gitcodeOpenId;
			}

			if (StrUtil.isBlank(email)) {
				email = gitcodeOpenId + "@gitcode.com";
			}

			defUser = DefUser.builder()
					.username(username)
					.nickName(StrUtil.isBlank(name) ? username : name)
					.avatar(avatar)
					.email(email)
					.gitcodeOpenId(gitcodeOpenId)
					.state(true)
					.systemType(2)
					.sex(1)
					.build();
			defUser.setSalt(username);
			defUser.setPassword(username);

			defUserService.registerByEmail(defUser);
			log.info("GitCode 用户自动注册成功: username={}, gitcodeOpenId={}", username, gitcodeOpenId);

			UserRegisterVo userRegisterVo = new UserRegisterVo();
			userRegisterVo.setAccount(defUser.getUsername());
			userRegisterVo.setEmail(defUser.getEmail());
			userRegisterVo.setUserId(defUser.getId());
			userRegisterVo.setName(defUser.getNickName());
			userRegisterVo.setSex(defUser.getSex());
			userRegisterVo.setAvatar(defUser.getAvatar());
			userRegisterVo.setTenantId(defUser.getTenantId());
			userRegisterVo.setUserType(UserTypeEnum.NORMAL.getValue());
			userRegisterVo.setGitcodeId(gitcodeOpenId);

			if (!imUserApi.register(userRegisterVo).getData()) {
				log.error("IM 用户注册失败，GitCode OpenID: {}", gitcodeOpenId);
				defUserService.getSuperManager().removeById(defUser.getId());
				throw new BizException("IM 用户注册失败，可能是邮箱已被绑定");
			}
			log.info("IM 用户注册成功: userId={}", defUser.getId());
		} else {
			String name = userJson.getStr("name");
			String avatar = userJson.getStr("avatar_url");
			String email = userJson.getStr("email");
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
			if (StrUtil.isNotBlank(email) && !StrUtil.equals(email, defUser.getEmail())) {
				update.setEmail(email);
				needUpdate = true;
			}
			if (needUpdate) {
				defUserService.getSuperManager().updateById(update);
			}
			UserRegisterVo userRegisterVo = new UserRegisterVo();
			userRegisterVo.setEmail(defUser.getEmail());
			userRegisterVo.setGitcodeId(gitcodeOpenId);
			imUserApi.bindOAuth(userRegisterVo);
		}

		return defUser;
	}
}
