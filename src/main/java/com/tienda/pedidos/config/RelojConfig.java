package com.tienda.pedidos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// El reloj se inyecta para poder probar la regla del horario de corte (20:00) sin depender de la hora real
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj() {
        return Clock.systemDefaultZone();
    }
}
