package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;

// Strategy: cada tipo de cliente encapsula su propia regla de descuento,
// sin depender de un orden de evaluacion frente a las demas
public interface EstrategiaDescuento {
    double calcular(ContextoPedido contexto);
}
