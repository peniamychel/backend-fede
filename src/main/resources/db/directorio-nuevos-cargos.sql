-- Nuevos nombres y orden del directorio.
--
-- Conversión de datos existentes:
--   Sindicato/Central: PRESIDENTE -> SECRETARIO_GENERAL
--                       SECRETARIO -> SECRETARIO_RELACIONES
--   Federación:         PRESIDENTE -> EJECUTIVO
--                       SECRETARIO -> SECRETARIO_GENERAL
--
-- Primero se amplía el ENUM para que convivan ambos catálogos durante la
-- conversión. Al final se dejan solamente los valores vigentes.

ALTER TABLE cargos
    MODIFY cargo ENUM(
        'PRESIDENTE', 'SECRETARIO',
        'EJECUTIVO', 'SECRETARIO_GENERAL', 'SECRETARIO_RELACIONES',
        'HACIENDAS', 'VOCAL'
    ) NOT NULL;

-- Conserva los pies personalizados. Solo cambia el renglón automático cuando
-- todavía termina exactamente con el nombre anterior del cargo.
UPDATE cargos
SET pie_firma = CONCAT(
        LEFT(pie_firma, CHAR_LENGTH(pie_firma) - CHAR_LENGTH('PRESIDENTE')),
        CASE WHEN ambito = 'FEDERACION' THEN 'EJECUTIVO' ELSE 'SECRETARIO GENERAL' END
    )
WHERE cargo = 'PRESIDENTE'
  AND pie_firma IS NOT NULL
  AND RIGHT(pie_firma, CHAR_LENGTH('PRESIDENTE')) = 'PRESIDENTE';

UPDATE cargos
SET pie_firma = CONCAT(
        LEFT(pie_firma, CHAR_LENGTH(pie_firma) - CHAR_LENGTH('SECRETARIO')),
        CASE WHEN ambito = 'FEDERACION' THEN 'SECRETARIO GENERAL'
             ELSE 'SECRETARIO RELACIONES' END
    )
WHERE cargo = 'SECRETARIO'
  AND pie_firma IS NOT NULL
  AND RIGHT(pie_firma, CHAR_LENGTH('SECRETARIO')) = 'SECRETARIO';

UPDATE cargos
SET cargo = CASE
    WHEN cargo = 'PRESIDENTE' AND ambito = 'FEDERACION' THEN 'EJECUTIVO'
    WHEN cargo = 'PRESIDENTE' THEN 'SECRETARIO_GENERAL'
    WHEN cargo = 'SECRETARIO' AND ambito = 'FEDERACION' THEN 'SECRETARIO_GENERAL'
    WHEN cargo = 'SECRETARIO' THEN 'SECRETARIO_RELACIONES'
    ELSE cargo
END
WHERE cargo IN ('PRESIDENTE', 'SECRETARIO');

ALTER TABLE cargos
    MODIFY cargo ENUM(
        'EJECUTIVO', 'SECRETARIO_GENERAL', 'SECRETARIO_RELACIONES',
        'HACIENDAS', 'VOCAL'
    ) NOT NULL;
