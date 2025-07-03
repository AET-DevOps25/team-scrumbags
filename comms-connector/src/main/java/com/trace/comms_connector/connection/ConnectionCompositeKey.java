package com.trace.comms_connector.connection;

import java.io.Serializable;
import java.util.UUID;

import com.trace.comms_connector.Platform;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ConnectionCompositeKey implements Serializable {
    private UUID projectId;
    private String platformChannelId;
    private Platform platform;
}
