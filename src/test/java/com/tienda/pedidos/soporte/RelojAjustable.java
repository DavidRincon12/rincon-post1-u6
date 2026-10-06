package com.tienda.pedidos.soporte;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

// Reloj de pruebas: permite fijar la hora del dia para ejercitar el horario de corte de morosos
public class RelojAjustable extends Clock {

    private static final LocalDate FECHA_BASE = LocalDate.of(2026, 10, 6);

    private Instant instante = FECHA_BASE.atTime(10, 0).toInstant(ZoneOffset.UTC);

    public void fijarHora(int hora, int minuto) {
        this.instante = FECHA_BASE.atTime(LocalTime.of(hora, minuto)).toInstant(ZoneOffset.UTC);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instante, zone);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
