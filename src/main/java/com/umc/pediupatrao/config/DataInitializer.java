package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Role;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder) {
        return args -> {
            seedUser(repo, encoder, "admin", "teste123", Role.ADMIN);
            seedUser(repo, encoder, "gerente01", "gerente123", Role.GERENTE);
            seedUser(repo, encoder, "atendente01", "atendente123", Role.ATENDENTE);
        };
    }

    //Cria o usuário se não existir. Se já existir mas estiver sem perfil, atribui o perfil
    private void seedUser(UsuarioRepository repo, PasswordEncoder encoder, String username, String rawPassword, Role role) {
        repo.findByUsername(username).ifPresentOrElse(existing -> {

            if (existing.getRole() == null) {
                existing.setRole(role);
                repo.save(existing);
                log.info("Perfil {} atribuído ao usuário existente '{}'.", role, username);
            }

        }, () -> {
            Usuario user = new Usuario();
            user.setUsername(username);
            user.setPassword(encoder.encode(rawPassword));
            user.setRole(role);
            repo.save(user);
            log.info("Usuário '{}' criado com perfil {}.", username, role);
        });
    }
}