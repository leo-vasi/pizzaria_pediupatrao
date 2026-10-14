package com.umc.pediupatrao.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
//exigido pelo Spring Data para ler do mongodB
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AlteracaoCampo {

    private String campo;
    private String valorAnterior;
    private String valorNovo;
}