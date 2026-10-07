CREATE TABLE productos (
    id     BIGINT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    precio DOUBLE NOT NULL
);

CREATE TABLE inventario (
    producto_id BIGINT PRIMARY KEY REFERENCES productos (id),
    stock       INT NOT NULL
);

CREATE TABLE clientes (
    id           BIGINT PRIMARY KEY,
    nombre       VARCHAR(100) NOT NULL,
    tipo_cliente VARCHAR(20),
    nit          VARCHAR(20)
);

CREATE TABLE facturas (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes (id),
    monto      DOUBLE NOT NULL,
    pagada     BOOLEAN NOT NULL
);

CREATE TABLE pedidos (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes (id),
    subtotal   DOUBLE NOT NULL,
    descuento  DOUBLE NOT NULL,
    impuesto   DOUBLE NOT NULL,
    total      DOUBLE NOT NULL,
    fecha      TIMESTAMP NOT NULL,
    estado     VARCHAR(20) NOT NULL
);

CREATE TABLE detalle_pedido (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id   BIGINT NOT NULL REFERENCES pedidos (id),
    producto_id BIGINT NOT NULL REFERENCES productos (id),
    cantidad    INT NOT NULL
);
