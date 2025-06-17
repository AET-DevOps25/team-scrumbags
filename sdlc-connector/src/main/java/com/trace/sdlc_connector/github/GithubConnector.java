package com.trace.sdlc_connector.github;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.github.eventhandler.*;
import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.message.MessageProcessor;
import com.trace.sdlc_connector.token.TokenRepo;
import com.trace.sdlc_connector.user.UserMappingRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@RestController
public class GithubConnector {

    private static final Logger logger = LoggerFactory.getLogger(GithubConnector.class);

    private final TokenRepo tokenRepo;
    private final MessageProcessor messageProcessor;

    private final Map<String, GithubEventHandler> eventHandler;
    public GithubConnector(TokenRepo tokenRepo, MessageProcessor messageProcessor, UserMappingRepo userMappingRepo) {
        this.tokenRepo = tokenRepo;
        this.messageProcessor = messageProcessor;

        this.eventHandler = Stream.of(
                        new CreateEventHandler(userMappingRepo),
                        new DeleteEventHandler(userMappingRepo),
                        new CommitCommentEventHandler(userMappingRepo),
                        new DeploymentEventHandler(userMappingRepo),
                        new DeploymentStatusEventHandler(userMappingRepo),
                        new DeploymentReviewEventHandler(userMappingRepo),
                        new DiscussionCommentEventHandler(userMappingRepo),
                        new DiscussionEventHandler(userMappingRepo),
                        new IssueCommentEventHandler(userMappingRepo),
                        new IssueEventHandler(userMappingRepo),
                        new MilestoneEventHandler(userMappingRepo),
                        new PackageEventHandler(userMappingRepo),
                        new PullRequestEventHandler(userMappingRepo),
                        new PullRequestReviewCommentEventHandler(userMappingRepo),
                        new PullRequestReviewEventHandler(userMappingRepo),
                        new PullRequestReviewThreadEventHandler(userMappingRepo),
                        new PushEventHandler(userMappingRepo),
                        new RegistryPackageEventHandler(userMappingRepo),
                        new ReleaseEventHandler(userMappingRepo),
                        new StatusEventHandler(userMappingRepo),
                        new SubIssueEventHandler(userMappingRepo),
                        new WorkflowDispatchEventHandler(userMappingRepo),
                        new WorkflowJobEventHandler(userMappingRepo),
                        new WorkflowRunEventHandler(userMappingRepo)
                )
                .collect(Collectors.toMap(GithubEventHandler::getEventType, handler -> handler));

    }

    @PostMapping("projects/{projectId}/webhook/github")
    public ResponseEntity<?> webhookHandler(
            @PathVariable UUID projectId,
            @RequestBody String payload,
            @RequestHeader("X-GitHub-Delivery") UUID eventId,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-Hub-Signature-256") String signature) {

        var now = System.currentTimeMillis();

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

        Message message = processWebhookEvent(eventType, eventId, projectId, payload, now);

        messageProcessor.processMessage(projectId, message);

        // dont return data as github will receive the response
        return ResponseEntity.ok("Webhook received");
    }

    public Message processWebhookEvent(String eventType, UUID eventId, UUID projectId, String payload, Long now) {
        logger.info("Processing GitHub webhook event: {}", eventType);

        GithubEventHandler handler = eventHandler.getOrDefault(eventType, null);

        if (handler == null) {
            logger.info("Unhandled GitHub event type: {}", eventType);
            return null;
        }

        var json = JsonPath.using(Configuration.builder()
                .options(Option.SUPPRESS_EXCEPTIONS).build()
        ).parse(payload);

        return handler.handleEvent(projectId, eventId, json, now);
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
