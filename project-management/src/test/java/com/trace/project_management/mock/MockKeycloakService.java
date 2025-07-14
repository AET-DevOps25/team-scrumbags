package com.trace.project_management.mock;

import com.trace.project_management.service.KeycloakService;
import com.trace.project_management.domain.User;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

public class MockKeycloakService extends KeycloakService {

    // maps
    private final Map<String, Set<String>> userRoles = new HashMap<String, Set<String>>();

    public MockKeycloakService() {
        super(null);
    }

    @Override
    @PostConstruct
    public void initKeycloak() {}

    /**
     * Create a project-specific role in Keycloak
     */
    @Override
    public String createRole(String roleName, String description) {
        // do nothing as we will just assign a string in the assignRoleToUser method
        return roleName;
    }

    /**
     * Assign a project role to a user
     */
    public void assignRoleToUser(String userId, String roleName) {
        var roles = this.userRoles.getOrDefault(userId, new HashSet<>());
        roles.add(roleName);
        this.userRoles.put(userId, roles);
    }

    public void removeRoleFromUser(String userId, String roleName) {
        var roles = this.userRoles.get(userId);
        if(roles == null){
            return;
        }

        roles.remove(roleName);
    }

    public Set<User> getUsersWithRole(String roleName) {
        return this.userRoles.entrySet().stream()
                .filter(e -> e.getValue().contains(roleName))
                .map(e -> new User(
                        e.getKey(),
                        e.getKey(),
                        e.getKey() + "@example.com" // Mock email
                )).collect(Collectors.toSet());
    }

    public Set<User> getAllUsers() {
        return this.userRoles.entrySet().stream()
                .map(e -> new User(
                        e.getKey(),
                        e.getKey(),
                        e.getKey() + "@example.com" // Mock email
                )).collect(Collectors.toSet());
    }


}
