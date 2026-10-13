package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente novoCliente(Cliente cliente) {
        return clienteRepository.save(cliente);
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        clienteRepository.deleteById(id);
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        // Se o cliente não tem ID (novo cliente), salva como novo
        if (cliente.getId() == null) {
            return clienteRepository.save(cliente);
        } // Se já tem ID (cliente existente), atualiza
        else {
            // Verifica se o cliente existe antes de atualizar
            if (clienteRepository.existsById(cliente.getId())) {
                return clienteRepository.save(cliente);  // Atualiza o cliente existente
            } else {
                throw new IllegalArgumentException("Cliente não encontrado para atualização.");
            }
        }
    }

}
