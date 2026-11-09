package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidosPorHoraResponse;
import com.umc.pediupatrao.dto.dashboard.PedidosPorStatusResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.VendaPorDiaResponse;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;


@Service
@PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
public class DashboardService {

    public static final int DIAS_PADRAO = 30;
    public static final int DIAS_MAXIMO = 366;
    public static final int LIMITE_PADRAO_RANKING = 5;
    public static final int LIMITE_MAXIMO_RANKING = 20;

    static final String SEM_TIPO = "Outros";

    private static final Set<StatusPedido> EM_ANDAMENTO = EnumSet.of(
            StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA);
    private static final BigDecimal CEM = new BigDecimal("100");

    private final MongoTemplate mongoTemplate;
    private final Clock relogio;

    @Autowired
    public DashboardService(MongoTemplate mongoTemplate) {
        this(mongoTemplate, Clock.system(AuditoriaService.FUSO_HORARIO));
    }

    // Usado nos testes para fixar a data de hoje
    DashboardService(MongoTemplate mongoTemplate, Clock relogio) {
        this.mongoTemplate = mongoTemplate;
        this.relogio = relogio;
    }

    //filtros
    // Aplica os valores padrão e valida os filtros recebidos da API
    public FiltroDashboard montarFiltro(LocalDate dataInicio, LocalDate dataFim, String tipoProduto) {
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now(relogio);
        LocalDate inicio = dataInicio != null ? dataInicio : fim.minusDays(DIAS_PADRAO - 1);

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        if (fim.toEpochDay() - inicio.toEpochDay() + 1 > DIAS_MAXIMO) {
            throw new IllegalArgumentException("O período máximo de consulta é de " + DIAS_MAXIMO + " dias.");
        }
        String tipo = (tipoProduto == null || tipoProduto.isBlank()) ? null : tipoProduto.trim();
        return new FiltroDashboard(inicio, fim, tipo);
    }

    // Tipos de produto do cardápio, para preencher o filtro da tela
    public List<String> listarTiposProduto() {
        return mongoTemplate.findDistinct(new Query(), "tipo", Produto.class, String.class).stream()
                .filter(Objects::nonNull)
                .filter(tipo -> !tipo.isBlank())
                .sorted()
                .toList();
    }


    public ResumoDashboardResponse resumo(FiltroDashboard filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro);
        FiltroDashboard anterior = filtro.periodoAnterior();
        List<Pedido> pedidosAnteriores = buscarPedidos(anterior);

        Indicadores atual = calcularIndicadores(pedidos);
        Indicadores passado = calcularIndicadores(pedidosAnteriores);

        ResumoDashboardResponse.Variacao variacao = new ResumoDashboardResponse.Variacao(
                anterior.dataInicio(),
                anterior.dataFim(),
                variacaoPercentual(passado.faturamento(), atual.faturamento()),
                variacaoPercentual(BigDecimal.valueOf(passado.pedidosRealizados()), BigDecimal.valueOf(atual.pedidosRealizados())),
                variacaoPercentual(passado.ticketMedio(), atual.ticketMedio()));

        return new ResumoDashboardResponse(
                new ResumoDashboardResponse.Periodo(filtro.dataInicio(), filtro.dataFim(), filtro.tipoProduto()),
                atual.faturamento(),
                atual.pedidosRealizados(),
                atual.ticketMedio(),
                atual.taxaCancelamento(),
                atual.tempoMedioSaidaMinutos(),
                atual.descontosConcedidos(),
                atual.clientesAtendidos(),
                contarEmAndamento(filtro),
                variacao,
                LocalDateTime.now(relogio));
    }

    // Cálculo dos indicadores separado da consulta, para poder ser testado sem banco
    static Indicadores calcularIndicadores(List<Pedido> pedidos) {
        List<Pedido> realizados = pedidos.stream().filter(p -> !cancelado(p)).toList();
        long cancelados = pedidos.size() - realizados.size();

        BigDecimal faturamento = somar(realizados.stream().map(Pedido::getTotal));
        BigDecimal ticketMedio = realizados.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : faturamento.divide(BigDecimal.valueOf(realizados.size()), 2, RoundingMode.HALF_UP);
        BigDecimal taxaCancelamento = pedidos.isEmpty()
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(cancelados).multiply(CEM)
                    .divide(BigDecimal.valueOf(pedidos.size()), 1, RoundingMode.HALF_UP);

        List<Long> minutosAteSaida = realizados.stream()
                .filter(p -> p.getCriadoEm() != null && p.getSaidaEm() != null)
                .map(p -> Duration.between(p.getCriadoEm(), p.getSaidaEm()).toMinutes())
                .toList();
        BigDecimal tempoMedio = minutosAteSaida.isEmpty()
                ? null
                : BigDecimal.valueOf(minutosAteSaida.stream().mapToLong(Long::longValue).sum())
                    .divide(BigDecimal.valueOf(minutosAteSaida.size()), 1, RoundingMode.HALF_UP);

        BigDecimal descontos = somar(realizados.stream().map(Pedido::getValorDesconto));
        long clientes = realizados.stream().map(Pedido::getClienteId).filter(Objects::nonNull).distinct().count();

        return new Indicadores(faturamento, realizados.size(), ticketMedio, taxaCancelamento, tempoMedio, descontos, clientes);
    }

    static BigDecimal variacaoPercentual(BigDecimal anterior, BigDecimal atual) {
        if (anterior == null || atual == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }


    public List<VendaPorDiaResponse> vendasPorDia(FiltroDashboard filtro) {
        Map<LocalDate, List<Pedido>> porDia = buscarPedidos(filtro).stream()
                .filter(p -> !cancelado(p))
                .collect(Collectors.groupingBy(p -> p.getCriadoEm().toLocalDate()));

        // Todos os dias do período aparecem, mesmo sem vendas, para o gráfico não "pular" datas
        return Stream.iterate(filtro.dataInicio(), dia -> !dia.isAfter(filtro.dataFim()), dia -> dia.plusDays(1))
                .map(dia -> {
                    List<Pedido> doDia = porDia.getOrDefault(dia, List.of());
                    return new VendaPorDiaResponse(dia, somar(doDia.stream().map(Pedido::getTotal)), doDia.size());
                })
                .toList();
    }

    public List<PedidosPorHoraResponse> pedidosPorHora(FiltroDashboard filtro) {
        Map<Integer, Long> porHora = buscarPedidos(filtro).stream()
                .filter(p -> !cancelado(p))
                .collect(Collectors.groupingBy(p -> p.getCriadoEm().getHour(), Collectors.counting()));

        return IntStream.range(0, 24)
                .mapToObj(hora -> new PedidosPorHoraResponse(hora, porHora.getOrDefault(hora, 0L)))
                .toList();
    }


    public List<PedidosPorStatusResponse> pedidosPorStatus(FiltroDashboard filtro) {
        Map<StatusPedido, Long> porStatus = buscarPedidos(filtro).stream()
                .filter(p -> p.getStatus() != null)
                .collect(Collectors.groupingBy(Pedido::getStatus, Collectors.counting()));

        // Todos os status aparecem, na ordem do fluxo, inclusive os que estão zerados
        return Stream.of(StatusPedido.values())
                .map(status -> new PedidosPorStatusResponse(status.name(), status.getRotulo(),
                        porStatus.getOrDefault(status, 0L)))
                .toList();
    }


    public List<ProdutoMaisVendidoResponse> produtosMaisVendidos(FiltroDashboard filtro, Integer limite) {
        int limiteAplicado = limite == null ? LIMITE_PADRAO_RANKING : limite;
        if (limiteAplicado < 1 || limiteAplicado > LIMITE_MAXIMO_RANKING) {
            throw new IllegalArgumentException("O limite do ranking deve estar entre 1 e " + LIMITE_MAXIMO_RANKING + ".");
        }

        Map<String, Acumulado> porProduto = new LinkedHashMap<>();
        buscarPedidos(filtro).stream()
                .filter(p -> !cancelado(p))
                .flatMap(p -> p.getItens().stream())
                .filter(item -> !filtro.temTipoProduto() || filtro.tipoProduto().equals(item.getTipoProduto()))
                .forEach(item -> porProduto
                        .computeIfAbsent(chaveProduto(item), chave -> new Acumulado(item))
                        .somar(item));

        List<Acumulado> ordenados = porProduto.values().stream()
                .sorted(Comparator.comparingLong(Acumulado::quantidade).reversed()
                        .thenComparing(Acumulado::receita, Comparator.reverseOrder()))
                .limit(limiteAplicado)
                .toList();

        return IntStream.range(0, ordenados.size())
                .mapToObj(i -> ordenados.get(i).paraResposta(i + 1))
                .toList();
    }


    // Pedidos criados no período
    List<Pedido> buscarPedidos(FiltroDashboard filtro) {
        Criteria criterio = Criteria.where("criadoEm")
                .gte(filtro.dataInicio().atStartOfDay())
                .lt(filtro.dataFim().plusDays(1).atStartOfDay());
        if (filtro.temTipoProduto()) {
            criterio = criterio.and("itens.tipoProduto").is(filtro.tipoProduto());
        }
        return mongoTemplate.find(new Query(criterio), Pedido.class);
    }

    private long contarEmAndamento(FiltroDashboard filtro) {
        Criteria criterio = Criteria.where("status").in(EM_ANDAMENTO);
        if (filtro.temTipoProduto()) {
            criterio = criterio.and("itens.tipoProduto").is(filtro.tipoProduto());
        }
        return mongoTemplate.count(new Query(criterio), Pedido.class);
    }


    // metodos auxiliares
    private static boolean cancelado(Pedido pedido) {
        return pedido.getStatus() == StatusPedido.CANCELADO;
    }

    private static BigDecimal somar(Stream<BigDecimal> valores) {
        return valores.filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String chaveProduto(ItemPedido item) {
        return item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
    }

    // Resultado do cálculo dos indicadores de um período
    record Indicadores(BigDecimal faturamento, long pedidosRealizados, BigDecimal ticketMedio,
                       BigDecimal taxaCancelamento, BigDecimal tempoMedioSaidaMinutos,
                       BigDecimal descontosConcedidos, long clientesAtendidos) {
    }

    // Soma de quantidade e receita de um produto ao montar o ranking
    private static final class Acumulado {
        private final String produtoId;
        private final String nomeProduto;
        private final String tipoProduto;
        private long quantidade;
        private BigDecimal receita = BigDecimal.ZERO;

        Acumulado(ItemPedido item) {
            this.produtoId = item.getProdutoId();
            this.nomeProduto = item.getNomeProduto();
            this.tipoProduto = item.getTipoProduto() != null ? item.getTipoProduto() : SEM_TIPO;
        }

        void somar(ItemPedido item) {
            quantidade += item.getQuantidade();
            if (item.getSubtotal() != null) {
                receita = receita.add(item.getSubtotal());
            }
        }

        long quantidade() {
            return quantidade;
        }

        BigDecimal receita() {
            return receita;
        }

        ProdutoMaisVendidoResponse paraResposta(int posicao) {
            return new ProdutoMaisVendidoResponse(posicao, produtoId, nomeProduto, tipoProduto, quantidade,
                    receita.setScale(2, RoundingMode.HALF_UP));
        }
    }
}
