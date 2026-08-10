-- Ubicación en el mapa y superficie de cada lote.
--
-- Las coordenadas van como DECIMAL y no como double por lo mismo que en los
-- sindicatos: un double redondea, y en coordenadas ese redondeo se traduce en
-- metros de error sobre el terreno. Con 7 decimales la precisión es de
-- alrededor de un centímetro.
--
-- La superficie va en hectáreas, que es como se mide la tierra acá. Cuatro
-- decimales llegan al metro cuadrado, de sobra para una parcela.
--
-- Es repetible.
--
--   mysql -u root -p -P 3307 -h 127.0.0.1 federa < lotes-ubicacion-y-superficie.sql

ALTER TABLE lotes
    ADD COLUMN IF NOT EXISTS latitud  decimal(10,7) NULL,
    ADD COLUMN IF NOT EXISTS longitud decimal(10,7) NULL,
    ADD COLUMN IF NOT EXISTS ubicacion_actualizada_en datetime NULL,
    ADD COLUMN IF NOT EXISTS superficie decimal(10,4) NULL;
