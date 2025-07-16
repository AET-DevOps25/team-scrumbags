package com.trace.comms_connector;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.trace.comms_connector.connection.ConnectionEntity;

import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
public class CommsThread extends Thread {
    @Autowired
    private CommsService commsService;

    private Logger logger = LoggerFactory.getLogger(CommsThread.class);

    /* 
     * Thread that pulls messages from external communication platforms and sends
     * these to the gen AI microservice every 24 hours
     */
    @Override
    public void run() {
        while (true) {
            Instant before = Instant.now();

            List<ConnectionEntity> connections = commsService.getAllConnections();

            logger.info("Pulling messages...");

            for (int i = 0; i < connections.size(); i++) {
                if (interrupted()) {
                    return;
                }
            
                ConnectionEntity connection = connections.get(i);

                try {
                    String msgs = "[]";

                    do {
                        msgs = commsService.getMessageBatchFromChannel(
                            connection.getProjectId(),
                            connection.getPlatform(),
                            connection.getPlatformChannelId(),
                            null,
                            true,
                            false
                        );
                        logger.info(msgs);
                    } while (!msgs.equals("[]"));

                } catch (RuntimeException re) {
                    
                    logger.error("Failed to pull messages from platform "
                        + connection.getPlatform().toString() + ", channel ID "
                        + connection.getPlatformChannelId());
                    
                    try {
                        sleep(Long.parseLong(re.getMessage()) * 1000);
                        i--; // Retry the same connection after waiting
                    } catch (Exception e) {
                        logger.error("An error has occured in the comms thread: " + e.getMessage());
                        logger.info("Stopping the comms thread...");
                        return;
                    }
                }
                
            }

            Instant after = Instant.now();

            // Sleep until the next 24-hour cycle
            Duration timeSpent = Duration.between(after, before);
            Duration timeToSleep = Duration.ofDays(1).minus(timeSpent).abs();

            logger.info("Sleeping until " + Instant.now().plus(timeToSleep).toString() + "...");

            try {
                // TODO: possibly allow custom waiting time instead of default 1 day
                sleep(timeToSleep);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void cancel() {
        if (!isAlive()) {
            logger.warn("Comms thread is not running, cannot stop!");
            return;
        }
        logger.info("Comms thread stopping...");
        interrupt();
    }

    public void startThread() {
        if (isAlive()) {
            logger.warn("Comms thread already running, not starting again!");
            return;
        }
        setName("CommsThread");
        logger.info("Comms thread starting...");
        start();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runCommsThreadOnStartup() {
        startThread();
    }

    @PreDestroy
    public void stopCommsThreadOnDestroy() {
        cancel();
    }
}