package com.livingagent.core.proxy.anthropic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class ClaudeProxyAuditService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProxyAuditService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    public void recordRequestReceived(String requestId, String requestedModel, String employeeId, String sessionId) {
        auditLog.info("AUDIT_REQUEST | requestId={} | model={} | employeeId={} | sessionId={} | timestamp={}",
            requestId, requestedModel, employeeId, sessionId, Instant.now());
    }

    public void recordModelResolved(String requestId, String resolvedModel, String providerName) {
        auditLog.info("AUDIT_MODEL_RESOLVED | requestId={} | resolvedModel={} | provider={} | timestamp={}",
            requestId, resolvedModel, providerName, Instant.now());
    }

    public void recordStreamEvent(String requestId, String eventType, long tokensGenerated) {
        if (log.isDebugEnabled()) {
            log.debug("AUDIT_STREAM_EVENT | requestId={} | type={} | tokens={}", requestId, eventType, tokensGenerated);
        }
    }

    public void recordCompleted(String requestId, String stopReason, int inputTokens, int outputTokens, long durationMs) {
        auditLog.info("AUDIT_COMPLETED | requestId={} | stop_reason={} | input_tokens={} | output_tokens={} | duration_ms={} | timestamp={}",
            requestId, stopReason, inputTokens, outputTokens, durationMs, Instant.now());
    }

    public void recordFailed(String requestId, String errorType, String errorMessage, long durationMs) {
        auditLog.error("AUDIT_FAILED | requestId={} | error_type={} | error={} | duration_ms={} | timestamp={}",
            requestId, errorType, errorMessage, durationMs, Instant.now());
    }
}
