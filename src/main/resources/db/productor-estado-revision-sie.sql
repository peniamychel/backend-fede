-- Estado y sugerencia persistentes de la revisión SIE.
-- Es repetible. Con ddl-auto=update Hibernate crea las columnas al reiniciar.

ALTER TABLE productores
    ADD COLUMN IF NOT EXISTS revision_sie_estado VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS revision_sie_mensaje VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS sie_nombres_sugeridos VARCHAR(60) NULL,
    ADD COLUMN IF NOT EXISTS sie_apellidos_sugeridos VARCHAR(60) NULL;
