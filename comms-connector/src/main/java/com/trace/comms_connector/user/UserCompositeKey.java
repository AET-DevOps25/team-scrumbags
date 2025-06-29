package com.trace.comms_connector.user;

import java.io.Serializable;
import java.util.UUID;

import com.trace.comms_connector.Platform;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class UserCompositeKey implements Serializable {
    private UUID projectId;
    private String platformUserId;
    private Platform platform;
}
