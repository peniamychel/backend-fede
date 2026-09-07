-- Referencia para instalaciones sin spring.jpa.hibernate.ddl-auto=update.
-- En desarrollo y en los contenedores actuales Hibernate agrega esta columna
-- al iniciar el backend. El script es repetible y no modifica observaciones.

ALTER TABLE productores
    ADD COLUMN IF NOT EXISTS observacion_manual VARCHAR(500) NULL;
