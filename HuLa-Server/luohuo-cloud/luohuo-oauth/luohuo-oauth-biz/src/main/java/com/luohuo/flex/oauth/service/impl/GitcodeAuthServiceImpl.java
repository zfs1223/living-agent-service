package com.luohuo.flex.oauth.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.luohuo.basic.exception.BizException;
import com.luohuo.flex.oauth.service.GitcodeAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class GitcodeAuthServiceImpl implements GitcodeAuthService {

	@Value("${gitcode.client-id}")
	private String clientId;

	@Value("${gitcode.client-secret}")
	private String clientSecret;

	@Value("${gitcode.redirect-uri}")
	private String redirectUri;

	@Override
	public JSONObject getGitcodeUserInfo(String code) {
		String tokenUrl = "https://gitcode.com/oauth/token";

		String finalRedirectUri = redirectUri;
		if (StrUtil.isNotBlank(finalRedirectUri)) {
			finalRedirectUri = finalRedirectUri.trim();
			if (finalRedirectUri.endsWith("/")) {
				finalRedirectUri = StrUtil.subBefore(finalRedirectUri, "/", true);
			}
		}

		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("grant_type", "authorization_code");
		paramMap.put("code", code);
		paramMap.put("client_id", clientId);
		paramMap.put("client_secret", clientSecret);
		paramMap.put("redirect_uri", finalRedirectUri);

		String tokenResponse;
		try {
			tokenResponse = HttpUtil.post(tokenUrl, paramMap);
		} catch (Exception e) {
			log.error("请求 GitCode Token 失败", e);
			throw new BizException("连接 GitCode 失败");
		}

		JSONObject tokenJson = JSONUtil.parseObj(tokenResponse);
		String accessToken = tokenJson.getStr("access_token");
		if (StrUtil.isBlank(accessToken)) {
			log.error("GitCode token 响应: {}", tokenResponse);
			throw new BizException("获取 GitCode Access Token 失败: " + tokenResponse);
		}

		String userUrl = "https://gitcode.com/api/v5/user";
		String userResponse;
		try {
			userResponse = HttpUtil.get(userUrl + "?access_token=" + accessToken);
		} catch (Exception e) {
			log.error("请求 GitCode 用户信息失败", e);
			throw new BizException("获取 GitCode 用户信息失败");
		}

		return JSONUtil.parseObj(userResponse);
	}
}