package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String listar(@RequestParam(name = "entidade", required = false) String tipoEntidade, Model model) {

        model.addAttribute("logs", auditoriaService.listar(tipoEntidade));
        model.addAttribute("entidade", tipoEntidade);
        model.addAttribute("content", "auditoria/lista :: content");

        return "layout";
    }
}