-- Referencia para instalaciones sin ddl-auto=update.
ALTER TABLE productores ADD COLUMN IF NOT EXISTS impresiones_manuales_sin_fase INTEGER NOT NULL DEFAULT 0;
ALTER TABLE productores ADD COLUMN IF NOT EXISTS reimpresion_manual_sin_fase BOOLEAN NOT NULL DEFAULT FALSE;
