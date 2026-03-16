package com.livingagent.core.security.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRE_MS = 5 * 60 * 1000;
    private static final int MAX_ATTEMPTS = 5;
    private static final long ATTEMPT_WINDOW_MS = 60 * 60 * 1000;

    private final Map<String, VerificationCode> codeStore = new ConcurrentHashMap<>();
    private final Map<String, AttemptRecord> attemptStore = new ConcurrentHashMap<>();
    
    private final SecureRandom random = new SecureRandom();
    
    private SmsSender smsSender;

    public PhoneVerificationService() {
    }

    public PhoneVerificationService(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    public void setSmsSender(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    public SendResult sendVerificationCode(String phone) {
        log.info("Sending verification code to: {}", maskPhone(phone));

        if (isRateLimited(phone)) {
            log.warn("Rate limited for phone: {}", maskPhone(phone));
            return SendResult.rateLimited("Too many requests, please try again later");
        }

        String code = generateCode();
        long expireTime = System.currentTimeMillis() + CODE_EXPIRE_MS;
        
        codeStore.put(phone, new VerificationCode(code, expireTime));
        
        if (smsSender != null) {
            try {
                boolean sent = smsSender.send(phone, "您的验证码是: " + code + "，有效期5分钟。");
                if (!sent) {
                    log.error("Failed to send SMS to: {}", maskPhone(phone));
                    return SendResult.failed("Failed to send SMS");
                }
            } catch (Exception e) {
                log.error("SMS sending error: {}", e.getMessage());
                return SendResult.failed("SMS service error: " + e.getMessage());
            }
        }

        recordAttempt(phone);
        
        log.info("Verification code sent to: {}, code: {}", maskPhone(phone), code);
        return SendResult.success(code);
    }

    public VerifyResult verifyCode(String phone, String code) {
        log.info("Verifying code for phone: {}", maskPhone(phone));

        if (phone == null || phone.isEmpty()) {
            return VerifyResult.failed("Phone number is required");
        }

        if (code == null || code.isEmpty()) {
            return VerifyResult.failed("Verification code is required");
        }

        if (!isValidPhoneFormat(phone)) {
            return VerifyResult.failed("Invalid phone number format");
        }

        VerificationCode storedCode = codeStore.get(phone);
        if (storedCode == null) {
            log.warn("No verification code found for: {}", maskPhone(phone));
            return VerifyResult.failed("No verification code found, please request a new one");
        }

        if (System.currentTimeMillis() > storedCode.expireTime) {
            codeStore.remove(phone);
            log.warn("Verification code expired for: {}", maskPhone(phone));
            return VerifyResult.failed("Verification code has expired, please request a new one");
        }

        if (!storedCode.code.equals(code)) {
            log.warn("Invalid verification code for: {}", maskPhone(phone));
            return VerifyResult.failed("Invalid verification code");
        }

        codeStore.remove(phone);
        clearAttempts(phone);
        
        log.info("Phone verified successfully: {}", maskPhone(phone));
        return VerifyResult.success();
    }

    public boolean isValidPhoneFormat(String phone) {
        if (phone == null || phone.isEmpty()) {
            return false;
        }
        
        String cleanPhone = phone.replaceAll("[\\s\\-\\(\\)]", "");
        
        return cleanPhone.matches("^1[3-9]\\d{9}$") ||
               cleanPhone.matches("^\\+861[3-9]\\d{9}$") ||
               cleanPhone.matches("^\\+\\d{10,15}$");
    }

    public String normalizePhone(String phone) {
        if (phone == null) return null;
        
        String normalized = phone.replaceAll("[\\s\\-\\(\\)]", "");
        
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        }
        
        return normalized;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private boolean isRateLimited(String phone) {
        AttemptRecord record = attemptStore.get(phone);
        if (record == null) {
            return false;
        }
        
        if (System.currentTimeMillis() - record.windowStart > ATTEMPT_WINDOW_MS) {
            attemptStore.remove(phone);
            return false;
        }
        
        return record.attemptCount >= MAX_ATTEMPTS;
    }

    private void recordAttempt(String phone) {
        AttemptRecord record = attemptStore.computeIfAbsent(phone, k -> new AttemptRecord());
        
        if (System.currentTimeMillis() - record.windowStart > ATTEMPT_WINDOW_MS) {
            record.windowStart = System.currentTimeMillis();
            record.attemptCount = 0;
        }
        
        record.attemptCount++;
    }

    private void clearAttempts(String phone) {
        attemptStore.remove(phone);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        codeStore.entrySet().removeIf(entry -> entry.getValue().expireTime < now);
        attemptStore.entrySet().removeIf(entry -> 
                now - entry.getValue().windowStart > ATTEMPT_WINDOW_MS);
        log.debug("Cleaned up expired verification codes");
    }

    private static class VerificationCode {
        final String code;
        final long expireTime;

        VerificationCode(String code, long expireTime) {
            this.code = code;
            this.expireTime = expireTime;
        }
    }

    private static class AttemptRecord {
        long windowStart = System.currentTimeMillis();
        int attemptCount = 0;
    }

    public record SendResult(boolean success, String code, String error) {
        public static SendResult success(String code) {
            return new SendResult(true, code, null);
        }
        
        public static SendResult failed(String error) {
            return new SendResult(false, null, error);
        }
        
        public static SendResult rateLimited(String error) {
            return new SendResult(false, null, error);
        }
    }

    public record VerifyResult(boolean isSuccess, String error) {
        public static VerifyResult success() {
            return new VerifyResult(true, null);
        }
        
        public static VerifyResult failed(String error) {
            return new VerifyResult(false, error);
        }
    }

    public interface SmsSender {
        boolean send(String phone, String message);
    }
}
