package com.umc.pediupatrao.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Pedido. Todos os valores são calculados pelo PedidoService no servidor:
// as requisições HTTP mandam apenas cliente, produtos e quantidades
// (veja CriarPedidoRequest).
@Document(collection = "pedidos")
@Getter
@Setter
public class Pedido {

    @Id
    private String id;
    private String clienteId;
    private String nomeCliente;

    private StatusPedido status;

    // Itens e valores
    private List<ItemPedido> itens = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal percentualDesconto = BigDecimal.ZERO;
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    // Entrada (RECEBIDO)
    private LocalDateTime criadoEm;
    private String criadoPor;

    // Saída (SAIU_PARA_ENTREGA ou RETIRADO)
    private LocalDateTime saidaEm;
    private String saidaPor;

    // Conclusão (FINALIZADO)
    private LocalDateTime finalizadoEm;
    private String finalizadoPor;

    // Desconto (só GERENTE)
    private LocalDateTime descontoAplicadoEm;
    private String descontoAplicadoPor;

    // Cancelamento (só GERENTE)
    private LocalDateTime canceladoEm;
    private String canceladoPor;
    private String motivoCancelamento;

    private List<AlteracaoStatus> historicoStatus = new ArrayList<>();
}