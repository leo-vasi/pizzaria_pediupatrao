package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Role;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

// Tudo aqui é restrito a admin já na camada service, então a regra vale tanto para as telas thymeleaf quanto para a API REST
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public Usuario salvarUsuario(Usuario usuario) {
        // criação não pode sobrescrever usuário existente
        usuario.setId(null);

        requireText(usuario.getUsername(), "Informe o username.");
        requireText(usuario.getPassword(), "Informe a senha.");
        if (usuario.getRole() == null) {
            throw new IllegalArgumentException("Selecione um perfil válido (ADMIN, GERENTE ou ATENDENTE).");
        }
        usuario.setUsername(usuario.getUsername().trim());
        if (usuarioRepository.existsByUsername(usuario.getUsername())) {
            throw new IllegalArgumentException("Já existe um usuário com este username.");
        }

        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        return usuarioRepository.save(usuario);
    }

    public void deletarUsuario(String id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado."));

        if (isCurrentUser(usuario)) {
            throw new IllegalStateException("Você não pode excluir o próprio usuário.");
        }
        if (usuario.getRole() == Role.ADMIN && usuarioRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalStateException("Não é possível excluir o último administrador do sistema.");
        }
        usuarioRepository.deleteById(id);
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario atualizarUsuario(String id, Usuario usuarioAtualizado) {
        Usuario usuarioExistente = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuário não encontrado"));

        requireText(usuarioAtualizado.getUsername(), "Informe o username");
        String novoUsername = usuarioAtualizado.getUsername().trim();
        if (!novoUsername.equals(usuarioExistente.getUsername()) && usuarioRepository.existsByUsername(novoUsername)) {
            throw new IllegalArgumentException("Já existe um usuário com este username");
        }

        // evita ficar sem nenhum admin no sistema
        Role novoRole = usuarioAtualizado.getRole();
        if (novoRole == null) {
            throw new IllegalArgumentException("Selecione um perfil válido (ADMIN, GERENTE ou ATENDENTE)");
        }
        if (novoRole != usuarioExistente.getRole()) {
            if (isCurrentUser(usuarioExistente)) {
                throw new IllegalStateException("Você não pode alterar o próprio perfil.");
            }
            if (usuarioExistente.getRole() == Role.ADMIN && usuarioRepository.countByRole(Role.ADMIN) <= 1) {
                throw new IllegalStateException("Não é possível remover o perfil do último administrador");
            }
        }

        usuarioExistente.setUsername(novoUsername);
        usuarioExistente.setRole(novoRole);

        // Se senha em branco = mantém a atual
        if (usuarioAtualizado.getPassword() != null && !usuarioAtualizado.getPassword().isBlank()) {
            usuarioExistente.setPassword(passwordEncoder.encode(usuarioAtualizado.getPassword()));
        }

        return usuarioRepository.save(usuarioExistente);
    }

    private boolean isCurrentUser(Usuario usuario) {
        return UsuarioLogado.username()
                .map(username -> username.equals(usuario.getUsername()))
                .orElse(false);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(message);

        }
    }
}