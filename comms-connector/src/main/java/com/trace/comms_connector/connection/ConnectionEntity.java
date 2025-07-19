package com.trace.comms_connector.connection;

import java.util.UUID;

import com.trace.comms_connector.Platform;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Connections")
@IdClass(ConnectionCompositeKey.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ConnectionEntity {
    @Id
    private UUID projectId;

    @Id
    private String platformChannelId;

    @Enumerated(EnumType.STRING)
    @Id
    private Platform platform;

    private String lastMessageId;
}
