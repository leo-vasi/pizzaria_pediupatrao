package com.umc.pediupatrao.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
public class CriarPedidoRequest {

    private String clienteId;
    private List<Item> itens = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private String produtoId;
        private int quantidade;

        public Item(String produtoId, int quantidade) {
            this.produtoId = produtoId;
            this.quantidade = quantidade;
        }
    }
}