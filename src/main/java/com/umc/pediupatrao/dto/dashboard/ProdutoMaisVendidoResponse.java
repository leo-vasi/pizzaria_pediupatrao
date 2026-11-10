package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

// Uma posição do ranking de produtos: unidades vendidas e receita bruta (soma dos subtotais dos itens).
public record ProdutoMaisVendidoResponse(
        int posicao,
        String produtoId,
        String nomeProduto,
        String tipoProduto,
        long quantidade,
        BigDecimal receita
) {
}
