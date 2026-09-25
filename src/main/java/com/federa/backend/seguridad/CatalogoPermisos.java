package com.federa.backend.seguridad;

import java.util.List;

public final class CatalogoPermisos {
    private CatalogoPermisos() {}

    public record Definicion(String codigo, String nombre, String grupo) {}

    public static final List<Definicion> TODOS = List.of(
            new Definicion("PRODUCTORES_VER", "Ver productores", "Productores"),
            new Definicion("FOTOS_PRODUCTORES_EDITAR", "Gestionar solo fotografías de productores", "Productores"),
            new Definicion("PRODUCTORES_OBSERVAR", "Marcar productores como observados y editar su nota", "Productores"),
            new Definicion("NUMERO_LOTE_EDITAR", "Asignar y editar solo el número de lote", "Productores"),
            new Definicion("PRODUCTORES_EDITAR", "Crear y editar productores", "Productores"),
            new Definicion("PRODUCTORES_ELIMINAR", "Mover a papelera y restaurar productores", "Productores"),
            new Definicion("SIE_REVISAR", "Consultar y aprobar revisión SIE", "Productores"),
            new Definicion("IMPORTAR_PADRON", "Importar padrón", "Importaciones"),
            new Definicion("CONCILIAR_UDESTRO", "Conciliar datos UDESTRO", "Importaciones"),
            new Definicion("JERARQUIA_EDITAR", "Editar centrales y sindicatos", "Jerarquía"),
            new Definicion("LOTES_EDITAR", "Editar parcelas y lotes", "Jerarquía"),
            new Definicion("DIRECTORIOS_EDITAR", "Editar directorios", "Jerarquía"),
            new Definicion("IMAGENES_EDITAR", "Subir y editar imágenes", "Archivos"),
            new Definicion("INFORMES_DESCARGAR", "Descargar informes", "Informes"),
            new Definicion("CARNETS_IMPRIMIR", "Imprimir carnets", "Carnets"),
            new Definicion("CARNETS_DISENO", "Editar diseño de carnet", "Carnets"),
            new Definicion("FASES_GESTIONAR", "Gestionar fases de impresión", "Carnets"),
            new Definicion("REUNIONES_GESTIONAR", "Gestionar reuniones", "Otros"),
            new Definicion("RESPALDOS_ADMINISTRAR", "Administrar respaldos", "Administración"),
            new Definicion("USUARIOS_ADMINISTRAR", "Administrar usuarios, roles y permisos", "Administración")
    );
}
