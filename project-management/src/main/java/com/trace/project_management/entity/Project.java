package com.trace.project_management.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    public Project() {
    }

    public Project(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static String ROLE_PREFIX = "project-";

    public static String projectIdToRoleName(UUID projectId) {
        return ROLE_PREFIX + projectId.toString();
    }

    public static UUID roleNameToProjectId(String roleName) {
        if (roleName.startsWith(ROLE_PREFIX)) {
            String idPart = roleName.substring(ROLE_PREFIX.length());
            return UUID.fromString(idPart);
        }

        return null; // or throw an exception if invalid
    }
}
