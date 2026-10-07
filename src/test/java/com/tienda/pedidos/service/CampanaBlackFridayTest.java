package com.tienda.pedidos.service;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.dto.ResultadoPedido;
import com.tienda.pedidos.soporte.ConfiguracionPruebas;
import com.tienda.pedidos.soporte.RelojAjustable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Black Friday activo: 25% fijo, compite con el descuento por tipo de cliente y gana el mayor
@SpringBootTest(properties = "promo.black-friday.activa=true")
@Import(ConfiguracionPruebas.class)
@Transactional
class CampanaBlackFridayTest {

    private static final double DELTA = 0.01;

    private static final long CLIENTE_VIP = 1L;
    private static final long CLIENTE_MOROSO = 3L;
    private static final long CLIENTE_ESTANDAR = 4L;

    private static final long MONITOR = 2L; // 600.000
    private static final long CABLE = 3L;   // 10.000

    @Autowired
    private GestorPedidos gestor;

    @Autowired
    private RelojAjustable reloj;

    @BeforeEach
    void reiniciarReloj() {
        reloj.fijarHora(10, 0);
    }

    @Test
    void clienteEstandarRecibe25PorCiento() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_ESTANDAR, item(CABLE, 1)));

        assertTrue(resultado.isConfirmado());
        assertEquals(8_925.0, resultado.getTotal(), DELTA); // 10.000 * 0.75 * 1.19
    }

    @Test
    void blackFridayGanaAlMayorDescuentoVip() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_VIP, item(MONITOR, 2)));

        assertEquals(1_071_000.0, resultado.getTotal(), DELTA); // 1.200.000 * 0.75 * 1.19 (25% > 15%)
    }

    @Test
    void morosoFueraDelHorarioDeCorteTambienRecibeBlackFriday() {
        reloj.fijarHora(20, 30);

        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_MOROSO, item(CABLE, 2)));

        assertEquals(17_850.0, resultado.getTotal(), DELTA); // 20.000 * 0.75 * 1.19
    }

    @Test
    void laCampanaNoSaltaLasValidaciones() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_MOROSO, item(CABLE, 2)));

        assertFalse(resultado.isConfirmado());
        assertEquals("Cliente con deuda pendiente: $150000.0", resultado.getMotivoRechazo());
    }

    private static PedidoRequest pedido(long clienteId, ItemPedido... items) {
        return new PedidoRequest(clienteId, "cliente@tienda.com", List.of(items));
    }

    private static ItemPedido item(long productoId, int cantidad) {
        return new ItemPedido(productoId, cantidad);
    }
}
