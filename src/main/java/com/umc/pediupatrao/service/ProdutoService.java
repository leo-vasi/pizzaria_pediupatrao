package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @PreAuthorize("hasRole('GERENTE')")
    public Produto novoProduto(Produto produto) {
        return produtoRepository.save(produto);
    }

    @PreAuthorize("isAuthenticated()")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    // Método para excluir cliente
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        produtoRepository.deleteById(id);
    }


    // Método para salvar um novo cliente ou atualizar um cliente existente
    @PreAuthorize("hasRole('GERENTE')")
    public Produto salvar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome do produto.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new IllegalArgumentException("Informe um preço maior que zero.");
        }
        if (produto.getId() != null && produto.getId().isBlank()) {
            produto.setId(null);
        }
        // Se o cliente não tem ID (novo cliente), salva como novo
        if (produto.getId() == null) {
            return produtoRepository.save(produto);  // Cria um novo cliente
        } // Se já tem ID (cliente existente), atualiza
        else {
            // Verifica se o cliente existe antes de atualizar
            if (produtoRepository.existsById(produto.getId())) {
                return produtoRepository.save(produto);  // Atualiza o cliente existente
            } else {
                throw new IllegalArgumentException("Cliente não encontrado para atualização.");
            }
        }
    }
}
