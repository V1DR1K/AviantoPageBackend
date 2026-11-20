ALTER TABLE entrada_catalogo
    ADD COLUMN vistas bigint NOT NULL DEFAULT 0 CHECK (vistas >= 0);
