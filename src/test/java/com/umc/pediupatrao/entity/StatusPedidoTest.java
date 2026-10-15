package com.umc.pediupatrao.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusPedidoTest {

    @Test
    void segueOFluxoObrigatorio() {
        assertTrue(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.EM_PREPARACAO));
        assertTrue(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.PRONTO));
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.SAIU_PARA_ENTREGA));
        assertTrue(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RETIRADO));
        assertTrue(StatusPedido.SAIU_PARA_ENTREGA.podeAvancarPara(StatusPedido.FINALIZADO));
        assertTrue(StatusPedido.RETIRADO.podeAvancarPara(StatusPedido.FINALIZADO));
    }

    @Test
    void recusaPularEtapas() {
        assertFalse(StatusPedido.EM_PREPARACAO.podeAvancarPara(StatusPedido.FINALIZADO));
        assertFalse(StatusPedido.RECEBIDO.podeAvancarPara(StatusPedido.PRONTO));
        assertFalse(StatusPedido.PRONTO.podeAvancarPara(StatusPedido.RECEBIDO));
    }

    @Test
    void estadosFinaisNaoTemTransicoes() {
        assertTrue(StatusPedido.CANCELADO.proximosStatus().isEmpty());
        assertTrue(StatusPedido.FINALIZADO.proximosStatus().isEmpty());
    }

    @Test
    void cancelamentoEDescontoSomenteAntesDaSaida() {
        assertTrue(StatusPedido.RECEBIDO.permiteCancelamentoOuDesconto());
        assertTrue(StatusPedido.EM_PREPARACAO.permiteCancelamentoOuDesconto());
        assertTrue(StatusPedido.PRONTO.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.SAIU_PARA_ENTREGA.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.RETIRADO.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.FINALIZADO.permiteCancelamentoOuDesconto());
        assertFalse(StatusPedido.CANCELADO.permiteCancelamentoOuDesconto());
    }
}
