package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.AlteracaoStatus;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

//classe de configuracão pra ter dados para testar
@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DadosDemonstracaoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracaoSeeder.class);

    private static final int DIAS_DE_HISTORICO = 30;
    private static final long SEMENTE = 2026L;
    private static final int[] HORARIOS_PONDERADOS = {11, 11, 12, 12, 12, 12, 13, 13, 13, 14, 14, 15, 16, 17, 18, 18, 19, 19, 19, 19, 19, 20, 20, 20, 20, 20, 21, 21, 21, 22, 22, 23};

    private static final String[] MOTIVOS_CANCELAMENTO = {"Cliente desistiu do pedido", "Endereço fora da área de entrega", "Cliente não atendeu a confirmação", "Produto indisponível no estoque", "Pedido duplicado"};

    private static final String ATENDENTE = "atendente01";
    private static final String GERENTE = "gerente01";

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final Random random = new Random(SEMENTE);

    public DadosDemonstracaoSeeder(PedidoRepository pedidoRepository, ClienteRepository clienteRepository, ProdutoRepository produtoRepository) {

        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void gerarDados() {
        if (pedidoRepository.count() > 0) {
            log.info("Já existe pedidos cadastrados");
            return;
        }

        List<Produto> produtos = produtoRepository.findAll().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
        if (produtos.isEmpty()) {
            log.warn("Nenhum produto ativo cadastrado");
            return;
        }

        List<Cliente> clientes = garantirClientes();
        LocalDateTime agora = LocalDateTime.now(AuditoriaService.FUSO_HORARIO);
        List<Pedido> pedidos = new ArrayList<>();

        for (int diasAtras = DIAS_DE_HISTORICO - 1; diasAtras >= 0; diasAtras--) {
            LocalDate dia = agora.toLocalDate().minusDays(diasAtras);
            int quantidadeNoDia = quantidadeDePedidos(dia);

            for (int i = 0; i < quantidadeNoDia; i++) {
                LocalDateTime criadoEm = dia.atTime(sortearHorario(), random.nextInt(60), random.nextInt(60));
                if (criadoEm.isAfter(agora)) {
                    continue; // não gera pedidos no futuro
                }
                Cliente cliente = clientes.get(random.nextInt(clientes.size()));
                pedidos.add(montarPedido(cliente, produtos, criadoEm, agora));
            }
        }

        pedidoRepository.saveAll(pedidos);
        log.info("Seed: {} pedidos gerados nos ultimos {} dias", pedidos.size(), DIAS_DE_HISTORICO);
    }


    //Clientes
    private List<Cliente> garantirClientes() {
        List<Cliente> existentes = clienteRepository.findAll();
        if (existentes.size() >= 5) {
            return existentes;
        }
        String[][] dados = {
                {"Ana Souza", "(11) 98123-4567", "Rua das Acácias", "Centro"},
                {"Bruno Lima", "(11) 97234-5678", "Av. Brasil", "Mogilar"},
                {"Carla Mendes", "(11) 96345-6789", "Rua Ipiranga", "Vila Oliveira"},
                {"Diego Rocha", "(11) 95456-7890", "Rua Dr. Deodato Wertheimer", "Centro"},
                {"Eduarda Alves", "(11) 94567-8901", "Av. Voluntário Fernando Pinheiro Franco", "Centro"},
                {"Felipe Costa", "(11) 93678-9012", "Rua Navajas", "Vila Industrial"},
                {"Gabriela Nunes", "(11) 92789-0123", "Rua Cabo Diogo Oliver", "Mogi Moderno"},
                {"Henrique Prado", "(11) 91890-1234", "Av. Japão", "Vila Natal"}
        };
        List<Cliente> novos = new ArrayList<>();
        for (String[] d : dados) {
            Cliente c = new Cliente();
            c.setNome(d[0]);
            c.setTelefone(d[1]);
            c.setLogradouro(d[2]);
            c.setNumero(String.valueOf(100 + random.nextInt(900)));
            c.setBairro(d[3]);
            c.setCidade("Mogi das Cruzes");
            c.setEstado("SP");
            novos.add(c);
        }
        List<Cliente> todos = new ArrayList<>(existentes);
        todos.addAll(clienteRepository.saveAll(novos));
        return todos;
    }


    // Pedidos simlando mais movimento de sexta a domingo
    private int quantidadeDePedidos(LocalDate dia) {

        DayOfWeek diaSemana = dia.getDayOfWeek();

        boolean fimDeSemana = diaSemana == DayOfWeek.FRIDAY || diaSemana == DayOfWeek.SATURDAY
                || diaSemana == DayOfWeek.SUNDAY;
        return (fimDeSemana ? 14 : 7) + random.nextInt(8);
    }

    private int sortearHorario() {
        return HORARIOS_PONDERADOS[random.nextInt(HORARIOS_PONDERADOS.length)];
    }

    private Pedido montarPedido(Cliente cliente, List<Produto> produtos, LocalDateTime criadoEm, LocalDateTime agora) {
        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setNomeCliente(cliente.getNome());
        pedido.setItens(sortearItens(produtos));

        String responsavelEntrada = random.nextInt(5) == 0 ? GERENTE : ATENDENTE;
        pedido.setCriadoEm(criadoEm);
        pedido.setCriadoPor(responsavelEntrada);
        registrar(pedido, StatusPedido.RECEBIDO, criadoEm, responsavelEntrada);

        if (random.nextInt(100) < 12) {
            int[] percentuais = {5, 10, 15};
            pedido.setPercentualDesconto(BigDecimal.valueOf(percentuais[random.nextInt(percentuais.length)])
                    .setScale(2, RoundingMode.HALF_UP));
            pedido.setDescontoAplicadoEm(criadoEm.plusMinutes(2));
            pedido.setDescontoAplicadoPor(GERENTE);
        }
        PedidoService.recalcular(pedido);

        avancarFluxo(pedido, criadoEm, agora);
        return pedido;
    }

    private List<ItemPedido> sortearItens(List<Produto> produtos) {
        List<Produto> embaralhados = new ArrayList<>(produtos);
        Collections.shuffle(embaralhados, random);
        int quantidadeDeItens = 1 + random.nextInt(Math.min(3, embaralhados.size()));

        List<ItemPedido> itens = new ArrayList<>();
        for (Produto produto : embaralhados.subList(0, quantidadeDeItens)) {
            int quantidade = 1 + random.nextInt(3);
            BigDecimal precoUnitario = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, precoUnitario, subtotal,
                    produto.getTipo()));
        }
        return itens;
    }

    private void avancarFluxo(Pedido pedido, LocalDateTime criadoEm, LocalDateTime agora) {
        boolean cancelar = random.nextInt(100) < 8;
        LocalDateTime momento = criadoEm;

        momento = momento.plusMinutes(2 + random.nextInt(7));
        if (momento.isAfter(agora)) {
            return;
        }
        if (cancelar && random.nextBoolean()) {
            cancelarPedido(pedido, momento);
            return;
        }
        registrar(pedido, StatusPedido.EM_PREPARACAO, momento, ATENDENTE);

        momento = momento.plusMinutes(15 + random.nextInt(16));
        if (momento.isAfter(agora)) {
            return;
        }
        if (cancelar) {
            cancelarPedido(pedido, momento);
            return;
        }
        registrar(pedido, StatusPedido.PRONTO, momento, ATENDENTE);

        boolean entrega = random.nextInt(100) < 70;
        momento = momento.plusMinutes(3 + random.nextInt(8));
        if (momento.isAfter(agora)) {
            return;
        }
        StatusPedido saida = entrega ? StatusPedido.SAIU_PARA_ENTREGA : StatusPedido.RETIRADO;
        pedido.setSaidaEm(momento);
        pedido.setSaidaPor(ATENDENTE);
        registrar(pedido, saida, momento, ATENDENTE);

        momento = momento.plusMinutes(entrega ? 15 + random.nextInt(26) : 1);
        if (momento.isAfter(agora)) {
            return;
        }
        pedido.setFinalizadoEm(momento);
        pedido.setFinalizadoPor(ATENDENTE);
        registrar(pedido, StatusPedido.FINALIZADO, momento, ATENDENTE);
    }

    private void cancelarPedido(Pedido pedido, LocalDateTime momento) {
        pedido.setCanceladoEm(momento);
        pedido.setCanceladoPor(GERENTE);
        pedido.setMotivoCancelamento(MOTIVOS_CANCELAMENTO[random.nextInt(MOTIVOS_CANCELAMENTO.length)]);
        registrar(pedido, StatusPedido.CANCELADO, momento, GERENTE);
    }

    private void registrar(Pedido pedido, StatusPedido status, LocalDateTime momento, String usuario) {
        pedido.setStatus(status);
        pedido.getHistoricoStatus().add(new AlteracaoStatus(status, momento, usuario));
    }
}
