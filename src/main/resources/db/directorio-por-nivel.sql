-- El directorio deja de ser solo de los sindicatos: ahora también las
-- centrales y la federación tienen el suyo.
--
-- Un cargo pasa a colgar de uno de los tres niveles. En la tabla eso son tres
-- claves foráneas de las que exactamente una está cargada, más una columna
-- `ambito` que dice cuál. Se guarda el ámbito en vez de deducirlo de qué FK no
-- es nula porque así las consultas y la restricción son directas.
--
-- Hace falta correrlo a mano porque `ddl-auto=update` no relaja un NOT NULL ni
-- crea claves únicas sobre columnas que ya existen: sindicato_id tiene que
-- pasar a admitir nulos para que un cargo de central pueda no tener sindicato.
--
-- Es repetible: las comprobaciones son sobre information_schema.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < directorio-por-nivel.sql

-- --------------------------------------------------------------- columnas

ALTER TABLE cargos
    ADD COLUMN IF NOT EXISTS ambito varchar(12) NULL,
    ADD COLUMN IF NOT EXISTS central_id bigint(20) NULL,
    ADD COLUMN IF NOT EXISTS federacion_id bigint(20) NULL;

-- Todo lo que ya existía es de un sindicato.
UPDATE cargos SET ambito = 'SINDICATO' WHERE ambito IS NULL;

ALTER TABLE cargos
    MODIFY COLUMN ambito varchar(12) NOT NULL,
    MODIFY COLUMN sindicato_id bigint(20) NULL;

-- Los dos tipos nuevos. Se pasa a varchar en vez de ampliar el enum: es lo que
-- mapea Hibernate para un @Enumerated(STRING) y evita tener que tocar la
-- columna cada vez que aparezca un cargo más.
ALTER TABLE cargos
    MODIFY COLUMN cargo varchar(12) NOT NULL;

-- --------------------------------------------------------------- índices

-- Las tres claves de vigencia, una por nivel. Funcionan por separado gracias a
-- que en MariaDB una clave única admite repetir los nulos: un cargo de central
-- tiene sindicato_id nulo, así que no compite en la clave del sindicato.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND INDEX_NAME = 'uk_cargo_central_vigente'),
    'DO 0',
    'ALTER TABLE cargos ADD CONSTRAINT uk_cargo_central_vigente UNIQUE (central_id, cargo, vigente)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND INDEX_NAME = 'uk_cargo_federacion_vigente'),
    'DO 0',
    'ALTER TABLE cargos ADD CONSTRAINT uk_cargo_federacion_vigente UNIQUE (federacion_id, cargo, vigente)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- Y la que impide que la misma persona ocupe dos cargos a la vez, en cualquier
-- nivel. Es la regla que pidió el usuario, garantizada por el motor: no
-- depende de que el código se acuerde de comprobarla ni de que dos peticiones
-- simultáneas no se crucen.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND INDEX_NAME = 'uk_cargo_productor_vigente'),
    'DO 0',
    'ALTER TABLE cargos ADD CONSTRAINT uk_cargo_productor_vigente UNIQUE (productor_id, vigente)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND INDEX_NAME = 'idx_cargo_central'),
    'DO 0',
    'ALTER TABLE cargos ADD INDEX idx_cargo_central (central_id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND INDEX_NAME = 'idx_cargo_federacion'),
    'DO 0',
    'ALTER TABLE cargos ADD INDEX idx_cargo_federacion (federacion_id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------- foráneas

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND CONSTRAINT_NAME = 'fk_cargo_central'),
    'DO 0',
    'ALTER TABLE cargos ADD CONSTRAINT fk_cargo_central FOREIGN KEY (central_id) REFERENCES centrales (id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargos'
                         AND CONSTRAINT_NAME = 'fk_cargo_federacion'),
    'DO 0',
    'ALTER TABLE cargos ADD CONSTRAINT fk_cargo_federacion FOREIGN KEY (federacion_id) REFERENCES federaciones (id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
