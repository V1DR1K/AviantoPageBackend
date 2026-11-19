window.crearGestionAvianto = ({ pedir, mostrarError, esc }) => {
  const $ = (id) => document.getElementById(id);
  const categorias = {
    marcas: ["Marcas", "marca", 72],
    tipos: ["Tipos de moto", "tipo de moto", 48],
    colores: ["Colores", "color", 40],
    pagos: ["Medios de pago", "medio de pago", 80],
    financiaciones: ["Financiaciones", "financiación", 140],
    "categorias-repuestos": ["Categorías de repuestos", "categoría de repuestos", 72],
    compatibilidades: ["Compatibilidades", "compatibilidad", 140],
    "categorias-trabajos": ["Categorías de taller", "categoría de taller", 72],
    intervenciones: ["Tipos de intervención", "tipo de intervención", 80],
  };
  const selector = $("grupo-gestion");
  const listado = $("opciones-gestion");
  const dialogo = $("dialogo-gestion");
  const formulario = $("form-gestion");
  const nombre = $("nombre-gestion");
  const mensaje = $("mensaje-gestion");
  const error = $("error-gestion");
  let opciones = [];
  let accion;

  selector.replaceChildren(...Object.entries(categorias).map(([id, [texto]]) => new Option(texto, id)));

  async function cargar() { opciones = await pedir("/gestion"); }

  function pintar() {
    const tipo = selector.value;
    const items = opciones.filter((opcion) => opcion.tipo === tipo);
    mensaje.textContent = items.length ? `${items.length} ${items.length === 1 ? "opción" : "opciones"}` : "Todavía no hay opciones.";
    listado.innerHTML = items.map((o) => `<li><strong>${esc(o.nombre)}</strong><div>
      <button type="button" class="enlace" data-gestion-editar="${o.id}">Editar<span class="sr"> ${esc(o.nombre)}</span></button>
      <button type="button" class="enlace enlace--riesgo" data-gestion-borrar="${o.id}">Eliminar<span class="sr"> ${esc(o.nombre)}</span></button>
    </div></li>`).join("");
  }

  async function abrir() {
    listado.replaceChildren();
    mensaje.textContent = "Cargando opciones…";
    $("agregar-gestion").disabled = true;
    try { await cargar(); if (!$("gestion").hidden) pintar(); }
    catch (ex) { await mostrarError(ex, mensaje); }
    finally { $("agregar-gestion").disabled = false; }
  }

  function abrirDialogo(tipo, opcion = null, borrar = false, destino = null) {
    accion = { tipo, opcion, borrar, destino };
    const etiqueta = categorias[tipo][1];
    $("titulo-dialogo-gestion").textContent = `${borrar ? "Eliminar" : opcion ? "Editar" : "Agregar"} ${etiqueta}`;
    nombre.value = opcion?.nombre || "";
    nombre.maxLength = categorias[tipo][2];
    nombre.required = !borrar;
    $("campo-nombre-gestion").hidden = borrar;
    $("confirmar-gestion").textContent = borrar ? `¿Eliminar «${opcion.nombre}»?` : "";
    $("guardar-gestion").textContent = borrar ? "Eliminar" : "Guardar";
    error.textContent = "";
    dialogo.showModal();
    (borrar ? $("cancelar-gestion") : nombre).focus();
  }

  function opcionesDe(tipo, elegidos) {
    const nombres = opciones.filter((opcion) => opcion.tipo === tipo).map((opcion) => opcion.nombre);
    const clave = (valor) => valor.toLocaleLowerCase("es-AR");
    const seleccion = new Set(elegidos.map(clave));
    return { nombres, seleccion, clave };
  }

  function contenido(campo, valor) {
    const elegidos = (Array.isArray(valor) ? valor : valor ? [valor] : []);
    const { nombres, seleccion, clave } = opcionesDe(campo.gestion, elegidos);
    if (!campo.multiple) {
      const actual = nombres.find((n) => seleccion.has(clave(n)));
      return '<option value="">Elegí una opción</option>' + nombres.map((n) =>
        `<option value="${esc(n)}"${n === actual ? " selected" : ""}>${esc(n)}</option>`).join("");
    }
    const disponibles = nombres.filter((n) => seleccion.has(clave(n)));
    return `<summary>${esc(disponibles.join(", ") || "Elegí una o más opciones")}</summary>
      <div class="opciones-multiples">${nombres.map((n) =>
        `<label class="check"><input type="checkbox" value="${esc(n)}"${seleccion.has(clave(n)) ? " checked" : ""}> ${esc(n)}</label>`).join("") || "<p>Agregá una opción con el botón +.</p>"}</div>`;
  }

  function control(campo, id, valor) {
    const etiqueta = esc(campo.etiqueta);
    const control = campo.multiple
      ? `<details id="${id}" data-multiple aria-labelledby="${id}-etiqueta">${contenido(campo, valor)}</details>`
      : `<select id="${id}" name="${campo.nombre}"${campo.obligatorio ? " required" : ""}>${contenido(campo, valor)}</select>`;
    return `<div class="campo"><label id="${id}-etiqueta"${campo.multiple ? "" : ` for="${id}"`}>${etiqueta}</label>
      <div class="gestion-selector">${control}
        <button type="button" class="boton boton--linea gestion-mas" data-gestion-nueva="${campo.gestion}" data-gestion-destino="${id}"
          aria-label="Agregar ${categorias[campo.gestion][1]}" aria-haspopup="dialog">+</button>
      </div></div>`;
  }

  function valores(id) {
    const control = $(id);
    return control.matches("[data-multiple]") ? [...control.querySelectorAll("input:checked")].map((input) => input.value) : control.value;
  }

  selector.addEventListener("change", () => { if (!$("agregar-gestion").disabled) pintar(); });
  $("agregar-gestion").addEventListener("click", () => abrirDialogo(selector.value));
  listado.addEventListener("click", (e) => {
    const boton = e.target.closest("[data-gestion-editar], [data-gestion-borrar]");
    if (!boton) return;
    const id = Number(boton.dataset.gestionEditar ?? boton.dataset.gestionBorrar);
    const opcion = opciones.find((o) => o.id === id);
    if (opcion) abrirDialogo(opcion.tipo, opcion, "gestionBorrar" in boton.dataset);
  });

  $("campos").addEventListener("click", (e) => {
    const boton = e.target.closest("[data-gestion-nueva]");
    if (!boton) return;
    const destino = boton.dataset.gestionDestino;
    if ($(destino).matches("[data-multiple]") && valores(destino).length >= 10) {
      $("estado-editor").textContent = "Podés seleccionar hasta diez opciones. Quitá una antes de agregar otra.";
      return;
    }
    abrirDialogo(boton.dataset.gestionNueva, null, false, destino);
  });
  $("campos").addEventListener("change", (e) => {
    const control = e.target.closest("[data-multiple]");
    if (control) {
      const seleccion = valores(control.id);
      if (seleccion.length > 10) { e.target.checked = false; return; }
      control.querySelector("summary").textContent = seleccion.join(", ") || "Elegí una o más opciones";
    }
  });
  $("campos").addEventListener("keydown", (e) => {
    const control = e.target.closest("[data-multiple][open]");
    if (e.key === "Escape" && control) { control.open = false; control.querySelector("summary").focus(); }
  });

  $("cancelar-gestion").addEventListener("click", () => dialogo.close());
  dialogo.addEventListener("cancel", (e) => { if ($("guardar-gestion").disabled) e.preventDefault(); });
  formulario.addEventListener("submit", async (e) => {
    e.preventDefault();
    const { tipo, opcion, borrar, destino } = accion;
    const guardar = $("guardar-gestion");
    guardar.disabled = $("cancelar-gestion").disabled = true;
    error.textContent = "";
    try {
      const metodo = borrar ? "DELETE" : opcion ? "PUT" : "POST";
      const actual = await pedir(`/gestion/${tipo}${opcion ? "/" + opcion.id : ""}`, {
        metodo, datos: borrar ? undefined : { nombre: nombre.value.trim() },
      });
      if (opcion) opciones = opciones.filter((o) => o.id !== opcion.id);
      if (actual) {
        opciones.push(actual);
        opciones.sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
      }
      dialogo.close();
      if (destino) {
        const control = $(destino);
        const multiple = control.matches("[data-multiple]");
        const anteriores = multiple ? valores(destino) : [];
        const nuevos = [...anteriores, actual.nombre];
        control.innerHTML = contenido({ gestion: tipo, multiple }, nuevos);
        control.dispatchEvent(new Event("input", { bubbles: true }));
        (multiple ? control.querySelector("summary") : control).focus();
      } else {
        pintar();
        $("agregar-gestion").focus();
      }
    } catch (ex) {
      if (ex.status === 401) dialogo.close();
      await mostrarError(ex, error);
    } finally {
      guardar.disabled = $("cancelar-gestion").disabled = false;
    }
  });
  return { abrir, cargar, control, valores };
};
