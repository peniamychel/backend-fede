package com.federa.backend.repository;

import com.federa.backend.model.SesionUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SesionUsuarioRepository extends JpaRepository<SesionUsuario, String> {
    @Modifying
    @Query("delete from SesionUsuario s where s.usuario.id = :usuarioId")
    int eliminarDeUsuario(@Param("usuarioId") Long usuarioId);

    Optional<SesionUsuario> findByIdAndRevocadaFalseAndExpiraEnAfter(String id, LocalDateTime ahora);

    @Modifying
    @Query("""
            update SesionUsuario s
               set s.revocada = true, s.revocadaEn = :ahora
             where s.usuario.id = :usuarioId and s.revocada = false
            """)
    int revocarActivasDeUsuario(@Param("usuarioId") Long usuarioId,
                                @Param("ahora") LocalDateTime ahora);
}
