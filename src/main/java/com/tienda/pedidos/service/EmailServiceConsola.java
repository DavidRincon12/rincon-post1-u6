package com.tienda.pedidos.service;

import org.springframework.stereotype.Service;

// Implementacion de desarrollo: imprime el correo en consola para no depender de un servidor SMTP real
@Service
public class EmailServiceConsola implements EmailService {

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        System.out.println("=== Correo para " + destinatario + " ===");
        System.out.println("Asunto: " + asunto);
        System.out.println(cuerpo);
    }
}
