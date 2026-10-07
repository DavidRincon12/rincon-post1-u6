package com.tienda.pedidos.descuento;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

// Campana: 12% si el pedido supera 20 unidades en total
@Component
public class DescuentoVolumen implements EstrategiaDescuento {

    private static final int UNIDADES_MINIMAS_EXCLUSIVAS = 20;

    @Override
    public double calcular(ContextoPedido contexto) {
        int totalUnidades = contexto.getRequest().getItems().stream()
            .mapToInt(ItemPedido::getCantidad).sum();
        return totalUnidades > UNIDADES_MINIMAS_EXCLUSIVAS ? 0.12 : 0.0;
    }
}
