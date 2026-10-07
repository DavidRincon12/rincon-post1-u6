package com.tienda.pedidos.descuento;

import com.tienda.pedidos.dto.ItemPedido;
import com.tienda.pedidos.dto.PedidoRequest;
import com.tienda.pedidos.validacion.ContextoPedido;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Pruebas unitarias sin Spring: la regla "gana el mayor descuento" se verifica en un solo lugar
class CalculadorDescuentoFinalTest {

    private final SelectorEstrategiaDescuento selector = new SelectorEstrategiaDescuento(
        new DescuentoVip(), new DescuentoFrecuente(null), new DescuentoEstandar());

    @Test
    void sinCampanasAplicaElDescuentoPorTipoDeCliente() {
        CalculadorDescuentoFinal calculador = calculador(false, false);

        assertEquals(0.15, calculador.calcular(contexto("VIP", 1_200_000, 2)));
        assertEquals(0.0, calculador.calcular(contexto("ESTANDAR", 10_000, 1)));
    }

    @Test
    void ganaLaCampanaCuandoSuperaAlTipoDeCliente() {
        assertEquals(0.25, calculador(true, false).calcular(contexto("VIP", 1_200_000, 2)));
        assertEquals(0.12, calculador(false, false).calcular(contexto("VIP", 250_000, 25)));
    }

    @Test
    void ganaElTipoDeClienteCuandoSuperaALasCampanas() {
        assertEquals(0.15, calculador(false, true).calcular(contexto("VIP", 1_410_000, 23)));
    }

    @Test
    void lasCampanasNoSeSumanEntreSi() {
        assertEquals(0.25, calculador(true, true).calcular(contexto("ESTANDAR", 210_000, 21)));
    }

    @Test
    void volumenExigeMasDe20Unidades() {
        DescuentoVolumen volumen = new DescuentoVolumen();

        assertEquals(0.0, volumen.calcular(contexto("ESTANDAR", 0, 20)));
        assertEquals(0.12, volumen.calcular(contexto("ESTANDAR", 0, 21)));
    }

    private CalculadorDescuentoFinal calculador(boolean blackFriday, boolean conNit) {
        return new CalculadorDescuentoFinal(selector, new DescuentoBlackFriday(blackFriday),
            new CorporativoFijo(conNit), new DescuentoVolumen());
    }

    private static ContextoPedido contexto(String tipoCliente, double subtotal, int unidades) {
        ContextoPedido contexto = new ContextoPedido(
            new PedidoRequest(1L, "c@tienda.com", List.of(new ItemPedido(3L, unidades))));
        contexto.setTipoCliente(tipoCliente);
        contexto.setSubtotal(subtotal);
        return contexto;
    }

    // Evita la base de datos: simula si el cliente tiene NIT registrado
    private static class CorporativoFijo extends DescuentoCorporativo {
        private final boolean conNit;

        CorporativoFijo(boolean conNit) {
            super(null);
            this.conNit = conNit;
        }

        @Override
        public double calcular(ContextoPedido contexto) {
            return conNit ? 0.10 : 0.0;
        }
    }
}
