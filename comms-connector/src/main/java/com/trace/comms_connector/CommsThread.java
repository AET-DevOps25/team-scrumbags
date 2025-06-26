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

import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
public class CommsThread extends Thread {
    @Autowired
    private CommsService commsService;

    /* 
     * Thread that pulls messages from external communication platforms and sends
     * these to the gen AI microservice every 24 hours
     */
    @Override
    public void run() {
        System.out.println("Comms thread running!");
        
        GenAiRestClient client = new GenAiRestClient();

        while (true) {
            Instant before = Instant.now();

            List<ConnectionEntity> connections = commsService.getAllConnections();

            // TODO: maybe abstract this switch case logic to an interface which all platforms' classes implement
            for (ConnectionEntity connection : connections) {
                if (interrupted()) {
                    return;
                }

                switch (connection.getPlatform()) {
                    case DISCORD:
                        DiscordRestClient discordClient = new DiscordRestClient();
                        String messageJsonArray = discordClient.getChannelMessages(
                            connection.getPlatformChannelId(),
                            connection.getLastMessageId(),
                            connection.getProjectId());
                        client.sendMessageListToGenAi(messageJsonArray);
                        break;
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
        this.cancel();
    }
}