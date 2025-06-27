package com.trace.project_management;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class ProjectManagementApplication {

    @Autowired
    private Environment env;

    public static void main(String[] args) {
        SpringApplication.run(ProjectManagementApplication.class, args);
    }

    @GetMapping("/public/hello")
    public String hello() {
        return "Hello, World!";
    }

    @GetMapping("/public/props")
    public ResponseEntity<String> printAllProperties() {
        return ResponseEntity.ok(env.toString());
    }
}