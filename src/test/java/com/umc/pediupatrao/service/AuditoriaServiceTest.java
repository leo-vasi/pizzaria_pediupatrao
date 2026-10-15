package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.OperacaoAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private RegistroAuditoriaRepository registroAuditoriaRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void compararDevolveSomenteCamposAlterados() {
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("nome", "Maria");
        antes.put("logradouro", "Rua das Flores");
        antes.put("complemento", null);

        Map<String, String> depois = new LinkedHashMap<>();
        depois.put("nome", "Maria");
        depois.put("logradouro", "Av. Brasil");
        depois.put("complemento", "");   // nulo e em branco são considerados iguais

        List<AlteracaoCampo> alteracoes = AuditoriaService.comparar(antes, depois);

        assertEquals(1, alteracoes.size());
        assertEquals("logradouro", alteracoes.get(0).getCampo());
        assertEquals("Rua das Flores", alteracoes.get(0).getValorAnterior());
        assertEquals("Av. Brasil", alteracoes.get(0).getValorNovo());
    }

    @Test
    void registrarGravaUsuarioPerfilEDataHoraEDescartaCredenciais() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("atendente01", "x", "ROLE_ATENDENTE"));

        auditoriaService.registrar(OperacaoAuditoria.CLIENTE_ATUALIZADO, "Cliente", "c1", List.of(
                new AlteracaoCampo("telefone", "11999990000", "11988887777"),
                new AlteracaoCampo("password", "senha-antiga", "senha-nova")));

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(registroAuditoriaRepository).save(captor.capture());
        RegistroAuditoria registro = captor.getValue();

        assertNull(registro.getId());
        assertEquals("atendente01", registro.getUsername());
        assertEquals("ATENDENTE", registro.getRole());
        assertNotNull(registro.getDataHora());
        assertEquals("c1", registro.getEntidadeId());
        assertEquals(1, registro.getAlteracoes().size());
        assertTrue(registro.getAlteracoes().stream().noneMatch(a -> a.getCampo().equals("password")));
    }

    @Test
    void telefoneEMascaradoMantendoOsQuatroUltimosDigitos() {
        assertEquals("*******4321", AuditoriaService.mascararTelefone("(11) 98765-4321"));
        assertEquals("****", AuditoriaService.mascararTelefone("123"));
        assertNull(AuditoriaService.mascararTelefone(null));
    }
}
