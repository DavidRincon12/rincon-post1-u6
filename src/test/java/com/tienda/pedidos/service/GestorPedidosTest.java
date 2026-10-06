package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.soporte.ConfiguracionPruebas;
import com.tienda.pedidos.soporte.EmailServiceRegistro;
import com.tienda.pedidos.soporte.RelojAjustable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pedidos de prueba que fijan el comportamiento observable de GestorPedidos.
// Cada prueba corre en una transaccion que se revierte, asi el stock y el historial no se contaminan.
@SpringBootTest
@Import(ConfiguracionPruebas.class)
@Transactional
class GestorPedidosTest {

    private static final double DELTA = 0.01;

    private static final long CLIENTE_VIP = 1L;
    private static final long CLIENTE_FRECUENTE = 2L;
    private static final long CLIENTE_MOROSO = 3L;
    private static final long CLIENTE_ESTANDAR = 4L;
    private static final long CLIENTE_INEXISTENTE = 99L;

    private static final long TECLADO = 1L;  // 100.000
    private static final long MONITOR = 2L;  // 600.000
    private static final long CABLE = 3L;    // 10.000
    private static final long SILLA = 4L;    // 50.000, stock 2

    @Autowired
    private GestorPedidos gestor;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private EmailServiceRegistro correos;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reiniciarDobles() {
        reloj.fijarHora(10, 0);
        correos.limpiar();
    }

    @Test
    void rechazaPedidoSinItems() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_ESTANDAR));

        assertFalse(resultado.isConfirmado());
        assertEquals("El pedido no contiene items", resultado.getMotivoRechazo());
    }

    @Test
    void rechazaPorStockInsuficiente() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_VIP, item(SILLA, 5)));

        assertFalse(resultado.isConfirmado());
        assertEquals("Stock insuficiente: producto 4", resultado.getMotivoRechazo());
        assertEquals(2, stockDe(SILLA));
    }

    @Test
    void rechazaClienteInexistente() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_INEXISTENTE, item(TECLADO, 1)));

        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente no registrado", resultado.getMotivoRechazo());
    }

    @Test
    void rechazaClienteMorosoDentroDelHorarioDeCorte() {
        reloj.fijarHora(19, 59);

        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_MOROSO, item(CABLE, 2)));

        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente con deuda pendiente: $150000.0", resultado.getMotivoRechazo());
    }

    @Test
    void permiteClienteMorosoFueraDelHorarioDeCorteSinDescuento() {
        reloj.fijarHora(20, 0);

        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_MOROSO, item(CABLE, 2)));

        assertTrue(resultado.isConfirmado());
        assertEquals(23_800.0, resultado.getTotal(), DELTA); // 20.000 * 1.19
    }

    @Test
    void aplicaDescuentoVipDel15PorCientoSobreUnMillon() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_VIP, item(MONITOR, 2)));

        assertTrue(resultado.isConfirmado());
        assertEquals(1_213_800.0, resultado.getTotal(), DELTA); // 1.200.000 * 0.85 * 1.19
        assertEquals(0.15, descuentoGuardado(resultado.getPedidoId()), 1e-9);
    }

    @Test
    void aplicaDescuentoVipDel10PorCientoEntreQuinientosMilYUnMillon() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_VIP, item(MONITOR, 1)));

        assertEquals(642_600.0, resultado.getTotal(), DELTA); // 600.000 * 0.90 * 1.19
    }

    @Test
    void aplicaDescuentoVipDel5PorCientoBajoQuinientosMil() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_VIP, item(TECLADO, 1)));

        assertEquals(113_050.0, resultado.getTotal(), DELTA); // 100.000 * 0.95 * 1.19
    }

    @Test
    void aplicaDescuentoFrecuenteDel4PorCientoConCincoPedidosPrevios() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_FRECUENTE, item(TECLADO, 2)));

        assertTrue(resultado.isConfirmado());
        assertEquals(228_480.0, resultado.getTotal(), DELTA); // 200.000 * 0.96 * 1.19
    }

    @Test
    void clienteEstandarNoRecibeDescuento() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_ESTANDAR, item(CABLE, 1)));

        assertEquals(11_900.0, resultado.getTotal(), DELTA); // 10.000 * 1.19
    }

    @Test
    void pedidoConfirmadoPersisteDetalleDescuentaStockYNotifica() {
        ResultadoPedido resultado = gestor.procesarPedido(
            pedido(CLIENTE_VIP, item(TECLADO, 1), item(CABLE, 3)));

        Long pedidoId = resultado.getPedidoId();
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM detalle_pedido WHERE pedido_id = ?", Integer.class, pedidoId));
        assertEquals(49, stockDe(TECLADO));
        assertEquals(97, stockDe(CABLE));

        assertEquals(1, correos.getEnviados().size());
        EmailServiceRegistro.CorreoEnviado correo = correos.getEnviados().get(0);
        assertEquals("cliente@tienda.com", correo.destinatario());
        assertEquals("Confirmacion de pedido #" + pedidoId, correo.asunto());
        assertTrue(correo.cuerpo().contains("Subtotal: $130000.0"));
        assertTrue(correo.cuerpo().contains("Descuento aplicado: 5%"));
    }

    private static PedidoRequest pedido(long clienteId, ItemPedido... items) {
        return new PedidoRequest(clienteId, "cliente@tienda.com", List.of(items));
    }

    private static ItemPedido item(long productoId, int cantidad) {
        return new ItemPedido(productoId, cantidad);
    }

    private int stockDe(long productoId) {
        return jdbcTemplate.queryForObject(
            "SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, productoId);
    }

    private double descuentoGuardado(Long pedidoId) {
        return jdbcTemplate.queryForObject("SELECT descuento FROM pedidos WHERE id = ?", Double.class, pedidoId);
    }
}
