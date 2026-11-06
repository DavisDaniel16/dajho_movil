Re# Revisión de integración Android y Supabase

Fecha: 2026-11-05. Proyecto MCP: itiosytelrpxkmfeormx.

Se revisó el código actual, incluidos los cambios locales que ya existían, y se contrastó con columnas, firmas de funciones, permisos, políticas, restricciones y triggers del backend mediante Supabase MCP. No se modificó código funcional, datos ni configuración remota. Este informe distingue evidencia estática de pruebas completas de uso.

## Resultado por módulo

| Módulo | Conexión actual | Resultado |
|---|---|---|
| Configuración | BuildConfig → SupabaseModule → Auth/Postgrest | La URL local coincide con MCP y existe clave configurada. El build permite valores vacíos si faltan propiedades; conviene fallar al compilar. No se imprimió la clave. |
| Registro | signUpWith Email + metadatos → trigger crear_perfil_nuevo | El trigger existe. Confirmación depende de plantilla de correo y redirect de Auth, no verificados en ejecución. |
| Confirmación | dajho://confirm + token_hash → verifyEmailOtp | Manifest y handler coinciden para ese formato; no se comprobó que el correo real entregue ese formato. |
| Login y alta de negocio | signInWith → negocio_usuario → crear_negocio | Firma RPC correcta; el fallo al crear negocio se oculta y permite continuar sin negocio. |
| Sesión y negocio | currentSessionOrNull/currentUserOrNull + consulta repetida | No existe selección central de negocio; se presupone una membresía por usuario. |
| Inicio | venta + gasto | Campos y estados existen. Fechas sin zona horaria y errores convertidos en ceros. |
| Venta y carrito | variante → producto + registrar_venta | Firma y claves JSON coinciden; falta gestionar caja, reintentos seguros, precisión monetaria y estado activo. |
| Cobro | efectivo, transferencia, QR, fiado | Se conectan a registrar_venta; pago único, fiado siempre sin_fecha. No hay pagos mixtos ni vencimiento configurable. |
| Balance y detalle | venta → detalle_venta → variante → producto; gasto | Campos y relaciones existen. Total vendido se presenta como ingresos, incluyendo fiado; no incorpora abonos. |
| Gastos | INSERT directo en gasto | Omite registrar_gasto y su movimiento de caja. |
| Inventario | variante → producto | Campos y relaciones existen; sin paginación. |
| Crear producto | crear_producto_con_variantes | Firma correcta; trigger anula el stock inicial y validación de precios difiere de CHECK. |
| Editar variante | UPDATE de stock y precios | Permisos existen; escritura absoluta de stock sin movimiento de inventario ni control de concurrencia. |
| Clientes | SELECT en selector de fiado | Selección conectada; administración de clientes pendiente. |
| Deudas | fiado → cliente/venta/detalle/variante/producto | Consulta y DTO coinciden en campos; abonos no conectados. |
| Empleados | obtener_empleados + crear_empleado_y_vinculacion | Parámetro incorrecto al listar; contrato de creación coincide. Editar/desactivar no implementado. |
| Perfil | perfil.nombre + negocio.nombre | Permisos de columnas correctos; dos escrituras independientes y posibilidad de anunciar éxito sin filas actualizadas. |
| Planes y suscripciones | Botón de aviso | Sin conexión a plan, suscripcion, solicitar_pago_pro, pago_suscripcion o factura_suscripcion. |
| Proveedores y compras | Aviso en Inicio | Sin gestión de proveedor ni llamada a registrar_compra. |
| Caja | Ninguna pantalla ni llamada | abrir_caja/cerrar_caja existen, pero no se usan. |
| Ajustes y anulaciones | Ninguna llamada | ajustar_inventario y anular_venta existen, pero no se usan. |
| Categorías y temporadas | Ninguna llamada | Tablas disponibles sin gestión ni asignación desde el alta de producto. |
| Soporte, administración y auditoría | Sin interfaz | Tablas y RPC disponibles; sin flujos Android ni MFA para administración. |
| Catálogo, búsqueda global e impresora | Avisos | Funciones pendientes; no hay integración implementada. |
| Términos y privacidad | Toast | No abren documentos. |

## Hallazgos prioritarios

### P1 — Listado de empleados roto por nombre de parámetro

`EmpleadosFragment.kt:105-108` llama `obtener_empleados` con `p_negocio`. La única firma desplegada espera `p_negocio_id bigint`. PostgREST resuelve por nombre de argumento; esta llamada no coincide. Cambiar el argumento y comprobar listado vacío y listado con empleados. `NuevoEmpleadoBottomSheet.kt:77-84` sí coincide con la firma de creación.

### P1 — No se puede comenzar a vender desde un negocio sin caja

`registrar_venta` exige una caja abierta. No hay llamadas a `abrir_caja` ni `cerrar_caja` en Android. La consulta de lectura encontró 3 negocios sin caja abierta de los 4 existentes. Implementar apertura/cierre, consultar estado antes del cobro y mostrar la acción que necesita el usuario.

### P1 — El stock inicial solicitado se descarta

`NuevoProductoBottomSheet.kt:195` envía `stock_actual`. La RPC lo inserta, pero el trigger `variante_stock_inicial` ejecuta `stock_inicial_cero`, que asigna cero incondicionalmente antes del INSERT. El formulario puede anunciar éxito con un stock diferente del solicitado. Definir un único flujo de entrada inicial con movimiento de inventario y validación de permisos.

### P1 — Gastos fuera del flujo de caja

`GastoBottomSheet.kt:98` inserta directamente. El único trigger de gasto es de auditoría; no genera movimiento de caja. La RPC `registrar_gasto` comprueba acceso, negocio activo y caja abierta y crea el movimiento negativo. Se encontraron 2 gastos actuales sin movimiento asociado. Usar la RPC y restringir la escritura directa; los datos existentes requieren conciliación separada, no realizada.

### P1 — Reintentar un cobro puede duplicarlo

`CobroBottomSheet.kt:274` genera un UUID nuevo en cada intento. El servidor usa `(negocio_id, uuid_dispositivo)` para reconocer ventas ya recibidas. Si el servidor confirma y se pierde la respuesta, el reintento usa otro identificador y puede descontar stock/cobrar nuevamente. Conservar identificador y payload del cobro hasta resolver su resultado; recuperar por identificador ante respuesta incierta.

### P1 — Edición de stock pierde trazabilidad y puede sobrescribir ventas

`EditarProductoBottomSheet.kt:80` lee stock y `:130-140` escribe un valor absoluto después. Una venta entre ambas operaciones puede quedar sobrescrita. No se llama `ajustar_inventario` y el trigger de auditoría no sustituye a `movimiento_inventario`. Separar precios de ajustes; registrar conteos/ajustes con motivo y bloqueo en servidor.

### P1 — Controles de suspensión y membresía inconsistentes

Las políticas permisivas `producto_insertar`, `variante_insertar` y `variante_editar_propietario` no exigen `negocio_activo`, a diferencia de las políticas ALL. Se combinan mediante OR, por lo que no imponen la misma restricción. `gasto_insertar` tampoco verifica negocio activo ni que `usuario_id = auth.uid()`. La RPC SECURITY DEFINER de crear productos no comprueba negocio activo. `crear_empleado_y_vinculacion` y `obtener_empleados` verifican propietario pero omiten `activo` en la membresía y estado del negocio. Unificar invariantes del servidor con mi_rol/negocio_activo según la operación. No se realizaron escrituras para demostrar explotación.

### P2 — Valores de precios aceptados por el formulario y rechazados por el backend

`NuevoProductoBottomSheet.kt:141-155` acepta cero para venta/costo y usa costo cero si está vacío. Las restricciones `variante_precio_costo_check` y `variante_precio_venta_check` exigen valores mayores que cero. El formulario oculta el detalle de error con un mensaje genérico. Alinear reglas y mensajes; la edición también carece de validaciones equivalentes antes de enviar.

### P2 — Métricas diarias usan una zona horaria implícita

`HomeFragment.kt:154-169` compara timestamptz con cadenas sin offset, mientras calcula el día con LocalDate del dispositivo. No garantiza el día de Ecuador; además el límite inclusivo 23:59:59 omite fracciones del último segundo. Usar intervalo [inicio del día, inicio del siguiente) con zona de negocio explícita. El catch sustituye errores por $0.00 y no permite distinguir ausencia de ventas de fallo de consulta.

### P2 — Dinero con Double y factor SQL redondeado prematuramente

El carrito y el cobro calculan pagos con Double, y PostgreSQL compara sumas numeric exactamente. Ejemplo de riesgo: 0.10 + 0.20 serializa como 0.30000000000000004 mientras el total servidor queda a centavos. Usar centavos enteros o BigDecimal con escala acordada. En `registrar_venta`, `v_factor_desc numeric(12,2)` reduce la precisión de la proporción antes de validar el mínimo; puede rechazar o aceptar incorrectamente precios cercanos al mínimo. Calcular esa validación con suficiente precisión y regla de redondeo explícita.

### P2 — Balance mezcla ventas con dinero cobrado

`BalanceFragment.kt:137-195` suma total de ventas completadas, incluido fiado, como ingreso y no consulta abono/venta_pago. Si el producto pretende mostrar flujo de dinero, el resultado está sobreestimado mientras existan saldos pendientes y no refleja cobros posteriores. Definir si se muestra resultado de ventas o flujo de cobros, y calcular desde las fuentes correspondientes.

### P2 — Alta de negocio puede fallar silenciosamente

`LoginActivity.kt:122-133` captura la excepción de crear_negocio, la registra y continúa a Home. Falta un estado de alta pendiente con error recuperable y relectura de membresía. No usar datos ficticios de identificación si faltan metadatos.

### P2 — Sin contexto único de negocio ni restauración de sesión coordinada

Las pantallas consultan `negocio_usuario` por usuario y consumen `decodeSingleOrNull` sin elegir negocio ni filtrar `activo`. El esquema permite varias membresías; no se detectaron usuarios con varias actualmente. La consulta RLS puede devolver membresías propias inactivas dentro de un negocio compartido. La cabecera y cada pantalla resuelven contexto independientemente. Centralizar sesión, membresía activa, negocio seleccionado y rol, y esperar inicialización de Auth antes de decidir navegación. El login siempre presenta formulario; no implementa recuperación de contraseña.

### P2 — Pantallas con argumentos de constructor no restaurables

CobroBottomSheet, ClientesBottomSheet, GastoBottomSheet, EditarProductoBottomSheet, EditarPerfilBottomSheet, DeudasDetalleBottomSheet, DetalleVentaBottomSheet y NuevoEmpleadoBottomSheet reciben argumentos obligatorios de constructor sin FragmentFactory. La restauración estándar después de recreación del proceso puede fallar. Usar arguments/SavedStateHandle y conservar el carrito/cobro pendiente.

### P2 — Sin paginación ni agregación de reportes

Inventario, clientes, ventas, deudas y balance leen listas completas sin range/paginación. Los totales calculados en cliente pueden quedar incompletos cuando se alcance el límite de respuesta de la API. Usar paginación en listas y agregación en servidor para métricas. No se verificó el valor configurado del límite de API.

### P2 — Búsqueda de ventas incompleta y respuestas fuera de orden

La interfaz promete buscar por nombre o código (`fragment_venta.xml:18`), pero `VentaFragment.kt:252` filtra solo sku. `searchProducts` inicia una nueva corrutina independiente; cancelar searchJob no cancela necesariamente la petición hija lanzada en otro lifecycleScope. Una respuesta anterior puede sustituir la búsqueda actual. Filtrar nombre/código y mantener una sola operación cancelable vinculada a la vista.

### P2 — Escrituras sin confirmación de filas y cambios parciales

Las actualizaciones de perfil/negocio y variante anuncian éxito sin solicitar resultado ni comprobar filas afectadas. Un filtro RLS puede dejar cero filas modificadas. Perfil y negocio se guardan en dos peticiones independientes: si falla la segunda, queda un cambio parcial. Validar resultados y definir una operación transaccional cuando el producto requiera guardar ambos juntos. El vendedor tampoco puede editar su propio nombre desde PerfilActivity, pese a tener permiso backend; confirmar si esta restricción de interfaz es intencional.

## Backend sin flujo de interfaz

Registrar abonos, abrir/cerrar caja, registrar compras, ajustar inventario, anular ventas, solicitar/validar pago Pro, autorizar/solicitar soporte y administración de estados existen en backend sin llamadas Android. Las tablas categoría, temporada, proveedor, suscripción, pagos/facturas de suscripción, acceso_soporte y auditoría no cuentan con gestión funcional en la app. La tarjeta de deudas de Inicio muestra un aviso aunque existe DeudasFragment en la navegación inferior.

La creación de empleados escribe directamente auth.users con contraseña cifrada y correo confirmado, sin alta en auth.identities. No se validó iniciar sesión con una cuenta creada así: necesita prueba de ciclo completo en entorno de pruebas y preferentemente un flujo de invitación/administración Auth desde servidor. No se enviaron contraseñas ni se crearon usuarios durante esta revisión.

El asesor de seguridad de la revisión anterior reportó 19 funciones SECURITY DEFINER ejecutables por anon y 30 por authenticated, además de protección de contraseñas filtradas desactivada. Poder ejecutar una función no prueba acceso indebido por sí mismo; revisar autorización interna y reducir permisos donde corresponda.

## Verificación y límites

- URL Android comparada con proyecto MCP: coincide.
- Firmas de las 5 RPC usadas por la app contrastadas: crear_negocio, registrar_venta, crear_producto_con_variantes y crear_empleado_y_vinculacion coinciden; obtener_empleados tiene el argumento incorrecto.
- Tablas/columnas, relaciones, políticas, permisos de columnas, restricciones y triggers consultados en lectura.
- Consultas agregadas: 2 gastos sin movimiento de caja; 3 negocios sin caja abierta; 0 usuarios con varias membresías; 2 fiados sin relaciones rotas con cliente/venta.
- `gradlew.bat testDebugUnitTest assembleDebug --no-configuration-cache`: BUILD SUCCESSFUL. El primer intento offline no pudo resolver aapt2; el posterior descargó dependencias y compiló.
- El test unitario existente solo verifica 2 + 2; el instrumentado verifica el paquete. No prueban integración.
- No se ejecutaron flujos de escritura ni correos, pagos, impresión, RPC mutantes o modificaciones de esquema. No hubo pruebas en dispositivo ni llamadas REST autenticadas de extremo a extremo; las lecturas SQL y la compilación no certifican esos flujos.
- No se modificaron archivos funcionales ni los cambios locales preexistentes. Se añadió únicamente este informe; Gradle generó sus artefactos habituales.

Orden recomendado: contrato de empleados; caja; stock inicial/ajustes; gastos; idempotencia del cobro; unificación de permisos; precisión y fechas; después completar las funciones pendientes y pruebas de integración en entorno separado.
