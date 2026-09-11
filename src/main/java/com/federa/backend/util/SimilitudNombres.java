package com.federa.backend.util;

import java.util.ArrayList;
import java.util.List;

/** Compara identidades tolerando tildes, orden de palabras y errores pequeños. */
public final class SimilitudNombres {

    private SimilitudNombres() {
    }

    /**
     * Devuelve un porcentaje entre 0 y 100.
     *
     * Cada palabra se empareja una sola vez con la palabra más parecida del otro
     * nombre. El divisor es la mayor cantidad de palabras, de modo que agregar o
     * quitar nombres también reduce el resultado. Así, "JUAN CARLOS PEREZ" y
     * "PEREZ JUAN CARLOS" dan 100%, mientras que identidades distintas no se
     * benefician solamente por tener una palabra común.
     */
    public static int porcentaje(String izquierda, String derecha) {
        List<String> a = palabras(izquierda);
        List<String> b = palabras(derecha);
        if (a.isEmpty() || b.isEmpty()) return 0;

        List<String> menores = a.size() <= b.size() ? a : b;
        List<String> disponibles = new ArrayList<>(a.size() <= b.size() ? b : a);
        double suma = 0;
        for (String palabra : menores) {
            int mejorIndice = -1;
            double mejor = -1;
            for (int i = 0; i < disponibles.size(); i++) {
                double actual = similitudPalabra(palabra, disponibles.get(i));
                if (actual > mejor) {
                    mejor = actual;
                    mejorIndice = i;
                }
            }
            if (mejorIndice >= 0) {
                suma += mejor;
                disponibles.remove(mejorIndice);
            }
        }
        return (int) Math.round(100d * suma / Math.max(a.size(), b.size()));
    }

    private static List<String> palabras(String valor) {
        String normalizado = Textos.normalizar(valor);
        if (normalizado == null) return List.of();
        String soloLetras = normalizado.replaceAll("[^A-Z0-9Ñ]+", " ").trim();
        return soloLetras.isEmpty() ? List.of() : List.of(soloLetras.split("\\s+"));
    }

    private static double similitudPalabra(String a, String b) {
        int maximo = Math.max(a.length(), b.length());
        if (maximo == 0) return 1;
        return 1d - ((double) distanciaLevenshtein(a, b) / maximo);
    }

    private static int distanciaLevenshtein(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) anterior[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1),
                        anterior[j - 1] + costo);
            }
            int[] intercambio = anterior;
            anterior = actual;
            actual = intercambio;
        }
        return anterior[b.length()];
    }
}
