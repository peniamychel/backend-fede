package com.federa.backend.service;

import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.dto.RevisionSieProductorResponse;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Productor;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static com.federa.backend.dto.RevisionSieProductorResponse.Estado.*;

@Service
public class RevisionSieProductorService {

    private final ProductorRepository productores;
    private final ConsultaPersonaService consultaSie;

    public RevisionSieProductorService(ProductorRepository productores,
                                       ConsultaPersonaService consultaSie) {
        this.productores = productores;
        this.consultaSie = consultaSie;
    }

    /**
     * Revisa una sola vez los datos que entraron por planilla. El bloqueo evita
     * que dos aperturas simultáneas hagan dos consultas al servicio externo.
     */
    @Transactional
    public RevisionSieProductorResponse revisar(Long id) {
        Productor productor = productores.findByIdParaRevisionSie(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", id));

        if (!productor.isRevisionSiePendiente()) {
            return respuesta(YA_REALIZADA, true, false,
                    "La revisión SIE de este productor ya fue realizada.");
        }

        return consultarSie(productor);
    }

    /**
     * Comprobación manual temporal para los registros que ya existían antes de
     * incorporar la marca de revisión automática. A diferencia de
     * {@link #revisar(Long)}, esta acción consulta SIE cada vez que el usuario
     * la confirma desde la ficha.
     */
    @Transactional
    public RevisionSieProductorResponse verificarManualmente(Long id) {
        Productor productor = productores.findByIdParaRevisionSie(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", id));
        return consultarSie(productor);
    }

    private RevisionSieProductorResponse consultarSie(Productor productor) {
        String ci = Textos.limpiar(productor.getCi());
        if (ci == null) {
            productor.setRevisionSiePendiente(false);
            return respuesta(ACEPTADA_SIN_CEDULA, true, false,
                    "El productor no tiene cédula; se conservaron los datos actuales.");
        }

        ConsultaPersonaResponse consulta = consultaSie.consultar(ci);
        return switch (consulta.estado()) {
            case NO_DISPONIBLE -> respuesta(NO_DISPONIBLE, false, false,
                    consulta.mensaje() + " Vuelve a intentarlo más tarde.");
            case NO_ENCONTRADA -> {
                productor.setRevisionSiePendiente(false);
                yield respuesta(ACEPTADA_SIN_COINCIDENCIA, true, false,
                        "La cédula no fue encontrada en SIE; se conservaron los datos actuales.");
            }
            case ENCONTRADA -> aplicarDatosSie(productor, consulta);
        };
    }

    private RevisionSieProductorResponse aplicarDatosSie(
            Productor productor, ConsultaPersonaResponse consulta) {
        String nombres = Textos.normalizarParaGuardar(consulta.nombres());
        String apellidos = Textos.normalizarParaGuardar(consulta.apellidos());
        if (apellidos == null) {
            apellidos = productor.getApellidos();
        }
        boolean modificados = !Objects.equals(productor.getNombres(), nombres)
                || !Objects.equals(productor.getApellidos(), apellidos);

        productor.setNombres(nombres);
        productor.setApellidos(apellidos);
        productor.setNombresCorregidos(null);
        productor.setApellidosCorregidos(null);
        productor.setRevisionSiePendiente(false);

        return modificados
                ? respuesta(CORREGIDA, true, true,
                        "SIE encontró diferencias y corrigió los nombres y apellidos.")
                : respuesta(VERIFICADA, true, false,
                        "Los nombres y apellidos coinciden con el servicio SIE.");
    }

    private RevisionSieProductorResponse respuesta(
            RevisionSieProductorResponse.Estado estado,
            boolean completada,
            boolean modificados,
            String mensaje) {
        return new RevisionSieProductorResponse(estado, completada, modificados, mensaje);
    }
}
