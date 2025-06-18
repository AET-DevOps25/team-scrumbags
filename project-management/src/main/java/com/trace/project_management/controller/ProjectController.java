package com.trace.project_management.controller;

import com.trace.project_management.entity.Project;
import com.trace.project_management.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Project project) {
        project = projectService.createProject(project);

        return ResponseEntity.ok(project);
    }

    @GetMapping
    public ResponseEntity<?> getProjects() {
        var projects = projectService.getProjectsOfUser();

        return ResponseEntity.ok(projects);
    }

    /**
     * Get a specific project by ID.
     * This endpoint is secured with the ProjectAccess annotation,
     * which automatically checks if the user has access to the project.
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
     * Get a specific project by ID.
     * This endpoint is secured with the ProjectAccess annotation,
     * which automatically checks if the user has access to the project.
     *
     * @param projectId The ID of the project to retrieve
     * @return ResponseEntity with the project details
     */
    @GetMapping("/{projectId}/users")
    public ResponseEntity<?> getUsersOfProject(@PathVariable UUID projectId) {
        var users = projectService.getUsersOfProject(projectId);

        return ResponseEntity.ok(users);
    }
}
