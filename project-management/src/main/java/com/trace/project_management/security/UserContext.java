package com.trace.project_management.security;

import com.trace.project_management.domain.User;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserContext {
    private final User user;

    public UserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            this.user = new User(
                    jwt.getClaimAsString("sub"),
                    jwt.getClaimAsString("preferred_username"),
                    jwt.getClaimAsString("email")
            );
        } else {
            this.user = new User(null, null, null);
        }
    }

    public User getUser() {
        return user;
    }
}