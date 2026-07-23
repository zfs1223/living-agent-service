package com.luohuo.flex.oauth.service;

import cn.hutool.json.JSONObject;

/**
 * GitHub Auth Service
 */
public interface GithubAuthService {
    /**
     * Get GitHub User Info by Auth Code
     * @param code Authorization Code
     * @return User Info JSON
     */
    JSONObject getGithubUserInfo(String code);
}
