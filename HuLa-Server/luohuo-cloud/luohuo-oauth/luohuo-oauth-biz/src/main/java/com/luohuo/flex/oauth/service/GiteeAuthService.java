package com.luohuo.flex.oauth.service;

import cn.hutool.json.JSONObject;

/**
 * Gitee Auth Service
 */
public interface GiteeAuthService {
    /**
     * Get Gitee User Info by Auth Code
     * @param code Authorization Code
     * @return User Info JSON
     */
    JSONObject getGiteeUserInfo(String code);
}
