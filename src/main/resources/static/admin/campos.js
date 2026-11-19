(() => {
  const texto = (nombre, etiqueta, max, obligatorio = false) => ({ nombre, etiqueta, max, obligatorio });
  const largo = (nombre, etiqueta, max, obligatorio = false) => ({ ...texto(nombre, etiqueta, max, obligatorio), tipo: "textarea", ancho: true });
  const entero = (nombre, etiqueta, max, min = 0, obligatorio = false) => ({ nombre, etiqueta, max, min, obligatorio, tipo: "number" });
  const importe = (nombre) => ({ nombre, etiqueta: "Precio", tipo: "importe" });
  const gestion = (nombre, etiqueta, tipo, obligatorio = false, multiple = false) => ({ nombre, etiqueta, gestion: tipo, obligatorio, multiple });
  const select = (nombre, etiqueta, opciones) => ({ nombre, etiqueta, tipo: "select", opciones, obligatorio: true });
  const check = (nombre, etiqueta) => ({ nombre, etiqueta, tipo: "checkbox" });

  window.AviantoCampos = {
    motos: {
      titulo: "Motos", agregar: "Agregar moto", nuevo: "Nueva moto", limite: 5,
      nombre: (moto) => `${moto.marcaNombre} ${moto.modelo}`,
      detalle: (moto) => `${moto.anio} · ${moto.tipo} · ${moto.ficha.cc} cc`,
      estado: (moto) => moto.disponibilidad,
      base: { disponibilidad: "Disponible", estado: "Usada", moneda: "USD", fotosCoinciden: true, anio: new Date().getFullYear(), km: 0 },
      secciones: [
        ["Identificación", [
          gestion("marcaNombre", "Marca", "marcas", true), texto("modelo", "Modelo", 90, true),
          gestion("tipo", "Tipo de moto", "tipos", true), gestion("color", "Color", "colores", true),
          entero("anio", "Año", 2100, 1885, true), entero("km", "Kilometraje", 9999999, 0, true),
          select("estado", "Condición", ["Usada", "0 km"]),
          select("disponibilidad", "Disponibilidad", ["Disponible", "Reservada", "Vendida"]),
        ]],
        ["Ficha técnica", [
          entero("ficha.cc", "Cilindrada (cc)", 10000, 1, true), entero("ficha.cilindros", "Cilindros", 16),
          texto("ficha.motor", "Disposición del motor", 80), entero("ficha.hp", "Potencia (hp)", 2000),
          entero("ficha.nm", "Torque (Nm)", 2000), entero("ficha.peso", "Peso (kg)", 2000),
          entero("ficha.tanque", "Tanque (L)", 200), entero("ficha.marchas", "Marchas", 20),
        ]],
        ["Condiciones comerciales", [
          importe("precio"), select("moneda", "Moneda", ["USD", "ARS"]),
          gestion("condiciones.pagos", "Medios de pago", "pagos", false, true),
          gestion("condiciones.financiacion", "Financiaciones", "financiaciones", false, true), texto("condiciones.permuta", "Permuta", 140),
          largo("condiciones.notas", "Condiciones particulares", 500),
        ]],
        ["Presentación", [
          check("destacada", "Mostrar entre las destacadas"),
          check("revisadaEnBanco", "Verificada en banco de pruebas"),
          check("fotosCoinciden", "Las fotos corresponden a esta moto"),
        ]],
      ],
    },
    repuestos: {
      titulo: "Repuestos y accesorios", agregar: "Agregar producto", nuevo: "Nuevo producto", limite: 1,
      nombre: (producto) => producto.nombre,
      detalle: (producto) => `${producto.codigo} · ${producto.compatibilidad}`,
      estado: (producto) => producto.disponibilidad === "stock" ? "En stock" : "Por encargo",
      base: { tipo: "Repuesto", disponibilidad: "encargo", precio: { moneda: "ARS" } },
      secciones: [
        ["Datos del producto", [
          texto("nombre", "Nombre", 96, true), texto("codigo", "Código", 48, true),
          gestion("marca", "Marca", "marcas"), gestion("compatibilidad", "Modelo o compatibilidad", "compatibilidades", true),
          gestion("categoria", "Categoría", "categorias-repuestos", true), select("tipo", "Tipo", ["Repuesto", "Accesorio"]),
          largo("descripcion", "Descripción", 500, true),
        ]],
        ["Disponibilidad y precio", [
          select("disponibilidad", "Disponibilidad", [["encargo", "Por encargo"], ["stock", "En stock"]]),
          importe("precio.valor"), select("precio.moneda", "Moneda", ["ARS", "USD"]),
        ]],
      ],
    },
    trabajos: {
      titulo: "Trabajos del taller", agregar: "Agregar trabajo", nuevo: "Nuevo trabajo", limite: 5,
      nombre: (trabajo) => trabajo.titulo,
      detalle: (trabajo) => [trabajo.tipo, trabajo.marca, trabajo.fecha].filter(Boolean).join(" · "),
      estado: (trabajo) => trabajo.categoria,
      base: {},
      secciones: [
        ["Datos del trabajo", [
          texto("titulo", "Título", 100, true),
          gestion("categoria", "Categoría", "categorias-trabajos", true),
          gestion("tipo", "Tipo de intervención", "intervenciones", true), gestion("marca", "Marca de la moto", "marcas"),
          { nombre: "fecha", etiqueta: "Fecha de publicación", tipo: "date" },
          { ...texto("instagramUrl", "Enlace a la publicación en Instagram", 500), tipo: "url" },
          largo("resumen", "Resumen", 360, true), largo("detalle", "Descripción completa", 2400, true),
        ]],
      ],
    },
  };
})();
