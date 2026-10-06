package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class GestorPedidos {

    private static final Logger log = LoggerFactory.getLogger(GestorPedidos.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmailService emailService;

    @Autowired
    private Clock reloj;

    // Punto de entrada unico: valida, calcula precio, persiste, notifica y registra el pedido.
    public ResultadoPedido procesarPedido(PedidoRequest request) {
        log.info("Iniciando procesamiento de pedido para cliente {}", request.getClienteId());

        // ---- Validacion de stock (mezclada con lectura directa de BD) ----
        if (request.getItems() == null || request.getItems().isEmpty()) {
            log.warn("Pedido rechazado: sin items. Cliente {}", request.getClienteId());
            return ResultadoPedido.rechazado("El pedido no contiene items");
        }
        for (ItemPedido item : request.getItems()) {
            Integer stockDisponible = DataAccessUtils.singleResult(jdbcTemplate.queryForList(
                "SELECT stock FROM inventario WHERE producto_id = ?",
                Integer.class, item.getProductoId()));
            if (stockDisponible == null || stockDisponible < item.getCantidad()) {
                log.warn("Stock insuficiente para producto {}", item.getProductoId());
                return ResultadoPedido.rechazado("Stock insuficiente: producto " + item.getProductoId());
            }
        }

        // ---- Validacion de cliente y mora, con excepcion por horario ----
        String tipoCliente = DataAccessUtils.singleResult(jdbcTemplate.queryForList(
            "SELECT tipo_cliente FROM clientes WHERE id = ?", String.class, request.getClienteId()));
        if (tipoCliente == null) {
            log.warn("Cliente no encontrado: {}", request.getClienteId());
            return ResultadoPedido.rechazado("Cliente no registrado");
        } else if (tipoCliente.equals("MOROSO")) {
            Double deudaPendiente = jdbcTemplate.queryForObject(
                "SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false",
                Double.class, request.getClienteId());
            if (deudaPendiente != null && deudaPendiente > 0) {
                LocalTime ahora = LocalTime.now(reloj);
                if (ahora.isBefore(LocalTime.of(20, 0))) {
                    log.warn("Cliente moroso con deuda pendiente: {}", deudaPendiente);
                    return ResultadoPedido.rechazado("Cliente con deuda pendiente: $" + deudaPendiente);
                } else {
                    log.info("Cliente moroso fuera del horario de corte; se permite el pedido excepcionalmente");
                }
            }
        }

        // ---- Calculo de subtotal (una consulta SQL por item, dentro del calculo de precio) ----
        double subtotal = 0;
        for (ItemPedido item : request.getItems()) {
            Double precioUnitario = jdbcTemplate.queryForObject(
                "SELECT precio FROM productos WHERE id = ?", Double.class, item.getProductoId());
            subtotal += precioUnitario * item.getCantidad();
        }

        // ---- Calculo de descuento (anidado segun tipo de cliente y monto) ----
        double descuento = 0;
        if (tipoCliente.equals("VIP")) {
            if (subtotal > 1_000_000) {
                descuento = 0.15;
            } else if (subtotal > 500_000) {
                descuento = 0.10;
            } else {
                descuento = 0.05;
            }
        } else if (tipoCliente.equals("FRECUENTE")) {
            Integer pedidosPrevios = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pedidos WHERE cliente_id = ?", Integer.class, request.getClienteId());
            if (pedidosPrevios != null && pedidosPrevios > 10) {
                descuento = 0.08;
            } else if (pedidosPrevios != null && pedidosPrevios > 3) {
                descuento = 0.04;
            }
        }

        double impuesto = (subtotal - subtotal * descuento) * 0.19;
        double total = subtotal - (subtotal * descuento) + impuesto;

        // ---- Persistencia directa via JDBC (sin repositorio, sin transaccion explicita) ----
        jdbcTemplate.update(
            "INSERT INTO pedidos (cliente_id, subtotal, descuento, impuesto, total, fecha, estado) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            request.getClienteId(), subtotal, descuento, impuesto, total,
            Timestamp.valueOf(LocalDateTime.now(reloj)), "CONFIRMADO");
        Long pedidoId = jdbcTemplate.queryForObject("CALL IDENTITY()", Long.class);

        for (ItemPedido item : request.getItems()) {
            jdbcTemplate.update(
                "INSERT INTO detalle_pedido (pedido_id, producto_id, cantidad) VALUES (?, ?, ?)",
                pedidoId, item.getProductoId(), item.getCantidad());
            jdbcTemplate.update(
                "UPDATE inventario SET stock = stock - ? WHERE producto_id = ?",
                item.getCantidad(), item.getProductoId());
        }

        // ---- Notificacion (construccion del mensaje embebida en el mismo metodo) ----
        String asunto = "Confirmacion de pedido #" + pedidoId;
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("Estimado cliente,\n\n").append("Su pedido ha sido confirmado.\n");
        cuerpo.append("Subtotal: $").append(subtotal).append("\n");
        if (descuento > 0) {
            cuerpo.append("Descuento aplicado: ").append((int) (descuento * 100)).append("%\n");
        }
        cuerpo.append("Impuesto: $").append(impuesto).append("\n").append("Total: $").append(total).append("\n");
        try {
            emailService.enviar(request.getClienteEmail(), asunto, cuerpo.toString());
        } catch (Exception e) {
            log.error("No se pudo enviar la notificacion del pedido {}: {}", pedidoId, e.getMessage());
            // Se continua el flujo aunque falle el envio del correo
        }

        log.info("Pedido {} confirmado. Total: {}", pedidoId, total);
        return ResultadoPedido.confirmado(pedidoId, total);
    }
}
