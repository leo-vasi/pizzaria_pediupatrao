package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;

// Filtros aceitos por todos os endpoints do dashboard, já validados e com os valores padrão aplicados.
// O período é fechado nas duas pontas: de dataInicio 00:00 até dataFim 23:59:59.
public record FiltroDashboard(LocalDate dataInicio, LocalDate dataFim, String tipoProduto) {

    public boolean temTipoProduto() {
        return tipoProduto != null;
    }

    // Quantidade de dias do período, contando o primeiro e o último
    public long dias() {
        return dataFim.toEpochDay() - dataInicio.toEpochDay() + 1;
    }

    // Período imediatamente anterior, com a mesma duração, usado para calcular a variação dos indicadores
    public FiltroDashboard periodoAnterior() {
        LocalDate fimAnterior = dataInicio.minusDays(1);
        return new FiltroDashboard(fimAnterior.minusDays(dias() - 1), fimAnterior, tipoProduto);
    }
}
