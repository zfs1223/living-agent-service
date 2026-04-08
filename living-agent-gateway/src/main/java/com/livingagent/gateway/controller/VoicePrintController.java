package com.livingagent.gateway.controller;

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

    public VoicePrintController(SpeakerVerificationService speakerVerificationService) {
        this.speakerVerificationService = speakerVerificationService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<VoicePrintResponse>> registerVoicePrint(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("speaker_id") String speakerId,
            @RequestParam(value = "name", required = false) String name
    ) {
        log.info("Registering voice print for speaker: {}", speakerId);

        if (!speakerVerificationService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("service_disabled", "Voice print service is disabled"));
        }

        try {
            if (speakerVerificationService.isUseRemote()) {
                SpeakerVerificationResult result = speakerVerificationService.registerSpeakerRemote(
                        speakerId,
                        audio.getBytes(),
                        name
                );
                return buildResponse(result);
            } else {
                Path tempFile = Files.createTempFile("voice_", ".wav");
                audio.transferTo(tempFile.toFile());

                SpeakerVerificationResult result = speakerVerificationService.registerSpeaker(
                        speakerId,
                        tempFile.toString(),
                        name
                );

                Files.deleteIfExists(tempFile);
                return buildResponse(result);
            }
        } catch (IOException e) {
            log.error("Failed to process audio file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("io_error", "Failed to process audio file: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<VoicePrintLoginResponse>> voicePrintLogin(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "threshold", required = false) Double threshold
    ) {
        log.info("Voice print login attempt");

        if (!speakerVerificationService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("service_disabled", "Voice print service is disabled"));
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
                VoicePrintLoginResponse response = new VoicePrintLoginResponse(
                        result.getSpeakerId(),
                        result.getName(),
                        result.getSimilarity(),
                        true
                );
                return ResponseEntity.ok(ApiResponse.success(response));
            } else {
                VoicePrintLoginResponse response = new VoicePrintLoginResponse(
                        null,
                        null,
                        result.getSimilarity(),
                        false
                );
                return ResponseEntity.status(401)
                        .body(ApiResponse.error("verification_failed", result.getMessage()));
            }
        } catch (IOException e) {
            log.error("Failed to process audio file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("io_error", "Failed to process audio file: " + e.getMessage()));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VoicePrintResponse>> verifyVoicePrint(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("speaker_id") String speakerId,
            @RequestParam(value = "threshold", required = false) Double threshold
    ) {
        log.info("Verifying voice print for speaker: {}", speakerId);

        if (!speakerVerificationService.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("service_disabled", "Voice print service is disabled"));
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

            return buildResponse(result);
        } catch (IOException e) {
            log.error("Failed to process audio file", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("io_error", "Failed to process audio file: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoicePrintInfo>>> listVoicePrints() {
        log.debug("Listing voice prints");

        List<VoicePrintInfo> voicePrints = new ArrayList<>();
        voicePrints.add(new VoicePrintInfo(
                "vp_001",
                "user_001",
                "张三",
                true,
                Instant.now()
        ));

        return ResponseEntity.ok(ApiResponse.success(voicePrints));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<VoicePrintStatusResponse>> getStatus() {
        VoicePrintStatusResponse response = new VoicePrintStatusResponse(
                speakerVerificationService.isEnabled(),
                speakerVerificationService.isUseRemote(),
                speakerVerificationService.getThreshold()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private ResponseEntity<ApiResponse<VoicePrintResponse>> buildResponse(SpeakerVerificationResult result) {
        if (result.isSuccess()) {
            VoicePrintResponse response = new VoicePrintResponse(
                    result.getSpeakerId(),
                    result.getName(),
                    result.getSimilarity(),
                    result.getThreshold(),
                    result.isVerified(),
                    result.getMessage(),
                    result.getAllResults()
            );
            return ResponseEntity.ok(ApiResponse.success(response));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("verification_failed", result.getMessage()));
        }
    }

    public record ApiResponse<T>(
            boolean success,
            T data,
            String error,
            String errorDescription
    ) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String error, String description) {
            return new ApiResponse<>(false, null, error, description);
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
