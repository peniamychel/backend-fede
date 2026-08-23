-- El número del acta, como está escrito en el libro.
--
-- El libro de actas del sindicato es el original; lo que se sube al sistema es
-- una foto de una de sus hojas. Sin el número, meses después nadie puede ir al
-- libro a cotejar lo que la pantalla dice que se decidió.
--
-- Queda en null en las actas que ya estaban cargadas: el número no se puede
-- inventar, hay que ir a mirarlo. La pantalla del acta las marca y deja
-- ponérselo. De la primera hoja en adelante, el sistema lo exige.
--
-- No lleva clave única: cada sindicato lleva su propio libro y numera desde
-- uno, así que «Acta N° 12» existe tantas veces como libros hay.
--
-- Es repetible.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < acta-codigo.sql

ALTER TABLE reuniones
    ADD COLUMN IF NOT EXISTS codigo_acta varchar(40) NULL;

-- Una reunión sin hojas no tiene acta, así que tampoco puede tener su número:
-- prometería un documento que no está. Limpia lo que hubiera quedado suelto.
UPDATE reuniones r
   SET r.codigo_acta = NULL
 WHERE r.codigo_acta IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM hojas_acta h WHERE h.reunion_id = r.id);
