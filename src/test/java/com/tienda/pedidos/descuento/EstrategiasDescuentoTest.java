package com.tienda.pedidos.descuento;

import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

// Pruebas unitarias sin Spring: las estrategias de descuento puras se prueban sin base de datos
class EstrategiasDescuentoTest {

    private final DescuentoVip vip = new DescuentoVip();
    private final DescuentoFrecuente frecuente = new DescuentoFrecuente(null);
    private final DescuentoEstandar estandar = new DescuentoEstandar();
    private final SelectorEstrategiaDescuento selector = new SelectorEstrategiaDescuento(vip, frecuente, estandar);

    @Test
    void vipRespetaLosTresRangosDeSubtotal() {
        assertEquals(0.15, vip.calcular(contextoConSubtotal(1_000_001)));
        assertEquals(0.10, vip.calcular(contextoConSubtotal(1_000_000)));
        assertEquals(0.10, vip.calcular(contextoConSubtotal(500_001)));
        assertEquals(0.05, vip.calcular(contextoConSubtotal(500_000)));
    }

    @Test
    void estandarNuncaDescuenta() {
        assertEquals(0.0, estandar.calcular(contextoConSubtotal(2_000_000)));
    }

    @Test
    void selectorEligeLaEstrategiaPorTipoDeCliente() {
        assertSame(vip, selector.seleccionar("VIP"));
        assertSame(frecuente, selector.seleccionar("FRECUENTE"));
        assertSame(estandar, selector.seleccionar("ESTANDAR"));
    }

    @Test
    void selectorUsaEstandarParaTiposSinReglaPropia() {
        assertSame(estandar, selector.seleccionar("MOROSO"));
    }

    private static ContextoPedido contextoConSubtotal(double subtotal) {
        ContextoPedido contexto = new ContextoPedido(new PedidoRequest(1L, "c@tienda.com", List.of()));
        contexto.setSubtotal(subtotal);
        return contexto;
    }
}
