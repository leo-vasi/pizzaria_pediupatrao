package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.OperacaoAuditoria;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Regras de negócio do PedidoService (RF02, RF03 e RF04) sem banco de dados, e os repositórios e a auditoria são simulados com mockito
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @BeforeEach
    void autenticarComoGerente() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("gerente01", "x", "ROLE_GERENTE"));
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }


    //RF02

    @Test
    void criarPedidoCalculaTotalComPrecosDoBanco() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria");
        Produto pizza = produto("p1", "Pizza Calabresa", 49.90);
        Produto refri = produto("p2", "Refrigerante 2L", 14.00);

        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(pizza));
        when(produtoRepository.findById("p2")).thenReturn(Optional.of(refri));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        CriarPedidoRequest request = new CriarPedidoRequest();
        request.setClienteId("c1");
        request.getItens().add(new CriarPedidoRequest.Item("p1", 2));
        request.getItens().add(new CriarPedidoRequest.Item("p2", 1));

        Pedido pedido = pedidoService.criarPedido(request);

        // 2 x 49,90 + 1 x 14,00 = 113,80
        assertEquals(new BigDecimal("113.80"), pedido.getTotal());
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("gerente01", pedido.getCriadoPor());
        assertNotNull(pedido.getCriadoEm());
        verify(auditoriaService).registrar(eq(OperacaoAuditoria.PEDIDO_CRIADO), eq("Pedido"), any(), anyList());
    }

    @Test
    void criarPedidoSemItensERecusado() {
        Cliente cliente = new Cliente();
        cliente.setId("c1");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));

        CriarPedidoRequest request = new CriarPedidoRequest();
        request.setClienteId("c1");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.criarPedido(request));
        verify(pedidoRepository, never()).save(any());
    }



    //RF03
    @Test
    void transicaoInvalidaERecusada() {
        Pedido pedido = pedido(StatusPedido.EM_PREPARACAO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("o1", StatusPedido.FINALIZADO));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void saidaRegistraDataHoraEResponsavel() {
        Pedido pedido = pedido(StatusPedido.PRONTO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido salvo = pedidoService.atualizarStatus("o1", StatusPedido.SAIU_PARA_ENTREGA);

        assertEquals(StatusPedido.SAIU_PARA_ENTREGA, salvo.getStatus());
        assertNotNull(salvo.getSaidaEm());
        assertEquals("gerente01", salvo.getSaidaPor());
        verify(auditoriaService).registrar(eq(OperacaoAuditoria.PEDIDO_STATUS_ALTERADO), eq("Pedido"), eq("o1"), anyList());
    }

    @Test
    void pedidoCanceladoNaoMudaDeStatus() {
        Pedido pedido = pedido(StatusPedido.CANCELADO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.atualizarStatus("o1", StatusPedido.EM_PREPARACAO));
    }



    //RF04
    @Test
    void descontoAcimaDeVintePorCentoERecusado() {
        Pedido pedido = pedido(StatusPedido.RECEBIDO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.aplicarDesconto("o1", new BigDecimal("25")));
    }

    @Test
    void descontoRecalculaTotal() {
        Pedido pedido = pedido(StatusPedido.RECEBIDO); // subtotal 100,00
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido salvo = pedidoService.aplicarDesconto("o1", new BigDecimal("15"));

        assertEquals(new BigDecimal("15.00"), salvo.getValorDesconto());
        assertEquals(new BigDecimal("85.00"), salvo.getTotal());
        verify(auditoriaService).registrar(eq(OperacaoAuditoria.PEDIDO_DESCONTO_APLICADO), eq("Pedido"), eq("o1"), anyList());
    }

    @Test
    void descontoDepoisDaSaidaERecusado() {
        Pedido pedido = pedido(StatusPedido.SAIU_PARA_ENTREGA);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class,
                () -> pedidoService.aplicarDesconto("o1", new BigDecimal("10")));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        Pedido pedido = pedido(StatusPedido.RECEBIDO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class, () -> pedidoService.cancelar("o1", "  "));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cancelamentoEAuditadoComJustificativa() {
        Pedido pedido = pedido(StatusPedido.EM_PREPARACAO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido salvo = pedidoService.cancelar("o1", "Cliente desistiu do pedido");

        assertEquals(StatusPedido.CANCELADO, salvo.getStatus());
        assertEquals("gerente01", salvo.getCanceladoPor());
        assertEquals(new BigDecimal("100.00"), salvo.getTotal()); // dados históricos preservados
        verify(auditoriaService).registrar(eq(OperacaoAuditoria.PEDIDO_CANCELADO), eq("Pedido"), eq("o1"),
                anyList(), eq("Cliente desistiu do pedido"));
    }

    @Test
    void cancelamentoDepoisDaSaidaERecusado() {
        Pedido pedido = pedido(StatusPedido.RETIRADO);
        when(pedidoRepository.findById("o1")).thenReturn(Optional.of(pedido));

        assertThrows(IllegalStateException.class, () -> pedidoService.cancelar("o1", "Motivo qualquer"));
    }

    //  metodos auxiliares

    private static Produto produto(String id, String nome, double preco) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        return p;
    }

    // Pedido com um item de 100,00 no status informado
    private static Pedido pedido(StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setId("o1");
        pedido.setStatus(status);
        pedido.setItens(List.of(new ItemPedido("p1", "Pizza", 1, new BigDecimal("100.00"), new BigDecimal("100.00"))));
        PedidoService.recalcular(pedido);
        return pedido;
    }
}
