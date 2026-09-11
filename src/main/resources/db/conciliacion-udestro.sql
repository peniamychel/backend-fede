-- Estructura aditiva para guardar la propuesta y sus decisiones antes de aplicar.
-- No clasifica, crea ni observa productores por sí sola.

CREATE TABLE IF NOT EXISTS conciliaciones_udestro (
    id BIGINT NOT NULL AUTO_INCREMENT,
    federacion_id BIGINT NOT NULL,
    nombre_archivo VARCHAR(180) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    filas_excel INT NOT NULL,
    fase VARCHAR(20) NOT NULL,
    aplicada_en DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    estado BIT NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    KEY idx_conciliacion_udestro_federacion (federacion_id),
    KEY idx_conciliacion_udestro_estado (fase),
    CONSTRAINT fk_conciliacion_udestro_federacion
        FOREIGN KEY (federacion_id) REFERENCES federaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS filas_conciliacion_udestro (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conciliacion_id BIGINT NOT NULL,
    numero_fila INT NULL,
    central_nombre VARCHAR(60) NULL,
    sindicato_nombre VARCHAR(60) NULL,
    nombres_udestro VARCHAR(60) NULL,
    apellidos_udestro VARCHAR(60) NULL,
    ci VARCHAR(20) NULL,
    central_destino_id BIGINT NULL,
    sindicato_destino_id BIGINT NULL,
    sindicato_nuevo BIT NOT NULL DEFAULT b'0',
    accion VARCHAR(32) NOT NULL,
    motivo VARCHAR(600) NULL,
    productor_id BIGINT NULL,
    productor_actualizado_en DATETIME NULL,
    clasificacion_anterior VARCHAR(30) NULL,
    candidatos_ids VARCHAR(1000) NULL,
    decision_conflicto VARCHAR(24) NULL,
    productor_seleccionado_id BIGINT NULL,
    similitud_nombre INT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    estado BIT NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    KEY idx_fila_udestro_conciliacion (conciliacion_id),
    KEY idx_fila_udestro_accion (conciliacion_id, accion),
    CONSTRAINT fk_fila_udestro_conciliacion
        FOREIGN KEY (conciliacion_id) REFERENCES conciliaciones_udestro(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
