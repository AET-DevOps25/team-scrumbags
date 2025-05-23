package com.trace.sdlc_connector.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.trace.sdlc_connector.DataEntity;
import com.trace.sdlc_connector.DataRepo;
import com.trace.sdlc_connector.github.eventhandler.*;
import com.trace.sdlc_connector.token.SupportedSystem;
import com.trace.sdlc_connector.token.TokenRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.UUID;


@RestController
public class GithubConnector {

    private static final Logger logger = LoggerFactory.getLogger(GithubConnector.class);

    private final TokenRepo tokenRepo;
    private final DataRepo dataRepo;

    public GithubConnector(TokenRepo tokenRepo, DataRepo dataRepo) {
        this.tokenRepo = tokenRepo;
        this.dataRepo = dataRepo;
    }

    @PostMapping("/{projectId}/github")
    public ResponseEntity<?> webhookHandler(
            @PathVariable UUID projectId,
            @RequestBody String payload,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature) {

        var secrets = tokenRepo.findAllByProjectIdAndSupportedSystem(projectId, SupportedSystem.GITHUB);

        // try all tokens
        String validSecret = null;
        for (var secret : secrets) {
            if (validateWebhookSignature(payload, secret.getToken(), signature)) {
                validSecret = secret.getToken();
                break;
            }
        }
        if (validSecret == null) {
            logger.warn("Invalid webhook signature");
            return ResponseEntity.badRequest().body("No valid secret for signature");
        }

        JsonNode jsonPayload;
        try {
            // Convert payload string to JsonNode
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            jsonPayload = mapper.readTree(payload);

        } catch (Exception e) {
            logger.error("Error processing webhook payload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook payload");
        }

        DataEntity dataEntity = processWebhookEvent(eventType, jsonPayload);
        dataEntity.setProjectId(projectId);

        dataRepo.save(dataEntity);

        // dont return data entity as github will receive the response
        return ResponseEntity.ok("Webhook received");
    }

    /**
     * Process a GitHub webhook event
     *
     * @param eventType GitHub event type from X-GitHub-Event header
     * @param payload   The JSON payload from GitHub
     */
    public DataEntity processWebhookEvent(String eventType, JsonNode payload) {
        logger.info("Processing GitHub webhook event: {}", eventType);

        DataEntity entity = new DataEntity();
        payload.fields().forEachRemaining(entry -> {
            entity.getData().put(entry.getKey(), entry.getValue().toString());
        });

        return entity;
    }

    /**
     * Validates a GitHub webhook payload using the X-Hub-Signature-256 header
     *
     * @param payload   The raw webhook payload
     * @param signature The X-Hub-Signature-256 header value
     * @return true if the signature is valid
     */
    public boolean validateWebhookSignature(String payload, String webhookSecret, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            logger.warn("Webhook secret not configured - skipping signature validation");
            return true;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            String computedSignature = toHexString(digest);
            return ("sha256=" + computedSignature).equalsIgnoreCase(signature);
        } catch (Exception e) {
            logger.error("Error validating webhook signature", e);
            return false;
        }
    }

    /**
     * Helper method to convert byte array to hex string
     */
    private static String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
}
