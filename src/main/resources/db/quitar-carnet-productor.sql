-- Elimina la columna del carné de productor.
--
-- El dato salió del sistema: no está en la entidad, ni en la API, ni en las
-- pantallas, ni en la nómina —ahí lo reemplazó el código del padrón—. Hibernate
-- no borra columnas, así que la de la base queda sin que nadie la lea ni la
-- escriba.
--
-- Por eso este script va aparte y NO hace falta correrlo. Está para cuando se
-- quiera dejar el esquema prolijo, y borra datos: en el padrón original 2.214
-- filas tenían carné. Conviene mirar antes qué hay:
--
--   SELECT COUNT(carnet_productor) FROM productores;
--
-- Es repetible.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < quitar-carnet-productor.sql

ALTER TABLE productores
    DROP INDEX IF EXISTS idx_productor_carnet,
    DROP COLUMN IF EXISTS carnet_productor;
