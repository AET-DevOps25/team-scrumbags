package com.example.scrumbags_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

@SpringBootApplication
@RestController
public class ScrumbagsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScrumbagsServerApplication.class, args);
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}
