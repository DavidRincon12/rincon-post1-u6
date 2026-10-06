package com.tienda.pedidos.soporte;

import com.tienda.pedidos.service.EmailService;

import java.util.ArrayList;
import java.util.List;

// Doble de prueba que guarda los correos enviados para verificar el contenido de la notificacion
public class EmailServiceRegistro implements EmailService {

    public record CorreoEnviado(String destinatario, String asunto, String cuerpo) {
    }

    private final List<CorreoEnviado> enviados = new ArrayList<>();

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        enviados.add(new CorreoEnviado(destinatario, asunto, cuerpo));
    }

    public List<CorreoEnviado> getEnviados() {
        return enviados;
    }

    public void limpiar() {
        enviados.clear();
    }
}
