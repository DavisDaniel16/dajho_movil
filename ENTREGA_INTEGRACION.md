# Entrega de integración Android y Supabase

Fecha: 2026-11-05. Backend: `itiosytelrpxkmfeormx`.

## Fases implementadas

### 1. Backend operativo

Se aplicaron 13 migraciones, conservadas en `supabase/migrations`, y se desplegó la Edge Function `crear-empleado` (versión 2, JWT obligatorio). La copia previa de las funciones está en `supabase/baseline`; es una referencia de revisión, no un script para restablecer toda la base.

- Caja, ventas, compras, abonos, gastos y ajustes serializan las operaciones de un negocio mediante bloqueos transaccionales.
- El stock inicial se registra como un movimiento real. La edición de inventario detecta cambios concurrentes y no sobrescribe ventas con una lectura anterior.
- Ventas, gastos, abonos y compras tienen protección contra reintentos duplicados. Las solicitudes pendientes se guardan antes de enviarlas y pueden recuperarse.
- Importes finitos, positivos según operación y con centavos coherentes. La proporción de descuento se calcula sin redondearla prematuramente.
- Fechas operativas y resúmenes diarios en `America/Guayaquil`; métricas agregadas en servidor.
- Perfil y nombre de negocio se guardan juntos en una transacción.
- Miembros inactivos y negocios suspendidos no pueden usar las operaciones corregidas.
- Escrituras directas de stock, gastos y permisos de empleados restringidas; las RPC validan rol y contexto.
- Se retiró acceso anónimo a funciones y acceso de usuarios a funciones exclusivas de triggers y servidor.
- Políticas separadas por operación e índices de claves foráneas. El asesor ya no reporta claves foráneas sin índice, políticas permisivas superpuestas ni evaluaciones repetidas de Auth en RLS.

### 2. Conexión Android

- Contexto único de negocio activo y selector en el nombre del negocio.
- Listado de empleados con el argumento `p_negocio_id` correcto.
- Cobro conserva la solicitud exacta ante pérdida de respuesta; permite fiado con abono inicial en efectivo y fecha de vencimiento opcional.
- Gastos usan la operación transaccional y generan movimiento de caja.
- Inventario usa RPC de edición validada, no actualización absoluta sin control.
- Búsqueda por nombre, SKU o código de barras. Catálogo, clientes y deudas se recuperan por páginas.
- Balance usa cobros recibidos y abonos, excluyendo lo pendiente de fiado; el resumen de Inicio sigue mostrando ventas.
- Pantallas con argumentos restaurables y carrito conservado durante recreación de proceso.
- Corrutinas asociadas a la vista y manejo de cancelación; errores de red no sustituyen métricas por ceros.
- Errores depurados: no se muestran ni registran las cabeceras que pueden contener tokens.
- Confirmación por token_hash o enlace de sesión; recuperación de contraseña y conservación de espacios en contraseñas.
- El build falla si falta la configuración de Supabase. Se desactivó backup de datos privados del aplicativo.

### 3. Funciones conectadas

| Función | Acceso |
|---|---|
| Apertura/cierre e historial de caja | Icono de usuario → Gestión del negocio → Caja |
| Recuperar gasto/abono/compra pendiente | Gestión del negocio, botón de recuperación cuando existe una solicitud |
| Clientes | Tarjeta de Inicio o Gestión del negocio; alta y edición |
| Proveedores y compras | Inicio/Gestión; selección de proveedor, líneas de compra y revisión antes de registrar |
| Abonos | Deudas → cliente → Registrar abono → deuda y método |
| Empleados | Inicio → Empleados; crear, cambiar tope y activar/desactivar |
| Categorías y temporadas | Gestión del negocio |
| Clasificación del producto | Inventario → editar → Nombre, marca, categoría y temporada |
| Anulación | Gestión → Historial de ventas; motivo obligatorio |
| Auditoría y movimientos | Gestión del negocio |
| Plan y comprobante de pago Pro | Gestión → Planes y pagos; también desde Perfil |
| Catálogo | Inicio → Catálogo; consulta, compartir e imprimir |
| Comprobante interno | Balance → venta; compartir o imprimir por los servicios de Android |
| Administración | Cuenta administradora → autenticador TOTP → negocios, validación de pagos y soporte |
| Soporte del propietario | Gestión → Acceso de soporte; autorización de solicitudes |

La creación de empleados usa Auth Admin en una función de servidor, con autorización del propietario y solicitudes recuperables. La clave de servicio permanece en el entorno de Supabase; no se añadió al APK. La antigua RPC que escribía directamente `auth.users` dejó de estar expuesta a usuarios.

## Verificación

- `gradlew.bat testDebugUnitTest assembleDebug --offline --no-configuration-cache`: correcto, 8 tests unitarios.
- `gradlew.bat connectedDebugAndroidTest --offline --no-configuration-cache`: correcto en Pixel 6, Android 17; 2 pruebas de restauración y 1 de contexto. El host de prueba existe solo en la variante debug.
- Se comprobó que eliminar la multiplicación por cantidad hace fallar el test monetario esperado y se restauró inmediatamente el código.
- `supabase/tests/integracion_rollback.sql`: correcto como rol `authenticated`. Cubre stock inicial y su movimiento, idempotencia de venta/gasto/abono/compra, concurrencia de stock, venta mixta, abono y saldo, compra, anulación, balance, cierre, rechazo de NaN, rechazo de acceso administrativo e aislamiento RLS. Los registros de prueba se revierten.
- `python tools/smoke_rest.py`: perfiles, ventas, RPC antigua de alta y Edge Function rechazan clientes anónimos con 401/404.
- Asesores: salud sin incidencias; sin funciones privilegiadas accesibles a `anon`. Las RPC autenticadas necesarias siguen apareciendo como aviso informativo de superficie privilegiada, con comprobaciones internas.
- Lint: correcto, sin errores y con 335 advertencias de localización, estilo, recursos y dependencias. Tres reglas del detector `UnsafeFragmentLifecycleObserverDetector` se excluyen porque recorren callbacks cíclicos durante minutos. No hay llamadas `LiveData.observe`, `OnBackPressedDispatcher.addCallback` ni `addMenuProvider` en el código. El resto de reglas continúa activo. [Problema documentado de AndroidX](https://issuetracker.google.com/issues/501504696).

APK de prueba: `app/build/outputs/apk/debug/app-debug.apk`. No se publicó en una tienda ni se generó una firma comercial de producción.

SHA-256 del APK: `6787E0A9BA3059F1A8A9F3EA534E0F02D292FA9318E0B60ABEE7C77D2E3F188C`.

Comprobación final de conservación: 4 negocios, 7 productos, 8 variantes, 2 gastos, 11 ventas y 6 usuarios; los mismos conteos del diagnóstico inicial.

## Información externa pendiente para cerrar la puesta en producción

1. **Términos y Privacidad:** faltan los enlaces oficiales. Las propiedades `TERMS_URL` y `PRIVACY_URL` de `local.properties` alimentan los enlaces de la app. No se redactaron ni publicaron documentos legales inventados.
2. **Impresora:** la app usa los servicios de impresión de Android y permite guardar/imprimir PDF. Bluetooth térmico depende del modelo y del servicio/controlador instalado. Falta prueba con la impresora real.
3. **Auth:** el MCP no expone la configuración de allowlist, plantillas de correo ni protección de contraseñas filtradas. Verificar `dajho://confirm` entre los redirects permitidos, revisar el enlace de confirmación/recuperación y habilitar protección de contraseñas filtradas desde la configuración Auth del proyecto. No se enviaron correos de prueba ni se restablecieron contraseñas reales.
4. **Histórico de gastos:** se conservaron dos gastos sin movimiento de caja. Uno tiene una caja coincidente en su intervalo; el otro, de $10 del 2026-11-05, no tiene ninguna. No se inventó caja ni se alteraron importes históricos. Se solicitó la clasificación del gasto sin caja.
5. **Facturación electrónica y cobro de suscripciones:** la solicitud de comprobante y su validación están conectadas. Emitir facturas fiscales y cobrar tarjetas requiere el proveedor, credenciales, datos de cobro y configuración correspondiente; no se implementó una emisión fiscal ficticia. Los comprobantes imprimibles actuales son internos.
6. **Alta de empleados de extremo a extremo:** se verificaron despliegue y rechazo anónimo, y se revisaron los controles de vinculación; falta una prueba con una cuenta de propietario de pruebas y login de un empleado creado por Auth Admin. No se crearon cuentas permanentes ni se usaron contraseñas reales para las pruebas.

Los archivos locales modificados antes de iniciar este trabajo se conservaron. La revisión inicial se mantiene en `REVISION_INTEGRACION.md` como diagnóstico histórico; este documento describe lo entregado y sus límites.
