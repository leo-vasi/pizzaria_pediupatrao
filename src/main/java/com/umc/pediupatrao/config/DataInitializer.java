package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Role;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
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
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo, PasswordEncoder encoder) {
        return args -> {
            seedUser(repo, encoder, "admin", "teste123", Role.ADMIN);
            seedUser(repo, encoder, "gerente01", "gerente123", Role.GERENTE);
            seedUser(repo, encoder, "atendente01", "atendente123", Role.ATENDENTE);
            seedProdutos(produtoRepo);
        };
    }

    // Cadastra um cardápio básico na primeira execução, para já ser possível registrar pedidos
    private void seedProdutos(ProdutoRepository produtoRepo) {
        if (produtoRepo.count() > 0) {
            return;
        }
        produtoRepo.save(novoProduto("Pizza Calabresa", "Pizza", 49.90));
        produtoRepo.save(novoProduto("Pizza Margherita", "Pizza", 52.00));
        produtoRepo.save(novoProduto("Pizza Quatro Queijos", "Pizza", 58.50));
        produtoRepo.save(novoProduto("Refrigerante 2L", "Bebida", 14.00));
        produtoRepo.save(novoProduto("Esfiha de Carne", "Lanche", 7.50));
        log.info("Cardápio inicial cadastrado.");
    }

    private Produto novoProduto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
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