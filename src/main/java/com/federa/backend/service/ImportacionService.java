package com.federa.backend.service;

import com.federa.backend.dto.ErrorFila;
import com.federa.backend.dto.ImportacionResponse;
import com.federa.backend.dto.SindicatoNuevo;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Observacion;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carga masiva del padrón desde la planilla MATRIX.
 * <p>
 * La simulación y la ejecución recorren <b>exactamente el mismo código</b>,
 * incluidos los INSERT: lo único que cambia es que al final la transacción se
 * deshace. Si fueran dos caminos distintos, la vista previa podría prometer un
 * resultado que la ejecución no cumple, y entonces no serviría para decidir.
 */
@Service
public class ImportacionService {

    /** Tope de errores detallados, para que una planilla rota no devuelva un JSON enorme. */
    private static final int MAX_ERRORES = 200;

    private static final int MAX_NOMBRE = 60;
    private static final int MAX_CI = 20;
    private static final int MAX_LOTE = 20;
    private static final int MAX_OBSERVACION = 500;

    private final LectorPlanilla lector;
    private final FederacionService federacionService;
    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final ProductorRepository productorRepository;
    private final TransactionTemplate transacciones;

    public ImportacionService(LectorPlanilla lector,
                              FederacionService federacionService,
                              CentralRepository centralRepository,
                              SindicatoRepository sindicatoRepository,
                              ProductorRepository productorRepository,
                              PlatformTransactionManager gestorTransacciones) {
        this.lector = lector;
        this.federacionService = federacionService;
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.productorRepository = productorRepository;
        this.transacciones = new TransactionTemplate(gestorTransacciones);
    }

    /**
     * @param simular               si es true no se persiste nada
     * @param crearJerarquia        da de alta las centrales y sindicatos que falten
     * @param ignorarFilasConError  importa las filas válidas aunque otras fallen
     */
    public ImportacionResponse importar(InputStream archivo, Long federacionId, boolean simular,
                                        boolean crearJerarquia, boolean ignorarFilasConError) {
        long inicio = System.nanoTime();

        // Leer no toca la base, así que va fuera de la transacción: si el
        // archivo está roto no tiene sentido haber abierto uno.
        List<LectorPlanilla.Fila> filas = lector.leer(archivo);

        return transacciones.execute(estado -> {
            Proceso proceso = new Proceso(federacionId, crearJerarquia);
            proceso.ejecutar(filas);

            boolean hayRechazos = !proceso.errores.isEmpty();
            boolean sePersiste = !simular && (!hayRechazos || ignorarFilasConError);

            if (sePersiste) {
                proceso.guardar();
            } else {
                // Cubre los dos casos: la simulación, y la ejecución real que se
                // aborta porque hubo filas inválidas y no se pidió ignorarlas.
                // En ambos hay que deshacer las centrales y sindicatos que el
                // recorrido pudo haber creado.
                estado.setRollbackOnly();
            }

            return proceso.informe(!sePersiste, System.nanoTime() - inicio);
        });
    }

    /**
     * Estado de una corrida. Es una clase interna y no un método largo para no
     * pasar ocho acumuladores entre métodos privados.
     */
    private final class Proceso {

        private final Long federacionId;
        private final boolean crearJerarquia;
        private final Federacion federacion;

        /** Centrales de la federación por nombre normalizado. */
        private final Map<String, Central> centrales = new HashMap<>();

        /** Sindicatos por "centralId|nombre": el nombre solo no identifica a ninguno. */
        private final Map<String, Sindicato> sindicatos = new HashMap<>();

        /** Identidades ya existentes, para detectar una segunda carga de la misma planilla. */
        private final Set<String> yaExistentes = new HashSet<>();

        private final Set<String> centralesNuevas = new LinkedHashSet<>();
        private final Map<String, SindicatoNuevo> sindicatosNuevos = new LinkedHashMap<>();
        private final List<ErrorFila> errores = new ArrayList<>();
        private final List<Productor> aGuardar = new ArrayList<>();

        private int erroresTotales;
        private int filasLeidas;
        private int lotes;
        private int observaciones;
        private int posiblesDuplicados;

        private Proceso(Long federacionId, boolean crearJerarquia) {
            this.federacionId = federacionId;
            this.crearJerarquia = crearJerarquia;
            this.federacion = federacionService.buscar(federacionId);
            precargar();
        }

        /**
         * Trae de una sola vez la jerarquía y las identidades existentes. Sin
         * esto habría dos consultas por fila y la importación se volvería
         * inviable con 4.051 filas.
         */
        private void precargar() {
            for (Central central : centralRepository.findByFederacionIdOrderByNombreAsc(federacionId)) {
                centrales.put(central.getNombre(), central);
                for (Sindicato sindicato : sindicatoRepository
                        .findByCentralIdOrderByNombreAsc(central.getId())) {
                    sindicatos.put(claveSindicato(central.getId(), sindicato.getNombre()), sindicato);
                }
            }
            for (Object[] fila : productorRepository.findIdentidadesPorFederacion(federacionId)) {
                yaExistentes.add(claveIdentidad((Long) fila[0], (String) fila[1], (String) fila[2]));
            }
        }

        private void ejecutar(List<LectorPlanilla.Fila> filas) {
            filasLeidas = filas.size();
            for (LectorPlanilla.Fila fila : filas) {
                procesar(fila);
            }
        }

        private void procesar(LectorPlanilla.Fila fila) {
            String nombreCentral = Textos.normalizar(fila.central());
            String nombreSindicato = Textos.normalizar(fila.sindicato());
            String nombres = Textos.normalizar(fila.nombres());
            String apellidos = Textos.normalizar(fila.apellidos());
            String ci = Textos.limpiar(fila.ci());
            String numeroLote = Textos.limpiar(fila.numeroLote());

            if (nombreCentral == null) {
                rechazar(fila, "central", fila.central(), "la central es obligatoria");
                return;
            }
            if (nombreSindicato == null) {
                rechazar(fila, "sindicato", fila.sindicato(), "el sindicato es obligatorio");
                return;
            }
            if (nombres == null) {
                rechazar(fila, "nombres", fila.nombres(), "los nombres son obligatorios");
                return;
            }
            if (excede(nombreCentral, MAX_NOMBRE)
                    || excede(nombreSindicato, MAX_NOMBRE)
                    || excede(nombres, MAX_NOMBRE)
                    || excede(apellidos, MAX_NOMBRE)) {
                rechazar(fila, null, nombres, "algún nombre supera los " + MAX_NOMBRE + " caracteres");
                return;
            }
            if (excede(ci, MAX_CI)) {
                rechazar(fila, "ci", ci, "la cédula supera los " + MAX_CI + " caracteres");
                return;
            }
            if (excede(numeroLote, MAX_LOTE)) {
                rechazar(fila, "numeroLote", numeroLote,
                        "el número de lote supera los " + MAX_LOTE + " caracteres");
                return;
            }

            Central central = centrales.get(nombreCentral);
            if (central == null) {
                if (!crearJerarquia) {
                    rechazar(fila, "central", nombreCentral,
                            "la central " + nombreCentral + " no existe en la federación "
                                    + federacion.getNombre());
                    return;
                }
                central = new Central();
                central.setNombre(nombreCentral);
                central.setFederacion(federacion);
                central = centralRepository.save(central);
                centrales.put(nombreCentral, central);
                // Anotarla acá y no después: este es el único punto donde se
                // sabe con certeza que no venía de la precarga.
                centralesNuevas.add(nombreCentral);
            }

            String claveSind = claveSindicato(central.getId(), nombreSindicato);
            Sindicato sindicato = sindicatos.get(claveSind);
            if (sindicato == null) {
                if (!crearJerarquia) {
                    rechazar(fila, "sindicato", nombreSindicato,
                            "el sindicato " + nombreSindicato + " no existe en la central "
                                    + nombreCentral);
                    return;
                }
                sindicato = new Sindicato();
                sindicato.setNombre(nombreSindicato);
                sindicato.setCentral(central);
                sindicato = sindicatoRepository.save(sindicato);
                sindicatos.put(claveSind, sindicato);
                sindicatosNuevos.putIfAbsent(claveSind,
                        new SindicatoNuevo(nombreCentral, nombreSindicato));
            }

            Productor productor = new Productor();
            productor.setNombres(nombres);
            productor.setApellidos(apellidos);
            productor.setCi(ci);
            productor.setSindicato(sindicato);

            if (numeroLote != null) {
                Lote lote = new Lote();
                lote.setNumero(numeroLote);
                productor.agregarLote(lote);
                lotes++;
            }

            for (String mensaje : motivos(fila.observaciones())) {
                Observacion observacion = new Observacion();
                observacion.setMensaje(mensaje);
                productor.agregarObservacion(observacion);
                observaciones++;
            }

            if (yaExistentes.contains(claveIdentidad(sindicato.getId(), nombres, apellidos))) {
                posiblesDuplicados++;
            }

            aGuardar.add(productor);
        }

        /**
         * Una celda de observaciones puede juntar varios motivos separados por
         * coma. El modelo guarda cada motivo como una fila propia, para poder
         * resolverlos de a uno.
         */
        private List<String> motivos(String celda) {
            String texto = Textos.limpiar(celda);
            if (texto == null) {
                return List.of();
            }
            List<String> lista = new ArrayList<>();
            for (String parte : texto.split(",")) {
                String motivo = Textos.limpiar(parte);
                if (motivo == null) {
                    continue;
                }
                lista.add(motivo.length() > MAX_OBSERVACION
                        ? motivo.substring(0, MAX_OBSERVACION)
                        : motivo);
            }
            return lista;
        }

        private void guardar() {
            productorRepository.saveAll(aGuardar);
            // Fuerza los INSERT ahora: si alguna restricción de la base se
            // rompe, que falle acá dentro de la transacción y no al confirmar,
            // cuando ya no se puede informar bien.
            productorRepository.flush();
        }

        private void rechazar(LectorPlanilla.Fila fila, String columna, String valor,
                              String mensaje) {
            erroresTotales++;
            if (errores.size() < MAX_ERRORES) {
                errores.add(ErrorFila.de(fila.numero(), columna, valor, mensaje));
            }
        }

        private ImportacionResponse informe(boolean simulacion, long nanos) {
            return new ImportacionResponse(
                    simulacion,
                    federacionId,
                    federacion.getNombre(),
                    filasLeidas,
                    aGuardar.size(),
                    erroresTotales,
                    aGuardar.size(),
                    lotes,
                    observaciones,
                    List.copyOf(centralesNuevas),
                    List.copyOf(sindicatosNuevos.values()),
                    posiblesDuplicados,
                    List.copyOf(errores),
                    Math.max(0, erroresTotales - errores.size()),
                    nanos / 1_000_000);
        }

        private boolean excede(String valor, int maximo) {
            return valor != null && valor.length() > maximo;
        }

        private String claveSindicato(Long centralId, String nombre) {
            return centralId + "|" + nombre;
        }

        private String claveIdentidad(Long sindicatoId, String nombres, String apellidos) {
            return sindicatoId + "|" + nombres + "|" + (apellidos == null ? "" : apellidos);
        }
    }
}
