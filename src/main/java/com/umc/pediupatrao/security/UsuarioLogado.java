package com.umc.pediupatrao.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

// Ferramenta para ler quem está logado a partir do SecurityContext sem precisar injetar Authentication em cada serviço/controller.
public final class UsuarioLogado {

    private static final String ROLE_PREFIX = "ROLE_";

    private UsuarioLogado() {
    }

    // Retorna vazio quando não há ninguém autenticado de verdade
    public static Optional<String> username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    // Para operações internas que não têm usuário logado
    public static String usernameOrSystem() {
        return username().orElse("system");
    }

    // Devolve o nome do perfil sem o prefixo "ROLE_"
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