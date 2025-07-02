package com.trace.comms_connector.util;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.comms_connector.CommsService;
import com.trace.comms_connector.Platform;
import com.trace.comms_connector.model.CommsMessage;

@Component
public class CommsMessageConverter {
    @Autowired
    private CommsService commsService;

    public String convertListToJsonArray(List<? extends CommsMessage> messages, UUID projectId, Platform platform) {
        // Convert to list of JSON strings according to the gen AI microservice
        List<String> jsonMessages = messages.stream()
            .map(msg -> {
                UUID userId = commsService.getUserIdByProjectIdAndPlatformDetails(
                    projectId, platform, msg.getAuthor().getId());
                return msg.getJsonString(userId, projectId);
            })
            .toList();

        // Convert to a single string of a JSON array
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(jsonMessages);
        } catch (Exception e) {
            return "{}";
        }
    }
}
