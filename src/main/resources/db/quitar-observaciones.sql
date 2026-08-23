-- Elimina la tabla de observaciones.
--
-- La bandeja de observaciones salió del sistema: no hay entidad, ni endpoint,
-- ni pantalla. Hibernate no borra tablas, así que la fila sobra en la base pero
-- no molesta: nadie la lee ni la escribe.
--
-- Por eso este script va aparte y NO hace falta correrlo. Está para cuando se
-- quiera dejar el esquema prolijo, y borra datos: si la tabla tuviera algo
-- guardado, se pierde. Conviene mirar antes qué hay:
--
--   SELECT COUNT(*) FROM observaciones;
--
-- Es repetible.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < quitar-observaciones.sql

DROP TABLE IF EXISTS observaciones;
