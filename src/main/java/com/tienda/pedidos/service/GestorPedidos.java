package com.tienda.pedidos.service;

import com.tienda.pedidos.descuento.SelectorEstrategiaDescuento;
import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.validacion.ContextoPedido;
import com.tienda.pedidos.validacion.ValidadorCliente;
import com.tienda.pedidos.validacion.ValidadorPedido;
import com.tienda.pedidos.validacion.ValidadorStock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Orquestador delgado: coordina validacion, descuento, persistencia y notificacion sin conocer sus detalles
@Service
public class GestorPedidos {

    private static final Logger log = LoggerFactory.getLogger(GestorPedidos.class);
    private static final double TASA_IMPUESTO = 0.19;

    private final ValidadorPedido primerValidador;
    private final SelectorEstrategiaDescuento selector;
    private final ProductoRepository productos;
    private final PedidoRepository repository;
    private final NotificacionPedidoService notificacion;

    public GestorPedidos(ValidadorStock stock, ValidadorCliente cliente,
                         SelectorEstrategiaDescuento selector, ProductoRepository productos,
                         PedidoRepository repository, NotificacionPedidoService notificacion) {
        stock.encadenar(cliente);
        this.primerValidador = stock;
        this.selector = selector;
        this.productos = productos;
        this.repository = repository;
        this.notificacion = notificacion;
    }

    public ResultadoPedido procesarPedido(PedidoRequest request) {
        log.info("Iniciando procesamiento de pedido para cliente {}", request.getClienteId());
        ContextoPedido contexto = new ContextoPedido(request);
        primerValidador.validar(contexto);
        if (contexto.isRechazado()) return ResultadoPedido.rechazado(contexto.getMotivoRechazo());

        double subtotal = calcularSubtotal(request);
        contexto.setSubtotal(subtotal);

        double descuento = selector.seleccionar(contexto.getTipoCliente()).calcular(contexto);
        double impuesto = (subtotal - subtotal * descuento) * TASA_IMPUESTO;
        double total = subtotal - (subtotal * descuento) + impuesto;

        Long pedidoId = repository.guardar(contexto, descuento, impuesto, total);
        notificacion.notificarConfirmacion(contexto, pedidoId, descuento, impuesto, total);
        log.info("Pedido {} confirmado. Total: {}", pedidoId, total);
        return ResultadoPedido.confirmado(pedidoId, total);
    }

    private double calcularSubtotal(PedidoRequest request) {
        double subtotal = 0;
        for (ItemPedido item : request.getItems()) {
            subtotal += productos.precioUnitario(item.getProductoId()) * item.getCantidad();
        }
        return subtotal;
    }
}
