package com.trace.scrumbags_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
// @SpringBootTest
// @Testcontainers
// class ScrumbagsServerApplicationTests {
//     @Container
//     static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:latest")
//         .withDatabaseName("testdb")
//         .withUsername("testuser")
//         .withPassword("testpass");
//     @DynamicPropertySource
//     static void overrideProps(DynamicPropertyRegistry registry) {
//         registry.add("spring.datasource.url", mysql::getJdbcUrl);
//         registry.add("spring.datasource.username", mysql::getUsername);
//         registry.add("spring.datasource.password", mysql::getPassword);
//         registry.add(
//             "spring.datasource.driver-class-name",
//             mysql::getDriverClassName
//         );
//     }
//     @Test
//     void contextLoads() {
//         // your test logic
//     }
// }
