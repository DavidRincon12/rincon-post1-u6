package com.tienda.pedidos.dto;

public class ItemPedido {
    private Long productoId;
    private int cantidad;

    public ItemPedido() {
    }

    public ItemPedido(Long productoId, int cantidad) {
        this.productoId = productoId;
        this.cantidad = cantidad;
    }

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
}
