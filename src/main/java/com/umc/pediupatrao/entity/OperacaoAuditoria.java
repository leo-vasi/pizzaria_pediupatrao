package com.umc.pediupatrao.entity;

public enum OperacaoAuditoria {

    PEDIDO_CRIADO("INCLUSÃO DE PEDIDO"),
    PEDIDO_STATUS_ALTERADO("ALTERAÇÃO DE STATUS DO PEDIDO"),
    PEDIDO_DESCONTO_APLICADO("APLICAÇÃO DE DESCONTO"),
    PEDIDO_CANCELADO("CANCELAMENTO DE PEDIDO"),

    CLIENTE_CRIADO("INCLUSÃO DE CLIENTE"),
    CLIENTE_ATUALIZADO("ALTERAÇÃO DE CLIENTE"),
    CLIENTE_EXCLUIDO("EXCLUSÃO DE CLIENTE");

    private final String label;

    OperacaoAuditoria(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}