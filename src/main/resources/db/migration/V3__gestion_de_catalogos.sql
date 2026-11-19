CREATE TABLE opcion_gestion (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo varchar(20) NOT NULL CHECK (tipo IN ('marcas','tipos','colores','pagos','financiaciones','categorias-repuestos','compatibilidades','categorias-trabajos','intervenciones')),
    nombre varchar(140) NOT NULL CHECK (nombre = btrim(nombre) AND nombre <> '')
);
CREATE UNIQUE INDEX opcion_gestion_nombre ON opcion_gestion (tipo, lower(nombre));

UPDATE entrada_catalogo
SET datos = jsonb_set(datos, '{condiciones,financiacion}',
    CASE WHEN btrim(coalesce(datos #>> '{condiciones,financiacion}', '')) = '' THEN '[]'::jsonb
         ELSE jsonb_build_array(datos #>> '{condiciones,financiacion}') END)
WHERE tipo = 'motos' AND jsonb_typeof(datos #> '{condiciones,financiacion}') = 'string';

UPDATE entrada_catalogo
SET datos = jsonb_set(datos, '{categoria}', to_jsonb(CASE datos->>'categoria'
    WHEN 'mecanica' THEN 'Mecánica' WHEN 'electronica' THEN 'Electrónica'
    WHEN 'reprogramaciones' THEN 'Reprogramaciones' ELSE datos->>'categoria' END))
WHERE tipo = 'trabajos' AND datos->>'categoria' IN ('mecanica','electronica','reprogramaciones');

INSERT INTO opcion_gestion (tipo, nombre)
SELECT DISTINCT ON (tipo, lower(btrim(nombre))) tipo, btrim(nombre)
FROM (
    SELECT v.tipo, v.nombre
    FROM entrada_catalogo e CROSS JOIN LATERAL (VALUES
      ('marcas', e.datos->>'marcaNombre'), ('tipos', e.datos->>'tipo'), ('colores', e.datos->>'color')
    ) v(tipo, nombre)
    WHERE e.tipo = 'motos'
    UNION ALL
    SELECT v.tipo, v.nombre
    FROM entrada_catalogo e CROSS JOIN LATERAL (VALUES
      ('marcas', e.datos->>'marca'), ('categorias-repuestos', e.datos->>'categoria'), ('compatibilidades', e.datos->>'compatibilidad')
    ) v(tipo, nombre) WHERE e.tipo = 'repuestos'
    UNION ALL
    SELECT v.tipo, v.nombre
    FROM entrada_catalogo e CROSS JOIN LATERAL (VALUES
      ('marcas', e.datos->>'marca'), ('categorias-trabajos', e.datos->>'categoria'), ('intervenciones', e.datos->>'tipo')
    ) v(tipo, nombre) WHERE e.tipo = 'trabajos'
    UNION ALL
    SELECT v.tipo, n.nombre
    FROM entrada_catalogo e CROSS JOIN LATERAL (VALUES
      ('pagos', e.datos #> '{condiciones,pagos}'),
      ('financiaciones', e.datos #> '{condiciones,financiacion}')
    ) v(tipo, nombres)
    CROSS JOIN LATERAL jsonb_array_elements_text(
      CASE WHEN jsonb_typeof(v.nombres) = 'array' THEN v.nombres ELSE '[]'::jsonb END
    ) n(nombre)
    WHERE e.tipo = 'motos'
) nombres
WHERE btrim(nombre) <> ''
ORDER BY tipo, lower(btrim(nombre)), btrim(nombre);

UPDATE entrada_catalogo
SET datos = CASE WHEN tipo = 'motos' AND datos->>'ejemplo' = 'true'
                 THEN jsonb_set(datos - 'ejemplo', '{revisadaEnBanco}', 'false')
                 ELSE datos - 'ejemplo' END,
    version = version + 1, actualizada = now()
WHERE datos ? 'ejemplo' OR tipo = 'motos';

UPDATE revision_catalogo SET revision = revision + 1;
