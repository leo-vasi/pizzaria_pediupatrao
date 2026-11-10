package com.umc.pediupatrao.dto.dashboard;

// Quantidade de pedidos realizados em cada hora do dia (0 a 23), somando todos os dias do período.
public record PedidosPorHoraResponse(int hora, long pedidos) {
}
