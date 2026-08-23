-- El pie de firma deja de ser una imagen y pasa a ser texto editable por cada
-- período. El sello pertenece al nivel institucional, no al dirigente.
-- Es repetible y no elimina las antiguas imágenes PIE_FIRMA: se conservan como
-- historial aunque la aplicación ya no permita subir nuevas.

ALTER TABLE cargos
    ADD COLUMN IF NOT EXISTS pie_firma VARCHAR(200) NULL;

ALTER TABLE sindicatos
    ADD COLUMN IF NOT EXISTS sello_clave VARCHAR(200) NULL;

ALTER TABLE centrales
    ADD COLUMN IF NOT EXISTS sello_clave VARCHAR(200) NULL;

ALTER TABLE federaciones
    ADD COLUMN IF NOT EXISTS sello_clave VARCHAR(200) NULL;
