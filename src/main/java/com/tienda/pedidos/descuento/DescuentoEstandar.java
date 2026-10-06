package com.tienda.pedidos.descuento;

import com.tienda.pedidos.validacion.ContextoPedido;
import org.springframework.stereotype.Component;

// Estrategia por defecto: cualquier tipo de cliente sin regla propia (ESTANDAR, MOROSO) no recibe descuento
@Component
public class DescuentoEstandar implements EstrategiaDescuento {

    @Override
    public double calcular(ContextoPedido contexto) { return 0.0; }
}
