package com.trace.project_management.controller;

import com.trace.project_management.service.KeycloakService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final KeycloakService keycloakService;

    public UserController(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers(){
        var users = keycloakService.getAllUsers();

        return ResponseEntity.ok(users);
    }
}
