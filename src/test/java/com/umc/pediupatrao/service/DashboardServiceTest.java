package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidosPorHoraResponse;
import com.umc.pediupatrao.dto.dashboard.PedidosPorStatusResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.VendaPorDiaResponse;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Regras de cálculo e validação do dashboard
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 6);

    @Mock
    private MongoTemplate mongoTemplate;

    private DashboardService dashboardService;

    @BeforeEach
    void criarServico() {
        Clock relogio = Clock.fixed(HOJE.atTime(12, 0).atZone(AuditoriaService.FUSO_HORARIO).toInstant(),
                AuditoriaService.FUSO_HORARIO);
        dashboardService = new DashboardService(mongoTemplate, relogio);
    }

    // ---------- RF05: filtros ----------

    @Test
    void filtroPadraoSaoOsUltimos30DiasAteHoje() {
        FiltroDashboard filtro = dashboardService.montarFiltro(null, null, null);

        assertEquals(LocalDate.of(2026, 10, 8), filtro.dataInicio());
        assertEquals(HOJE, filtro.dataFim());
        assertEquals(30, filtro.dias());
    }

    @Test
    void dataInicialPosteriorAFinalERecusada() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.montarFiltro(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), null));
    }

    @Test
    void periodoMaiorQue366DiasERecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.montarFiltro(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 11, 1), null));
    }

    @Test
    void tipoProdutoEmBrancoEIgnorado() {
        FiltroDashboard filtro = dashboardService.montarFiltro(null, null, "   ");

        assertNull(filtro.tipoProduto());
    }

    @Test
    void periodoAnteriorTemAMesmaDuracao() {
        FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7), null);

        FiltroDashboard anterior = filtro.periodoAnterior();

        assertEquals(LocalDate.of(2026, 10, 25), anterior.dataInicio());
        assertEquals(LocalDate.of(2026, 10, 31), anterior.dataFim());
    }


    @Test
    void indicadoresIgnoramPedidosCanceladosNoFaturamento() {
        LocalDateTime entrada = HOJE.atTime(19, 0);
        List<Pedido> pedidos = List.of(
                pedido(StatusPedido.FINALIZADO, "50.00", "0.00", "c1", entrada, entrada.plusMinutes(20)),
                pedido(StatusPedido.FINALIZADO, "30.00", "5.00", "c1", entrada, entrada.plusMinutes(40)),
                pedido(StatusPedido.EM_PREPARACAO, "20.00", "0.00", "c2", entrada, null),
                pedido(StatusPedido.CANCELADO, "40.00", "0.00", "c3", entrada, null));

        DashboardService.Indicadores indicadores = DashboardService.calcularIndicadores(pedidos);

        assertEquals(new BigDecimal("100.00"), indicadores.faturamento());
        assertEquals(3, indicadores.pedidosRealizados());
        assertEquals(new BigDecimal("33.33"), indicadores.ticketMedio());       // 100,00 / 3
        assertEquals(new BigDecimal("25.0"), indicadores.taxaCancelamento());   // 1 de 4
        assertEquals(new BigDecimal("30.0"), indicadores.tempoMedioSaidaMinutos()); // (20 + 40) / 2
        assertEquals(new BigDecimal("5.00"), indicadores.descontosConcedidos());
        assertEquals(2, indicadores.clientesAtendidos());                       // c1 e c2
    }

    @Test
    void indicadoresSemPedidosFicamZerados() {
        DashboardService.Indicadores indicadores = DashboardService.calcularIndicadores(List.of());

        assertEquals(new BigDecimal("0.00"), indicadores.faturamento());
        assertEquals(new BigDecimal("0.00"), indicadores.ticketMedio());
        assertEquals(new BigDecimal("0.0"), indicadores.taxaCancelamento());
        assertNull(indicadores.tempoMedioSaidaMinutos());
    }

    @Test
    void variacaoPercentualEmRelacaoAoPeriodoAnterior() {
        assertEquals(new BigDecimal("50.0"), DashboardService.variacaoPercentual(new BigDecimal("100"), new BigDecimal("150")));
        assertEquals(new BigDecimal("-25.0"), DashboardService.variacaoPercentual(new BigDecimal("200"), new BigDecimal("150")));
        assertNull(DashboardService.variacaoPercentual(BigDecimal.ZERO, new BigDecimal("150"))); // sem base
    }

    @Test
    void resumoComparaComOPeriodoAnteriorEContaPedidosEmAndamento() {
        LocalDateTime entrada = HOJE.atTime(12, 0);
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(
                List.of(pedido(StatusPedido.FINALIZADO, "100.00", "0.00", "c1", entrada, null)), // período atual
                List.of(pedido(StatusPedido.FINALIZADO, "50.00", "0.00", "c1", entrada, null))); // período anterior
        when(mongoTemplate.count(any(Query.class), eq(Pedido.class))).thenReturn(4L);

        ResumoDashboardResponse resumo = dashboardService.resumo(dashboardService.montarFiltro(null, null, null));

        assertEquals(new BigDecimal("100.00"), resumo.faturamento());
        assertEquals(new BigDecimal("100.0"), resumo.variacao().faturamento()); // de 50 para 100
        assertEquals(4, resumo.pedidosEmAndamento());
    }


    @Test
    void vendasPorDiaIncluemDiasSemVenda() {
        LocalDate inicio = LocalDate.of(2026, 11, 1);
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, "60.00", "0.00", "c1", inicio.plusDays(1).atTime(20, 0), null),
                pedido(StatusPedido.CANCELADO, "99.00", "0.00", "c1", inicio.plusDays(1).atTime(21, 0), null)));

        List<VendaPorDiaResponse> vendas = dashboardService.vendasPorDia(
                new FiltroDashboard(inicio, inicio.plusDays(2), null));

        assertEquals(3, vendas.size());
        assertEquals(0, vendas.get(0).pedidos());
        assertEquals(new BigDecimal("60.00"), vendas.get(1).faturamento()); // cancelado não entra
        assertEquals(1, vendas.get(1).pedidos());
    }

    @Test
    void pedidosPorHoraCobremAs24Horas() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.FINALIZADO, "10.00", "0.00", "c1", HOJE.atTime(19, 10), null),
                pedido(StatusPedido.FINALIZADO, "10.00", "0.00", "c1", HOJE.atTime(19, 50), null)));

        List<PedidosPorHoraResponse> porHora = dashboardService.pedidosPorHora(dashboardService.montarFiltro(null, null, null));

        assertEquals(24, porHora.size());
        assertEquals(2, porHora.get(19).pedidos());
        assertEquals(0, porHora.get(10).pedidos());
    }


    @Test
    void pedidosPorStatusListamTodosOsStatus() {
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(
                pedido(StatusPedido.CANCELADO, "10.00", "0.00", "c1", HOJE.atTime(12, 0), null)));

        List<PedidosPorStatusResponse> porStatus = dashboardService.pedidosPorStatus(dashboardService.montarFiltro(null, null, null));

        assertEquals(StatusPedido.values().length, porStatus.size());
        assertEquals(1, porStatus.stream().filter(s -> s.status().equals("CANCELADO")).findFirst().orElseThrow().quantidade());
        assertEquals(0, porStatus.stream().filter(s -> s.status().equals("RECEBIDO")).findFirst().orElseThrow().quantidade());
    }


    @Test
    void rankingOrdenaPorQuantidadeERespeitaOLimite() {
        Pedido pedido1 = pedido(StatusPedido.FINALIZADO, "0.00", "0.00", "c1", HOJE.atTime(12, 0), null);
        pedido1.setItens(List.of(
                item("p1", "Pizza Calabresa", 3, "49.90", "Pizza"),
                item("p2", "Refrigerante 2L", 1, "14.00", "Bebida")));
        Pedido pedido2 = pedido(StatusPedido.FINALIZADO, "0.00", "0.00", "c2", HOJE.atTime(13, 0), null);
        pedido2.setItens(List.of(item("p2", "Refrigerante 2L", 1, "14.00", "Bebida")));
        Pedido cancelado = pedido(StatusPedido.CANCELADO, "0.00", "0.00", "c3", HOJE.atTime(14, 0), null);
        cancelado.setItens(List.of(item("p2", "Refrigerante 2L", 10, "14.00", "Bebida")));
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(pedido1, pedido2, cancelado));

        List<ProdutoMaisVendidoResponse> ranking = dashboardService.produtosMaisVendidos(
                dashboardService.montarFiltro(null, null, null), 1);

        assertEquals(1, ranking.size());
        assertEquals("Pizza Calabresa", ranking.get(0).nomeProduto()); // 3 unidades contra 2 (cancelado não conta)
        assertEquals(new BigDecimal("149.70"), ranking.get(0).receita());
    }

    @Test
    void rankingFiltradoPorTipoMostraSoProdutosDoTipo() {
        Pedido pedido = pedido(StatusPedido.FINALIZADO, "0.00", "0.00", "c1", HOJE.atTime(12, 0), null);
        pedido.setItens(List.of(
                item("p1", "Pizza Calabresa", 1, "49.90", "Pizza"),
                item("p2", "Refrigerante 2L", 5, "14.00", "Bebida")));
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(List.of(pedido));

        List<ProdutoMaisVendidoResponse> ranking = dashboardService.produtosMaisVendidos(
                dashboardService.montarFiltro(null, null, "Pizza"), null);

        assertEquals(1, ranking.size());
        assertEquals("Pizza", ranking.get(0).tipoProduto());
    }

    @Test
    void limiteDoRankingForaDaFaixaERecusado() {
        FiltroDashboard filtro = dashboardService.montarFiltro(null, null, null);

        assertThrows(IllegalArgumentException.class, () -> dashboardService.produtosMaisVendidos(filtro, 0));
        assertThrows(IllegalArgumentException.class, () -> dashboardService.produtosMaisVendidos(filtro, 21));
    }

    //auxiliares
    private static Pedido pedido(StatusPedido status, String total, String desconto, String clienteId,
                                 LocalDateTime criadoEm, LocalDateTime saidaEm) {
        Pedido pedido = new Pedido();
        pedido.setStatus(status);
        pedido.setTotal(new BigDecimal(total));
        pedido.setValorDesconto(new BigDecimal(desconto));
        pedido.setClienteId(clienteId);
        pedido.setCriadoEm(criadoEm);
        pedido.setSaidaEm(saidaEm);
        return pedido;
    }

    private static ItemPedido item(String produtoId, String nome, int quantidade, String preco, String tipo) {
        BigDecimal precoUnitario = new BigDecimal(preco);
        return new ItemPedido(produtoId, nome, quantidade, precoUnitario,
                precoUnitario.multiply(BigDecimal.valueOf(quantidade)), tipo);
    }
}
