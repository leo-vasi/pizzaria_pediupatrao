package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Página do dashboard administrativo (ADMIN e GERENTE, regra no SecurityConfig).
// A página não recebe dados pelo Thymeleaf: tudo é carregado pela API /api/dashboard (RF06).
// Usa o layout do AdminLTE em um template próprio, sem alterar o layout das demais telas.
@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
