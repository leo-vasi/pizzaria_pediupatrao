package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.OperacaoAuditoria;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@Service
public class AuditoriaService {

    public static final ZoneId FUSO_HORARIO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_EXIBICAO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Campos que nunca devem ser gravados no log de auditoria
    private static final Set<String> CAMPOS_SENSIVEIS = Set.of("password", "senha", "token", "secret");

    // Dados pessoais mascarados na exibição a auditoria
    private static final Set<String> CAMPOS_MASCARADOS = Set.of("telefone");

    private final RegistroAuditoriaRepository registroAuditoriaRepository;

    public AuditoriaService(RegistroAuditoriaRepository registroAuditoriaRepository) {
        this.registroAuditoriaRepository = registroAuditoriaRepository;
    }

    // ===================== gravação =====================

    public RegistroAuditoria registrar(OperacaoAuditoria operacao, String tipoEntidade, String entidadeId,
                                       List<AlteracaoCampo> alteracoes, String justificativa) {
        List<AlteracaoCampo> alteracoesSeguras = alteracoes == null ? List.of() : alteracoes.stream()
                .filter(alteracao -> !isCampoSensivel(alteracao.getCampo()))
                .toList();

        RegistroAuditoria registro = RegistroAuditoria.builder()
                .username(UsuarioLogado.usernameOrSystem())
                .role(UsuarioLogado.roleOrSystem())
                .dataHora(LocalDateTime.now(FUSO_HORARIO))
                .operacao(operacao)
                .tipoEntidade(tipoEntidade)
                .entidadeId(entidadeId)
                .alteracoes(alteracoesSeguras)
                .justificativa(justificativa)
                .build();

        return registroAuditoriaRepository.save(registro);
    }

    public RegistroAuditoria registrar(OperacaoAuditoria operacao, String tipoEntidade, String entidadeId,
                                       List<AlteracaoCampo> alteracoes) {
        return registrar(operacao, tipoEntidade, entidadeId, alteracoes, null);
    }

    // Compara dois snapshots (nome do campo -> valor) e devolve só os campos que mudaram
    // Valores nulos e em branco são tratados como iguais.
    public static List<AlteracaoCampo> comparar(Map<String, String> antes, Map<String, String> depois) {
        List<AlteracaoCampo> alteracoes = new ArrayList<>();
        Set<String> campos = new java.util.LinkedHashSet<>();
        if (antes != null) {
            campos.addAll(antes.keySet());
        }
        if (depois != null) {
            campos.addAll(depois.keySet());
        }
        for (String campo : campos) {
            String valorAnterior = normalizar(antes == null ? null : antes.get(campo));
            String valorNovo = normalizar(depois == null ? null : depois.get(campo));
            if (!Objects.equals(valorAnterior, valorNovo)) {
                alteracoes.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
            }
        }
        return alteracoes;
    }

    // ===================== consulta =====================

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<RegistroAuditoriaView> listar(String tipoEntidade) {
        List<RegistroAuditoria> registros = (tipoEntidade == null || tipoEntidade.isBlank())
                ? registroAuditoriaRepository.findAllByOrderByDataHoraDesc()
                : registroAuditoriaRepository.findByTipoEntidadeOrderByDataHoraDesc(tipoEntidade);
        return registros.stream().map(AuditoriaService::paraView).toList();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public List<RegistroAuditoriaView> listarPorEntidade(String tipoEntidade, String entidadeId) {
        return registroAuditoriaRepository.findByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(tipoEntidade, entidadeId)
                .stream().map(AuditoriaService::paraView).toList();
    }

    private static RegistroAuditoriaView paraView(RegistroAuditoria registro) {
        List<AlteracaoCampo> alteracoes = registro.getAlteracoes() == null ? List.of() : registro.getAlteracoes().stream()
                .map(alteracao -> isCampoMascarado(alteracao.getCampo())
                        ? new AlteracaoCampo(alteracao.getCampo(), mascararTelefone(alteracao.getValorAnterior()), mascararTelefone(alteracao.getValorNovo()))
                        : alteracao)
                .toList();
        return new RegistroAuditoriaView(
                registro.getId(),
                registro.getUsername(),
                registro.getRole(),
                registro.getDataHora() == null ? "" : registro.getDataHora().format(FORMATO_EXIBICAO),
                registro.getOperacao() == null ? "" : registro.getOperacao().name(),
                registro.getOperacao() == null ? "" : registro.getOperacao().getLabel(),
                registro.getTipoEntidade(),
                registro.getEntidadeId(),
                alteracoes,
                registro.getJustificativa());
    }

    // ===================== auxiliares =====================

    private static boolean isCampoSensivel(String campo) {
        return campo != null && CAMPOS_SENSIVEIS.contains(campo.toLowerCase(Locale.ROOT));
    }

    private static boolean isCampoMascarado(String campo) {
        return campo != null && CAMPOS_MASCARADOS.contains(campo.toLowerCase(Locale.ROOT));
    }

    // Mantém apenas os últimos 4 dígitos
    static String mascararTelefone(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        if (digitos.length() <= 4) {
            return "****";
        }
        return "*".repeat(digitos.length() - 4) + digitos.substring(digitos.length() - 4);
    }

    private static String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    // Representação somente leitura de um evento de auditoria já formatada e mascarada
    @Getter
    @AllArgsConstructor
    public static class RegistroAuditoriaView {
        private final String id;
        private final String username;
        private final String role;
        private final String dataHora;
        private final String operacao;
        private final String rotuloOperacao;
        private final String tipoEntidade;
        private final String entidadeId;
        private final List<AlteracaoCampo> alteracoes;
        private final String justificativa;
    }
}