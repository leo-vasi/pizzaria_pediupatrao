package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

// Um ponto do gráfico de vendas: faturamento e quantidade de pedidos realizados no dia.
public record VendaPorDiaResponse(LocalDate data, BigDecimal faturamento, long pedidos) {
}
