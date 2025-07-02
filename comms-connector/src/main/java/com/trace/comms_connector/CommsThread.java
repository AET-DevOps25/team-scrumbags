package com.trace.comms_connector;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

    /* 
     * Thread that pulls messages from external communication platforms and sends
     * these to the gen AI microservice every 24 hours
     */
    @Override
    public void run() {
        System.out.println("Comms thread running!");

        while (true) {
            Instant before = Instant.now();

            List<ConnectionEntity> connections = commsService.getAllConnections();

            for (ConnectionEntity connection : connections) {
                if (interrupted()) {
                    return;
                }

                List<? extends CommsMessage> messages = null;

                switch (connection.getPlatform()) {
                    case DISCORD:
                        messages = discordClient.getChannelMessages(
                            connection.getPlatformChannelId(),
                            connection.getLastMessageId(),
                            connection.getProjectId());
                        break;
                }

                if (!messages.isEmpty() || messages != null) {
                    String messageJsonArray = msgConverter.convertListToJsonArray(
                        messages, connection.getProjectId(), connection.getPlatform());

                    commsClient.sendMessageListToGenAi(messageJsonArray);

                    // Update last message ID
                    commsService.saveConnection(
                        connection.getProjectId(),
                        connection.getPlatformChannelId(),
                        connection.getPlatform(),
                        messages.get(0).getId()
                    );
                }
            }

            Instant after = Instant.now();

            // Sleep until the next 24-hour cycle
            Duration timeSpent = Duration.between(after, before);
            try {
                // TODO: possibly allow custom waiting time instead of default 1 day
                sleep(Duration.ofDays(1).minus(timeSpent).abs());
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
        System.out.println("Comms thread stopping!");
        this.cancel();
    }
}