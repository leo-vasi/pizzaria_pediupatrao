package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.AuditoriaService.RegistroAuditoriaView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaApiController {

    private final AuditoriaService auditoriaService;

    public AuditoriaApiController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<RegistroAuditoriaView> listar(
            @RequestParam(name = "entityType", required = false) String tipoEntidade,
            @RequestParam(name = "entityId", required = false) String entidadeId) {

        if (tipoEntidade != null && entidadeId != null) {
            return auditoriaService.listarPorEntidade(tipoEntidade, entidadeId);
        }
        return auditoriaService.listar(tipoEntidade);
    }
}