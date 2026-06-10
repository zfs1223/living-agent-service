package com.livingagent.core.security.bash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class BashSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(BashSecurityValidator.class);

    /** 可配置的白名单命令（全部大写，逗号分隔，通过 application.yml 覆盖） */
    @Value("${security.bash.allowlisted-commands:}")
    private String allowlistedCommandsConfig;

    /** 可配置的额外禁止模式（用于扩展危险检测规则，逗号分隔的正则） */
    @Value("${security.bash.blocklisted-patterns:}")
    private String blocklistedPatternsConfig;

    /** 解析后的白名单命令缓存 */
    private volatile List<String> allowlistedCommands;

    /** 解析后的额外禁止模式缓存 */
    private volatile List<Pattern> extraBlockedPatterns;

    /** 内置安全检查规则 */
    private static final List<BashCheck> BUILTIN_CHECKS = List.of(
        new BashCheck("SHELL_METACHAR", Pattern.compile("[;&|`$]"),
            "Shell metacharacters detected", BashValidationResult.BashSeverity.MEDIUM),
        new BashCheck("SUDO", Pattern.compile("\\bsudo\\b"),
            "Privilege escalation (sudo) detected", BashValidationResult.BashSeverity.CRITICAL),
        new BashCheck("RM_RF", Pattern.compile("\\brm\\s+(-[a-zA-Z]*)?r"),
            "Recursive deletion (rm -r) detected", BashValidationResult.BashSeverity.CRITICAL),
        new BashCheck("CMD_SUBSTITUTION", Pattern.compile("\\$\\("),
            "Command substitution detected", BashValidationResult.BashSeverity.HIGH),
        new BashCheck("IFS_INJECTION", Pattern.compile("\\bIFS\\s*="),
            "IFS manipulation detected", BashValidationResult.BashSeverity.HIGH),
        new BashCheck("REVERSE_SHELL", Pattern.compile("(?i)(nc\\s+-e|/dev/tcp|ncat)"),
            "Reverse shell detected", BashValidationResult.BashSeverity.CRITICAL),
        new BashCheck("DANGEROUS_COMMANDS", Pattern.compile("(?i)\\b(chmod|chown|passwd|shutdown|reboot|systemctl|service|mkfifo)\\b"),
            "Dangerous system command detected", BashValidationResult.BashSeverity.HIGH),
        new BashCheck("NETWORK_UPLOAD", Pattern.compile("(?i)\\b(wget|curl|fetch)\\s"),
            "Network upload/download detected", BashValidationResult.BashSeverity.HIGH),
        new BashCheck("ENV_INJECTION", Pattern.compile("(?i)\\b(LD_PRELOAD|PATH\\s*=|PYTHONPATH\\s*=)\\b"),
            "Environment variable injection detected", BashValidationResult.BashSeverity.HIGH),
        new BashCheck("DISK_OPERATION", Pattern.compile("(?i)\\bdd\\s+if="),
            "Disk operation detected", BashValidationResult.BashSeverity.CRITICAL),
        new BashCheck("PYTHON_IMPORT_BYPASS", Pattern.compile("(?i)(__import__|importlib\\.import_module)"),
            "Python import bypass detected", BashValidationResult.BashSeverity.HIGH)
    );

    public BashValidationResult validate(String command) {
        if (command == null || command.isBlank()) {
            return BashValidationResult.safe();
        }

        // 白名单检查：白名单中的命令直接放行
        List<String> allowedCmds = getAllowlistedCommands();
        if (!allowedCmds.isEmpty()) {
            String trimmedCmd = command.trim().toLowerCase();
            for (String allowed : allowedCmds) {
                if (trimmedCmd.equals(allowed) || trimmedCmd.startsWith(allowed + " ")) {
                    log.debug("Bash command passed whitelist check: {}", command.length() > 80
                        ? command.substring(0, 80) + "..." : command);
                    return BashValidationResult.safe();
                }
            }
        }

        BashValidationResult worst = BashValidationResult.safe();

        // 内置规则检查
        for (BashCheck check : BUILTIN_CHECKS) {
            if (check.pattern.matcher(command).find()) {
                BashValidationResult result = BashValidationResult.threat(
                    check.threatType,
                    check.description + ": " + command,
                    check.severity
                );
                if (result.severity().ordinal() > worst.severity().ordinal()) {
                    worst = result;
                }
            }
        }

        // 额外配置的禁止模式检查
        List<Pattern> extraPatterns = getExtraBlockedPatterns();
        for (Pattern pattern : extraPatterns) {
            if (pattern.matcher(command).find()) {
                BashValidationResult result = BashValidationResult.threat(
                    "CUSTOM_BLOCKED", "Custom blocked pattern matched: " + command,
                    BashValidationResult.BashSeverity.HIGH
                );
                if (result.severity().ordinal() > worst.severity().ordinal()) {
                    worst = result;
                }
            }
        }

        if (!worst.isSafe()) {
            log.warn("Bash security threat detected: type={}, severity={}, command={}",
                worst.threatType(), worst.severity(),
                command.length() > 100 ? command.substring(0, 100) + "..." : command);
        }

        return worst;
    }

    /** 获取当前可用的检查规则列表（内置 + 额外配置） */
    public List<String> getActiveThreatTypes() {
        List<String> types = new ArrayList<>();
        BUILTIN_CHECKS.forEach(c -> types.add(c.threatType));
        if (getExtraBlockedPatterns().size() > 0) {
            types.add("CUSTOM_BLOCKED (" + getExtraBlockedPatterns().size() + " patterns)");
        }
        return Collections.unmodifiableList(types);
    }

    /** 解析配置的白名单命令 */
    private List<String> getAllowlistedCommands() {
        if (allowlistedCommands == null) {
            synchronized (this) {
                if (allowlistedCommands == null) {
                    if (allowlistedCommandsConfig == null || allowlistedCommandsConfig.isBlank()) {
                        allowlistedCommands = Collections.emptyList();
                    } else {
                        allowlistedCommands = parseCommandList(allowlistedCommandsConfig);
                        log.info("BashSecurityValidator allowlist loaded: {} commands", allowlistedCommands.size());
                    }
                }
            }
        }
        return allowlistedCommands;
    }

    /** 解析配置的额外禁止模式 */
    private List<Pattern> getExtraBlockedPatterns() {
        if (extraBlockedPatterns == null) {
            synchronized (this) {
                if (extraBlockedPatterns == null) {
                    if (blocklistedPatternsConfig == null || blocklistedPatternsConfig.isBlank()) {
                        extraBlockedPatterns = Collections.emptyList();
                    } else {
                        extraBlockedPatterns = parseCommandList(blocklistedPatternsConfig).stream()
                            .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                            .collect(Collectors.toList());
                        log.info("BashSecurityValidator extra blocklist loaded: {} patterns", extraBlockedPatterns.size());
                    }
                }
            }
        }
        return extraBlockedPatterns;
    }

    private List<String> parseCommandList(String config) {
        List<String> result = new ArrayList<>();
        for (String part : config.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.toLowerCase());
            }
        }
        return result;
    }

    private record BashCheck(
        String threatType,
        Pattern pattern,
        String description,
        BashValidationResult.BashSeverity severity
    ) {}
}
