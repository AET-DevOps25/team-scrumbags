package com.trace.project_management.service;

import com.trace.project_management.entity.Project;
import com.trace.project_management.repository.ProjectRepository;
import com.trace.project_management.security.SecurityService;
import com.trace.project_management.security.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    private final KeycloakService keycloakService;
    private final UserContext userContext;
    private final SecurityService securityService;

    public ProjectService(ProjectRepository projectRepository, KeycloakService keycloakService, UserContext userContext, SecurityService securityService) {
        this.projectRepository = projectRepository;
        this.keycloakService = keycloakService;
        this.userContext = userContext;
        this.securityService = securityService;
    }

    /**
     * Create a new project and assign creator role
     */
    @Transactional
    public Project createProject(Project project) {
        // Create project entity
        project = projectRepository.save(project);

        // create a role in Keycloak for the project
        var roleName = keycloakService.createRole(Project.projectIdToRoleName(project.getId()), "Role for project " + project.getId());

        // assign role to user
        keycloakService.assignRoleToUser(userContext.getUserId(), roleName);

        return project;
    }

    public List<Project> getProjectsOfUser() {
        // This method should return all projects associated with the user
        if (securityService.isAdmin()) {
            return projectRepository.findAll();
        }

        List<UUID> userProjectIds = securityService.getUserProjectIds();
        return projectRepository.findAllById(userProjectIds);
    }

    public Project getProjectById(UUID projectId) {
        // Check if the user has access to the project
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        return projectRepository.findById(projectId).orElse(null);
    }

    public Set<UUID> getUsersOfProject(UUID projectId) {
        // Check if the user has access to the project
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        // Fetch users associated with the project
        return keycloakService.getUsersWithRole("project-" + projectId.toString());
    }

    public Set<UUID> assignUsersToProject(UUID projectId, Set<UUID> userIds) {
        // Check if the user has access to the project
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        // Assign users to the project in Keycloak
        for (UUID userId : userIds) {
            keycloakService.assignRoleToUser(userId.toString(), Project.projectIdToRoleName(projectId));
        }

        return userIds;
    }

    public Set<UUID> removeUsersFromProject(UUID projectId, Set<UUID> userIds) {
        // Check if the user has access to the project
        if (!securityService.hasProjectAccess(projectId)) {
            throw new SecurityException("Access denied to project with ID: " + projectId);
        }

        // Remove users from the project in Keycloak
        for (UUID userId : userIds) {
            keycloakService.removeRoleFromUser(userId.toString(), Project.projectIdToRoleName(projectId));
        }

        return userIds;
    }
}
