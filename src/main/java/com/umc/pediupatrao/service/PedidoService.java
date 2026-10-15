package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.entity.OperacaoAuditoria;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.AlteracaoStatus;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;


@Service
public class PedidoService {

    public static final String ENTIDADE_AUDITORIA = "Pedido";

    public static final BigDecimal PERCENTUAL_MAXIMO_DESCONTO = new BigDecimal("20");
    public static final int QUANTIDADE_MAXIMA_POR_ITEM = 100;
    private static final int TAMANHO_MINIMO_JUSTIFICATIVA = 5;
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter FORMATO_AUDITORIA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    // ===================== RF02 criação =====================

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido criarPedido(CriarPedidoRequest request) {
        if (request == null || request.getClienteId() == null || request.getClienteId().isBlank()) {
            throw new IllegalArgumentException("Informe o cliente do pedido.");
        }
        Cliente cliente = clienteRepository.findById(request.getClienteId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado."));

        List<CriarPedidoRequest.Item> itensSolicitados = request.getItens() == null ? List.of() : request.getItens().stream()
                .filter(item -> item != null && item.getQuantidade() != 0)
                .toList();
        if (itensSolicitados.isEmpty()) {
            throw new IllegalArgumentException("O pedido deve ter pelo menos um item.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (CriarPedidoRequest.Item solicitado : itensSolicitados) {
            itens.add(construirItem(solicitado));
        }

        String usuario = UsuarioLogado.usernameOrSystem();
        LocalDateTime agora = agora();

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setNomeCliente(cliente.getNome());
        pedido.setItens(itens);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setCriadoEm(agora);
        pedido.setCriadoPor(usuario);
        pedido.getHistoricoStatus().add(new AlteracaoStatus(StatusPedido.RECEBIDO, agora, usuario));
        recalcular(pedido);

        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        alteracoes.add(new AlteracaoCampo("cliente", null, cliente.getNome()));
        alteracoes.add(new AlteracaoCampo("itens", null, descreverItens(itens)));
        alteracoes.add(new AlteracaoCampo("valorTotal", null, moeda(salvo.getTotal())));
        alteracoes.add(new AlteracaoCampo("status", null, StatusPedido.RECEBIDO.name()));
        alteracoes.add(new AlteracaoCampo("dataHoraEntrada", null, agora.format(FORMATO_AUDITORIA)));
        alteracoes.add(new AlteracaoCampo("responsavelEntrada", null, usuario));
        auditoriaService.registrar(OperacaoAuditoria.PEDIDO_CRIADO, ENTIDADE_AUDITORIA, salvo.getId(), alteracoes);

        return salvo;
    }

    private ItemPedido construirItem(CriarPedidoRequest.Item solicitado) {
        if (solicitado.getProdutoId() == null || solicitado.getProdutoId().isBlank()) {
            throw new IllegalArgumentException("Item sem produto informado.");
        }
        if (solicitado.getQuantidade() < 1 || solicitado.getQuantidade() > QUANTIDADE_MAXIMA_POR_ITEM) {
            throw new IllegalArgumentException("A quantidade de cada item deve estar entre 1 e " + QUANTIDADE_MAXIMA_POR_ITEM + ".");
        }
        Produto produto = produtoRepository.findById(solicitado.getProdutoId())
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + solicitado.getProdutoId()));
        if (Boolean.FALSE.equals(produto.getAtivo())) {
            throw new IllegalArgumentException("Produto inativo: " + produto.getNome());
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new IllegalArgumentException("Produto sem preço válido: " + produto.getNome());
        }

        // O preço vem SEMPRE do banco, nunca da requisição
        BigDecimal precoUnitario = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(solicitado.getQuantidade())).setScale(2, RoundingMode.HALF_UP);
        return new ItemPedido(produto.getId(), produto.getNome(), solicitado.getQuantidade(), precoUnitario, subtotal);
    }

    // Recalcula subtotal, valor do desconto e total a partir dos itens e do desconto autorizado
    static void recalcular(Pedido pedido) {
        BigDecimal subtotal = pedido.getItens().stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentual = pedido.getPercentualDesconto() == null ? BigDecimal.ZERO : pedido.getPercentualDesconto();
        BigDecimal valorDesconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);

        pedido.setSubtotal(subtotal);
        pedido.setPercentualDesconto(percentual);
        pedido.setValorDesconto(valorDesconto);
        pedido.setTotal(subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP));
    }

    // ===================== consultas =====================

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll(Sort.by(Sort.Direction.DESC, "criadoEm"));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido não encontrado."));
    }

    // ===================== RF03 fluxo de status =====================

    @PreAuthorize("hasAnyRole('GERENTE', 'ATENDENTE')")
    public Pedido atualizarStatus(String id, StatusPedido novoStatus) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido não encontrado."));
        StatusPedido atual = pedido.getStatus();

        if (novoStatus == null) {
            throw new IllegalArgumentException("Informe o novo status.");
        }
        if (novoStatus == StatusPedido.CANCELADO) {
            throw new IllegalArgumentException("Para cancelar, use a operação de cancelamento (exclusiva do GERENTE).");
        }
        if (atual == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido cancelado não pode ter o status alterado.");
        }
        if (atual == null || !atual.podeAvancarPara(novoStatus)) {
            throw new IllegalStateException("Transição inválida: " + rotulo(atual) + " -> " + novoStatus.getRotulo()
                    + ". Próximos status permitidos: " + rotulosPermitidos(atual) + ".");
        }

        String usuario = UsuarioLogado.usernameOrSystem();
        LocalDateTime agora = agora();

        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        alteracoes.add(new AlteracaoCampo("status", atual.name(), novoStatus.name()));

        if (novoStatus.isSaida()) {
            // data/hora e responsável são obrigatórios
            pedido.setSaidaEm(agora);
            pedido.setSaidaPor(usuario);
            alteracoes.add(new AlteracaoCampo("dataHoraSaida", null, agora.format(FORMATO_AUDITORIA)));
            alteracoes.add(new AlteracaoCampo("responsavelSaida", null, usuario));
        }
        if (novoStatus == StatusPedido.FINALIZADO) {
            pedido.setFinalizadoEm(agora);
            pedido.setFinalizadoPor(usuario);
        }

        pedido.setStatus(novoStatus);
        pedido.getHistoricoStatus().add(new AlteracaoStatus(novoStatus, agora, usuario));
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(OperacaoAuditoria.PEDIDO_STATUS_ALTERADO, ENTIDADE_AUDITORIA, salvo.getId(), alteracoes);
        return salvo;
    }

    // ===================== RF04 cancelamento e desconto (só pra gerente) =====================

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido não encontrado."));

        String motivo = justificativa == null ? "" : justificativa.trim();
        if (motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new IllegalArgumentException("Informe uma justificativa para o cancelamento (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }
        StatusPedido atual = pedido.getStatus();
        if (atual == StatusPedido.CANCELADO) {
            throw new IllegalStateException("O pedido já está cancelado.");
        }
        if (atual == null || !atual.permiteCancelamentoOuDesconto()) {
            throw new IllegalStateException("Não é possível cancelar: o pedido já saiu para entrega ou foi retirado ("
                    + rotulo(atual) + ").");
        }

        String usuario = UsuarioLogado.usernameOrSystem();
        LocalDateTime agora = agora();

        // Dados históricos (itens, valores, entrada) são mantidos
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setCanceladoEm(agora);
        pedido.setCanceladoPor(usuario);
        pedido.setMotivoCancelamento(motivo);
        pedido.getHistoricoStatus().add(new AlteracaoStatus(StatusPedido.CANCELADO, agora, usuario));
        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> alteracoes = List.of(
                new AlteracaoCampo("status", atual.name(), StatusPedido.CANCELADO.name()),
                new AlteracaoCampo("dataHoraCancelamento", null, agora.format(FORMATO_AUDITORIA)),
                new AlteracaoCampo("responsavelCancelamento", null, usuario));
        auditoriaService.registrar(OperacaoAuditoria.PEDIDO_CANCELADO, ENTIDADE_AUDITORIA, salvo.getId(), alteracoes, motivo);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido não encontrado."));

        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Informe um percentual de desconto maior que zero.");
        }
        if (percentual.compareTo(PERCENTUAL_MAXIMO_DESCONTO) > 0) {
            throw new IllegalArgumentException("O desconto máximo permitido é de " + PERCENTUAL_MAXIMO_DESCONTO + "%.");
        }
        StatusPedido atual = pedido.getStatus();
        if (atual == null || !atual.permiteCancelamentoOuDesconto()) {
            throw new IllegalStateException("Não é possível aplicar desconto no status atual (" + rotulo(atual)
                    + "): somente antes da saída para entrega ou retirada.");
        }

        BigDecimal percentualAnterior = pedido.getPercentualDesconto();
        BigDecimal totalAnterior = pedido.getTotal();

        // O novo percentual substitui o anterior (não é cumulativo), então nunca ultrapassa 20 porcento
        pedido.setPercentualDesconto(percentual.setScale(2, RoundingMode.HALF_UP));
        recalcular(pedido);
        pedido.setDescontoAplicadoEm(agora());
        pedido.setDescontoAplicadoPor(UsuarioLogado.usernameOrSystem());
        Pedido salvo = pedidoRepository.save(pedido);

        List<AlteracaoCampo> alteracoes = List.of(
                new AlteracaoCampo("percentualDesconto", percentual(percentualAnterior), percentual(salvo.getPercentualDesconto())),
                new AlteracaoCampo("valorDesconto", null, moeda(salvo.getValorDesconto())),
                new AlteracaoCampo("valorTotal", moeda(totalAnterior), moeda(salvo.getTotal())));
        auditoriaService.registrar(OperacaoAuditoria.PEDIDO_DESCONTO_APLICADO, ENTIDADE_AUDITORIA, salvo.getId(), alteracoes);
        return salvo;
    }

    // ===================== auxiliares =====================

    private static LocalDateTime agora() {
        return LocalDateTime.now(AuditoriaService.FUSO_HORARIO);
    }

    private static String rotulo(StatusPedido status) {
        return status == null ? "sem status" : status.getRotulo();
    }

    private static String rotulosPermitidos(StatusPedido status) {
        if (status == null || status.proximosStatus().isEmpty()) {
            return "nenhum";
        }
        return status.proximosStatus().stream().map(StatusPedido::getRotulo).collect(Collectors.joining(", "));
    }

    private static String descreverItens(List<ItemPedido> itens) {
        return itens.stream()
                .map(i -> i.getQuantidade() + "x " + i.getNomeProduto() + " (" + moeda(i.getPrecoUnitario()) + ")")
                .collect(Collectors.joining("; "));
    }

    private static String moeda(BigDecimal valor) {
        return valor == null ? null : "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", valor);
    }

    private static String percentual(BigDecimal valor) {
        return valor == null ? null : valor.stripTrailingZeros().toPlainString() + "%";
    }
}