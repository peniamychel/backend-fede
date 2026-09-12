package com.federa.backend.service;

import com.federa.backend.dto.ConsultaPersonaResponse;
import com.federa.backend.dto.ConfirmacionSieRequest;
import com.federa.backend.dto.RevisionSieProductorResponse;
import com.federa.backend.dto.RevisionSieProductorResponse.Datos;
import com.federa.backend.exception.ReglaNegocioException;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.EstadoRevisionSieProductor;
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
            return respuestaPersistida(productor);
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
        if (productor.getRevisionSieEstado()
                == EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE) {
            return propuestaGuardada(productor);
        }
        return consultarSie(productor);
    }

    private RevisionSieProductorResponse consultarSie(Productor productor) {
        String ci = Textos.limpiar(productor.getCi());
        if (ci == null) {
            return registrar(productor, EstadoRevisionSieProductor.SIN_CEDULA,
                    ACEPTADA_SIN_CEDULA,
                    "No se pudo consultar SIE porque el productor no tiene cédula. "
                            + "Debe revisarse y corregirse manualmente.");
        }

        ConsultaPersonaResponse consulta = consultaSie.consultar(ci);
        return switch (consulta.estado()) {
            case NO_DISPONIBLE -> respuesta(NO_DISPONIBLE, false, false,
                    consulta.mensaje() + " Vuelve a intentarlo más tarde.");
            case NO_ENCONTRADA -> {
                yield registrar(productor, EstadoRevisionSieProductor.NO_ENCONTRADO,
                        ACEPTADA_SIN_COINCIDENCIA,
                        "La cédula no fue encontrada en SIE. Los datos actuales deben "
                                + "revisarse y corregirse manualmente.");
            }
            case ENCONTRADA -> proponerDatosSie(productor, consulta);
        };
    }

    private RevisionSieProductorResponse proponerDatosSie(
            Productor productor, ConsultaPersonaResponse consulta) {
        Datos actuales = datosActuales(productor);
        Datos propuestos = datosPropuestos(productor, consulta);
        if (!Objects.equals(actuales, propuestos)) {
            productor.setRevisionSiePendiente(false);
            productor.setRevisionSieEstado(EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE);
            productor.setRevisionSieMensaje(
                    "El nombre registrado no coincide con el resultado de SIE. "
                            + "La corrección sugerida está pendiente de aceptación.");
            productor.setSieNombresSugeridos(propuestos.nombres());
            productor.setSieApellidosSugeridos(propuestos.apellidos());
            return new RevisionSieProductorResponse(REQUIERE_CONFIRMACION, false, false,
                    productor.getRevisionSieMensaje(),
                    actuales, propuestos);
        }
        return registrar(productor, EstadoRevisionSieProductor.VERIFICADO,
                VERIFICADA, "Los nombres y apellidos coinciden con el servicio SIE.");
    }

    @Transactional
    public RevisionSieProductorResponse confirmar(Long id, ConfirmacionSieRequest decision) {
        Productor productor = productores.findByIdParaRevisionSie(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", id));
        if (productor.getRevisionSieEstado()
                != EstadoRevisionSieProductor.DIFERENCIA_PENDIENTE) {
            throw new ReglaNegocioException(
                    "Este productor no tiene una corrección SIE pendiente de aceptación.");
        }
        if (!Objects.equals(datosActuales(productor), decision.actuales())) {
            throw new ReglaNegocioException(
                    "La ficha cambió desde la consulta. Recargá y verificá nuevamente con SIE.");
        }
        Datos guardados = datosSugeridos(productor);
        if (!Objects.equals(guardados, decision.propuestos())) {
            throw new ReglaNegocioException(
                    "La sugerencia guardada cambió. Recargá la ficha antes de continuar.");
        }
        if (!decision.aceptar()) {
            return new RevisionSieProductorResponse(CONSERVADA, true, false,
                    productor.getRevisionSieMensaje(), datosActuales(productor), guardados);
        }

        Datos anteriores = datosActuales(productor);
        productor.setNombres(guardados.nombres());
        productor.setApellidos(guardados.apellidos());
        productor.setNombresCorregidos(null);
        productor.setApellidosCorregidos(null);
        productor.setRevisionSiePendiente(false);
        productor.setRevisionSieEstado(EstadoRevisionSieProductor.CORREGIDO_SIE);
        productor.setRevisionSieMensaje(
                "Se aceptó la corrección de SIE: «" + nombreDe(anteriores)
                        + "» cambió a «" + nombreDe(guardados) + "».");
        limpiarSugerencia(productor);

        return respuesta(CORREGIDA, true, true,
                productor.getRevisionSieMensaje());
    }

    /**
     * Da por válidos los datos actuales cuando SIE no encontró la cédula.
     * No borra una observación manual, porque esa marca puede corresponder a
     * una revisión administrativa diferente de la identidad consultada.
     */
    @Transactional
    public RevisionSieProductorResponse aprobarDatosActuales(Long id) {
        Productor productor = productores.findByIdParaRevisionSie(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("productor", id));
        if (productor.getRevisionSieEstado() != EstadoRevisionSieProductor.NO_ENCONTRADO) {
            throw new ReglaNegocioException(
                    "Solo se pueden aprobar manualmente datos que SIE no encontró.");
        }
        productor.setRevisionSiePendiente(false);
        productor.setRevisionSieEstado(EstadoRevisionSieProductor.APROBADO_MANUAL);
        productor.setRevisionSieMensaje(
                "Los datos actuales fueron revisados y aprobados manualmente después de que "
                        + "la cédula no fue encontrada en SIE.");
        limpiarSugerencia(productor);
        return respuesta(APROBADA_MANUAL, true, false,
                productor.getRevisionSieMensaje());
    }

    private Datos datosActuales(Productor productor) {
        return new Datos(productor.getCi(),
                productor.getNombresCorregidos() != null
                        ? productor.getNombresCorregidos() : productor.getNombres(),
                productor.getApellidosCorregidos() != null
                        ? productor.getApellidosCorregidos() : productor.getApellidos());
    }

    private Datos datosPropuestos(Productor productor, ConsultaPersonaResponse consulta) {
        String apellidos = Textos.normalizarParaGuardar(consulta.apellidos());
        return new Datos(productor.getCi(), Textos.normalizarParaGuardar(consulta.nombres()),
                apellidos == null ? datosActuales(productor).apellidos() : apellidos);
    }

    private Datos datosSugeridos(Productor productor) {
        return new Datos(productor.getCi(), productor.getSieNombresSugeridos(),
                productor.getSieApellidosSugeridos());
    }

    private String nombreDe(Datos datos) {
        return (datos.nombres() + " " + (datos.apellidos() == null ? "" : datos.apellidos()))
                .trim();
    }

    private RevisionSieProductorResponse propuestaGuardada(Productor productor) {
        return new RevisionSieProductorResponse(REQUIERE_CONFIRMACION, false, false,
                productor.getRevisionSieMensaje(), datosActuales(productor),
                datosSugeridos(productor));
    }

    private RevisionSieProductorResponse registrar(
            Productor productor,
            EstadoRevisionSieProductor estadoPersistente,
            RevisionSieProductorResponse.Estado estadoRespuesta,
            String mensaje) {
        productor.setRevisionSiePendiente(false);
        productor.setRevisionSieEstado(estadoPersistente);
        productor.setRevisionSieMensaje(mensaje);
        limpiarSugerencia(productor);
        return respuesta(estadoRespuesta, true, false, mensaje);
    }

    private RevisionSieProductorResponse respuestaPersistida(Productor productor) {
        if (productor.getRevisionSieEstado() == null) {
            return respuesta(YA_REALIZADA, true, false,
                    "La revisión SIE de este productor ya fue realizada.");
        }
        return switch (productor.getRevisionSieEstado()) {
            case DIFERENCIA_PENDIENTE -> propuestaGuardada(productor);
            case NO_ENCONTRADO -> respuesta(ACEPTADA_SIN_COINCIDENCIA, true, false,
                    productor.getRevisionSieMensaje());
            case SIN_CEDULA -> respuesta(ACEPTADA_SIN_CEDULA, true, false,
                    productor.getRevisionSieMensaje());
            case VERIFICADO -> respuesta(VERIFICADA, true, false,
                    productor.getRevisionSieMensaje());
            case CORREGIDO_SIE -> respuesta(CORREGIDA, true, false,
                    productor.getRevisionSieMensaje());
            case CORREGIDO_MANUAL -> respuesta(CORREGIDA_MANUAL, true, false,
                    productor.getRevisionSieMensaje());
            case APROBADO_MANUAL -> respuesta(APROBADA_MANUAL, true, false,
                    productor.getRevisionSieMensaje());
        };
    }

    private void limpiarSugerencia(Productor productor) {
        productor.setSieNombresSugeridos(null);
        productor.setSieApellidosSugeridos(null);
    }

    private RevisionSieProductorResponse respuesta(
            RevisionSieProductorResponse.Estado estado,
            boolean completada,
            boolean modificados,
            String mensaje) {
        return new RevisionSieProductorResponse(estado, completada, modificados, mensaje);
    }
}
