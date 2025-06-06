package com.trace.scrumbags_server.repository;

import com.trace.scrumbags_server.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {}
