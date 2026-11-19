CREATE TABLE cuenta_admin (
    usuario varchar(80) PRIMARY KEY,
    clave_hash varchar(100) NOT NULL,
    requiere_activacion boolean NOT NULL,
    revision integer NOT NULL DEFAULT 1
);

CREATE TABLE revision_catalogo (
    tipo varchar(10) PRIMARY KEY,
    revision bigint NOT NULL DEFAULT 0
);

INSERT INTO revision_catalogo (tipo) VALUES ('motos'), ('repuestos'), ('trabajos');

CREATE TABLE entrada_catalogo (
    tipo varchar(10) NOT NULL REFERENCES revision_catalogo(tipo),
    id varchar(100) NOT NULL,
    datos jsonb NOT NULL CHECK (jsonb_typeof(datos) = 'object'),
    visible boolean NOT NULL,
    version integer NOT NULL DEFAULT 1,
    creada timestamptz NOT NULL DEFAULT now(),
    actualizada timestamptz NOT NULL DEFAULT now(),
    archivada timestamptz,
    PRIMARY KEY (tipo, id)
);

CREATE INDEX entrada_catalogo_publica ON entrada_catalogo (tipo, creada DESC, id)
    WHERE visible AND archivada IS NULL;

CREATE TABLE imagen_variante (
    clave char(64) NOT NULL CHECK (clave ~ '^[a-f0-9]{64}$'),
    variante smallint NOT NULL CHECK (variante IN (160, 360, 720, 1440)),
    ancho smallint NOT NULL,
    alto smallint NOT NULL,
    contenido bytea NOT NULL,
    PRIMARY KEY (clave, variante)
);

CREATE TABLE carga_inicial (
    nombre varchar(60) PRIMARY KEY
);
