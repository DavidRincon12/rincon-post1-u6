# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción

Proyecto Spring Boot `pedidos-service` (groupId `com.tienda`) para diagnosticar y refactorizar
antipatrones en un sistema de gestión de pedidos de comercio electrónico.

## Decisiones de diseño

### Parte 1 — Diagnóstico de `GestorPedidos`

> Las líneas citadas corresponden a
> [`GestorPedidos.java` en el commit del código de partida](src/main/java/com/tienda/pedidos/service/GestorPedidos.java),
> antes de cualquier refactorización.

**Antipatrón identificado: God Object y Spaghetti Code combinados.**

#### 1. God Object: seis razones distintas para cambiar en un solo método

`procesarPedido()` (líneas 33-138, ~106 líneas) es el único método público de la clase y
concentra seis responsabilidades que cambian por motivos y a ritmos diferentes:

| # | Responsabilidad | Líneas | Quién pediría el cambio |
|---|---|---|---|
| 1 | Validación de stock (`SELECT stock FROM inventario`) | 36-49 | Bodega / inventario |
| 2 | Validación de cliente y mora, con excepción por horario de corte | 51-70 | Cartera |
| 3 | Cálculo de subtotal (una consulta `SELECT precio` por ítem) | 72-78 | Catálogo |
| 4 | Cálculo de descuento por tipo de cliente + impuesto del 19 % | 80-101 | Mercadeo / contabilidad |
| 5 | Persistencia del pedido, del detalle y descuento de inventario vía JDBC | 103-118 | Base de datos |
| 6 | Construcción y envío del correo de confirmación | 120-134 | Comunicaciones |

A esto se suma el registro (`log.info`/`log.warn`) intercalado en todas las secciones (líneas 34,
38, 46, 55, 64, 67, 132, 136). La clase depende directamente de `JdbcTemplate`, `EmailService` y
del reloj del sistema, y conoce el nombre de **cinco tablas** (`inventario`, `clientes`,
`facturas`, `productos`, `pedidos`) más `detalle_pedido`. Cualquier cambio en reglas de negocio,
esquema de base de datos o formato del correo obliga a abrir y releer el mismo método.

#### 2. Spaghetti Code: anidamiento y mezcla de niveles de abstracción

- **Validación de mora (líneas 57-69): 3 niveles de anidamiento condicional.**
  `else if (tipoCliente.equals("MOROSO"))` → `if (deudaPendiente != null && deudaPendiente > 0)`
  → `if (ahora.isBefore(LocalTime.of(20, 0)))` con su `else`. La regla "un moroso puede comprar
  después de las 20:00" queda enterrada en el tercer nivel, mezclada con una consulta SQL
  (líneas 58-60) y con la lectura del reloj (línea 62).
- **Cálculo de descuento (líneas 81-98): 2 niveles de `if/else if` encadenados por tipo de
  cliente**, con una consulta SQL (`SELECT COUNT(*) FROM pedidos`, líneas 91-92) metida en medio
  de la rama `FRECUENTE`. La rama `VIP` tiene tres sub-casos y `FRECUENTE` dos; no hay rama
  explícita para los demás tipos (el descuento queda en `0` por omisión, línea 81).
- **Cuatro niveles de abstracción en la misma secuencia de líneas**: SQL embebido (líneas 42-44,
  52-53, 58-60, 75-76, 91-92, 104-117), reglas de negocio (umbrales `1_000_000`, `500_000`,
  `> 10`, `> 3`, hora de corte `20:00`, impuesto `0.19`), formato de texto del correo
  (`StringBuilder`, líneas 122-128) y manejo de errores de infraestructura (`try/catch`,
  líneas 129-134).
- **El flujo de control usa retornos tempranos en cuatro puntos distintos** (líneas 39, 47, 56 y
  65) intercalados con escrituras en el log, lo que obliga a leer el método completo para saber
  en qué estado puede terminar un pedido.
- **Sin transacción explícita**: el `INSERT` del pedido (líneas 104-108), el `CALL IDENTITY()`
  (línea 109) y los `INSERT`/`UPDATE` por ítem (líneas 111-118) pueden quedar a medias si falla
  uno intermedio.

#### 3. Costo de extender: un nuevo tipo de cliente

Agregar un tipo de cliente con reglas de descuento propias (p. ej. `CORPORATIVO`) obliga a
**modificar el bloque de las líneas 82-98** insertando una rama `else if` más, y si la regla
necesita datos de la base, a escribir otra consulta SQL dentro de esa rama. No se puede probar
la regla nueva sin levantar la base de datos ni el servicio de correo, porque todo vive en el
mismo método. Es una violación directa de OCP y SRP.

### Ajustes mínimos al código de partida

Para que el código de partida compilara y pudiera probarse se hicieron tres ajustes que **no
cambian su comportamiento observable**:

1. `LocalTime.now()` → `LocalTime.now(reloj)`, con un `Clock` inyectado (`RelojConfig`), para
   probar la regla de corte de las 20:00 sin depender de la hora real.
2. Las consultas de stock y tipo de cliente usan `DataAccessUtils.singleResult(queryForList(...))`
   en vez de `queryForObject(...)`, que lanza `EmptyResultDataAccessException` cuando no hay
   fila. Así la rama `tipoCliente == null` ("Cliente no registrado") es alcanzable, como pretende
   el código original.
3. La base H2 corre en `MODE=LEGACY` porque H2 2.x retiró `CALL IDENTITY()` del modo por defecto.

## Cómo ejecutar

```bash
mvn spring-boot:run
mvn test
```
