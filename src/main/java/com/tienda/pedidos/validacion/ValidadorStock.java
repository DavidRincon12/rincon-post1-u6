package com.tienda.pedidos.validacion;

import com.tienda.pedidos.dto.ItemPedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

// Eslabon 1: el pedido trae items y hay stock para cada uno (si falla, no tiene sentido consultar al cliente)
@Component
public class ValidadorStock extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorStock.class);

    private final JdbcTemplate jdbcTemplate;

    public ValidadorStock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        List<ItemPedido> items = contexto.getRequest().getItems();
        if (items == null || items.isEmpty()) {
            log.warn("Pedido rechazado: sin items. Cliente {}", contexto.getRequest().getClienteId());
            contexto.rechazar("El pedido no contiene items");
            return;
        }
        for (ItemPedido item : items) {
            Integer stock = DataAccessUtils.singleResult(jdbcTemplate.queryForList(
                "SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, item.getProductoId()));
            if (stock == null || stock < item.getCantidad()) {
                log.warn("Stock insuficiente para producto {}", item.getProductoId());
                contexto.rechazar("Stock insuficiente: producto " + item.getProductoId());
                return;
            }
        }
    }
}
