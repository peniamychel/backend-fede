-- Agrega created_at, updated_at y estado a todas las tablas.
--
-- Solo hace falta en una base que YA tiene datos. En una base vacía Hibernate
-- crea las tres columnas solo, porque están en EntidadAuditable, de la que
-- heredan todas las entidades.
--
-- Por qué hace falta: `ddl-auto=update` agrega columnas `not null` sin valor
-- por defecto. Sobre una tabla con filas, MariaDB las rellena con el valor
-- implícito del tipo, y eso deja fechas '0000-00-00 00:00:00' —que JPA después
-- no puede leer como LocalDateTime— y estado en 0, es decir todos los
-- registros existentes deshabilitados de golpe. Este script arregla las dos
-- cosas.
--
-- Sobre las fechas de las filas viejas: llevan la fecha en que se corrió esta
-- migración, no la de su alta real, que no está registrada en ningún lado. De
-- las nuevas en adelante son ciertas.
--
-- El DEFAULT se deja puesto a propósito, aunque Hibernate no lo genere: así un
-- INSERT hecho a mano desde el cliente de MariaDB sigue funcionando.
--
-- Es repetible, y de dos maneras distintas. Las columnas van con IF NOT
-- EXISTS. Y el relleno solo toca filas con la fecha inválida, así que una
-- segunda corrida no encuentra ninguna: no puede volver a habilitar algo que
-- se deshabilitó a propósito.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < auditoria-y-estado.sql

ALTER TABLE federaciones
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE centrales
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE sindicatos
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE productores
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

-- Los lotes necesitan un paso más. La tabla ya tenía una columna `estado`,
-- pero era otra cosa: el estado de la parcela ("EN PRODUCCIÓN", "ABANDONADO"),
-- que viene de la planilla. Se la renombra a `estado_lote` para dejar libre el
-- nombre `estado` al booleano de habilitado, igual que en las demás tablas.
--
-- El renombre conserva los datos. En la API el campo sigue llamándose
-- `estado`: el cambio es interno.
--
-- Va con la comprobación explícita y no con `CHANGE COLUMN IF EXISTS`, que no
-- alcanza: en una segunda corrida `estado` vuelve a existir —ahora como el
-- booleano nuevo— y ese IF EXISTS lo renombraría a estado_lote, arruinando las
-- dos columnas. La condición correcta es que estado_lote todavía NO exista.
SET @renombrar := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lotes'
              AND COLUMN_NAME = 'estado_lote'),
    'DO 0',
    'ALTER TABLE lotes CHANGE COLUMN estado estado_lote varchar(20) DEFAULT NULL');
PREPARE renombrado FROM @renombrar;
EXECUTE renombrado;
DEALLOCATE PREPARE renombrado;

ALTER TABLE lotes
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE observaciones
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE cargos
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE imagenes_productor
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE imagenes_cargo
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

ALTER TABLE saludos
    ADD COLUMN IF NOT EXISTS created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS estado bit(1) NOT NULL DEFAULT b'1';

-- ---------------------------------------------------------------------------
-- Relleno de las filas que ya existían.
--
-- La condición es la fecha inválida, que es la marca inconfundible de una fila
-- rellenada por el ALTER TABLE. Una fila creada por la aplicación nunca la
-- tiene, así que este bloque no la toca.
-- ---------------------------------------------------------------------------

UPDATE federaciones      SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE centrales         SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE sindicatos        SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE productores       SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE lotes             SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE observaciones     SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE cargos            SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE imagenes_productor SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE imagenes_cargo    SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE usuarios          SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;
UPDATE saludos           SET created_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP, estado = b'1' WHERE created_at IS NULL OR YEAR(created_at) < 2000;

-- ---------------------------------------------------------------------------
-- Unificación de la auditoría que ya existía a medias.
--
-- Productor y Usuario llevaban sus propias columnas, mantenidas con un
-- @PrePersist a mano, y el usuario tenía además su propio `activo`. Ahora eso
-- lo cubre EntidadAuditable para todas las tablas, así que se traen los valores
-- reales —que son mejores que la fecha de la migración— y se retiran las
-- columnas viejas.
--
-- Repetible: si las columnas ya no están, los IF EXISTS no hacen nada.
-- ---------------------------------------------------------------------------

-- El traspaso va dentro de un IF sobre information_schema por lo mismo que el
-- renombre de los lotes: una vez retiradas las columnas, un UPDATE que las
-- nombre no es "sin efecto" sino un error que corta el script a la mitad.

SET @traer := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'productores'
              AND COLUMN_NAME = 'creado_en'),
    'UPDATE productores SET created_at = creado_en, updated_at = actualizado_en
      WHERE creado_en IS NOT NULL AND YEAR(creado_en) >= 2000',
    'DO 0');
PREPARE t FROM @traer; EXECUTE t; DEALLOCATE PREPARE t;

SET @traer := IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios'
              AND COLUMN_NAME = 'creado_en'),
    'UPDATE usuarios SET created_at = creado_en, estado = activo
      WHERE creado_en IS NOT NULL AND YEAR(creado_en) >= 2000',
    'DO 0');
PREPARE t FROM @traer; EXECUTE t; DEALLOCATE PREPARE t;

ALTER TABLE productores
    DROP COLUMN IF EXISTS creado_en,
    DROP COLUMN IF EXISTS actualizado_en;

ALTER TABLE usuarios
    DROP COLUMN IF EXISTS creado_en,
    DROP COLUMN IF EXISTS activo;
