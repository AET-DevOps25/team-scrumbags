package com.trace.project_management.controller;

import com.trace.project_management.entity.Project;
import com.trace.project_management.repository.ProjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * Create a new project.
     *
     * @param project the project to create
     * @return ResponseEntity with the created project
     */
    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Project project) {
        project = projectRepository.save(project);

        return ResponseEntity.ok(project);
    }

    /**
     * Get all projects.
     *
     * @return ResponseEntity with the list of projects
     */
    @GetMapping
    public ResponseEntity<?> getProjects() {
        var projects = projectRepository.findAll();

        return ResponseEntity.ok(projects);
    }
}
