package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AplicarDescontoRequest;
import com.umc.pediupatrao.dto.CancelarPedidoRequest;
import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoApiController {

    private final PedidoService pedidoService;

    public PedidoApiController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> criar(@RequestBody CriarPedidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(request));
    }

    @GetMapping
    public List<Pedido> listar() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, parseStatus(status));
    }

    @PostMapping("/{id}/cancelar")
    public Pedido cancelar(@PathVariable String id, @RequestBody CancelarPedidoRequest request) {
        return pedidoService.cancelar(id, request.getJustificativa());
    }

    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody AplicarDescontoRequest request) {
        return pedidoService.aplicarDesconto(id, request.getPercentual());
    }

    // Converte o texto recebido no enum; valor inválido vira 400 pelo ApiExceptionHandler
    static StatusPedido parseStatus(String valor) {
        try {
            return StatusPedido.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Status inválido: " + valor);
        }
    }
}
