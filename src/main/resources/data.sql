INSERT INTO productos (id, nombre, precio) VALUES (1, 'Teclado mecanico', 100000);
INSERT INTO productos (id, nombre, precio) VALUES (2, 'Monitor 27 pulgadas', 600000);
INSERT INTO productos (id, nombre, precio) VALUES (3, 'Cable USB-C', 10000);
INSERT INTO productos (id, nombre, precio) VALUES (4, 'Silla ergonomica', 50000);

INSERT INTO inventario (producto_id, stock) VALUES (1, 50);
INSERT INTO inventario (producto_id, stock) VALUES (2, 10);
INSERT INTO inventario (producto_id, stock) VALUES (3, 100);
INSERT INTO inventario (producto_id, stock) VALUES (4, 2);

INSERT INTO clientes (id, nombre, tipo_cliente) VALUES (1, 'Cliente VIP', 'VIP');
INSERT INTO clientes (id, nombre, tipo_cliente) VALUES (2, 'Cliente frecuente', 'FRECUENTE');
INSERT INTO clientes (id, nombre, tipo_cliente) VALUES (3, 'Cliente moroso', 'MOROSO');
INSERT INTO clientes (id, nombre, tipo_cliente) VALUES (4, 'Cliente estandar', 'ESTANDAR');
INSERT INTO clientes (id, nombre, tipo_cliente, nit) VALUES (5, 'Empresa cliente', 'ESTANDAR', '900123456-7');

INSERT INTO facturas (cliente_id, monto, pagada) VALUES (3, 150000, false);
INSERT INTO facturas (cliente_id, monto, pagada) VALUES (3, 80000, true);

-- Historial del cliente frecuente: 5 pedidos previos (rango de 4% de descuento)
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
VALUES (2, 50000, 0, 9500, 59500, TIMESTAMP '2026-08-01 10:00:00', 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
VALUES (2, 50000, 0, 9500, 59500, TIMESTAMP '2026-08-10 10:00:00', 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
VALUES (2, 50000, 0, 9500, 59500, TIMESTAMP '2026-08-20 10:00:00', 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
VALUES (2, 50000, 0, 9500, 59500, TIMESTAMP '2026-09-01 10:00:00', 'CONFIRMADO');
INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado)
VALUES (2, 50000, 0, 9500, 59500, TIMESTAMP '2026-09-15 10:00:00', 'CONFIRMADO');
