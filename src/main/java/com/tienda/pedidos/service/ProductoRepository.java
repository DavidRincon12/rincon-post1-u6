package com.tienda.pedidos.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Saca la consulta de precios del calculo del subtotal, que queda como logica pura en GestorPedidos
@Repository
public class ProductoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public double precioUnitario(Long productoId) {
        return jdbcTemplate.queryForObject("SELECT precio FROM productos WHERE id = ?", Double.class, productoId);
    }
}
