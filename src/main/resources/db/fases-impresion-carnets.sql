-- Referencia para instalaciones que no utilicen spring.jpa.hibernate.ddl-auto=update.
ALTER TABLE centrales
    ADD COLUMN fase_impresion_activa_numero INT NULL,
    ADD COLUMN ultima_fase_impresion_numero INT NOT NULL DEFAULT 0;

ALTER TABLE productores
    ADD COLUMN fase_impresion_pendiente BIT NOT NULL DEFAULT 0,
    ADD COLUMN reimpresion_fase_pendiente BIT NOT NULL DEFAULT 0;

CREATE TABLE fases_impresion_carnet (
    id BIGINT NOT NULL AUTO_INCREMENT,
    central_id BIGINT NOT NULL,
    numero INT NOT NULL,
    estado VARCHAR(16) NOT NULL,
    abierta_en DATETIME NOT NULL,
    cerrada_en DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fase_impresion_central_numero UNIQUE (central_id, numero),
    CONSTRAINT fk_fase_impresion_central FOREIGN KEY (central_id) REFERENCES centrales(id),
    KEY idx_fase_impresion_central_estado (central_id, estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE productores_fase_impresion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fase_id BIGINT NOT NULL,
    productor_id BIGINT NOT NULL,
    agregado_en DATETIME NOT NULL,
    pendiente BIT NOT NULL,
    reimpresion BIT NOT NULL,
    impresiones_en_fase INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_productor_fase_impresion UNIQUE (fase_id, productor_id),
    CONSTRAINT fk_productor_fase_fase FOREIGN KEY (fase_id)
        REFERENCES fases_impresion_carnet(id),
    CONSTRAINT fk_productor_fase_productor FOREIGN KEY (productor_id)
        REFERENCES productores(id),
    KEY idx_productor_fase_fase (fase_id),
    KEY idx_productor_fase_productor (productor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE grupos_impresion_credencial
    ADD COLUMN fase_id BIGINT NULL,
    ADD CONSTRAINT fk_grupo_impresion_fase FOREIGN KEY (fase_id)
        REFERENCES fases_impresion_carnet(id),
    ADD KEY idx_grupo_impresion_fase (fase_id);

ALTER TABLE detalles_grupo_impresion_credencial
    ADD COLUMN fase_pendiente_anterior BIT NULL,
    ADD COLUMN fase_impresiones_anteriores INT NULL;
