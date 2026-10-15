package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.OperacaoAuditoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ClienteService {

    // Usado como entityType nos registros de auditoria deste serviço
    public static final String ENTIDADE_AUDITORIA = "Cliente";

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente novoCliente(Cliente cliente) {
        cliente.setId(null);
        return salvar(cliente);
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cliente não encontrado."));
        clienteRepository.deleteById(id);
        // Guarda os últimos dados conhecidos do cliente excluído no log de auditoria
        auditoriaService.registrar(OperacaoAuditoria.CLIENTE_EXCLUIDO, ENTIDADE_AUDITORIA, id,
                AuditoriaService.comparar(snapshot(existente), Map.of()));
    }

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Salva um novo cliente ou atualiza um existente
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        if (cliente.getNome() == null || cliente.getNome().isBlank()) {
            throw new IllegalArgumentException("Informe o nome do cliente.");
        }

        // Sem id = cliente novo
        if (cliente.getId() == null || cliente.getId().isBlank()) {
            cliente.setId(null);
            Cliente salvo = clienteRepository.save(cliente);
            auditoriaService.registrar(OperacaoAuditoria.CLIENTE_CRIADO, ENTIDADE_AUDITORIA, salvo.getId(),
                    AuditoriaService.comparar(Map.of(), snapshot(salvo)));
            return salvo;
        }

        // Com id = atualização; registra só os campos que mudaram
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new NoSuchElementException("Cliente não encontrado para atualização."));
        Map<String, String> antes = snapshot(anterior);

        Cliente salvo = clienteRepository.save(cliente);

        var alteracoes = AuditoriaService.comparar(antes, snapshot(salvo));
        if (!alteracoes.isEmpty()) {
            auditoriaService.registrar(OperacaoAuditoria.CLIENTE_ATUALIZADO, ENTIDADE_AUDITORIA, salvo.getId(), alteracoes);
        }
        return salvo;
    }

    // Campos auditados de um cliente: nome, telefone e endereço completo
    private static Map<String, String> snapshot(Cliente c) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("nome", c.getNome());
        data.put("telefone", c.getTelefone());
        data.put("cep", c.getCep());
        data.put("logradouro", c.getLogradouro());
        data.put("numero", c.getNumero());
        data.put("complemento", c.getComplemento());
        data.put("bairro", c.getBairro());
        data.put("cidade", c.getCidade());
        data.put("estado", c.getEstado());
        data.put("endereco", c.getEndereco());
        return data;
    }
}