package com.umc.pediupatrao.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Helper to read the authenticated user from the security context.
 * Used by the services to enforce business rules and to fill audit/responsibility fields.
 */
public final class CurrentUser {

    private static final String ROLE_PREFIX = "ROLE_";

    private CurrentUser() {
    }

    public static Optional<String> username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    /** Username of the authenticated user, or "system" for internal operations (e.g. startup seeding). */
    public static String usernameOrSystem() {
        return username().orElse("system");
    }

    /** Role name without the "ROLE_" prefix (e.g. "GERENTE"), or "SYSTEM" when there is no authenticated user. */
    public static String roleOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || username().isEmpty()) {
            return "SYSTEM";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElse("UNKNOWN");
    }
}
