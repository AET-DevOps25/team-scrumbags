package com.trace.comms_connector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.trace.comms_connector.connection.ConnectionEntity;
import com.trace.comms_connector.discord.DiscordRestClient;
import com.trace.comms_connector.model.CommsMessage;
import com.trace.comms_connector.util.CommsMessageConverter;

import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
public class CommsThread extends Thread {
    @Autowired
    private CommsService commsService;

    @Autowired
    private DiscordRestClient discordClient;

    @Autowired
    private CommsRestClient commsClient;

    @Autowired
    private CommsMessageConverter msgConverter;

    private Logger logger = LoggerFactory.getLogger(CommsThread.class);

    /* 
     * Thread that pulls messages from external communication platforms and sends
     * these to the gen AI microservice every 24 hours
     */
    @Override
    public void run() {
        logger.info("Comms thread running!");

        while (true) {
            Instant before = Instant.now();

            List<ConnectionEntity> connections = commsService.getAllConnections();

            logger.info("Pulling messages...");

            for (ConnectionEntity connection : connections) {
                List<? extends CommsMessage> messageBatch = new ArrayList<>();
                List<? extends CommsMessage> allMessages = new ArrayList<>();

                do {
                    // Get next batch of messages from the channel
                    switch (connection.getPlatform()) {
                        case DISCORD:
                            messageBatch = discordClient.getChannelMessages(
                                connection.getPlatformChannelId(),
                                connection.getLastMessageId(),
                                connection.getProjectId());
                            break;
                    }

                    // Update last message ID
                    commsService.saveConnection(
                        connection.getProjectId(),
                        connection.getPlatformChannelId(),
                        connection.getPlatform(),
                        messageBatch.get(0).getId()
                    );

                    allMessages.addAll(messageBatch);
                } while (!interrupted() && !messageBatch.isEmpty());

                if (!allMessages.isEmpty()) {
                    String messageJsonArray = msgConverter.convertListToJsonArray(
                        allMessages, connection.getProjectId(), connection.getPlatform());

                    commsClient.sendMessageListToGenAi(messageJsonArray);
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
        interrupt();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runCommsThreadOnStartup() {
        this.start();
    }

    @PreDestroy
    public void stopCommsThreadOnDestroy() {
        logger.info("Comms thread stopping!");
        this.cancel();
    }
}