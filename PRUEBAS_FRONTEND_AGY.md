# Plan de pruebas frontend para Agy

## Objetivo

Ejecutar pruebas de extremo a extremo en la app Android DAJHO y verificar que las opciones de **Gestión del negocio** leen y escriben en el proyecto Supabase correcto. El emulador y el backend deben ser de pruebas. No usar cuentas, productos, clientes, referencias de pago ni datos de producción.

La cuenta usada en la revisión del 6 de noviembre de 2026 fue propietaria de **Tienda de Prueba**. Se comprobó que se pueden leer datos de caja, clientes, planes/pagos e historial de ventas. Se crearon por la interfaz un cliente, proveedor, categoría y temporada llamados `PRUEBA-CODEX-0611`, se verificaron en Supabase y se borraron después. También se abrió la caja #7 con monto inicial cero y se cerró declarando cero; quedó cerrada con diferencia cero.

## Preparación y seguridad de la prueba

1. Usar APK `debug` y emulador/dispositivo de pruebas con conexión de red.
2. Iniciar sesión con un propietario de pruebas y seleccionar explícitamente un negocio de pruebas.
3. Registrar el nombre e ID del negocio, versión del APK, fecha, dispositivo y cuenta (sin copiar contraseñas ni tokens al informe).
4. Confirmar con Agy que la base conectada es la de pruebas. La URL configurada históricamente en este workspace es `https://itiosytelrpxkmfeormx.supabase.co`; verificar el entorno antes de escribir.
5. Crear registros con prefijo único `QA-AGY-<fecha>-<caso>`. Antes de guardar, comprobar que no exista ya ese prefijo.
6. No hacer pagos reales ni validar pagos usando un administrador real. No anular ventas reales. No comprar a un proveedor real ni cambiar stock de productos preexistentes sin plan de reversión revisado.
7. Guardar evidencia de pantalla y resultado, pero ocultar datos personales, correo, referencias, tokens y credenciales.

## Casos de prueba

### 1. Navegación y lectura

Abrir desde Perfil → Gestión del negocio cada módulo y registrar si carga, muestra estado vacío/error correctamente y permite actualizar:

- Caja
- Clientes
- Planes y pagos
- Proveedores
- Compras
- Categorías
- Temporadas
- Historial de ventas

Verificar en Supabase que las filas mostradas pertenecen al negocio seleccionado, que el estado/campos coinciden y que una lista vacía no se presenta como un fallo de conexión.

### 2. Caja — prioridad alta, defecto ya observado

**Precondición:** negocio QA sin caja abierta. Si ya existe caja abierta, no abrir otra; verificar que la acción sea “Cerrar caja”.

1. Abrir Caja y guardar monto inicial `0.00`.
2. Confirmar que aparece una caja abierta en pantalla y en Supabase.
3. Confirmar que la acción principal cambia a “Cerrar caja”, con el número correcto de caja.
4. Cerrar declarando `0.00` si no hubo movimientos; comprobar estado cerrado, esperado/declarado y diferencia cero.
5. Reabrir módulo y verificar que ofrece “Abrir caja”.

**Defecto conocido:** el 2026-11-06, tras abrir la caja #7, la pantalla mostró `ABIERTA` en la fila pero todavía ofrecía **“Abrir caja”** en el encabezado. La caja se cerró por backend después de confirmar que no tenía movimientos. Intentar reproducir y documentar si persiste.

No cerrar una caja que ya tuviera movimientos o que no haya sido abierta durante esta prueba.

### 3. Clientes

1. Crear `QA-AGY-<fecha>-CLIENTE` con teléfono/identificación ficticios reservados para QA.
2. Comprobar que aparece en la lista y que la fila en Supabase tiene el `negocio_id` seleccionado.
3. Editar el nombre o teléfono y verificar persistencia tras actualizar/reabrir.
4. Borrar el registro solo si no tiene ventas/deudas relacionadas; si no existe borrado en UI, documentar el registro para limpieza controlada.

### 4. Proveedores

Repetir alta, lectura y edición con `QA-AGY-<fecha>-PROVEEDOR`. Comprobar identificación y teléfono. Probar lista con más de una página si el entorno permite >200 proveedores: el selector de compras podría consultar solo la primera página.

### 5. Categorías y temporadas

1. Crear categoría `QA-AGY-<fecha>-CAT` y temporada `QA-AGY-<fecha>-TEMP`.
2. Para temporada, probar fechas válidas; opcionalmente verificar que una fecha inválida se rechace con mensaje claro.
3. Confirmar que ambas aparecen al volver a abrir cada pantalla.
4. Comprobar que el formulario de clasificación de producto carga estas opciones desde Supabase.
5. Limpiar después de desvincularlas de cualquier producto; no cambiar productos existentes sin autorización y reversión explícita.

### 6. Compras — completar solo con variante QA

El backend ya pasó pruebas transaccionales/idempotentes de compra con rollback. Falta el flujo UI real.

1. Crear un producto y variante exclusivamente QA, con SKU `QA-AGY-<fecha>`, precio/costo válidos y stock inicial conocido.
2. Crear proveedor QA y registrar compra con cantidad/costo pequeños conocidos.
3. Verificar en Supabase compra, detalle de compra, incremento de stock, movimiento de inventario y proveedor/negocio correctos.
4. Comprobar que repetir/reanudar una solicitud no duplica compra ni stock.
5. No borrar manualmente movimientos de auditoría. Limpiar el producto/proveedor solo si las FK lo permiten y la política del entorno lo autoriza; en caso contrario dejar identificado el dato para reset del entorno QA.

### 7. Planes y pagos — no transferir dinero

1. Verificar que el plan actual, estado, vigencia, planes activos e historial reflejan datos Supabase.
2. Enviar una solicitud de QA con referencia ficticia `QA-AGY-<fecha>-PAGO` solo si el negocio y backend son de pruebas.
3. Confirmar que aparece como pendiente en el historial y que no activa Pro antes de validación.
4. Confirmar que propietario no puede aprobar su propio pago y que tarjeta no se presenta como método habilitado por esta función.
5. No ejecutar aprobación administrativa salvo que Agy disponga de una cuenta admin de pruebas con segundo factor y un plan de reversión. No usar ni compartir credenciales admin reales.

### 8. Historial de ventas

1. Verificar que las filas corresponden al negocio y que IDs, fecha, total y estado coinciden con Supabase.
2. Comprobar paginación/“cargar más” con más de 200 registros si hay volumen.
3. No anular una venta previa existente.
4. Si se prueba anulación, crear primero venta exclusivamente QA con producto QA, caja abierta QA y pago QA; comprobar restauración de stock y movimientos de caja/inventario. Registrar motivo identificable.
5. No borrar una anulación: es evidencia contable/auditable.

## Pruebas negativas y permisos

- Probar acceso con rol vendedor a pantallas/acciones restringidas (proveedor, compra, categoría, temporada, historial/anulación según diseño).
- El backend debe rechazar operaciones de otro negocio y acciones sin rol; no enviar IDs de negocio ajenos fuera de un fixture autorizado.
- Repetir una solicitud QA con el mismo identificador y mismo payload: una sola operación. Reutilizar el identificador con payload distinto: rechazo.
- Verificar que errores de red/auth no se muestran como listas vacías ni importes cero.

## Criterios de aceptación

- Cada módulo enseña datos del negocio activo y refresca después de una escritura.
- Altas/ediciones aparecen en Supabase con relación correcta al negocio.
- Caja refleja el estado real y la acción disponible coincide con el estado.
- Compra mantiene consistencia entre cabecera, detalles, stock y movimientos.
- Solicitud de pago queda pendiente y no otorga plan hasta validación autorizada.
- Historial coincide con la base y las anulaciones QA revierten los efectos esperados.
- Pruebas sin permisos son rechazadas y ninguna operación se duplica al reintentar.
- Se eliminan solo los registros QA sin dependencias; se anotan los que requieren reset del entorno.

## Evidencia a entregar

Por caso: ID, módulo, precondiciones, pasos ejecutados, resultado esperado/obtenido, captura redactada, consulta/filas verificadas, datos QA creados y estado de limpieza. Clasificar cada resultado como **Pasó**, **Falló**, **Bloqueado** o **No ejecutado**. No afirmar que hubo prueba UI de un módulo si solo se ejecutó una prueba SQL.
