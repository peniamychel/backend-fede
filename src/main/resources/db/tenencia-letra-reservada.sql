-- Referencia para instalaciones que no usan spring.jpa.hibernate.ddl-auto=update.
-- Es nullable: las tenencias existentes siguen con letras automáticas.
ALTER TABLE tenencias_lote
    ADD COLUMN IF NOT EXISTS letra_reservada varchar(1) DEFAULT NULL;
