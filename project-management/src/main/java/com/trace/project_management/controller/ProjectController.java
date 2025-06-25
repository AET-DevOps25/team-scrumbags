package com.trace.project_management.controller;

import com.trace.project_management.entity.Project;
import com.trace.project_management.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Create a new project and assign the user to the project.
     *
     * @param project The project details to create
     * @return ResponseEntity with the created project
     */
    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Project project) {
        project = projectService.createProject(project);

        return ResponseEntity.ok(project);
    }

    /**
     * Get all projects associated with the user.
     *
     * @return ResponseEntity with a list of projects
     */
    @GetMapping
    public ResponseEntity<?> getProjects() {
        var projects = projectService.getProjectsOfUser();

        return ResponseEntity.ok(projects);
    }

    /**
     * Get a specific project by ID.
     *
     * @param projectId The ID of the project to retrieve
     * @return ResponseEntity with the project details
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<?> getProjectById(@PathVariable UUID projectId) {
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(project);
    }

    /**
     * Get the users associated with a specific project.
     *
     * @param projectId The ID of the project
     * @return ResponseEntity with a list of users in the project
     */
    @GetMapping("/{projectId}/users")
    public ResponseEntity<?> getUsersOfProject(@PathVariable UUID projectId) {
        var users = projectService.getUsersOfProject(projectId);

        return ResponseEntity.ok(users);
    }

    @PostMapping("/{projectId}/users")
    public ResponseEntity<?> assignUsersToProject(@PathVariable UUID projectId, @RequestBody Set<UUID> userIds) {
        var users = projectService.assignUsersToProject(projectId, userIds);

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/{projectId}/users")
    public ResponseEntity<?> removeUsersFromProject(@PathVariable UUID projectId, @RequestBody Set<UUID> userIds) {
        var users = projectService.removeUsersFromProject(projectId, userIds);

        return ResponseEntity.ok(users);
    }
}
