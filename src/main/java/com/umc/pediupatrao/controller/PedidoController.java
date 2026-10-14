package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CriarPedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Controller
public class PedidoController {

    private static final String PREFIXO_QUANTIDADE = "qty_";

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoController(PedidoService pedidoService, ClienteService clienteService, ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping("/pedidos")
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String formularioCriacao(Model model) {
        List<Produto> produtos = produtoService.listarProdutos().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()))
                .toList();
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    // Recebe o cliente e um campo "qty_{produtoId}" por produto.
    // Preços e totais NUNCA são lidos do formulário.
    @PostMapping("/pedidos/salvar")
    public String criar(@RequestParam(required = false) String clienteId,
                        @RequestParam Map<String, String> params,
                        RedirectAttributes redirect) {
        try {
            CriarPedidoRequest request = new CriarPedidoRequest();
            request.setClienteId(clienteId);
            params.forEach((name, value) -> {
                if (name.startsWith(PREFIXO_QUANTIDADE) && value != null && !value.isBlank()) {
                    int quantidade;
                    try {
                        quantidade = Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Quantidade inválida: " + value);
                    }
                    if (quantidade != 0) {
                        request.getItens().add(new CriarPedidoRequest.Item(name.substring(PREFIXO_QUANTIDADE.length()), quantidade));
                    }
                }
            });

            Pedido pedido = pedidoService.criarPedido(request);
            redirect.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
            return "redirect:/pedidos/" + pedido.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    @GetMapping("/pedidos/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("pedido", pedidoService.buscarPorId(id));
        } catch (NoSuchElementException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        model.addAttribute("descontoMaximo", PedidoService.PERCENTUAL_MAXIMO_DESCONTO);
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @PostMapping("/pedidos/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam String status, RedirectAttributes redirect) {
        try {
            pedidoService.atualizarStatus(id, PedidoApiController.parseStatus(status));
            redirect.addFlashAttribute("sucesso", "Status atualizado.");
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
                           RedirectAttributes redirect) {
        try {
            pedidoService.cancelar(id, justificativa);
            redirect.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam(required = false) BigDecimal percentual,
                                  RedirectAttributes redirect) {
        try {
            pedidoService.aplicarDesconto(id, percentual);
            redirect.addFlashAttribute("sucesso", "Desconto aplicado.");
        } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }
}