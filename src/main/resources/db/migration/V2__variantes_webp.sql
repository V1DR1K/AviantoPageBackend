ALTER TABLE imagen_variante ADD COLUMN formato varchar(4) NOT NULL DEFAULT 'jpg'
    CHECK (formato IN ('jpg', 'webp'));
ALTER TABLE imagen_variante DROP CONSTRAINT imagen_variante_pkey;
ALTER TABLE imagen_variante ADD PRIMARY KEY (clave, variante, formato);
