package com.trace.project_management.controller;

import com.trace.project_management.entity.User;
import com.trace.project_management.repository.UserRepository;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Retrieves a list of all users.
     *
     * @return a list of all {@link User} entities in the database
     */
    @GetMapping
    public ResponseEntity<List<User>> allUsers() {
        var users = userRepository.findAll();

        return ResponseEntity.ok(users);
    }

    /**
     * Creates a new user and saves it to the database.
     *
     * @param user the {@link User} entity to be created
     * @return the created {@link User} entity
     */
    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        user = userRepository.save(user);

        return ResponseEntity.ok(user);
    }
}
