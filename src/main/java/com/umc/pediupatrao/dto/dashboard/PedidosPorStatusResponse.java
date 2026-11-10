package com.umc.pediupatrao.dto.dashboard;

// Quantidade de pedidos em cada status, com o rótulo pronto para exibição.
public record PedidosPorStatusResponse(String status, String rotulo, long quantidade) {
}
