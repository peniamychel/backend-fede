-- La central cambia su número por una abreviatura de tres letras.
--
-- El número no se convierte en nada: un "2" no es una sigla, y no hay forma de
-- deducir una a partir de él sin inventarla. Las centrales que lo tenían
-- cargado quedan con la abreviatura vacía, para que alguien la escriba.
--
-- Hibernate agrega la columna nueva solo, pero nunca borra: la vieja se va acá.
--
-- Es repetible.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < central-abreviatura.sql

ALTER TABLE centrales
    DROP INDEX IF EXISTS uk_central_numero,
    DROP COLUMN IF EXISTS numero,
    ADD COLUMN IF NOT EXISTS abreviatura varchar(3) NULL;

-- Va aparte del ALTER porque la columna tiene que existir antes de indexarla, y
-- en un mismo ALTER el orden no está garantizado.
--
-- Única a secas, no por federación. En MariaDB una clave única admite varios
-- NULL, así que las centrales sin sigla conviven sin problema y solo se controla
-- que no se repita una sigla ya usada.
CREATE UNIQUE INDEX IF NOT EXISTS uk_central_abreviatura ON centrales (abreviatura);
