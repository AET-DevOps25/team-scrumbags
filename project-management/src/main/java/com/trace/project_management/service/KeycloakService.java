package com.trace.project_management.service;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class KeycloakService {

    private final Environment environment;

    private Keycloak keycloak;
    private RealmResource realmResource;

    public KeycloakService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void initKeycloak() {
        String keycloakUrl = environment.getProperty("keycloak.auth-server-url");
        String realm = environment.getProperty("keycloak.realm");
        String adminUsername = environment.getProperty("keycloak.admin.username");
        String adminPassword = environment.getProperty("keycloak.admin.password");

        // Initialize Keycloak admin client
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakUrl)
                .realm("master")
                .username(adminUsername)
                .password(adminPassword)
                .clientId("admin-cli")
                .build();

        this.realmResource = keycloak.realm(realm);
    }

    /**
     * Create a project-specific role in Keycloak
     */
    public String createProjectRole(UUID projectId) {
        String roleName = "project-" + projectId.toString();

        try {
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            role.setDescription("Role for project " + projectId);

            realmResource.roles().create(role);
            return roleName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create project role in Keycloak", e);
        }
    }

    /**
     * Assign a project role to a user
     */
    public void assignRoleToUser(String userId, String roleName) {
        try {
            UserResource userResource = realmResource.users().get(userId);
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(role));
        } catch (Exception e) {
            throw new RuntimeException("Failed to assign role to user in Keycloak", e);
        }
    }

    public List<UUID> getUsersWithRole(String roleName) {
        try {
            return realmResource.roles()
                    .get(roleName)
                    .getUserMembers()
                    .stream()
                    .map(user -> UUID.fromString(user.getId()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch users with role in Keycloak", e);
        }
    }
}
