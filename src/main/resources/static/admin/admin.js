(() => {
  const $ = (selector) => document.querySelector(selector);
  const campos = window.AviantoCampos;
  const editor = $("#editor");
  const acceso = $("#ingreso");
  const cambioClave = $("#cambio-clave");
  const estadoGeneral = $("#estado-general");
  const rutas = [$("#acceso"), $("#vista-clave"), $("#panel")];
  const confirmacion = $("#confirmacion");
  const esc = (valor) => String(valor ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[c]);
  const buscarTexto = (s) => s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
  const urlFoto = (foto) => `/media/${encodeURIComponent(foto.clave)}/160.jpg`;

  let sesion;
  let tipo = "motos";
  let publicaciones = [];
  let seleccion;
  let fotos = [];
  let lectura = 0;
  let subiendo = false;
  let guardando = false;
  let modificado = false;

  class ErrorApi extends Error {
    constructor(status, mensaje, camposInvalidos = []) {
      super(mensaje);
      this.status = status;
      this.camposInvalidos = camposInvalidos;
    }
  }

  async function pedir(ruta, { metodo = "GET", datos, body } = {}) {
    const headers = { Accept: "application/json, application/problem+json" };
    if (metodo !== "GET" && sesion) headers[sesion.encabezadoCsrf] = sesion.csrf;
    if (datos) {
      body = JSON.stringify(datos);
      headers["Content-Type"] = "application/json";
    }
    let respuesta;
    try {
      respuesta = await fetch(`/api/admin${ruta}`, { method: metodo, body, headers, cache: "no-store" });
    } catch {
      throw new Error("No pudimos conectar con el servidor. Revisá la conexión y volvé a intentar.");
    }
    const json = respuesta.headers.get("content-type")?.includes("json");
    const resultado = json ? await respuesta.json() : null;
    if (respuesta.ok) return resultado;

    const mensajes = {
      401: ruta === "/login" ? "El usuario o la contraseña no coinciden." : "La sesión terminó. Volvé a ingresar.",
      403: "El acceso ya no es válido. Recargá la página e iniciá sesión.",
      429: "Hubo varios intentos seguidos. Esperá un momento y volvé a intentar.",
    };
    throw new ErrorApi(respuesta.status,
      resultado?.detail || mensajes[respuesta.status] || `No pudimos completar la operación (${respuesta.status}).`,
      resultado?.campos);
  }

  function mostrar(vista, titulo) {
    rutas.forEach((panel) => { panel.hidden = panel.id !== vista; });
    if (titulo) $(titulo).focus({ preventScroll: true });
    window.scrollTo(0, 0);
  }

  async function identificar(mensaje = "", enfocar = true) {
    sesion = await pedir("/sesion");
    $("#salir").hidden = !sesion.usuario;
    $("#abrir-clave").hidden = !sesion.usuario || sesion.activacion;
    estadoGeneral.textContent = mensaje;
    if (!sesion.usuario) {
      mostrar("acceso", enfocar ? "#acceso-titulo" : null);
      return;
    }
    if (sesion.activacion) {
      abrirClave();
      return;
    }
    mostrar("panel");
    abrirLista();
  }

  async function mostrarError(error, destino) {
    if (error.status === 401) {
      await identificar(error.message).catch(() => { destino.textContent = error.message; });
      return;
    }
    destino.textContent = error.message;
  }

  acceso.addEventListener("submit", async (e) => {
    e.preventDefault();
    const boton = acceso.querySelector("button");
    boton.disabled = true;
    $("#error-ingreso").textContent = "";
    try {
      await pedir("/login", { metodo: "POST", body: new URLSearchParams(new FormData(acceso)) });
      acceso.reset();
      await identificar();
    } catch (error) {
      $("#error-ingreso").textContent = error.message;
      $("#clave-ingreso").focus();
    } finally {
      boton.disabled = false;
    }
  });

  function abrirClave() {
    cambioClave.reset();
    $("#clave-confirmar").setCustomValidity("");
    $("#error-clave").textContent = "";
    $("#clave-titulo").textContent = sesion.activacion ? "Activar cuenta" : "Cambiar contraseña";
    $("#clave-ayuda").textContent = sesion.activacion
      ? "Ingresá el código de activación y elegí una nueva contraseña de al menos 12 caracteres."
      : "Usá al menos 12 caracteres para la nueva contraseña.";
    $("#activacion").hidden = !sesion.activacion;
    $("#codigo-activacion").required = sesion.activacion;
    $("#volver-desde-clave").hidden = sesion.activacion;
    mostrar("vista-clave", "#clave-titulo");
  }

  $("#abrir-clave").addEventListener("click", () => { if (salirDelEditor()) abrirClave(); });
  $("#volver-desde-clave").addEventListener("click", () => { mostrar("panel"); abrirLista(); });
  cambioClave.addEventListener("input", () => $("#clave-confirmar").setCustomValidity(""));
  cambioClave.addEventListener("submit", async (e) => {
    e.preventDefault();
    const datos = Object.fromEntries(new FormData(cambioClave));
    if (datos.nueva !== datos.confirmar) {
      $("#clave-confirmar").setCustomValidity("Las contraseñas no coinciden.");
      $("#clave-confirmar").reportValidity();
      return;
    }
    delete datos.confirmar;
    const boton = cambioClave.querySelector("button[type='submit']");
    boton.disabled = true;
    $("#error-clave").textContent = "";
    try {
      await pedir("/clave", { metodo: "POST", datos });
      cambioClave.reset();
      await identificar("Contraseña actualizada. Ingresá con la nueva contraseña.");
    } catch (error) {
      await mostrarError(error, $("#error-clave"));
    } finally {
      boton.disabled = false;
    }
  });

  $("#salir").addEventListener("click", async () => {
    if (!salirDelEditor()) return;
    try {
      await pedir("/logout", { metodo: "POST" });
      await identificar();
    } catch (error) {
      await mostrarError(error, estadoGeneral);
    }
  });

  function abrirLista({ mensaje = "", enfocar = true } = {}) {
    modificado = false;
    seleccion = null;
    $("#gestion").hidden = true;
    $("#abrir-gestion").removeAttribute("aria-current");
    $("#edicion").hidden = true;
    $("#listado").hidden = false;
    $("#titulo-lista").textContent = campos[tipo].titulo;
    $("#nueva").textContent = campos[tipo].agregar;
    $("#buscar").placeholder = { motos: "Marca o modelo", repuestos: "Nombre o código", trabajos: "Título o intervención" }[tipo];
    document.querySelectorAll("[data-catalogo]").forEach((boton) => {
      if (boton.dataset.catalogo === tipo) boton.setAttribute("aria-current", "page");
      else boton.removeAttribute("aria-current");
    });
    if (enfocar) {
      $("#titulo-lista").focus({ preventScroll: true });
      window.scrollTo(0, 0);
    }
    cargarLista(mensaje);
  }

  async function cargarLista(mensaje = "") {
    const solicitud = ++lectura;
    $("#contenedor-lista").hidden = true;
    $("#reintentar").hidden = true;
    $("#estado-lista").textContent = "Cargando publicaciones…";
    $("#nueva").disabled = true;
    try {
      const datos = await pedir(`/catalogos/${tipo}`);
      if (solicitud !== lectura) return;
      publicaciones = datos;
      pintarLista(mensaje);
    } catch (error) {
      if (solicitud !== lectura) return;
      $("#reintentar").hidden = false;
      await mostrarError(error, $("#estado-lista"));
    } finally {
      if (solicitud === lectura) $("#nueva").disabled = false;
    }
  }

  function pintarLista(mensaje = "") {
    const config = campos[tipo];
    const texto = buscarTexto($("#buscar").value.trim());
    const visibilidad = $("#visibilidad").value;
    const filtradas = publicaciones.filter((p) => {
      const nombre = [config.nombre(p.datos), config.detalle(p.datos), config.estado(p.datos)].join(" ");
      return (!visibilidad || p.visible === (visibilidad === "visible")) && buscarTexto(nombre).includes(texto);
    });
    $("#contenedor-lista").hidden = !filtradas.length;
    $("#estado-lista").textContent = mensaje || (filtradas.length
      ? `${filtradas.length} ${filtradas.length === 1 ? "publicación" : "publicaciones"}`
      : publicaciones.length ? "No hay publicaciones con esos filtros." : "Todavía no hay publicaciones.");
    $("#filas").innerHTML = filtradas.map((p) => {
      const nombre = esc(config.nombre(p.datos));
      const estado = esc(config.estado(p.datos));
      const miniatura = p.datos.imagenes?.[0];
      const visible = `<span class="visibilidad" data-visible="${p.visible}">${p.visible ? "Publicada" : "Oculta"}</span>`;
      return `<tr>
        <td><div class="tabla__publicacion">
          ${miniatura ? `<img src="${urlFoto(miniatura)}" width="56" height="46" loading="lazy" decoding="async" alt="">` : '<span class="sin-foto" aria-hidden="true"></span>'}
          <div><strong>${nombre}</strong>
            <span class="tabla__detalle">${esc(config.detalle(p.datos))}</span>
            <div class="dato-movil"><span class="sr">Estado: </span>${estado} · ${visible}</div>
          </div>
        </div></td>
        <td class="ocultar-movil">${estado}</td><td class="ocultar-movil">${visible}</td>
        <td><button class="enlace" type="button" data-editar="${esc(p.id)}">Editar<span class="sr"> ${nombre}</span></button></td>
      </tr>`;
    }).join("");
  }

  document.querySelector(".panel__nav").addEventListener("click", (e) => {
    const boton = e.target.closest("[data-catalogo]");
    if (!boton || boton.dataset.catalogo === tipo && $("#edicion").hidden && $("#gestion").hidden || !salirDelEditor()) return;
    tipo = boton.dataset.catalogo;
    publicaciones = [];
    $("#buscar").value = $("#visibilidad").value = "";
    abrirLista();
  });
  const gestion = window.crearGestionAvianto({ pedir, mostrarError, esc });
  $("#abrir-gestion").addEventListener("click", () => {
    if (!salirDelEditor()) return;
    lectura++;
    $("#listado").hidden = $("#edicion").hidden = true;
    $("#gestion").hidden = false;
    document.querySelectorAll("[data-catalogo]").forEach((b) => b.removeAttribute("aria-current"));
    $("#abrir-gestion").setAttribute("aria-current", "page");
    $("#titulo-gestion").focus();
    gestion.abrir();
  });

  $("#buscar").addEventListener("input", () => pintarLista());
  $("#visibilidad").addEventListener("change", () => pintarLista());
  $("#reintentar").addEventListener("click", () => cargarLista());
  $("#nueva").addEventListener("click", () => abrirEditor());
  $("#filas").addEventListener("click", (e) => {
    const boton = e.target.closest("[data-editar]");
    if (boton) abrirEditor(publicaciones.find((p) => p.id === boton.dataset.editar));
  });

  const leerCampo = (datos, nombre) => nombre.split(".").reduce((valor, clave) => valor?.[clave], datos);
  function grabarCampo(datos, nombre, valor) {
    const [grupo, clave] = nombre.split(".");
    if (clave) (datos[grupo] ??= {})[clave] = valor;
    else datos[grupo] = valor;
  }

  function entrada(campo, datos) {
    const { nombre, etiqueta, tipo: elemento, max, min, paso, obligatorio, opciones, ancho } = campo;
    const id = `campo-${nombre.replace(".", "-")}`;
    const valor = leerCampo(datos, nombre);
    if (campo.gestion) return gestion.control(campo, id, valor);
    if (elemento === "importe") {
      return `<div class="campo"><label for="${id}">${esc(etiqueta)}</label><input id="${id}" name="${nombre}"
        type="text" inputmode="decimal" data-importe maxlength="19" value="${valor == null ? "" : formatoImporte.format(valor)}"></div>`;
    }
    if (elemento === "kilometraje") {
      return `<div class="campo"><label for="${id}">${esc(etiqueta)}</label><input id="${id}" name="${nombre}"
        type="text" inputmode="numeric" pattern="[0-9.]*" data-kilometraje maxlength="9" required
        value="${valor == null ? "" : formatoKilometraje.format(valor)}"></div>`;
    }
    const base = `id="${id}" name="${nombre}"${obligatorio ? " required" : ""}`;
    const label = `<label for="${id}">${esc(etiqueta)}</label>`;
    if (elemento === "checkbox") {
      return `<label class="check" for="${id}"><input type="checkbox" ${base}${valor ? " checked" : ""}> ${esc(etiqueta)}</label>`;
    }
    let control;
    if (elemento === "select") {
      control = `<select ${base}>${opciones.map((opcion) => {
        const [v, rotulo] = Array.isArray(opcion) ? opcion : [opcion, opcion];
        return `<option value="${esc(v)}"${valor === v ? " selected" : ""}>${esc(rotulo)}</option>`;
      }).join("")}</select>`;
    } else if (elemento === "textarea") {
      control = `<textarea ${base} rows="3" maxlength="${max}">${esc(valor)}</textarea>`;
    } else {
      const limite = elemento === "number" ? ` min="${min}" max="${max}" step="${paso || "1"}"` :
        max ? ` maxlength="${max}"` : "";
      control = `<input ${base} type="${elemento || "text"}"${limite} value="${esc(Array.isArray(valor) ? valor.join(", ") : valor)}">`;
    }
    return `<div class="campo${ancho ? " ancho" : ""}">${label}${control}</div>`;
  }

  async function abrirEditor(item) {
    const tipoInicial = tipo;
    const solicitud = ++lectura;
    try { await gestion.cargar(); }
    catch (error) { await mostrarError(error, $("#estado-lista")); return; }
    if (solicitud !== lectura || tipo !== tipoInicial || $("#listado").hidden || $("#panel").hidden) return;
    const config = campos[tipo];
    seleccion = item || null;
    modificado = false;
    const datos = item?.datos || config.base;
    fotos = (datos.imagenes || []).map((foto) => ({ ...foto }));
    $("#titulo-editor").textContent = item ? config.nombre(datos) : config.nuevo;
    $("#campos").innerHTML = config.secciones.map(([titulo, definiciones]) =>
      `<fieldset><legend>${esc(titulo)}</legend>${titulo === "Ficha técnica" ? `
        <div class="gemini-accion"><button type="button" id="gemini-ficha" class="boton boton--linea">Autocompletar con Gemini</button>
        <p id="estado-gemini" role="status"></p></div>` : ""}<div class="campos-grid">${definiciones.map((campo) => entrada(campo, datos)).join("")}</div></fieldset>`).join("");
    $("#visible").checked = item?.visible ?? true;
    $("#cargar-fotos").value = "";
    $("#cargar-fotos").multiple = config.limite > 1;
    $("#ayuda-foto").textContent = config.limite > 1
      ? "Hasta cinco fotos JPEG, PNG o WebP de 6 MB cada una. La primera se usa como portada."
      : "Una foto JPEG, PNG o WebP de hasta 6 MB.";
    $("#estado-fotos").textContent = $("#estado-editor").textContent = "";
    $("#solicitar-archivo").hidden = !item;
    $("#listado").hidden = true;
    $("#edicion").hidden = false;
    pintarFotos();
    $("#titulo-editor").focus({ preventScroll: true });
    window.scrollTo(0, 0);
  }

  function salirDelEditor() {
    if (subiendo || guardando) return false;
    if (modificado && !window.confirm("Hay cambios sin guardar. ¿Querés salir de la edición?")) return false;
    modificado = false;
    return true;
  }

  for (const id of ["#volver-lista", "#cancelar-editor"]) {
    $(id).addEventListener("click", () => { if (salirDelEditor()) abrirLista(); });
  }
  window.addEventListener("beforeunload", (e) => { if (modificado || subiendo || guardando) e.preventDefault(); });
  editor.addEventListener("input", (e) => {
    if (e.target.id !== "cargar-fotos") modificado = true;
    e.target.removeAttribute("aria-invalid");
  });

  function pintarFotos() {
    $("#lista-fotos").innerHTML = fotos.map((foto, i) => `<li>
      <img src="${urlFoto(foto)}" width="160" height="120" alt="" decoding="async">
      <label for="alt-${i}">Descripción de la foto ${i + 1}</label>
      <input id="alt-${i}" value="${esc(foto.alt)}" data-alt="${i}" maxlength="160">
      <div class="fotos__acciones">
        ${i > 0 ? `<button type="button" class="enlace" data-foto="${i}" data-accion="subir">Subir<span class="sr"> foto ${i + 1}</span></button>` : ""}
        ${i < fotos.length - 1 ? `<button type="button" class="enlace" data-foto="${i}" data-accion="bajar">Bajar<span class="sr"> foto ${i + 1}</span></button>` : ""}
        <button type="button" class="enlace enlace--riesgo" data-foto="${i}" data-accion="quitar">Quitar<span class="sr"> foto ${i + 1}</span></button>
      </div>
    </li>`).join("");
    $("#cargar-fotos").disabled = subiendo || fotos.length >= campos[tipo].limite;
    $("#guardar").disabled = subiendo || guardando;
  }

  $("#lista-fotos").addEventListener("input", (e) => {
    if (e.target.matches("[data-alt]")) fotos[Number(e.target.dataset.alt)].alt = e.target.value;
  });
  $("#lista-fotos").addEventListener("click", (e) => {
    const boton = e.target.closest("[data-foto]");
    if (!boton || subiendo || guardando) return;
    const i = Number(boton.dataset.foto);
    const [foto] = fotos.splice(i, 1);
    if (boton.dataset.accion === "subir") fotos.splice(i - 1, 0, foto);
    if (boton.dataset.accion === "bajar") fotos.splice(i + 1, 0, foto);
    modificado = true;
    pintarFotos();
    const objetivo = Math.min(fotos.length - 1, i + (boton.dataset.accion === "subir" ? -1 : boton.dataset.accion === "bajar" ? 1 : 0));
    ($( `[data-alt="${objetivo}"]`) || $("#cargar-fotos")).focus();
  });

  $("#cargar-fotos").addEventListener("change", async (e) => {
    const input = e.target;
    const archivos = [...input.files];
    const estado = $("#estado-fotos");
    if (!archivos.length) return;
    if (fotos.length + archivos.length > campos[tipo].limite || archivos.some((f) => f.size > 6 * 1024 * 1024)) {
      estado.textContent = "Revisá la cantidad de fotos y el límite de 6 MB por archivo.";
      input.value = "";
      return;
    }
    const body = new FormData();
    archivos.forEach((archivo) => body.append("archivos", archivo));
    subiendo = true;
    pintarFotos();
    estado.textContent = "Subiendo fotos y preparando miniaturas…";
    try {
      const nuevas = await pedir("/imagenes", { metodo: "POST", body });
      let agregadas = 0;
      nuevas.forEach((foto) => {
        if (!fotos.some((existente) => existente.clave === foto.clave)) {
          fotos.push({ clave: foto.clave, alt: "" });
          agregadas++;
        }
      });
      modificado ||= agregadas > 0;
      estado.textContent = agregadas ? `${agregadas} ${agregadas === 1 ? "foto lista" : "fotos listas"}.` : "Estas fotos ya estaban en la publicación.";
    } catch (error) {
      await mostrarError(error, estado);
    } finally {
      subiendo = false;
      input.value = "";
      pintarFotos();
    }
  });

  const formatoImporte = new Intl.NumberFormat("es-AR", { maximumFractionDigits: 2 });
  const formatoKilometraje = new Intl.NumberFormat("es-AR", { maximumFractionDigits: 0, useGrouping: true });
  function leerImporte(texto) {
    if (!texto) return null;
    if (!/^(?:[0-9]+|[0-9]{1,3}(?:\.[0-9]{3})+)(?:,[0-9]{1,2})?$/.test(texto)) return NaN;
    return Number(texto.replaceAll(".", "").replace(",", "."));
  }

  function comprobarImporte(input) {
    const valor = leerImporte(input.value.trim());
    const valido = valor === null || Number.isFinite(valor) && valor <= 999999999999;
    input.setCustomValidity(valido ? "" : "Ingresá un importe válido, por ejemplo 26.000,50.");
    return valido;
  }
  function leerKilometraje(texto) {
    const digitos = texto.replaceAll(".", "");
    return /^\d+$/.test(digitos) ? Number(digitos) : NaN;
  }
  function comprobarKilometraje(input) {
    const valor = leerKilometraje(input.value.trim());
    const valido = !input.value.trim() || Number.isSafeInteger(valor) && valor <= 9999999;
    input.setCustomValidity(valido ? "" : "Ingresá un kilometraje entre 0 y 9.999.999 km.");
    return valido;
  }
  editor.addEventListener("input", (e) => {
    if (e.target.matches("[data-importe], [data-kilometraje]")) e.target.setCustomValidity("");
  });
  editor.addEventListener("focusout", (e) => {
    if (e.target.matches("[data-importe]") && comprobarImporte(e.target) && e.target.value.trim()) {
      e.target.value = formatoImporte.format(leerImporte(e.target.value.trim()));
    }
    if (e.target.matches("[data-kilometraje]") && comprobarKilometraje(e.target) && e.target.value.trim()) {
      e.target.value = formatoKilometraje.format(leerKilometraje(e.target.value.trim()));
    }
  });

  $("#campos").addEventListener("click", async (e) => {
    const boton = e.target.closest("#gemini-ficha");
    if (!boton) return;
    const destino = $("#estado-gemini");
    const identificacion = ["marcaNombre", "modelo", "anio"].map((campo) => editor.elements.namedItem(campo));
    for (const input of identificacion) if (!input.reportValidity() || !input.value.trim()) {
      destino.textContent = "Completá marca, modelo y año.";
      input.focus();
      return;
    }
    const [marca, modelo, anio] = identificacion.map((input) => input.value.trim());
    const camposFicha = [...editor.querySelectorAll('[name^="ficha."]')];
    const previos = camposFicha.map((input) => input.value);
    const campoTipo = editor.elements.namedItem("tipo");
    const tipoPrevio = campoTipo.value;
    boton.disabled = true;
    destino.textContent = "Buscando especificaciones…";
    try {
      const ficha = await pedir("/ficha-tecnica/gemini", { metodo: "POST", datos: { marca, modelo, anio: Number(anio) } });
      if (!boton.isConnected || $("#edicion").hidden) return;
      if (identificacion.some((input, i) => input.value.trim() !== [marca, modelo, anio][i])) {
        destino.textContent = "Cambió la identificación. Volvé a ejecutar la consulta.";
        return;
      }
      let aplicados = 0;
      camposFicha.forEach((input, i) => {
        const valor = ficha[input.name.slice(6)];
        if (valor != null && input.value === previos[i]) {
          input.value = valor;
          aplicados++;
        }
      });
      if (ficha.tipo && campoTipo.value === tipoPrevio && [...campoTipo.options].some((opcion) => opcion.value === ficha.tipo)) {
        campoTipo.value = ficha.tipo;
        aplicados++;
      }
      modificado ||= aplicados > 0;
      destino.textContent = aplicados
        ? `${aplicados} ${aplicados === 1 ? "campo completado" : "campos completados"}. Revisá los datos antes de guardar.`
        : "Revisá los cambios que hiciste durante la consulta y volvé a intentar si corresponde.";
    } catch (error) {
      if (destino.isConnected) await mostrarError(error, destino);
    } finally { boton.disabled = false; }
  });

  function datosEditor() {
    const salida = { imagenes: fotos.map(({ clave, alt }) => ({ clave, alt: alt?.trim() || "" })) };
    for (const [, definiciones] of campos[tipo].secciones) {
      for (const campo of definiciones) {
        if (campo.gestion) {
          grabarCampo(salida, campo.nombre, gestion.valores(`campo-${campo.nombre.replace(".", "-")}`));
          continue;
        }
        const input = editor.elements.namedItem(campo.nombre);
        let valor = input.value.trim();
        if (campo.tipo === "number") valor = input.value === "" ? null : input.valueAsNumber;
        if (campo.tipo === "kilometraje") valor = input.value === "" ? null : leerKilometraje(input.value.trim());
        if (campo.tipo === "checkbox") valor = input.checked;
        if (campo.nombre === "fecha" && !valor) valor = null;
        if (campo.tipo === "importe") valor = leerImporte(valor);
        grabarCampo(salida, campo.nombre, valor);
      }
    }
    if (tipo === "repuestos" && salida.precio.valor == null) salida.precio = null;
    return salida;
  }

  editor.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (subiendo || guardando) return;
    const importe = editor.querySelector("[data-importe]");
    if (importe && !comprobarImporte(importe)) { importe.reportValidity(); return; }
    const kilometraje = editor.querySelector("[data-kilometraje]");
    if (kilometraje && !comprobarKilometraje(kilometraje)) { kilometraje.reportValidity(); kilometraje.focus(); return; }
    $("#estado-editor").textContent = "";
    guardando = true;
    pintarFotos();
    try {
      const datos = { datos: datosEditor(), visible: $("#visible").checked };
      if (seleccion) {
        datos.version = seleccion.version;
        await pedir(`/catalogos/${tipo}/${encodeURIComponent(seleccion.id)}`, { metodo: "PUT", datos });
      } else {
        await pedir(`/catalogos/${tipo}`, { metodo: "POST", datos });
      }
      abrirLista({ mensaje: "Publicación guardada." });
    } catch (error) {
      await mostrarError(error, $("#estado-editor"));
      if (error.camposInvalidos?.length) {
        const primero = error.camposInvalidos[0];
        const input = editor.elements.namedItem(primero.campo);
        if (input) {
          input.setAttribute("aria-invalid", "true");
          input.focus();
        }
        $("#estado-editor").textContent += ` ${primero.mensaje}`;
      }
    } finally {
      guardando = false;
      pintarFotos();
    }
  });

  $("#solicitar-archivo").addEventListener("click", () => {
    if (guardando || subiendo) return;
    $("#error-archivo").textContent = "";
    confirmacion.showModal();
  });
  $("#archivar").addEventListener("click", async () => {
    if (!seleccion || guardando) return;
    const boton = $("#archivar");
    boton.disabled = true;
    guardando = true;
    try {
      await pedir(`/catalogos/${tipo}/${encodeURIComponent(seleccion.id)}?version=${seleccion.version}`, { metodo: "DELETE" });
      confirmacion.close();
      abrirLista({ mensaje: "Publicación archivada." });
    } catch (error) {
      if (error.status === 401) confirmacion.close();
      await mostrarError(error, $("#error-archivo"));
    } finally {
      guardando = false;
      boton.disabled = false;
    }
  });

  identificar("", false).catch((error) => { estadoGeneral.textContent = error.message; });
})();
