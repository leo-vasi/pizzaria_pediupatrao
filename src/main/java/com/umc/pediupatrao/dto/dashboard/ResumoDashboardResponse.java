package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Indicadores gerais (big numbers) do período filtrado, com a variação em relação ao período anterior.
public record ResumoDashboardResponse(
        Periodo periodo,
        BigDecimal faturamento,
        long pedidosRealizados,
        BigDecimal ticketMedio,
        BigDecimal taxaCancelamento,
        BigDecimal tempoMedioSaidaMinutos,
        BigDecimal descontosConcedidos,
        long clientesAtendidos,
        long pedidosEmAndamento,
        Variacao variacao,
        LocalDateTime atualizadoEm
) {

    public record Periodo(LocalDate dataInicio, LocalDate dataFim, String tipoProduto) {
    }

    // Variação percentual em relação ao período anterior de mesma duração.
    // Fica null quando o período anterior não tem base de comparação (valor zero).
    public record Variacao(
            LocalDate dataInicioAnterior,
            LocalDate dataFimAnterior,
            BigDecimal faturamento,
            BigDecimal pedidosRealizados,
            BigDecimal ticketMedio
    ) {
    }
}
