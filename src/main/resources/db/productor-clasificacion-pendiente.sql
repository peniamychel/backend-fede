-- Conserva la clasificación del Excel mientras no hay parcela asignada.
-- Repetible y aditiva: no modifica datos, correlativos, lotes ni impresiones.
-- ddl-auto=update también agrega esta columna al iniciar la nueva versión.
ALTER TABLE productores
    ADD COLUMN IF NOT EXISTS clasificacion_pendiente varchar(30) NULL;

-- La revisión se calcula a partir de las tenencias vigentes: todos los
-- productores sin lote numerado quedan pendientes, incluidos los históricos.
-- No se inventan clasificaciones para datos antiguos que no las conservaban.
