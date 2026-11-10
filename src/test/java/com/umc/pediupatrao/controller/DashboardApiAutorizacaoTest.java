package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Comprova o acesso e o tratamento de erros da API do dashboard
@WebMvcTest(DashboardApiController.class)
@Import(SecurityConfig.class)
class DashboardApiAutorizacaoTest {

    private static final String URL_RESUMO = "/api/dashboard/resumo";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void anonimoRecebe401() throws Exception {
        mvc.perform(get(URL_RESUMO)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get(URL_RESUMO)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteAcessaODashboard() throws Exception {
        mvc.perform(get(URL_RESUMO)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/pedidos-por-status")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void periodoInvalidoRetorna400ComMensagem() throws Exception {
        when(dashboardService.montarFiltro(any(), any(), isNull()))
                .thenThrow(new IllegalArgumentException("A data inicial não pode ser posterior à data final."));

        mvc.perform(get(URL_RESUMO).param("dataInicio", "2026-11-10").param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A data inicial não pode ser posterior à data final."));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void dataEmFormatoErradoRetorna400() throws Exception {
        mvc.perform(get(URL_RESUMO).param("dataInicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor inválido para o parâmetro 'dataInicio'. Use o formato AAAA-MM-DD."));
    }
}
