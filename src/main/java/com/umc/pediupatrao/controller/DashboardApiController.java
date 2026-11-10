package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.dto.dashboard.PedidosPorHoraResponse;
import com.umc.pediupatrao.dto.dashboard.PedidosPorStatusResponse;
import com.umc.pediupatrao.dto.dashboard.ProdutoMaisVendidoResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.VendaPorDiaResponse;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;


//dataInicio e dataFim no formato AAAA-MM-DD
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public ResumoDashboardResponse resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipoProduto) {
        return dashboardService.resumo(filtro(dataInicio, dataFim, tipoProduto));
    }

    @GetMapping("/vendas-por-dia")
    public List<VendaPorDiaResponse> vendasPorDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipoProduto) {
        return dashboardService.vendasPorDia(filtro(dataInicio, dataFim, tipoProduto));
    }

    @GetMapping("/pedidos-por-hora")
    public List<PedidosPorHoraResponse> pedidosPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipoProduto) {
        return dashboardService.pedidosPorHora(filtro(dataInicio, dataFim, tipoProduto));
    }

    @GetMapping("/pedidos-por-status")
    public List<PedidosPorStatusResponse> pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipoProduto) {
        return dashboardService.pedidosPorStatus(filtro(dataInicio, dataFim, tipoProduto));
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoMaisVendidoResponse> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipoProduto,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.produtosMaisVendidos(filtro(dataInicio, dataFim, tipoProduto), limite);
    }

    // Opções para o filtro de tipo de produto da tela
    @GetMapping("/tipos-produto")
    public List<String> tiposProduto() {
        return dashboardService.listarTiposProduto();
    }

    private FiltroDashboard filtro(LocalDate dataInicio, LocalDate dataFim, String tipoProduto) {
        return dashboardService.montarFiltro(dataInicio, dataFim, tipoProduto);
    }
}
