package com.trace.sdlc_connector.github.eventhandler;

import com.jayway.jsonpath.DocumentContext;
import com.trace.sdlc_connector.*;
import com.trace.sdlc_connector.user.UserMappingRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DeploymentReviewEventHandler extends GithubEventHandler {

    private static final String EVENT_TYPE = "deployment_review";

    public DeploymentReviewEventHandler(UserMappingRepo userMappingRepo) {
        super(EVENT_TYPE, userMappingRepo);
    }

    @Override
    public Message handleEvent(UUID projectId, UUID eventId, DocumentContext payload, Long now) {
        var message = super.handleEvent(projectId, eventId, payload, now);

        message.getMetadata().setType(EVENT_TYPE + " " + payload.read("$.action", String.class));

        message.getContent().putAll(
                JsonUtils.extract(payload, "$.since", "$.workflow_run.id", "$.comment",
                        "$.approver.id", "$.approver.login",
                        "$.environment", "$.requestor.id", "$.requestor.login"
                )
        );

        // extract array fields
        List<Map<String, Object>> reviewers = payload.read("$.reviewers", List.class);
        if (reviewers != null) {
            message.getContent().put(
                    "reviewers", new ArrayList<Map<String, Object>>()
            );

            List<Map<String, Object>> list = (List<Map<String, Object>>) message.getContent().get("reviewers");

            for (Map<String, Object> reviewer : reviewers) {
                list.add(
                        Map.of(
                                "id", reviewer.get("id"),
                                "login", reviewer.get("login")
                        )
                );
            }
        }

        List<Map<String, Object>> workflowJobRuns = payload.read("$.workflow_job_runs", List.class);
        if (workflowJobRuns != null) {
            message.getContent().put(
                    "workflow_job_runs", new ArrayList<Map<String, Object>>()
            );

            List<Map<String, Object>> list = (List<Map<String, Object>>) message.getContent().get("workflow_job_runs");

            for (Map<String, Object> workflowJobRun : workflowJobRuns) {
                list.add(
                        Map.of(
                                "id", workflowJobRun.get("id")
                        )
                );
            }
        }

        return message;
    }
}
