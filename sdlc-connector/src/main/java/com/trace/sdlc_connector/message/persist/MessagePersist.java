package com.trace.sdlc_connector.message.persist;

import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.message.MessageProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile("persist")
public class MessagePersist extends MessageProcessor {


    private final MessageRepo messageRepo;

    public MessagePersist(MessageRepo messageRepo) {
        super();
        this.messageRepo = messageRepo;
    }

    public void processMessage(UUID projectId, Message message) {
        messageRepo.save(message.toMessageEntity());
    }
}
