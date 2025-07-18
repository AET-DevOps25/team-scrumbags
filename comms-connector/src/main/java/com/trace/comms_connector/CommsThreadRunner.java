package com.trace.comms_connector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class CommsThreadRunner {
    @Autowired
    private CommsService commsService;

    @EventListener(ApplicationReadyEvent.class)
    public void runCommsThreadOnStartup() {
        CommsThread.setCommsService(commsService);
        CommsThread.getInstance().startThread();
    }

    @PreDestroy
    public void stopCommsThreadOnDestroy() {
        CommsThread.getInstance().stopThread();
    }
    
}
