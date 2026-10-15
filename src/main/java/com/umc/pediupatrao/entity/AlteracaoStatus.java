package com.umc.pediupatrao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AlteracaoStatus {

    private StatusPedido status;
    private LocalDateTime alteradoEm;
    private String alteradoPor;
}