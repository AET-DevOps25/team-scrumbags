package com.trace.sdlc_connector.message;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public abstract class MessageProcessor {

    public abstract void processMessage(UUID projectId, Message message);
}
