-- La reunión pasa a tener varias vueltas de lista y un acta de varias hojas.
--
-- Dos cambios que van juntos porque tocan la misma tabla:
--
--   1. La asistencia colgaba de la reunión, y eso solo permitía pasar lista una
--      vez. En una asamblea se llama lista al empezar, más tarde para los que
--      llegaron con retraso, y a veces al final. Ahora cuelga de la llamada.
--
--   2. El acta era un archivo único en cuatro columnas de `reuniones`. En la
--      práctica el acta es el cuaderno fotografiado hoja por hoja con el
--      teléfono, así que pasa a ser una tabla con orden.
--
-- **Este no es opcional.** `ddl-auto=update` agrega la columna `llamada_id`
-- pero no borra `reunion_id`, que quedó NOT NULL y sin valor por omisión: hasta
-- correr esto, registrar una asistencia falla.
--
-- Es repetible: todo comprueba antes de tocar nada.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < reuniones-llamadas-y-actas.sql

-- ------------------------------------------------- las vueltas de lista

CREATE TABLE IF NOT EXISTS llamadas_lista (
    id         bigint(20) NOT NULL AUTO_INCREMENT,
    reunion_id bigint(20) NOT NULL,
    numero     int(11)    NOT NULL,
    abierta    bit(1)     NOT NULL DEFAULT b'1',
    cerrada_en datetime            DEFAULT NULL,
    nota       varchar(200)        DEFAULT NULL,
    created_at datetime   NOT NULL DEFAULT current_timestamp(),
    updated_at datetime   NOT NULL DEFAULT current_timestamp(),
    estado     bit(1)     NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    UNIQUE KEY uk_llamada_reunion_numero (reunion_id, numero),
    CONSTRAINT fk_llamada_reunion FOREIGN KEY (reunion_id) REFERENCES reuniones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE asistencias
    ADD COLUMN IF NOT EXISTS llamada_id bigint(20) NULL;

-- Las asistencias que ya existen fueron todas de una sola pasada de lista, que
-- es lo único que el sistema permitía. Se convierten en la primera llamada de
-- su reunión: una vuelta abierta perdería el dato de que esa lista ya se tomó,
-- así que se crea cerrada.
--
-- Las columnas de auditoría van explícitas: cuando la tabla la creó Hibernate
-- en vez de este script, quedaron NOT NULL y sin valor por omisión.
--
-- Van preparadas y no sueltas para que el script se pueda correr dos veces: en
-- la segunda corrida `reunion_id` ya no existe, y MariaDB analiza el SQL suelto
-- aunque no lo vaya a ejecutar.
SET @hay_reunion_id := EXISTS(SELECT 1 FROM information_schema.COLUMNS
                               WHERE TABLE_SCHEMA = DATABASE()
                                 AND TABLE_NAME = 'asistencias'
                                 AND COLUMN_NAME = 'reunion_id');

SET @sql := IF(@hay_reunion_id,
    'INSERT INTO llamadas_lista
            (reunion_id, numero, abierta, cerrada_en, nota, created_at, updated_at, estado)
     SELECT DISTINCT a.reunion_id, 1, b''0'', NOW(), ''Lista tomada antes de las vueltas'',
            NOW(), NOW(), b''1''
       FROM asistencias a
      WHERE a.reunion_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM llamadas_lista l
                         WHERE l.reunion_id = a.reunion_id AND l.numero = 1)',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(@hay_reunion_id,
    'UPDATE asistencias a
       JOIN llamadas_lista l ON l.reunion_id = a.reunion_id AND l.numero = 1
        SET a.llamada_id = l.id
      WHERE a.llamada_id IS NULL',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- La clave foránea a la reunión se va primero, y no por prolijidad: InnoDB usa
-- el índice `uk_asistencia` para sostenerla, así que mientras exista la clave
-- el índice no se puede tocar.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asistencias'
                         AND CONSTRAINT_NAME = 'fk_asistencia_reunion'),
    'ALTER TABLE asistencias DROP FOREIGN KEY fk_asistencia_reunion',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- La clave única pasa de (reunión, productor) a (llamada, productor): quien
-- está en la primera vuelta y también en la segunda tiene dos registros, que es
-- justamente lo que se quiere poder ver.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asistencias'
                         AND INDEX_NAME = 'uk_asistencia'
                         AND COLUMN_NAME = 'reunion_id'),
    'ALTER TABLE asistencias DROP INDEX uk_asistencia',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asistencias'
                         AND INDEX_NAME = 'uk_asistencia'),
    'DO 0',
    'ALTER TABLE asistencias ADD CONSTRAINT uk_asistencia UNIQUE (llamada_id, productor_id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asistencias'
                         AND CONSTRAINT_NAME = 'fk_asistencia_llamada'),
    'DO 0',
    'ALTER TABLE asistencias ADD CONSTRAINT fk_asistencia_llamada
        FOREIGN KEY (llamada_id) REFERENCES llamadas_lista (id)');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'asistencias'
                         AND COLUMN_NAME = 'llamada_id' AND IS_NULLABLE = 'YES'),
    'ALTER TABLE asistencias MODIFY COLUMN llamada_id bigint(20) NOT NULL',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- Y recién ahora se va la columna: mientras siguiera NOT NULL, cada asistencia
-- nueva pediría una reunión que ya nadie le pasa.
ALTER TABLE asistencias
    DROP COLUMN IF EXISTS reunion_id;

-- ------------------------------------------------------ el acta por hojas

CREATE TABLE IF NOT EXISTS hojas_acta (
    id           bigint(20)   NOT NULL AUTO_INCREMENT,
    reunion_id   bigint(20)   NOT NULL,
    orden        int(11)      NOT NULL,
    clave        varchar(200) NOT NULL,
    nombre       varchar(160) NOT NULL,
    tipo_mime    varchar(60)  NOT NULL,
    tamano_bytes int(11)      NOT NULL,
    created_at   datetime     NOT NULL DEFAULT current_timestamp(),
    updated_at   datetime     NOT NULL DEFAULT current_timestamp(),
    estado       bit(1)       NOT NULL DEFAULT b'1',
    PRIMARY KEY (id),
    KEY idx_hoja_reunion (reunion_id),
    CONSTRAINT fk_hoja_reunion FOREIGN KEY (reunion_id) REFERENCES reuniones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- El acta que estaba en las cuatro columnas se convierte en la hoja 1. El
-- archivo en disco no se mueve: la clave sigue siendo la misma.
SET @sql := IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reuniones'
                         AND COLUMN_NAME = 'acta_clave'),
    'INSERT INTO hojas_acta (reunion_id, orden, clave, nombre, tipo_mime, tamano_bytes,
                             created_at, updated_at, estado)
     SELECT r.id, 1, r.acta_clave,
            COALESCE(r.acta_nombre, ''acta''),
            COALESCE(r.acta_tipo_mime, ''application/pdf''),
            COALESCE(r.acta_tamano_bytes, 0),
            NOW(), NOW(), b''1''
       FROM reuniones r
      WHERE r.acta_clave IS NOT NULL AND r.acta_clave <> ''''
        AND NOT EXISTS (SELECT 1 FROM hojas_acta h WHERE h.reunion_id = r.id)',
    'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE reuniones
    DROP COLUMN IF EXISTS acta_clave,
    DROP COLUMN IF EXISTS acta_nombre,
    DROP COLUMN IF EXISTS acta_tipo_mime,
    DROP COLUMN IF EXISTS acta_tamano_bytes;

-- ------------------------------------------------- los vetos, por reunión

-- No toda asamblea es para sancionar. La mayoría es informativa, y ofrecer el
-- veto en todas invita a usarlo donde no corresponde: se habilita a propósito.
-- Las reuniones que ya existen quedan en «no», que es lo prudente.
ALTER TABLE reuniones
    ADD COLUMN IF NOT EXISTS vetos_habilitados bit(1) NOT NULL DEFAULT b'0';
