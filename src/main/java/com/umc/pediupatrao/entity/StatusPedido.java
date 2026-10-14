package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Set;




public enum StatusPedido {

    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String rotulo;

    StatusPedido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    // Próximos estados permitidos pelo fluxo normal cancelamento é tratado à parte
    public Set<StatusPedido> proximosStatus() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeAvancarPara(StatusPedido destino) {
        return destino != null && proximosStatus().contains(destino);
    }

    // Estados que representam o pedido saindo da loja a saída precisa registrar data/hora e usuário
    public boolean isSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    // Cancelamento e desconto só antes do pedido sair da loja
    public boolean permiteCancelamentoOuDesconto() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean isFinal() {
        return this == FINALIZADO || this == CANCELADO;
    }
}