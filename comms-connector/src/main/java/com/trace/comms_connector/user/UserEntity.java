package com.trace.comms_connector.user;

import java.util.UUID;

import com.trace.comms_connector.Platform;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Users")
@IdClass(UserCompositeKey.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserEntity {
    @Id
    private UUID projectId;
    
    @Id
    private String platformUserId;

    @Enumerated(EnumType.STRING)
    @Id
    private Platform platform;

    private UUID userId;
}
