-- Número de cada productor dentro de su central: el "1" de 2-IVI-1.
--
-- Hibernate agrega la columna solo, pero la deja en NULL, y un productor sin
-- número no tiene código. Acá se numeran los que ya estaban cargados.
--
-- Se numera por central y en orden de id, que es el orden en que entraron al
-- padrón: el que se cargó primero lleva el 1. No se usa el orden alfabético
-- porque el nombre cambia -de hecho hay correcciones pendientes de confirmar- y
-- el número no puede moverse cuando alguien corrige una letra.
--
-- **Se saltean los números que llevan 666**: el 666, el 1666, el 2666, el 3666
-- y los tramos de 6660 a 6669. No es una superstición del sistema, es de la
-- gente: nadie quiere que su credencial diga eso, y en centrales de más de tres
-- mil afiliados el número aparecería varias veces. Saltearlos cuesta un puñado
-- de números en una serie que no tiene por qué ser continua.
--
-- Es repetible, pero solo toca a los que no tienen número: los ya numerados
-- conservan el suyo. Correrlo dos veces no renumera a nadie.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < productor-correlativo.sql

ALTER TABLE productores
    ADD COLUMN IF NOT EXISTS correlativo int NULL;

-- La numeración arranca después del último número ya entregado en cada central,
-- así los que se agreguen a una central a medio numerar no pisan a nadie.
--
-- `admisibles` es la serie de números entregables con su posición: 1, 2, …,
-- 665, 667, … El número que le toca al k-ésimo productor sin numerar de una
-- central es el admisible que está k posiciones después del último ya
-- entregado ahí. Contar posiciones en vez de sumar uno es lo que hace que el
-- salto no desacomode a los que siguen.
WITH RECURSIVE numeros AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM numeros WHERE n < 100000
),
admisibles AS (
    SELECT n, ROW_NUMBER() OVER (ORDER BY n) AS puesto
    FROM numeros
    WHERE n NOT LIKE '%666%'
),
base AS (
    SELECT s2.central_id AS central_id,
           COALESCE(MAX(p2.correlativo), 0) AS desde
    FROM productores p2
    JOIN sindicatos s2 ON s2.id = p2.sindicato_id
    GROUP BY s2.central_id
),
arranque AS (
    -- Cuántos admisibles quedaron atrás en esa central. Con la central vacía es
    -- cero, y el primero se lleva el 1.
    SELECT b.central_id,
           (SELECT COUNT(*) FROM admisibles a WHERE a.n <= b.desde) AS ya
    FROM base b
),
orden AS (
    SELECT p3.id AS id,
           s3.central_id AS central_id,
           ROW_NUMBER() OVER (PARTITION BY s3.central_id ORDER BY p3.id) AS puesto
    FROM productores p3
    JOIN sindicatos s3 ON s3.id = p3.sindicato_id
    WHERE p3.correlativo IS NULL
)
UPDATE productores p
    JOIN orden ON orden.id = p.id
    JOIN arranque ON arranque.central_id = orden.central_id
    JOIN admisibles ON admisibles.puesto = arranque.ya + orden.puesto
SET p.correlativo = admisibles.n
WHERE p.correlativo IS NULL;

-- Los que ya tenían número no se tocan: cambiarlo invalidaría la credencial que
-- esa persona tiene en la mano. Si alguno quedó con un 666 de antes, sale acá
-- para decidirlo a mano.
SELECT p.id, p.nombres, p.apellidos, p.correlativo, c.nombre AS central
  FROM productores p
  JOIN sindicatos s ON s.id = p.sindicato_id
  JOIN centrales c ON c.id = s.central_id
 WHERE CAST(p.correlativo AS char) LIKE '%666%'
 ORDER BY c.nombre, p.correlativo;
