package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import org.springframework.data.repository.Repository;

import java.util.List;


public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, String> {

    RegistroAuditoria save(RegistroAuditoria registro);

    List<RegistroAuditoria> findAllByOrderByDataHoraDesc();

    List<RegistroAuditoria> findByTipoEntidadeOrderByDataHoraDesc(String tipoEntidade);

    List<RegistroAuditoria> findByTipoEntidadeAndEntidadeIdOrderByDataHoraDesc(String tipoEntidade, String entidadeId);
}