-- El lote pasa a pertenecer al sindicato, y quién lo tiene se vuelve historial.
--
-- El modelo anterior colgaba el lote del productor, y eso decía algo falso: si
-- el productor cambiaba de sindicato, su lote lo seguía. La tierra no se mueve.
-- Lo que cambia es quién la tiene, y eso es una sucesión de períodos, no un
-- campo.
--
-- Lo mismo vale para los sistemas: son un agregado del lote que se puede
-- vender y trasladar a otro, así que también son períodos.
--
-- Es repetible: todo va condicionado sobre information_schema.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < lotes-tenencia-y-sistemas.sql

-- ------------------------------------------------- el lote y su sindicato

ALTER TABLE lotes ADD COLUMN IF NOT EXISTS sindicato_id bigint(20) NULL;

-- Cada lote hereda el sindicato del productor que lo tenía. Es la única
-- información de ubicación que existe hoy, y es correcta: el productor estaba
-- en el sindicato donde está su tierra.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
                         AND COLUMN_NAME = 'productor_id'),
    'UPDATE lotes l JOIN productores p ON p.id = l.productor_id
        SET l.sindicato_id = p.sindicato_id
      WHERE l.sindicato_id IS NULL',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------ tenencia del lote

CREATE TABLE IF NOT EXISTS tenencias_lote (
    id            bigint(20)  NOT NULL AUTO_INCREMENT,
    lote_id       bigint(20)  NOT NULL,
    productor_id  bigint(20)  NOT NULL,
    desde         date        NOT NULL,
    hasta         date            NULL,
    -- TRUE mientras la tiene, NULL cuando la dejó. En MariaDB una clave única
    -- admite repetir los nulos, así que con esto la base permite todos los
    -- períodos cerrados que hagan falta pero rechaza dos tenedores vigentes
    -- del mismo lote.
    vigente       bit(1)          NULL,
    motivo        varchar(16)     NULL,
    observaciones varchar(300)    NULL,
    created_at    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado        bit(1)      NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenencia_lote_vigente (lote_id, vigente),
    KEY idx_tenencia_lote_productor (productor_id),
    CONSTRAINT fk_tenencia_lote_lote      FOREIGN KEY (lote_id)      REFERENCES lotes (id),
    CONSTRAINT fk_tenencia_lote_productor FOREIGN KEY (productor_id) REFERENCES productores (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- El tenedor actual de cada lote pasa a ser su primer período. La fecha de
-- inicio es la de alta del lote: es lo más cercano a la verdad que hay.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
                         AND COLUMN_NAME = 'productor_id'),
    'INSERT INTO tenencias_lote (lote_id, productor_id, desde, vigente, created_at, updated_at, estado)
     SELECT l.id, l.productor_id, DATE(l.created_at), b''1'', NOW(), NOW(), b''1''
       FROM lotes l
      WHERE l.productor_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM tenencias_lote t WHERE t.lote_id = l.id)',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- Ya no hace falta: quién tiene el lote lo dice la tenencia vigente.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
                         AND CONSTRAINT_NAME = 'fk_lote_productor'),
    'ALTER TABLE lotes DROP FOREIGN KEY fk_lote_productor', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE lotes DROP COLUMN IF EXISTS productor_id;

-- Recién ahora, con todos los lotes ubicados, la columna puede exigirse.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
                         AND COLUMN_NAME = 'sindicato_id' AND IS_NULLABLE = 'YES'),
    'ALTER TABLE lotes MODIFY COLUMN sindicato_id bigint(20) NOT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
                         AND CONSTRAINT_NAME = 'fk_lote_sindicato'),
    'DO 0',
    'ALTER TABLE lotes ADD CONSTRAINT fk_lote_sindicato
        FOREIGN KEY (sindicato_id) REFERENCES sindicatos (id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
                         AND INDEX_NAME = 'idx_lote_sindicato'),
    'DO 0', 'ALTER TABLE lotes ADD INDEX idx_lote_sindicato (sindicato_id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- --------------------------------------------------------------- sistemas

CREATE TABLE IF NOT EXISTS sistemas (
    id            bigint(20)   NOT NULL AUTO_INCREMENT,
    codigo        varchar(20)  NOT NULL,
    descripcion   varchar(200)     NULL,
    created_at    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado        bit(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sistema_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Dónde estuvo cada sistema y cuándo. Las dos claves únicas dicen las dos
-- reglas: un sistema está en un solo lote a la vez, y un lote tiene a lo sumo
-- un sistema.
CREATE TABLE IF NOT EXISTS tenencias_sistema (
    id            bigint(20)  NOT NULL AUTO_INCREMENT,
    sistema_id    bigint(20)  NOT NULL,
    lote_id       bigint(20)  NOT NULL,
    desde         date        NOT NULL,
    hasta         date            NULL,
    vigente       bit(1)          NULL,
    motivo        varchar(16)     NULL,
    observaciones varchar(300)    NULL,
    created_at    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado        bit(1)      NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenencia_sistema_vigente (sistema_id, vigente),
    UNIQUE KEY uk_tenencia_lote_sistema_vigente (lote_id, vigente),
    CONSTRAINT fk_tenencia_sistema_sistema FOREIGN KEY (sistema_id) REFERENCES sistemas (id),
    CONSTRAINT fk_tenencia_sistema_lote    FOREIGN KEY (lote_id)    REFERENCES lotes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
