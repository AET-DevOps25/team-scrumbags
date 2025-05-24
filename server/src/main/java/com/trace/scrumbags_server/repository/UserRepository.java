package com.example.scrumbags_server.repository;

import com.example.scrumbags_server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {}
