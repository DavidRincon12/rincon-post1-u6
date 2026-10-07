# Post-contenido — Unidad 6: Antipatrones de Diseño

## Descripción

Repositorio del post-contenido de la Unidad 6 de Patrones de Diseño de Software — Sexto
Semestre. Un único proyecto Spring Boot (`pedidos-service`, groupId `com.tienda`, en la raíz del
repositorio) con dos partes:

1. Diagnóstico y refactorización de un antipatrón combinado (God Object + Spaghetti Code) en
   `GestorPedidos`, aplicando Chain of Responsibility y Strategy.
2. Diagnóstico y corrección de un segundo antipatrón (Golden Hammer) introducido al hacer crecer
   el mismo proyecto con tres campañas de descuento.

El historial de commits refleja el proceso completo: código de partida → pruebas que fijan el
comportamiento → diagnóstico → refactorización, y lo mismo para la Parte 2.

### Estructura final

```
src/main/java/com/tienda/pedidos/
├── PedidosServiceApplication.java
├── config/       RelojConfig                         (Clock inyectable)
├── dto/          PedidoRequest, ItemPedido, ResultadoPedido
├── validacion/   ContextoPedido, ValidadorPedido, ValidadorStock, ValidadorCliente
├── descuento/    EstrategiaDescuento, DescuentoVip, DescuentoFrecuente, DescuentoEstandar,
│                 SelectorEstrategiaDescuento, DescuentoBlackFriday, DescuentoCorporativo,
│                 DescuentoVolumen, CalculadorDescuentoFinal
└── service/      GestorPedidos, PedidoRepository, ProductoRepository,
                  NotificacionPedidoService, EmailService, EmailServiceConsola
```

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

### Parte 2 — Diagnóstico del crecimiento: tres campañas como eslabones de la cadena

> Las líneas citadas corresponden al
> [commit `feat: agregar 3 campanas de descuento como eslabones…`](https://github.com/DavidRincon12/rincon-post1-u6/tree/232e0dc2635849bc112308b70363af4a627f5d6b/src/main/java/com/tienda/pedidos),
> antes de corregir el diseño.

Mercadeo pidió tres campañas: **BLACK_FRIDAY** (25 % fijo mientras
`promo.black-friday.activa=true`), **CORPORATIVO** (10 % si el cliente tiene NIT) y **VOLUMEN**
(12 % si el pedido supera 20 unidades). Se resolvieron como tres subclases de `ValidadorPedido`
(`PromocionBlackFriday`, `PromocionCorporativo`, `PromocionVolumen`) enganchadas al final de la
cadena en `GestorPedidos` (líneas 37-38):

```java
stock.encadenar(cliente)
    .encadenar(blackFriday).encadenar(corporativo).encadenar(volumen);
```

El código compila y las campañas funcionan (`CampanasDescuentoTest` y `CampanaBlackFridayTest`
pasan). El problema es de diseño.

**Antipatrón identificado: Golden Hammer.** Se reutilizó Chain of Responsibility porque "ya
funcionó" en la Parte 1, sin comprobar que el problema nuevo tuviera la misma forma. La evidencia:

1. **No hay dependencia de orden entre las tres campañas ni con los validadores.** Cada una lee
   datos independientes (una bandera, el NIT, la suma de cantidades) y llama a
   `aplicarDescuentoCampana()`, que se queda con el máximo (`ContextoPedido`, líneas 27-29).
   El máximo es conmutativo: ejecutar `PromocionVolumen` antes que `PromocionCorporativo` da el
   mismo resultado. En cambio `ValidadorStock` *sí* debe ir antes que `ValidadorCliente`. La
   propiedad que justificaba la cadena (orden + corte anticipado) no existe aquí.
2. **Ninguna de las tres usa el corte anticipado.** El contrato de `ValidadorPedido`
   (línea 3: *"cada validador decide si el pedido continúa o se rechaza"*) se rompe:
   `PromocionBlackFriday` lo declara en su propio comentario (líneas 22-23: *"nunca rechaza --
   este eslabón no valida nada"*), y las otras dos tampoco llaman a `rechazar()` nunca. Son
   cálculos de porcentaje con nombre y herencia de validador.
3. **El contexto se volvió un buzón compartido.** Hizo falta un campo mutable nuevo,
   `descuentoCampana` (`ContextoPedido`, línea 13), para que eslabones que no validan nada
   pudieran devolver un valor. El cálculo del descuento quedó partido en dos lugares: la cadena
   escribe `descuentoCampana` y `GestorPedidos` lo combina con el Strategy en la línea 57
   (`Math.max(descuentoTipoCliente, contexto.getDescuentoCampana())`).
4. **La regla de combinación está escondida en un setter.** "El mayor gana" vive dentro de
   `aplicarDescuentoCampana()`. Si mercadeo pide que dos campañas se *sumen*, la cadena no lo
   permite sin ambigüedad: cada eslabón sobrescribe el mismo campo y no sabe qué escribieron los
   demás ni en qué orden.
5. **La cadena corre antes del cálculo de precios.** Cuando se ejecutan los eslabones,
   `contexto.getSubtotal()` aún vale `0` (el subtotal se calcula después, línea 52). Por eso
   `PromocionVolumen` recalcula las unidades desde el request (líneas 12-13) y ninguna campaña
   podría depender del monto del pedido. Es una señal de que el descuento está en la etapa
   equivocada del flujo.
6. **Duplicación de consultas.** `PromocionCorporativo` vuelve a consultar la tabla `clientes`
   (líneas 18-19) aunque `ValidadorCliente` ya lo hizo en la misma cadena.

En cambio, las tres campañas tienen exactamente la forma de `DescuentoVip` y
`DescuentoFrecuente`: calculan un porcentaje a partir de datos del pedido o del cliente, sin
orden y sin cortar el flujo. El patrón que ya existía para esa forma era Strategy.

### Parte 2 — Corrección aplicada

**Patrón aplicado: Strategy, extendiendo el que ya existía desde la Parte 1.** Las campañas
pasaron a ser `DescuentoBlackFriday`, `DescuentoCorporativo` y `DescuentoVolumen`, todas
implementaciones de `EstrategiaDescuento`. `CalculadorDescuentoFinal` reúne la estrategia por tipo
de cliente (vía `SelectorEstrategiaDescuento`) y las campañas, y aplica la regla de negocio
"gana el mayor" en un solo lugar:

```java
double porTipoCliente = selectorPorCliente.seleccionar(contexto.getTipoCliente()).calcular(contexto);
double porCampana = campanas.stream().mapToDouble(e -> e.calcular(contexto)).max().orElse(0.0);
return Math.max(porTipoCliente, porCampana);
```

Resultado:

- `ValidadorPedido` vuelve a tener solo los dos eslabones que justifican la cadena:
  `ValidadorStock → ValidadorCliente`.
- `GestorPedidos` delega todo el descuento en `CalculadorDescuentoFinal`, que se ejecuta
  **después** de calcular el subtotal, así que una campaña futura sí podría depender del monto.
- `ContextoPedido` vuelve a su forma original: sin `descuentoCampana`.
- Si mañana dos campañas deben sumarse, el cambio es una línea en `CalculadorDescuentoFinal`
  (`sum()` en vez de `max()`), visible y probada de forma unitaria en
  `CalculadorDescuentoFinalTest`.

*Alternativa descartada:* mantener las campañas en la cadena. Es justamente la causa del
antipatrón: reutilizar una herramienta conocida sin verificar que el problema nuevo tuviera su
misma forma (orden y corte anticipado).

**Eliminar, no comentar, el código descartado.** `PromocionBlackFriday`, `PromocionCorporativo`,
`PromocionVolumen` y el campo `descuentoCampana` se borraron por completo con `git rm`, no se
dejaron comentados "por si acaso". El código comentado que nadie se atreve a borrar es como nace
un Lava Flow; la referencia histórica queda en los commits `feat: agregar 3 campanas…` y
`docs: diagnosticar Golden Hammer…`, no en el código activo.

#### Comparación antes / después (Parte 2)

`CampanasDescuentoTest` y `CampanaBlackFridayTest` se escribieron contra la versión con los tres
eslabones (Golden Hammer) y se ejecutaron sin modificar ninguna aserción contra la versión
corregida. Los 11 pedidos de la Parte 1 también siguen dando lo mismo.

| Caso | Cliente | Pedido | Descuento | Total (cadena = Strategy) |
|---|---|---|---|---|
| Corporativo | ESTANDAR con NIT | 1 cable | 10 % | $10.710 |
| Volumen | ESTANDAR | 21 cables | 12 % | $219.912 |
| Volumen en el límite | ESTANDAR | 20 cables | 0 % | $238.000 |
| Volumen con varios ítems | ESTANDAR | 15 + 6 cables | 12 % | $219.912 |
| Volumen vs VIP 5 % | VIP | 25 cables | 12 % | $261.800 |
| VIP 15 % vs volumen | VIP | 2 monitores + 21 cables | 15 % | $1.426.215 |
| Corporativo + volumen | ESTANDAR con NIT | 21 cables | 12 % (no 22 %) | $219.912 |
| Black Friday | ESTANDAR | 1 cable | 25 % | $8.925 |
| Black Friday vs VIP 15 % | VIP | 2 monitores | 25 % | $1.071.000 |
| Black Friday a moroso 20:30 | MOROSO | 2 cables | 25 % | $17.850 |
| Black Friday a moroso 10:00 | MOROSO | 2 cables | — | Rechazado: deuda pendiente |

```
Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Cómo ejecutar

Requisitos: JDK 17 o superior y Maven 3.8+.

```bash
mvn spring-boot:run
mvn test
```

La base H2 en memoria se crea y se llena al arrancar con `schema.sql` y `data.sql`. La campaña de
Black Friday se activa con `promo.black-friday.activa=true` en `application.properties` (o con
`mvn spring-boot:run -Dspring-boot.run.arguments=--promo.black-friday.activa=true`).

## Herramientas utilizadas

- Java 17, Spring Boot 3.5, Spring JDBC (`JdbcTemplate`), Maven, H2 Database
- JUnit 5 y Spring Boot Test
- VS Code / IntelliJ IDEA, Git, GitHub

## Conclusiones

Un God Object no se corrige partiéndolo en clases al azar: hay que contar sus razones para
cambiar y separar por esas razones, y tener pruebas que fijen el comportamiento antes de mover
una sola línea fue lo que permitió refactorizar con confianza. Cada patrón resuelve un problema
con una forma concreta: Chain of Responsibility encaja cuando hay orden y corte anticipado, y
Strategy cuando hay que elegir o combinar reglas independientes. La Parte 2 mostró que el mayor
riesgo después de una buena refactorización es enamorarse de la solución y aplicarla a todo
(Golden Hammer), y que la señal de alarma son las clases que heredan un contrato que no cumplen.
Por último, borrar el código descartado en lugar de comentarlo mantiene el diseño limpio, y el
historial de Git cuenta mejor que cualquier comentario por qué se tomó cada decisión.
