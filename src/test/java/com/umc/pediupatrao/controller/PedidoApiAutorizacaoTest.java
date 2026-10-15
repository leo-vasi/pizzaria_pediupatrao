package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Comprova a autorização no backend da API REST (RF01) desconto e cancelamento são exclusivos do gerente independentemente da tela
@WebMvcTest(PedidoApiController.class)
@Import(SecurityConfig.class)
class PedidoApiAutorizacaoTest {

    private static final String URL_DESCONTO = "/api/pedidos/o1/desconto";
    private static final String URL_CANCELAMENTO = "/api/pedidos/o1/cancelar";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PedidoService pedidoService;

    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    void anonimoRecebe401() throws Exception {
        mvc.perform(post(URL_DESCONTO).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\": 10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoPodeAplicarDesconto() throws Exception {
        mvc.perform(post(URL_DESCONTO).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\": 10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeAplicarDesconto() throws Exception {
        mvc.perform(post(URL_DESCONTO).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\": 10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeCancelar() throws Exception {
        mvc.perform(post(URL_CANCELAMENTO).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justificativa\": \"teste de acesso\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerentePodeAplicarDesconto() throws Exception {
        when(pedidoService.aplicarDesconto(anyString(), any())).thenReturn(new Pedido());

        mvc.perform(post(URL_DESCONTO).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\": 10}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void requisicaoSemTokenCsrfERecusada() throws Exception {
        mvc.perform(post(URL_DESCONTO)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percentual\": 10}"))
                .andExpect(status().isForbidden());
    }
}
