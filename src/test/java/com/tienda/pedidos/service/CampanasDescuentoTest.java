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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Campanas Corporativo y Volumen con Black Friday inactivo (valor por defecto).
// Fijan los totales que debe conservar cualquier rediseno del calculo de descuento.
@SpringBootTest
@Import(ConfiguracionPruebas.class)
@Transactional
class CampanasDescuentoTest {

    private static final double DELTA = 0.01;

    private static final long CLIENTE_VIP = 1L;
    private static final long CLIENTE_ESTANDAR = 4L;
    private static final long CLIENTE_CORPORATIVO = 5L; // ESTANDAR con NIT registrado

    private static final long MONITOR = 2L; // 600.000
    private static final long CABLE = 3L;   // 10.000

    @Autowired
    private GestorPedidos gestor;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private EmailServiceRegistro correos;

    @BeforeEach
    void reiniciarDobles() {
        reloj.fijarHora(10, 0);
        correos.limpiar();
    }

    @Test
    void clienteConNitRecibeDescuentoCorporativoDel10PorCiento() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_CORPORATIVO, item(CABLE, 1)));

        assertTrue(resultado.isConfirmado());
        assertEquals(10_710.0, resultado.getTotal(), DELTA); // 10.000 * 0.90 * 1.19
    }

    @Test
    void pedidoDeMasDe20UnidadesRecibeDescuentoPorVolumenDel12PorCiento() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_ESTANDAR, item(CABLE, 21)));

        assertEquals(219_912.0, resultado.getTotal(), DELTA); // 210.000 * 0.88 * 1.19
        assertTrue(correos.getEnviados().get(0).cuerpo().contains("Descuento aplicado: 12%"));
    }

    @Test
    void exactamente20UnidadesNoRecibeDescuentoPorVolumen() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_ESTANDAR, item(CABLE, 20)));

        assertEquals(238_000.0, resultado.getTotal(), DELTA); // 200.000 * 1.19
    }

    @Test
    void elVolumenSumaUnidadesDeVariosItems() {
        ResultadoPedido resultado = gestor.procesarPedido(
            pedido(CLIENTE_ESTANDAR, item(CABLE, 15), item(CABLE, 6)));

        assertEquals(219_912.0, resultado.getTotal(), DELTA);
    }

    @Test
    void volumenGanaAlDescuentoVipCuandoEsMayor() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_VIP, item(CABLE, 25)));

        assertEquals(261_800.0, resultado.getTotal(), DELTA); // 250.000 * 0.88 * 1.19 (12% > 5%)
    }

    @Test
    void vipGanaAlVolumenCuandoEsMayor() {
        ResultadoPedido resultado = gestor.procesarPedido(
            pedido(CLIENTE_VIP, item(MONITOR, 2), item(CABLE, 21)));

        assertEquals(1_426_215.0, resultado.getTotal(), DELTA); // 1.410.000 * 0.85 * 1.19 (15% > 12%)
    }

    @Test
    void corporativoYVolumenNoSeSumanSinoQueGanaElMayor() {
        ResultadoPedido resultado = gestor.procesarPedido(pedido(CLIENTE_CORPORATIVO, item(CABLE, 21)));

        assertEquals(219_912.0, resultado.getTotal(), DELTA); // 12%, no 22%
    }

    private static PedidoRequest pedido(long clienteId, ItemPedido... items) {
        return new PedidoRequest(clienteId, "cliente@tienda.com", List.of(items));
    }

    private static ItemPedido item(long productoId, int cantidad) {
        return new ItemPedido(productoId, cantidad);
    }
}
