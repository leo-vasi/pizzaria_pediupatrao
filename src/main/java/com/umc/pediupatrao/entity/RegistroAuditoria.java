package com.umc.pediupatrao.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;


@Document(collection = "audit_logs")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE) // exigido pelo Spring Data para ler do MongoDB
public class RegistroAuditoria {

    @Id
    private String id;

    private String username;

    // Perfil do usuário no momento da operação (ADMIN, GERENTE, ATENDENTE)
    private String role;

    @Indexed
    private LocalDateTime dataHora;

    private OperacaoAuditoria operacao;

    // Tipo da entidade afetada e o id dela
    @Indexed
    private String tipoEntidade;
    private String entidadeId;

    private List<AlteracaoCampo> alteracoes;
    private String justificativa;
}