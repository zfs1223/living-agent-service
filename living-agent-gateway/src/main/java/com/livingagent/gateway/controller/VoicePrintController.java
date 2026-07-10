package com.livingagent.gateway.controller;

import com.livingagent.core.security.AccessGateService;
import com.livingagent.core.security.auth.AuthMetricsService;
import com.livingagent.gateway.controller.common.ApiResponse;
import com.livingagent.core.service.voice.SpeakerVerificationService;
import com.livingagent.core.service.voice.SpeakerVerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/voiceprint")
public class VoicePrintController {

    private static final Logger log = LoggerFactory.getLogger(VoicePrintController.class);

    private final SpeakerVerificationService speakerVerificationService;
    private final AccessGateService accessGateService;
    private final AuthMetricsService authMetricsService;

    public VoicePrintController(SpeakerVerificationService speakerVerificationService, AccessGateService accessGateService, AuthMetricsService authMetricsService) {
        this.speakerVerificationService = speakerVerificationService;
        this.accessGateService = accessGateService;
        this.authMetricsService = authMetricsService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<VoicePrintResponse>> registerVoicePrint(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("speaker_id") String speakerId,
            @RequestParam(value = "name", required = false) String name,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Registering voice print for speaker: {}", speakerId);

        if (!speakerVerificationService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("service_disabled", "Voice print service is disabled"));
        }

        try {
            if (speakerVerificationService.isUseRemote()) {
                SpeakerVerificationResult result = speakerVerificationService.registerSpeakerRemote(
                        speakerId,
                        audio.getBytes(),
                        name
                );
                return buildResponse(result, "voiceprint_register");
            } else {
                Path tempFile = Files.createTempFile("voice_", ".wav");
                audio.transferTo(tempFile.toFile());

                SpeakerVerificationResult result = speakerVerificationService.registerSpeaker(
                        speakerId,
                        tempFile.toString(),
                        name
                );

                Files.deleteIfExists(tempFile);
                return buildResponse(result, "voiceprint_register");
            }
        } catch (IOException e) {
            log.error("Failed to process audio file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("io_error", "Failed to process audio file: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<VoicePrintLoginResponse>> voicePrintLogin(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "threshold", required = false) Double threshold,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Voice print login attempt");

        if (!speakerVerificationService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("service_disabled", "Voice print service is disabled"));
        }

        try {
            SpeakerVerificationResult result;

            if (speakerVerificationService.isUseRemote()) {
                result = speakerVerificationService.identifySpeakerRemote(audio.getBytes());
            } else {
                Path tempFile = Files.createTempFile("voice_", ".wav");
                audio.transferTo(tempFile.toFile());

                result = speakerVerificationService.verifySpeaker(tempFile.toString(), null);

                Files.deleteIfExists(tempFile);
            }

            if (result.isSuccess() && result.isVerified()) {
                authMetricsService.recordSuccess("voiceprint_login", "VoicePrintController");
                VoicePrintLoginResponse response = new VoicePrintLoginResponse(
                        result.getSpeakerId(),
                        result.getName(),
                        result.getSimilarity(),
                        true
                );
                return ResponseEntity.ok(ApiResponse.ok(response));
            } else {
                authMetricsService.recordFailure("voiceprint_login", "VoicePrintController", "verification_failed");
                VoicePrintLoginResponse response = new VoicePrintLoginResponse(
                        null,
                        null,
                        result.getSimilarity(),
                        false
                );
                return ResponseEntity.status(401)
                        .body(ApiResponse.err("verification_failed", result.getMessage()));
            }
        } catch (IOException e) {
            log.error("Failed to process audio file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("io_error", "Failed to process audio file: " + e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VoicePrintResponse>> verifyVoicePrint(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("speaker_id") String speakerId,
            @RequestParam(value = "threshold", required = false) Double threshold,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId
    ) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.info("Verifying voice print for speaker: {}", speakerId);

        if (!speakerVerificationService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("service_disabled", "Voice print service is disabled"));
        }

        try {
            SpeakerVerificationResult result;

            if (speakerVerificationService.isUseRemote()) {
                result = speakerVerificationService.verifySpeakerRemote(audio.getBytes(), speakerId);
            } else {
                Path tempFile = Files.createTempFile("voice_", ".wav");
                audio.transferTo(tempFile.toFile());

                result = speakerVerificationService.verifySpeaker(tempFile.toString(), speakerId);

                Files.deleteIfExists(tempFile);
            }

            return buildResponse(result, "voiceprint_verify");
        } catch (IOException e) {
            log.error("Failed to process audio file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("io_error", "Failed to process audio file: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoicePrintInfo>>> listVoicePrints(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        log.debug("Listing voice prints");

        List<VoicePrintInfo> voicePrints = new ArrayList<>();
        voicePrints.add(new VoicePrintInfo(
                "vp_001",
                "user_001",
                "张三",
                true,
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.ok(voicePrints));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<VoicePrintStatusResponse>> getStatus(
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        if (employeeId != null && !employeeId.isBlank() && !accessGateService.canRoute(employeeId, "brain", "MainBrain")) {
            return ResponseEntity.status(403).body(ApiResponse.err("forbidden", "Access denied before routing"));
        }
        VoicePrintStatusResponse response = new VoicePrintStatusResponse(
                speakerVerificationService.isEnabled(),
                speakerVerificationService.isUseRemote(),
                speakerVerificationService.getThreshold()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private ResponseEntity<ApiResponse<VoicePrintResponse>> buildResponse(SpeakerVerificationResult result, String method) {
        if (result.isSuccess()) {
            if (result.isVerified()) {
                authMetricsService.recordSuccess(method, "VoicePrintController");
            } else {
                authMetricsService.recordFailure(method, "VoicePrintController", "not_verified");
            }
            VoicePrintResponse response = new VoicePrintResponse(
                    result.getSpeakerId(),
                    result.getName(),
                    result.getSimilarity(),
                    result.getThreshold(),
                    result.isVerified(),
                    result.getMessage(),
                    result.getAllResults()
            );
            return ResponseEntity.ok(ApiResponse.ok(response));
        } else {
            authMetricsService.recordFailure(method, "VoicePrintController", result.getMessage() != null ? result.getMessage() : "verification_failed");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.err("verification_failed", result.getMessage()));
        }
    }

    public record VoicePrintResponse(
            String speakerId,
            String name,
            double similarity,
            double threshold,
            boolean verified,
            String message,
            List<Map<String, Object>> allResults
    ) {}

    public record VoicePrintLoginResponse(
            String speakerId,
            String name,
            double similarity,
            boolean verified
    ) {}

    public record VoicePrintStatusResponse(
            boolean enabled,
            boolean useRemote,
            double threshold
    ) {}

    public record VoicePrintInfo(
            String id,
            String user_id,
            String name,
            boolean active,
            Instant created_at
    ) {}
}
