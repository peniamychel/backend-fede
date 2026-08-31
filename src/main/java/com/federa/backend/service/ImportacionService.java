package com.federa.backend.service;

import com.federa.backend.dto.ErrorFila;
import com.federa.backend.dto.ImportacionResponse;
import com.federa.backend.dto.SindicatoNuevo;
import com.federa.backend.model.Central;
import com.federa.backend.model.Federacion;
import com.federa.backend.model.Lote;
import com.federa.backend.model.Productor;
import com.federa.backend.model.Sindicato;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.LoteRepository;
import com.federa.backend.repository.ProductorRepository;
import com.federa.backend.repository.SindicatoRepository;
import com.federa.backend.util.Textos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

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
    private static final Set<String> EXTENSIONES_REFERENCIA = Set.of(
            "A", "B", "C", "D", "E", "F", "G", "H");

    private final LectorPlanilla lector;
    private final FederacionService federacionService;
    private final CentralRepository centralRepository;
    private final SindicatoRepository sindicatoRepository;
    private final ProductorRepository productorRepository;
    private final LoteRepository loteRepository;
    private final NumeradorPadron numerador;
    private final LoteService loteService;
    private final TransactionTemplate transacciones;

    public ImportacionService(LectorPlanilla lector,
                              FederacionService federacionService,
                              CentralRepository centralRepository,
                              SindicatoRepository sindicatoRepository,
                              ProductorRepository productorRepository,
                              LoteRepository loteRepository,
                              NumeradorPadron numerador,
                              LoteService loteService,
                              PlatformTransactionManager gestorTransacciones) {
        this.lector = lector;
        this.federacionService = federacionService;
        this.centralRepository = centralRepository;
        this.sindicatoRepository = sindicatoRepository;
        this.productorRepository = productorRepository;
        this.loteRepository = loteRepository;
        this.numerador = numerador;
        this.loteService = loteService;
        this.transacciones = new TransactionTemplate(gestorTransacciones);
    }

    /**
     * @param simular               si es true no se persiste nada
     * @param crearJerarquia        aprueba dar de alta los sindicatos que falten; las centrales
     *                              nunca se crean durante una importación
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

        /** Centrales por sigla, para impedir que nombre y abreviatura apunten a dos distintas. */
        private final Map<String, Central> centralesPorAbreviatura = new HashMap<>();

        /** Sindicatos por "centralId|nombre": el nombre solo no identifica a ninguno. */
        private final Map<String, Sindicato> sindicatos = new HashMap<>();

        /** Identidades ya existentes, para detectar una segunda carga de la misma planilla. */
        private final Set<String> yaExistentes = new HashSet<>();

        /** Por central, el número que le toca al próximo productor. */
        private final Map<Long, Integer> proximoNumero = new HashMap<>();

        /**
         * Quién se queda con qué lote, para armarlo cuando los dos existan.
         */
        private final List<Entrega> porEntregar = new ArrayList<>();

        private final Set<String> centralesNuevas = new LinkedHashSet<>();
        private final Map<String, SindicatoNuevo> sindicatosNuevos = new LinkedHashMap<>();
        private final List<ErrorFila> errores = new ArrayList<>();
        private final List<Productor> aGuardar = new ArrayList<>();

        /**
         * Los lotes de esta tanda. Van aparte porque hay que insertarlos antes
         * que las tenencias que los apuntan.
         */
        private final List<Lote> nuevosLotes = new ArrayList<>();

        private int erroresTotales;
        private int filasLeidas;
        private int lotes;
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
                centrales.put(Textos.normalizar(central.getNombre()), central);
                String abreviatura = Textos.normalizar(central.getAbreviatura());
                if (abreviatura != null) {
                    centralesPorAbreviatura.put(abreviatura, central);
                }
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
            String nombreCentral = Textos.normalizarParaGuardar(fila.central());
            String abreviatura = Textos.normalizar(fila.abreviatura());
            String nombreSindicato = Textos.normalizarParaGuardar(fila.sindicato());
            String nombres = Textos.normalizarParaGuardar(fila.nombres());
            String apellidos = Textos.normalizarParaGuardar(fila.apellidos());
            String ci = Textos.limpiar(fila.ci());
            String numeroLote = Textos.limpiar(fila.numeroLote());
            String extensionReferencia = Textos.normalizar(fila.extension());
            EstadoLote clasificacion = clasificacionImportada(fila.clasificacion());

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
            if (abreviatura != null && (abreviatura.length() > 3
                    || !abreviatura.matches("[A-Z0-9]+"))) {
                rechazar(fila, "abreviatura", fila.abreviatura(),
                        "la abreviatura debe tener hasta 3 letras o números");
                return;
            }
            if (extensionReferencia != null
                    && !EXTENSIONES_REFERENCIA.contains(extensionReferencia)) {
                rechazar(fila, "extension", fila.extension(),
                        "la extensión solo puede ser una letra entre A y H");
                return;
            }
            if (fila.clasificacion() != null && clasificacion == null) {
                rechazar(fila, "clasificacion", fila.clasificacion(),
                        "la clasificación debe ser SIN SISTEMA, SISTEMA, BLANCO, "
                                + "FRACCIONADO, DETALLISTA o COMUNITARIO");
                return;
            }

            Central central = centrales.get(Textos.normalizar(nombreCentral));
            if (central == null) {
                centralesNuevas.add(nombreCentral);
                String detalleAbreviatura = abreviatura == null
                        ? " y asignarle su abreviatura"
                        : " con la abreviatura " + abreviatura;
                rechazar(fila, "central", nombreCentral,
                        "la central " + nombreCentral + " no está registrada en "
                                + federacion.getNombre() + ". Debés crearla manualmente"
                                + detalleAbreviatura + " antes de importar estas filas");
                return;
            }

            if (abreviatura != null) {
                Central dueñaDeAbreviatura = centralesPorAbreviatura.get(abreviatura);
                String abreviaturaRegistrada = Textos.normalizar(central.getAbreviatura());
                if (abreviaturaRegistrada == null) {
                    rechazar(fila, "abreviatura", fila.abreviatura(),
                            "la central " + central.getNombre() + " todavía no tiene abreviatura. "
                                    + "Asignale " + abreviatura + " manualmente antes de importar");
                    return;
                }
                if (!abreviatura.equals(abreviaturaRegistrada)) {
                    String detalle = dueñaDeAbreviatura == null
                            ? "la abreviatura registrada es " + abreviaturaRegistrada
                            : "esa abreviatura pertenece a " + dueñaDeAbreviatura.getNombre();
                    rechazar(fila, "abreviatura", fila.abreviatura(),
                            "la abreviatura no corresponde a " + central.getNombre() + ": "
                                    + detalle);
                    return;
                }
            }

            String claveSind = claveSindicato(central.getId(), nombreSindicato);
            Sindicato sindicato = sindicatos.get(claveSind);
            if (sindicato == null) {
                sindicatosNuevos.putIfAbsent(claveSind,
                        new SindicatoNuevo(nombreCentral, nombreSindicato));
                if (!crearJerarquia) {
                    rechazar(fila, "sindicato", nombreSindicato,
                            "el sindicato " + nombreSindicato + " todavía no existe en la central "
                                    + nombreCentral + " y no fue aprobado para crearse");
                    return;
                }
                sindicato = new Sindicato();
                sindicato.setNombre(nombreSindicato);
                sindicato.setCentral(central);
                sindicato = sindicatoRepository.save(sindicato);
                sindicatos.put(claveSind, sindicato);
            }

            Productor productor = new Productor();
            productor.setNombres(nombres);
            productor.setApellidos(apellidos);
            productor.setCi(ci);
            productor.setRevisionSiePendiente(true);
            productor.setSindicato(sindicato);
            productor.setCorrelativo(numerarEn(central));

            if (numeroLote != null) {
                // El lote se da de alta en el sindicato —ahí está la tierra— y
                // el productor de la planilla queda como su primer tenedor. La
                // fecha de inicio es hoy: la planilla no dice desde cuándo lo
                // tiene, y inventarla sería peor que dejar constancia de que el
                // período empieza cuando se cargó.
                //
                // La tenencia no se arma todavía: engancha al productor con el
                // lote, y los dos están sin guardar. Queda anotada y se arma en
                // guardar(), cuando las dos puntas ya existen en la base.
                Lote lote = new Lote();
                lote.setNumero(numeroLote);
                lote.setSindicato(sindicato);
                EstadoLote clasificacionDefinitiva = clasificacion == null
                        ? EstadoLote.SIN_SISTEMA
                        : clasificacion;
                lote.setEstadoLote(clasificacionDefinitiva);
                lote.setEstadoOriginal(clasificacionDefinitiva == EstadoLote.CON_SISTEMA
                        ? "SISTEMA"
                        : clasificacionDefinitiva.name().replace('_', ' '));
                // EXTENSION es solo una referencia del archivo. No se escribe
                // en Lote.extension: la letra visible se recalcula para todo el
                // grupo y puede cambiar si uno de sus miembros tiene SISTEMA.
                nuevosLotes.add(lote);
                porEntregar.add(new Entrega(productor, lote, extensionReferencia, fila.numero()));
                lotes++;
            }

            // La columna «Observaciones» de la planilla se sigue leyendo para no
            // romper el formato del archivo real, pero ya no se guarda: la
            // bandeja de observaciones salió del sistema.
            if (yaExistentes.contains(claveIdentidad(sindicato.getId(), nombres, apellidos))) {
                posiblesDuplicados++;
            }

            aGuardar.add(productor);
        }

        /**
         * El número que le toca dentro de su central, y anota el que sigue.
         * <p>
         * La cuenta se lleva en memoria y no consultando el máximo por cada
         * fila: los productores de esta corrida todavía no están escritos, así
         * que la base seguiría devolviendo el mismo máximo y las cuatrocientas
         * filas saldrían con el mismo número.
         * <p>
         * Arranca donde quedó la central —en una vacía, en 1— para que importar
         * dos planillas seguidas continúe la serie en vez de pisarla.
         */
        private int numerarEn(Central central) {
            int numero = proximoNumero.computeIfAbsent(central.getId(),
                    id -> numerador.siguiente(id));
            proximoNumero.put(central.getId(), NumeradorPadron.despuesDe(numero));
            return numero;
        }

        private void guardar() {
            // Las dos puntas antes que el nudo. La tenencia referencia al lote
            // y al productor, los dos con NOT NULL, así que hasta que las dos
            // filas existan no se puede insertar.
            //
            // Y las tenencias se arman recién ahora, no al leer la fila: como
            // `Lote` propaga en cascada a sus tenencias, guardar un lote que ya
            // tuviera una colgando intentaba escribir una tenencia que apunta a
            // un productor todavía sin guardar, y Hibernate lo rechazaba.
            loteRepository.saveAll(nuevosLotes);
            productorRepository.saveAll(aGuardar);

            LocalDate hoy = LocalDate.now();
            List<Entrega> entregasOrdenadas = porEntregar.stream()
                    .sorted(Comparator
                            .comparing((Entrega e) -> claveGrupo(e.lote()))
                            .thenComparingInt(e -> e.lote().getEstadoLote()
                                    == EstadoLote.CON_SISTEMA ? 0 : 1)
                            .thenComparingInt(e -> ordenExtension(e.extensionReferencia()))
                            .thenComparingInt(Entrega::fila))
                    .toList();
            Map<String, Lote> grupos = new LinkedHashMap<>();
            for (Entrega entrega : entregasOrdenadas) {
                entrega.productor().tomarLote(entrega.lote(), hoy);
                grupos.putIfAbsent(claveGrupo(entrega.lote()), entrega.lote());
            }

            // Fuerza los INSERT ahora: si alguna restricción de la base se
            // rompe, que falle acá dentro de la transacción y no al confirmar,
            // cuando ya no se puede informar bien.
            productorRepository.flush();

            // Aplica la misma regla que usa el alta manual: un solo productor
            // queda sin letra; desde dos se asigna A-H, con SISTEMA primero.
            // También considera los productores que ya estaban en la base.
            for (Lote referencia : grupos.values()) {
                loteService.recalcularCodigosDelGrupo(referencia);
            }
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
            return centralId + "|" + Textos.normalizar(nombre);
        }

        private String claveIdentidad(Long sindicatoId, String nombres, String apellidos) {
            return sindicatoId + "|" + Textos.normalizar(nombres) + "|"
                    + (apellidos == null ? "" : Textos.normalizar(apellidos));
        }

        private String claveGrupo(Lote lote) {
            return lote.getSindicato().getId() + "|" + Textos.normalizar(lote.getNumero());
        }
    }

    /** Un productor de la planilla y el lote que la planilla le pone. */
    private record Entrega(Productor productor, Lote lote, String extensionReferencia, int fila) {
    }

    static EstadoLote clasificacionImportada(String valor) {
        String normalizada = Textos.normalizar(valor);
        if (normalizada == null) {
            return null;
        }
        return switch (normalizada) {
            case "SISTEMA" -> EstadoLote.CON_SISTEMA;
            case "SIN SISTEMA" -> EstadoLote.SIN_SISTEMA;
            case "BLANCO" -> EstadoLote.BLANCO;
            case "FRACCIONADO" -> EstadoLote.FRACCIONADO;
            case "DETALLISTA" -> EstadoLote.DETALLISTA;
            case "COMUNITARIO" -> EstadoLote.COMUNITARIO;
            default -> null;
        };
    }

    private static int ordenExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return EXTENSIONES_REFERENCIA.size();
        }
        return extension.charAt(0) - 'A';
    }
}
