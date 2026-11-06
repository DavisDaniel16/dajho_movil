# Implementación por fases

Autorizado el 2026-11-05: corregir Android y backend conservando datos.

1. Backend transaccional: permisos, caja, stock inicial, concurrencia, importes y contratos.
2. Android: sesión/negocio, cobro persistente, gastos, inventario, fechas y reportes.
3. Flujos: caja, abonos, clientes, proveedores/compras, empleados, planes y gestión de catálogo.
4. Verificación: compilación, pruebas de dinero y contratos, regresiones SQL con rollback y revisión de seguridad.

Estado: fases 1 a 3 implementadas y desplegadas en el backend. Fase 4 aprobada: compilación, 8 pruebas unitarias, 3 pruebas en emulador, comprobaciones REST anónimas, regresiones SQL y Lint (con la exclusión puntual documentada en Gradle). Los pendientes externos se detallan en ENTREGA_INTEGRACION.md; no se considera cerrada la puesta en producción hasta resolverlos.

Los cambios previos del usuario se conservan. No se reinterpretaron importes históricos ni se inventaron movimientos de caja para gastos anteriores.

Pendientes externos: enlaces oficiales de Términos/Privacidad, modelo y servicio de impresión, confirmación del gasto histórico de $10 sin caja, allowlist/plantillas de Auth y protección de contraseñas filtradas (el MCP conectado no expone esa configuración). La emisión fiscal electrónica requiere su configuración y credenciales; no se sustituye con un comprobante interno.
