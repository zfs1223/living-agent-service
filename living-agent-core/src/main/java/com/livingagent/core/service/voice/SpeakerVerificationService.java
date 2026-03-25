package com.livingagent.core.service.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

@Service
public class SpeakerVerificationService {
    private static final Logger logger = LoggerFactory.getLogger(SpeakerVerificationService.class);

    @Value("${ai-models.speaker-verification.enabled:true}")
    private boolean enabled;

    @Value("${ai-models.speaker-verification.model-path:${AI_MODELS_PATH:/app/ai-models}/cam/campplus_cn_en_common.pt}")
    private String modelPath;

    @Value("${ai-models.speaker-verification.threshold:0.33}")
    private double threshold;

    @Value("${ai-models.speaker-verification.temp-dir:/tmp/speaker-verification}")
    private String tempDir;

    @Value("${ai-models.python-scripts.speaker:${PYTHON_SCRIPTS_PATH:/opt/python_scripts}/speaker/speaker_verifier.py}")
    private String pythonScript;

    @Value("${ai-models.speaker-verification.remote-service-url:}")
    private String remoteServiceUrl;

    @Value("${ai-models.speaker-verification.use-remote:false}")
    private boolean useRemote;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        try {
            Path tempPath = Path.of(tempDir);
            if (!Files.exists(tempPath)) {
                Files.createDirectories(tempPath);
                logger.info("Created temp directory: {}", tempDir);
            }
        } catch (IOException e) {
            logger.warn("Failed to create temp directory: {}, error: {}", tempDir, e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SpeakerVerificationResult verifySpeaker(String audioPath, String speakerId) {
        if (!enabled) {
            logger.warn("Speaker verification is disabled");
            return SpeakerVerificationResult.failure("Speaker verification is disabled");
        }

        try {
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                logger.error("Audio file not found: {}", audioPath);
                return SpeakerVerificationResult.failure("Audio file not found");
            }

            Map<String, Object> params = new HashMap<>();
            params.put("action", "verify");
            params.put("audio_path", audioPath);
            params.put("model_path", modelPath);
            params.put("threshold", threshold);
            if (speakerId != null && !speakerId.isEmpty()) {
                params.put("speaker_id", speakerId);
            }

            String result = executePythonScript(params);
            return parseVerificationResult(result);
        } catch (Exception e) {
            logger.error("Failed to verify speaker", e);
            return SpeakerVerificationResult.failure("Failed to verify speaker: " + e.getMessage());
        }
    }

    public SpeakerVerificationResult verifySpeakerFromPcm(byte[] pcmData, String speakerId, int sampleRate) {
        if (!enabled) {
            logger.warn("Speaker verification is disabled");
            return SpeakerVerificationResult.failure("Speaker verification is disabled");
        }

        try {
            Path tempPath = Files.createTempDirectory(Path.of(tempDir), "pcm_");
            String audioPath = tempPath.resolve("audio.wav").toString();

            savePcmAsWav(pcmData, audioPath, sampleRate);

            SpeakerVerificationResult result = verifySpeaker(audioPath, speakerId);

            Files.deleteIfExists(Path.of(audioPath));
            Files.deleteIfExists(tempPath);

            return result;
        } catch (Exception e) {
            logger.error("Failed to verify speaker from PCM", e);
            return SpeakerVerificationResult.failure("Failed to verify speaker from PCM: " + e.getMessage());
        }
    }

    private void savePcmAsWav(byte[] pcmData, String wavPath, int sampleRate) throws IOException {
        int bitsPerSample = 16;
        int channels = 1;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int totalSize = 36 + dataSize;

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

        dos.writeBytes("RIFF");
        dos.writeInt(Integer.reverseBytes(totalSize));
        dos.writeBytes("WAVE");
        dos.writeBytes("fmt ");
        dos.writeInt(Integer.reverseBytes(16));
        dos.writeShort(Short.reverseBytes((short) 1));
        dos.writeShort(Short.reverseBytes((short) channels));
        dos.writeInt(Integer.reverseBytes(sampleRate));
        dos.writeInt(Integer.reverseBytes(byteRate));
        dos.writeShort(Short.reverseBytes((short) blockAlign));
        dos.writeShort(Short.reverseBytes((short) bitsPerSample));
        dos.writeBytes("data");
        dos.writeInt(Integer.reverseBytes(dataSize));
        dos.write(pcmData);

        Files.write(Path.of(wavPath), baos.toByteArray());
    }

    public SpeakerVerificationResult registerSpeaker(String speakerId, String audioPath, String name) {
        if (!enabled) {
            logger.warn("Speaker verification is disabled");
            return SpeakerVerificationResult.failure("Speaker verification is disabled");
        }

        try {
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                logger.error("Audio file not found: {}", audioPath);
                return SpeakerVerificationResult.failure("Audio file not found");
            }

            Map<String, Object> params = new HashMap<>();
            params.put("action", "register");
            params.put("speaker_id", speakerId);
            params.put("audio_path", audioPath);
            params.put("name", name != null ? name : speakerId);
            params.put("model_path", modelPath);

            String result = executePythonScript(params);
            return parseVerificationResult(result);
        } catch (Exception e) {
            logger.error("Failed to register speaker", e);
            return SpeakerVerificationResult.failure("Failed to register speaker: " + e.getMessage());
        }
    }

    public SpeakerVerificationResult registerSpeakerFromPcm(String speakerId, byte[] pcmData, String name, int sampleRate) {
        if (!enabled) {
            logger.warn("Speaker verification is disabled");
            return SpeakerVerificationResult.failure("Speaker verification is disabled");
        }

        try {
            Path tempPath = Files.createTempDirectory(Path.of(tempDir), "pcm_");
            String audioPath = tempPath.resolve("audio.wav").toString();

            savePcmAsWav(pcmData, audioPath, sampleRate);

            SpeakerVerificationResult result = registerSpeaker(speakerId, audioPath, name);

            Files.deleteIfExists(Path.of(audioPath));
            Files.deleteIfExists(tempPath);

            return result;
        } catch (Exception e) {
            logger.error("Failed to register speaker from PCM", e);
            return SpeakerVerificationResult.failure("Failed to register speaker from PCM: " + e.getMessage());
        }
    }

    public SpeakerVerificationResult extractEmbedding(String audioPath) {
        if (!enabled) {
            logger.warn("Speaker verification is disabled");
            return SpeakerVerificationResult.failure("Speaker verification is disabled");
        }

        try {
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                logger.error("Audio file not found: {}", audioPath);
                return SpeakerVerificationResult.failure("Audio file not found");
            }

            Map<String, Object> params = new HashMap<>();
            params.put("action", "extract");
            params.put("audio_path", audioPath);
            params.put("model_path", modelPath);

            String result = executePythonScript(params);
            return parseVerificationResult(result);
        } catch (Exception e) {
            logger.error("Failed to extract embedding", e);
            return SpeakerVerificationResult.failure("Failed to extract embedding: " + e.getMessage());
        }
    }

    private SpeakerVerificationResult parseVerificationResult(String result) {
        try {
            Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);

            boolean success = Boolean.TRUE.equals(resultMap.get("success"));
            boolean verified = resultMap.containsKey("verified") ? Boolean.TRUE.equals(resultMap.get("verified")) : false;
            String speakerId = resultMap.containsKey("speaker_id") ? (String) resultMap.get("speaker_id") : null;
            String name = resultMap.containsKey("name") ? (String) resultMap.get("name") : null;
            Double similarity = resultMap.containsKey("similarity") ? ((Number) resultMap.get("similarity")).doubleValue() : 0.0;
            double threshold = resultMap.containsKey("threshold") ? ((Number) resultMap.get("threshold")).doubleValue() : this.threshold;
            String message = resultMap.containsKey("message") ? (String) resultMap.get("message") : "";

            SpeakerVerificationResult verificationResult = new SpeakerVerificationResult(success, verified, message);
            verificationResult.setSpeakerId(speakerId);
            verificationResult.setName(name);
            verificationResult.setSimilarity(similarity);
            verificationResult.setThreshold(threshold);

            if (resultMap.containsKey("all_results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> allResults = (List<Map<String, Object>>) resultMap.get("all_results");
                verificationResult.setAllResults(allResults);
            }

            return verificationResult;
        } catch (Exception e) {
            logger.error("Failed to parse verification result", e);
            return SpeakerVerificationResult.failure("Failed to parse verification result");
        }
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
        logger.info("Speaker verification threshold set to {}", threshold);
    }

    private String executePythonScript(Map<String, Object> params) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "python3",
            pythonScript
        );
        
        // Do not merge stderr to stdout to avoid log interference with JSON output
        processBuilder.redirectErrorStream(false);
        Process process = processBuilder.start();
        
        String jsonInput = objectMapper.writeValueAsString(params);
        try (OutputStream os = process.getOutputStream()) {
            os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }
        
        // Read and log stderr for debugging
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }
        if (errorOutput.length() > 0) {
            logger.debug("Python script stderr: {}", errorOutput.toString());
        }
        
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Python script failed with exit code: " + exitCode + ", stderr: " + errorOutput.toString());
        }
        
        return output.toString();
    }

    private SpeakerVerificationResult callRemoteService(String endpoint, byte[] audioData, Map<String, String> params) {
        if (remoteServiceUrl == null || remoteServiceUrl.isEmpty()) {
            return SpeakerVerificationResult.failure("Remote service URL not configured");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("audio", new ByteArrayResource(audioData) {
                @Override
                public String getFilename() {
                    return "audio.wav";
                }
            });

            for (Map.Entry<String, String> entry : params.entrySet()) {
                body.add(entry.getKey(), entry.getValue());
            }

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            String url = remoteServiceUrl + endpoint;
            logger.debug("Calling remote service: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                
                // Handle nested response structure: {"success": true, "data": {...}}
                if (result.containsKey("data") && result.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    data.put("success", result.get("success"));
                    return parseVerificationResult(objectMapper.writeValueAsString(data));
                }
                
                return parseVerificationResult(objectMapper.writeValueAsString(result));
            } else {
                return SpeakerVerificationResult.failure("Remote service returned: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("Failed to call remote service", e);
            return SpeakerVerificationResult.failure("Failed to call remote service: " + e.getMessage());
        }
    }

    public SpeakerVerificationResult verifySpeakerRemote(byte[] audioData, String speakerId) {
        Map<String, String> params = new HashMap<>();
        params.put("speaker_id", speakerId);
        params.put("threshold", String.valueOf(threshold));
        return callRemoteService("/verify", audioData, params);
    }

    public SpeakerVerificationResult registerSpeakerRemote(String speakerId, byte[] audioData, String name) {
        Map<String, String> params = new HashMap<>();
        params.put("speaker_id", speakerId);
        params.put("name", name != null ? name : speakerId);
        return callRemoteService("/register", audioData, params);
    }

    public SpeakerVerificationResult identifySpeakerRemote(byte[] audioData) {
        Map<String, String> params = new HashMap<>();
        params.put("threshold", String.valueOf(threshold));
        return callRemoteService("/identify", audioData, params);
    }

    public boolean isUseRemote() {
        return useRemote && remoteServiceUrl != null && !remoteServiceUrl.isEmpty();
    }
}
