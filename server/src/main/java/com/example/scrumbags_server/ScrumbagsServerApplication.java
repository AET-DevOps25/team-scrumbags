package com.example.scrumbags_server;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class ScrumbagsServerApplication {

    public static void main(String[] args) {
        // Load .env file from root directory
        Dotenv dotenv = Dotenv.configure()
                .directory(".")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        // Set as system properties so Spring can use them
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(ScrumbagsServerApplication.class, args);
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}