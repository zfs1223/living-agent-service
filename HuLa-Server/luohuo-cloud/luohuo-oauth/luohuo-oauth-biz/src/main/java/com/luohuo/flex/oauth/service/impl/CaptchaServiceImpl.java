package com.luohuo.flex.oauth.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.luohuo.basic.cache.lock.DistributedLock;
import com.luohuo.basic.cache.repository.CachePlusOps;
import com.luohuo.basic.utils.TimeUtils;
import com.luohuo.flex.model.vo.query.BindEmailReq;
import com.luohuo.flex.service.SysConfigService;
import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.ChineseCaptcha;
import com.wf.captcha.ChineseGifCaptcha;
import com.wf.captcha.GifCaptcha;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import com.luohuo.basic.base.R;
import com.luohuo.basic.cache.redis2.CacheResult;
import com.luohuo.basic.model.cache.CacheKey;
import com.luohuo.basic.utils.ArgumentAssert;
import com.luohuo.flex.base.service.tenant.DefUserService;
import com.luohuo.flex.common.cache.common.CaptchaCacheKeyBuilder;
import com.luohuo.flex.model.enumeration.base.MsgTemplateCodeEnum;
import com.luohuo.flex.msg.vo.update.ExtendMsgSendVO;
import com.luohuo.flex.oauth.granter.CaptchaTokenGranter;
import com.luohuo.flex.oauth.properties.CaptchaProperties;
import com.luohuo.flex.oauth.service.CaptchaService;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.luohuo.basic.exception.code.ResponseEnum.CAPTCHA_ERROR;

import com.luohuo.flex.msg.facade.MsgFacade;
/**
 * 验证码服务
 *
 * @author 乾乾
 */
@Service
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaServiceImpl implements CaptchaService {

	private final SysConfigService configService;
    private final CachePlusOps cachePlusOps;
    private final CaptchaProperties captchaProperties;
    private final MsgFacade msgFacade;
    private final DefUserService defUserService;
	private final DistributedLock distributedLock;

	private static final int CAPTCHA_EXPIRE_SECONDS = 5 * 60;
	private static final int SEND_COUNT_WINDOW_HOURS = 24;
	private static final int RESEND_INTERVAL_SECONDS_FIRST_THREE = 60;
	private static final int RESEND_INTERVAL_SECONDS_AFTER_THREE = 5 * 60;

    /**
     * 注意验证码生成需要服务器支持，若您的验证码接口显示不出来，
     * 需要在 接口 所在的服务器安装字体库！ 若是docker部署，需要在docker镜像内部安装。
     * <p>
     * centos执行（其他服务器自行百度）：
     * yum install fontconfig
     * fc-cache --force
     *
     * @param key      验证码 uuid
     * @throws IOException
     */
    @Override
    public HashMap<String, Object> createImg(String key) throws IOException {
//		String uuid = IdUtil.fastUUID();
//		long expireTime = 300;
//
//		com.luohuo.flex.system.core.user.domain.dto.captcha.SpecCaptcha specCaptcha = new com.luohuo.flex.system.core.user.domain.dto.captcha.SpecCaptcha(120, 45, 4);
//		// 设置类型，纯数字、纯字母、字母数字混合
//		specCaptcha.setCharType(com.luohuo.flex.system.core.user.domain.dto.captcha.Captcha.TYPE_ONLY_NUMBER);
//		cachePlusOps.hSet(CaptchaCacheKeyBuilder.hashBuild("numberCode", uuid, expireTime), specCaptcha.text().toLowerCase());
//
//		HashMap<String, Object> map = new HashMap<>();
//		map.put("uuid", uuid);
//		map.put("img", specCaptcha.toBase64());
//		return map;

        if (StrUtil.isBlank(key)) {
			key = IdUtil.fastUUID();
        }
        Captcha captcha = createCaptcha();

        CacheKey cacheKey = CaptchaCacheKeyBuilder.build(key, CaptchaTokenGranter.GRANT_TYPE);
        cachePlusOps.set(cacheKey, captcha.text().toLowerCase());

		HashMap<String, Object> map = new HashMap<>();
		map.put("uuid", key);
		map.put("img", captcha.toBase64());
		return map;
    }


    @Override
    public R<Boolean> sendSmsCode(String mobile, String templateCode) {
        if (MsgTemplateCodeEnum.REGISTER_SMS.eq(templateCode)) {
            // 查user表判断重复
            boolean flag = defUserService.checkMobile(mobile, null);
            ArgumentAssert.isFalse(flag, "该手机号已经被注册");
        } else if (MsgTemplateCodeEnum.MOBILE_LOGIN.eq(templateCode)) {
            //查user表判断是否存在
            boolean flag = defUserService.checkMobile(mobile, null);
            ArgumentAssert.isTrue(flag, "该手机号尚未注册，请先注册后在登陆。");
        } else if (MsgTemplateCodeEnum.MOBILE_EDIT.eq(templateCode)) {
            //查user表判断是否存在
            boolean flag = defUserService.checkMobile(mobile, null);
            ArgumentAssert.isFalse(flag, "该手机号已经被他人使用");
        }

        CacheKey cacheKey = CaptchaCacheKeyBuilder.build(mobile, templateCode);
		assertSendAllowed(cacheKey);

        String code = RandomUtil.randomNumbers(4);
        // cacheKey.setExpire(Duration.ofMinutes(15));  // 可以修改有效期
        cachePlusOps.set(cacheKey, code);

        log.info("短信验证码 cacheKey={}, code={}", cacheKey, code);

        // 在「运营平台」-「消息模板」配置一个「模板标识」为 templateCode， 且「模板内容」中需要有 code 占位符
        // 也可以考虑给模板增加一个过期时间等参数
        ExtendMsgSendVO msgSendVO = ExtendMsgSendVO.builder().code(templateCode).build();
        msgSendVO.addParam("code", code);
        msgSendVO.addRecipient(mobile);
        return R.success(msgFacade.sendByTemplate(msgSendVO));
    }

    @Override
    public R<Long> sendEmailCode(BindEmailReq bindEmailReq) {
        if (MsgTemplateCodeEnum.REGISTER_EMAIL.eq(bindEmailReq.getTemplateCode())) {
            // 查user表判断重复
            boolean flag = defUserService.checkEmail(bindEmailReq.getEmail(), null);
            ArgumentAssert.isFalse(flag, "该邮箱已经被注册");
        } else if (MsgTemplateCodeEnum.EMAIL_EDIT.eq(bindEmailReq.getTemplateCode())) {
            //查user表判断是否存在
            boolean flag = defUserService.checkEmail(bindEmailReq.getEmail(), null);
            ArgumentAssert.isFalse(flag, "该邮箱已经被他人使用");
        }

//		CacheKey imgKey = CaptchaCacheKeyBuilder.build(bindEmailReq.getClientId(), CaptchaTokenGranter.GRANT_TYPE);
//		CacheResult<String> result = cachePlusOps.get(imgKey);
//		ArgumentAssert.isFalse(!bindEmailReq.getCode().equals(result.getValue()), "图片验证码错误");

        CacheKey cacheKey = CaptchaCacheKeyBuilder.build(bindEmailReq.getEmail(), bindEmailReq.getTemplateCode());
		assertSendAllowed(cacheKey);

		String code = RandomUtil.randomNumbers(6);
        cachePlusOps.set(cacheKey, code);
        log.info("邮件验证码 cacheKey={}, code={}", cacheKey, code);

        // 在「运营平台」-「消息模板」配置一个「模板标识」为 templateCode， 且「模板内容」中需要有 code 占位符
		int expireTime = CAPTCHA_EXPIRE_SECONDS;
        ExtendMsgSendVO msgSendVO = ExtendMsgSendVO.builder().code(bindEmailReq.getTemplateCode()).build();
        msgSendVO.addParam("emailCode", code);
		msgSendVO.addParam("systemName", configService.get("systemName"));
		msgSendVO.addParam("expireMinutes", String.valueOf(expireTime / 60));
		msgSendVO.addParam("adminEmail", configService.get("masterEmail"));
		msgSendVO.addParam("currentTime", TimeUtils.nowToStr());

        msgSendVO.addRecipient(bindEmailReq.getEmail());
		msgFacade.sendByTemplate(msgSendVO);
		return R.success(cachePlusOps.ttl(cacheKey));
    }

    @Override
    public R<Boolean> checkCaptcha(String key, String templateCode, String value) {
        if (StrUtil.isBlank(value)) {
            return R.fail(CAPTCHA_ERROR.build("请输入验证码"));
        }
        CacheKey cacheKey = CaptchaCacheKeyBuilder.build(key, templateCode);
        CacheResult<String> code = cachePlusOps.get(cacheKey);
        if (StrUtil.isEmpty(code.getValue())) {
            return R.fail(CAPTCHA_ERROR.build("验证码已过期"));
        }
        if (!StrUtil.equalsIgnoreCase(value, code.getValue())) {
            return R.fail(CAPTCHA_ERROR.build("验证码不正确"));
        }
        cachePlusOps.del(cacheKey);
        return R.success(true);
    }


    private Captcha createCaptcha() {

        CaptchaProperties.CaptchaType type = captchaProperties.getType();
        Captcha captcha = switch (type) {
            case GIF ->
                    new GifCaptcha(captchaProperties.getWidth(), captchaProperties.getHeight(), captchaProperties.getLen());
            case SPEC ->
                    new SpecCaptcha(captchaProperties.getWidth(), captchaProperties.getHeight(), captchaProperties.getLen());
            case CHINESE ->
                    new ChineseCaptcha(captchaProperties.getWidth(), captchaProperties.getHeight(), captchaProperties.getLen());
            case CHINESE_GIF ->
                    new ChineseGifCaptcha(captchaProperties.getWidth(), captchaProperties.getHeight(), captchaProperties.getLen());
            default ->
                    new ArithmeticCaptcha(captchaProperties.getWidth(), captchaProperties.getHeight(), captchaProperties.getLen());
        };
        captcha.setCharType(captchaProperties.getCharType());

        return captcha;
    }

	private void assertSendAllowed(CacheKey captchaKey) {
		String lockKey = captchaKey.getKey() + ":send_lock";
		boolean locked = distributedLock.lock(lockKey, 3000);
		ArgumentAssert.isTrue(locked, "操作频繁，请稍后重试");
		try {
			CacheKey resendLockKey = new CacheKey(captchaKey.getKey() + ":resend_lock");
			if (Boolean.TRUE.equals(cachePlusOps.exists(resendLockKey))) {
				Long ttl = cachePlusOps.ttl(resendLockKey);
				long seconds = ttl == null || ttl < 0 ? 0 : ttl;
				ArgumentAssert.isFalse(true, "请{}秒后再试", seconds);
			}

			int sendCount = cachePlusOps.integerInc(captchaKey.getKey() + ":send_count", SEND_COUNT_WINDOW_HOURS, TimeUnit.HOURS);
			int intervalSeconds = sendCount <= 3 ? RESEND_INTERVAL_SECONDS_FIRST_THREE : RESEND_INTERVAL_SECONDS_AFTER_THREE;
			cachePlusOps.integerInc(resendLockKey.getKey(), intervalSeconds, TimeUnit.SECONDS);
		} finally {
			distributedLock.releaseLock(lockKey);
		}
	}
}
