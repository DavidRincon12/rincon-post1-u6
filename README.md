# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción

Proyecto Spring Boot `pedidos-service` (groupId `com.tienda`) para diagnosticar y refactorizar
antipatrones en un sistema de gestión de pedidos de comercio electrónico.

## Decisiones de diseño

### Parte 1 — Diagnóstico de `GestorPedidos`

> Las líneas citadas corresponden a
> [`GestorPedidos.java` en el commit del código de partida](https://github.com/DavidRincon12/rincon-post1-u6/blob/6eca164224ade19d7c22c3677b0f927934aa5350/src/main/java/com/tienda/pedidos/service/GestorPedidos.java),
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

### Parte 1 — Refactorización aplicada

`GestorPedidos` quedó dividido en cuatro capas cohesivas y actúa solo como orquestador
(`procesarPedido()` pasó de ~106 líneas a 18):

```
GestorPedidos (orquestador)
├── validacion/  ValidadorPedido ← ValidadorStock → ValidadorCliente   (Chain of Responsibility)
├── descuento/   EstrategiaDescuento ← DescuentoVip | DescuentoFrecuente | DescuentoEstandar
│                SelectorEstrategiaDescuento                            (Strategy + fábrica simple)
├── service/     PedidoRepository, ProductoRepository                  (persistencia)
└── service/     NotificacionPedidoService → EmailService              (notificación)
```

**Patrón aplicado — validaciones como Chain of Responsibility.** Las validaciones tienen una
dependencia de orden real y necesitan corte anticipado: si `ValidadorStock` rechaza el pedido,
`ValidadorCliente` ni siquiera debe consultar la mora. Cada eslabón tiene un único motivo de
rechazo y se prueba por separado.
*Alternativa descartada:* un método `validarTodo()` con una lista de `Predicate<ContextoPedido>`.
Evalúa todos los predicados aunque el primero ya falló y no permite que un validador decida no
delegar al siguiente, que es justo el corte anticipado que la cadena ofrece.

**Patrón aplicado — descuento como Strategy (y no como otro eslabón de la cadena).** Las reglas
de descuento no dependen de un orden entre sí ni cortan el flujo: siempre se aplica exactamente
una regla según el tipo de cliente. Un `Map<String, EstrategiaDescuento>` en
`SelectorEstrategiaDescuento` reemplaza el `if/else if` anidado; agregar un tipo de cliente es
crear una clase nueva y registrarla, sin tocar las existentes (OCP).
*Alternativa descartada:* modelar cada descuento como eslabón de la cadena. Habría obligado a
inventar un mecanismo para garantizar que solo un eslabón fije el descuento, con más indirección
y sin ganar nada.

**Persistencia y notificación.** `PedidoRepository` (`@Repository`) es el único que conoce las
tablas `pedidos`, `detalle_pedido` e `inventario` para escribir; `ProductoRepository` saca la
consulta de precios del cálculo del subtotal, que queda como lógica pura en `GestorPedidos`;
`NotificacionPedidoService` (`@Service`) arma el correo y absorbe sus fallos. Todo con inyección
por constructor en lugar de `@Autowired` sobre campos.

**Corrección sobre el código de referencia de la guía.** La guía escribe
`this.primerValidador = stock.encadenar(cliente)`, pero `encadenar()` devuelve el *siguiente*
eslabón, así que la cadena empezaría en `ValidadorCliente` y el stock nunca se validaría. Aquí se
encadena primero y se guarda `stock` como primer validador; la prueba
`rechazaPorStockInsuficiente` lo cubre.

#### Comparación antes / después (Parte 1)

Los mismos 11 pedidos de `GestorPedidosTest` se ejecutaron contra el `GestorPedidos` original
(commit `test: agregar pedidos de prueba…`) y contra la versión refactorizada, sin cambiar una sola
aserción. Ambos producen:

| Caso | Cliente | Pedido | Resultado (antes = después) |
|---|---|---|---|
| Sin ítems | ESTANDAR | — | Rechazado: "El pedido no contiene items" |
| Stock insuficiente | VIP | 5 sillas (stock 2) | Rechazado: "Stock insuficiente: producto 4" |
| Cliente inexistente | id 99 | 1 teclado | Rechazado: "Cliente no registrado" |
| Moroso 19:59 | MOROSO | 2 cables | Rechazado: "Cliente con deuda pendiente: $150000.0" |
| Moroso 20:00 | MOROSO | 2 cables | Confirmado, total $23.800 (sin descuento) |
| VIP > $1.000.000 | VIP | 2 monitores | Confirmado, 15 %, total $1.213.800 |
| VIP > $500.000 | VIP | 1 monitor | Confirmado, 10 %, total $642.600 |
| VIP resto | VIP | 1 teclado | Confirmado, 5 %, total $113.050 |
| Frecuente (5 previos) | FRECUENTE | 2 teclados | Confirmado, 4 %, total $228.480 |
| Estándar | ESTANDAR | 1 cable | Confirmado, total $11.900 |
| Persistencia y correo | VIP | 1 teclado + 3 cables | 2 filas de detalle, stock descontado, 1 correo con "Descuento aplicado: 5%" |

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
