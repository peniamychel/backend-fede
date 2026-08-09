-- Código de credencial para cada productor, y las tablas de reuniones.
--
-- Hace falta a mano por lo de siempre: `ddl-auto=update` agrega columnas pero
-- no las rellena, y acá el código no puede quedar en null —es lo que se
-- escanea— ni repetido.
--
-- Es repetible: las comprobaciones son sobre information_schema.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < codigo-y-reuniones.sql

-- ------------------------------------------------- código del productor

ALTER TABLE productores
    ADD COLUMN IF NOT EXISTS codigo varchar(16) NULL;

-- Diez hexadecimales en mayúscula, de cinco bytes aleatorios.
--
-- Va con RANDOM_BYTES y no con UUID(): el UUID de MariaDB es de versión 1, o
-- sea que arranca con la marca de tiempo, y todas las filas generadas en la
-- misma corrida salían con el mismo prefijo. Códigos que se parecen entre sí
-- son códigos que se pueden adivinar a partir de otro, y con el código solo
-- alcanza para que a alguien le tomen asistencia.
--
-- Para 4.051 filas la probabilidad de que dos coincidan es despreciable, y si
-- aun así pasara, la clave única de abajo lo diría en voz alta.
UPDATE productores
   SET codigo = UPPER(HEX(RANDOM_BYTES(5)))
 WHERE codigo IS NULL OR codigo = '';

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'productores'
                         AND INDEX_NAME = 'uk_productor_codigo'),
    'DO 0',
    'ALTER TABLE productores ADD CONSTRAINT uk_productor_codigo UNIQUE (codigo)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- --------------------------------------------------------------- reuniones

CREATE TABLE IF NOT EXISTS reuniones (
    id             bigint(20)   NOT NULL AUTO_INCREMENT,
    tipo           varchar(24)  NOT NULL,
    titulo         varchar(120) NOT NULL,
    fecha          date         NOT NULL,
    lugar          varchar(120)     NULL,
    observaciones  varchar(500)     NULL,
    cerrada        bit(1)       NOT NULL DEFAULT b'0',
    sindicato_id   bigint(20)       NULL,
    central_id     bigint(20)       NULL,
    federacion_id  bigint(20)       NULL,
    created_at     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado         bit(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    KEY idx_reunion_fecha (fecha),
    KEY idx_reunion_sindicato (sindicato_id),
    KEY idx_reunion_central (central_id),
    KEY idx_reunion_federacion (federacion_id),
    CONSTRAINT fk_reunion_sindicato  FOREIGN KEY (sindicato_id)  REFERENCES sindicatos (id),
    CONSTRAINT fk_reunion_central    FOREIGN KEY (central_id)    REFERENCES centrales (id),
    CONSTRAINT fk_reunion_federacion FOREIGN KEY (federacion_id) REFERENCES federaciones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Una fila por persona presente. La clave única es la que hace que escanear
-- dos veces el mismo carnet no cuente doble: la segunda choca contra la base
-- en vez de depender de que el código se acuerde de comprobarlo.
CREATE TABLE IF NOT EXISTS asistencias (
    id            bigint(20) NOT NULL AUTO_INCREMENT,
    reunion_id    bigint(20) NOT NULL,
    productor_id  bigint(20) NOT NULL,
    registrada_en datetime   NOT NULL,
    created_at    datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado        bit(1)     NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asistencia (reunion_id, productor_id),
    KEY idx_asistencia_productor (productor_id),
    CONSTRAINT fk_asistencia_reunion   FOREIGN KEY (reunion_id)   REFERENCES reuniones (id),
    CONSTRAINT fk_asistencia_productor FOREIGN KEY (productor_id) REFERENCES productores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
