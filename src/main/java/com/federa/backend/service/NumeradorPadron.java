package com.federa.backend.service;

import com.federa.backend.model.Productor;
import com.federa.backend.exception.RecursoNoEncontradoException;
import com.federa.backend.repository.CentralRepository;
import com.federa.backend.repository.ProductorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reparte los números que llevan los productores dentro de su central: el "1"
 * de {@code 2-IVI-1}.
 * <p>
 * Vive aparte de {@link ProductorService} y {@link SindicatoService} porque los
 * dos necesitan numerar y el productor ya depende del sindicato: ponerlo en
 * cualquiera de los dos haría que se dependieran mutuamente.
 */
@Service
public class NumeradorPadron {

    private final ProductorRepository productorRepository;
    private final CentralRepository centralRepository;

    public NumeradorPadron(ProductorRepository productorRepository,
                           CentralRepository centralRepository) {
        this.productorRepository = productorRepository;
        this.centralRepository = centralRepository;
    }

    /**
     * El número que sigue en esa central.
     * <p>
     * Es el máximo entregado más uno, leído dentro de la transacción de quien
     * llama. Dos altas simultáneas en la misma central podrían sacar el mismo
     * número, y no hay clave única que lo impida: la central no es una columna
     * de productores sino algo a lo que se llega navegando por el sindicato. En
     * un padrón que se carga a mano, de a un productor por vez, no se da; si
     * alguna vez se carga en paralelo, esto hay que revisarlo.
     */
    @Transactional
    public int siguiente(Long centralId) {
        return siguiente(centralId, null);
    }

    /** El que sigue, sin contar a los productores de un sindicato. */
    @Transactional
    public int siguiente(Long centralId, Long sindicatoExcluido) {
        // El máximo y la posterior escritura del productor ocurren dentro de
        // la misma transacción. Bloquear la central impide que dos altas
        // simultáneas lean el mismo máximo y reciban el mismo código.
        centralRepository.findByIdParaNumerar(centralId)
                .orElseThrow(() -> new RecursoNoEncontradoException("central", centralId));
        Integer maximo = productorRepository
                .maxCorrelativoDeCentral(centralId, sindicatoExcluido);
        return admisible(maximo == null ? 1 : maximo + 1);
    }

    /**
     * El primer número entregable a partir de este, salteando los que llevan
     * 666.
     * <p>
     * No es una superstición del sistema: es de la gente. Nadie quiere que su
     * credencial diga 666, y con centrales que pasan de tres mil afiliados el
     * número aparecería cuatro veces —666, 1666, 2666, 3666— más los tramos de
     * 6660 a 6669 si alguna vez se llega. Saltearlos cuesta un puñado de
     * números en una serie que no tiene por qué ser continua; discutirlo con
     * cada afiliado al que le toque cuesta más.
     * <p>
     * Se mira el número escrito, no el valor: lo que molesta es lo que se lee
     * impreso en la credencial.
     */
    public static int admisible(int numero) {
        int candidato = numero;
        while (Integer.toString(candidato).contains(PROHIBIDO)) {
            candidato++;
        }
        return candidato;
    }

    /** El que va después de este, ya salteado. Para numerar en serie. */
    public static int despuesDe(int numero) {
        return admisible(numero + 1);
    }

    /** Si ese número se puede entregar. */
    public static boolean esAdmisible(int numero) {
        return !Integer.toString(numero).contains(PROHIBIDO);
    }

    private static final String PROHIBIDO = "666";

    /**
     * Renumera a todos los productores de un sindicato que acaba de mudarse.
     * <p>
     * Los números que traían pertenecían a la numeración de la central que
     * dejaron, así que en la nueva chocarían con los que ya están dados. Se les
     * da un tramo nuevo, en el orden en que fueron cargados para que el
     * resultado sea el mismo si esto se corre dos veces.
     * <p>
     * Su código cambia, y con él la credencial impresa: mudar un sindicato de
     * central obliga a reimprimir las de su gente.
     */
    @Transactional
    public void renumerar(Long sindicatoId, Long centralDestinoId) {
        List<Productor> productores =
                productorRepository.findBySindicatoIdOrderByIdAsc(sindicatoId);
        if (productores.isEmpty()) {
            return;
        }
        int numero = siguiente(centralDestinoId, sindicatoId);
        for (Productor productor : productores) {
            productor.setCorrelativo(numero);
            numero = despuesDe(numero);
        }
        productorRepository.flush();
    }
}
