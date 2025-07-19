package com.trace.comms_connector;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.trace.comms_connector.connection.ConnectionEntity;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class CommsThread extends Thread {
    private static CommsService commsService;

    private static boolean alive = false;
    private static CommsThread instance;

    private Logger logger = LoggerFactory.getLogger(CommsThread.class);

    /* 
     * Thread that pulls messages from external communication platforms and sends
     * these to the gen AI microservice every 24 hours
     */
    @Override
    public void run() {
        while (true) {
            Instant before = Instant.now();

            List<ConnectionEntity> connections = CommsThread.commsService.getAllConnections();

            logger.info("Pulling messages...");

            for (int i = 0; i < connections.size(); i++) {
                ConnectionEntity connection = connections.get(i);

                try {
                    String msgs = "[]";

                    do {
                        msgs = CommsThread.commsService.getMessageBatchFromChannel(
                            connection.getProjectId(),
                            connection.getPlatform(),
                            connection.getPlatformChannelId(),
                            null,
                            true,
                            true
                        );
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
                        CommsThread.alive = false;
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
                CommsThread.alive = false;
                return;
            }
        }
    }

    public void stopThread() throws RuntimeException {
        synchronized (CommsThread.class) {
            if (!CommsThread.alive) {
                logger.warn("Comms thread is not running, cannot stop!");
                throw new RuntimeException("Comms thread is not running, cannot stop!");
            }
            logger.info("Comms thread stopping...");
            CommsThread.instance.interrupt();
        }
    }

    public void startThread() throws RuntimeException {
        synchronized (CommsThread.class) {
            if (CommsThread.alive) {
                logger.warn("Comms thread already running, not starting again!");
                throw new RuntimeException("Comms thread already running, not starting again!");
            }
            logger.info("Comms thread starting...");
            CommsThread.alive = true;
            CommsThread.instance = this;
            CommsThread.instance.start();
        }
    }

    public static CommsThread getInstance() {
        synchronized (CommsThread.class) {
            if (!alive) {
                CommsThread.instance = new CommsThread();
            }
            return CommsThread.instance;
        }
    }

    public static void setCommsService(CommsService service) {
        synchronized (CommsThread.class) {
            CommsThread.commsService = service;
        }
    }
}