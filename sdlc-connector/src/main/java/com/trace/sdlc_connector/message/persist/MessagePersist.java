package com.trace.sdlc_connector.message.persist;

import com.trace.sdlc_connector.message.Message;
import com.trace.sdlc_connector.message.MessageEntity;
import com.trace.sdlc_connector.message.MessageProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "trace.sdlc.mode", havingValue = "persist")
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
