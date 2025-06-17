package com.trace.project_management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfiguration = new org.springframework.web.cors.CorsConfiguration();
                    corsConfiguration.setAllowedOriginPatterns(java.util.List.of("*"));
                    corsConfiguration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    corsConfiguration.setAllowedHeaders(java.util.List.of("*"));
                    corsConfiguration.setAllowCredentials(true);
                    return corsConfiguration;
                }))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(resourceServer -> {
                    resourceServer.jwt(jwtConfigurer -> {
                        jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter());
                    });
                });

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    public Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter() {
        // Instead of returning a lambda directly, create an explicit class
        return new Converter<Jwt, Collection<GrantedAuthority>>() {
            private final JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();

            @Override
            public Collection<GrantedAuthority> convert(Jwt jwt) {
                // Get standard authorities from JWT scope/roles
                Collection<GrantedAuthority> authorities = delegate.convert(jwt);

                // Extract custom claims from Keycloak JWT
                Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                if (realmAccess != null && realmAccess.containsKey("roles")) {
                    @SuppressWarnings("unchecked")
                    Collection<String> roles = (Collection<String>) realmAccess.get("roles");

                    // Add all roles with proper prefix
                    Collection<GrantedAuthority> keycloakAuthorities = roles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());

                    // Combine all authorities
                    authorities.addAll(keycloakAuthorities);
                }

                return authorities;
            }
        };
    }

    @Bean
    public GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
        return authorities -> authorities;
    }

    /**
     * Utility method to check if a user has access to a specific project
     *
     * @param authorities the user's granted authorities
     * @param projectId   the project ID to check access for
     * @return true if the user has access to the project, false otherwise
     */
    public static boolean hasProjectAccess(Collection<? extends GrantedAuthority> authorities, UUID projectId) {
        if (projectId == null) {
            return false;
        }

        // Check for project-specific role
        String projectRole = "ROLE_project-" + projectId.toString();

        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority ->
                        authority.equals(projectRole) || authority.equals("ROLE_admin"));
    }

    public static List<UUID> getUserProjectIds(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_project-"))
                .map(authority -> UUID.fromString(authority.substring("ROLE_project-".length())))
                .collect(Collectors.toList());
    }

    public static boolean hasAdminAccess(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_admin"));
    }
}
