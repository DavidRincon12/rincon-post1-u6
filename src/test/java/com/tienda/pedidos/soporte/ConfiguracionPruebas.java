package com.tienda.pedidos.soporte;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Reemplaza el reloj del sistema y el correo de consola por dobles controlables desde las pruebas
@TestConfiguration
public class ConfiguracionPruebas {

    @Bean
    @Primary
    public RelojAjustable relojAjustable() {
        return new RelojAjustable();
    }

    @Bean
    @Primary
    public EmailServiceRegistro emailServiceRegistro() {
        return new EmailServiceRegistro();
    }
}
