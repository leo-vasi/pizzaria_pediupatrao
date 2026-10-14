package com.umc.pediupatrao.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class AplicarDescontoRequest {
    private BigDecimal percentual;
}