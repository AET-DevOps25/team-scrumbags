package com.trace.project_management.security;

import com.trace.project_management.config.SecurityConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SecurityService {

    public boolean hasProjectAccess(UUID projectId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return SecurityConfig.hasProjectAccess(auth.getAuthorities(), projectId);
    }

    public List<UUID> getUserProjectIds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return SecurityConfig.getUserProjectIds(auth.getAuthorities());
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return SecurityConfig.hasAdminAccess(auth.getAuthorities());
    }
}