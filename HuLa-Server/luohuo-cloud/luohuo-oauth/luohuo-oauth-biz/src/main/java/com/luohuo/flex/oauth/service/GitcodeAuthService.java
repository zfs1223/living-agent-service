package com.luohuo.flex.oauth.service;

import cn.hutool.json.JSONObject;

public interface GitcodeAuthService {
	JSONObject getGitcodeUserInfo(String code);
}
