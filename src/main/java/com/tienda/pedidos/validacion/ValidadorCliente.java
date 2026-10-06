package com.tienda.pedidos.validacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;

// Eslabon 2: existencia y mora del cliente (depende de que el eslabon anterior haya pasado)
@Component
public class ValidadorCliente extends ValidadorPedido {

    private static final Logger log = LoggerFactory.getLogger(ValidadorCliente.class);
    private static final LocalTime HORA_DE_CORTE = LocalTime.of(20, 0);

    private final JdbcTemplate jdbcTemplate;
    private final Clock reloj;

    public ValidadorCliente(JdbcTemplate jdbcTemplate, Clock reloj) {
        this.jdbcTemplate = jdbcTemplate;
        this.reloj = reloj;
    }

    @Override
    protected void ejecutar(ContextoPedido contexto) {
        Long clienteId = contexto.getRequest().getClienteId();
        String tipo = DataAccessUtils.singleResult(jdbcTemplate.queryForList(
            "SELECT tipo_cliente FROM clientes WHERE id = ?", String.class, clienteId));
        if (tipo == null) {
            log.warn("Cliente no encontrado: {}", clienteId);
            contexto.rechazar("Cliente no registrado");
            return;
        }
        contexto.setTipoCliente(tipo);

        if (tipo.equals("MOROSO")) {
            Double deuda = jdbcTemplate.queryForObject(
                "SELECT SUM(monto) FROM facturas WHERE cliente_id = ? AND pagada = false",
                Double.class, clienteId);
            boolean fueraDeHorarioDeCorte = !LocalTime.now(reloj).isBefore(HORA_DE_CORTE);
            if (deuda != null && deuda > 0 && !fueraDeHorarioDeCorte) {
                log.warn("Cliente moroso con deuda pendiente: {}", deuda);
                contexto.rechazar("Cliente con deuda pendiente: $" + deuda);
            }
        }
    }
}
