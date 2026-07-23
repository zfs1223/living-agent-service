package com.luohuo.flex.oauth.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.luohuo.basic.exception.BizException;
import com.luohuo.flex.oauth.service.GithubAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class GithubAuthServiceImpl implements GithubAuthService {

	@Value("${github.client-id}")
	private String clientId;

	@Value("${github.client-secret}")
	private String clientSecret;

	@Value("${github.redirect-uri}")
	private String redirectUri;

	@Override
	public JSONObject getGithubUserInfo(String code) {
		// 1. 获取 Access Token
		String tokenUrl = "https://github.com/login/oauth/access_token";

		String finalRedirectUri = redirectUri;
		if (StrUtil.isNotBlank(finalRedirectUri)) {
			finalRedirectUri = finalRedirectUri.trim();
			if (finalRedirectUri.endsWith("/")) {
				finalRedirectUri = StrUtil.subBefore(finalRedirectUri, "/", true);
			}
		}

		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("client_id", clientId);
		paramMap.put("client_secret", clientSecret);
		paramMap.put("code", code);
		paramMap.put("redirect_uri", finalRedirectUri);

		String tokenResponse;
		try {
			tokenResponse = HttpRequest.post(tokenUrl)
					.header("Accept", "application/json")
					.form(paramMap)
					.execute()
					.body();
		} catch (Exception e) {
			log.error("请求 GitHub Token 失败", e);
			throw new BizException("连接 GitHub 失败");
		}

		JSONObject tokenJson = JSONUtil.parseObj(tokenResponse);
		String accessToken = tokenJson.getStr("access_token");
		if (StrUtil.isBlank(accessToken)) {
			log.error("GitHub token 响应: {}", tokenResponse);
			throw new BizException("获取 GitHub Access Token 失败: " + tokenResponse);
		}

		// 2. 获取用户信息
		String userResponse;
		try {
			userResponse = HttpRequest.get("https://api.github.com/user")
					.header("Authorization", "token " + accessToken)
					.header("Accept", "application/json")
					.execute()
					.body();
		} catch (Exception e) {
			log.error("请求 GitHub 用户信息失败", e);
			throw new BizException("获取 GitHub 用户信息失败");
		}

		JSONObject userJson = JSONUtil.parseObj(userResponse);

		// 3. 获取用户邮箱（可能为空，需要额外调用）
		try {
			String emailsResponse = HttpRequest.get("https://api.github.com/user/emails")
					.header("Authorization", "token " + accessToken)
					.header("Accept", "application/json")
					.execute()
					.body();
			JSONArray emails = JSONUtil.parseArray(emailsResponse);
			String primaryEmail = null;
			for (int i = 0; i < emails.size(); i++) {
				JSONObject item = emails.getJSONObject(i);
				if (item.getBool("primary", false) && item.getBool("verified", false)) {
					primaryEmail = item.getStr("email");
					break;
				}
			}
			if (StrUtil.isBlank(primaryEmail)) {
				// 兼容：使用第一个邮箱
				if (emails.size() > 0) {
					primaryEmail = emails.getJSONObject(0).getStr("email");
				}
			}
			if (StrUtil.isNotBlank(primaryEmail)) {
				userJson.set("email", primaryEmail);
			}
		} catch (Exception e) {
			log.warn("获取 GitHub 邮箱失败，继续使用基本信息: {}", e.getMessage());
		}

		return userJson;
	}
}
