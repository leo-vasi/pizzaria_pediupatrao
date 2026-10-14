package com.umc.pediupatrao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private int quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal subtotal;
}