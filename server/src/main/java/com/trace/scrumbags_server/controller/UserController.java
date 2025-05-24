package com.example.scrumbags_server.controller;

import com.example.scrumbags_server.entity.User;
import com.example.scrumbags_server.repository.UserRepository;
import java.util.List;
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
    public List<User> allUsers() {
        return userRepository.findAll();
    }

    /**
     * Creates a new user and saves it to the database.
     *
     * @param user the {@link User} entity to be created
     * @return the created {@link User} entity
     */
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }
}
