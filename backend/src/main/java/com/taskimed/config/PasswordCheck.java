package com.taskimed.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordCheck {

    public static void main(String[] args) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();

        // Password real que conoces del usuario aarroyot
        String rawPassword = "Cibertec@1"; // cambia esto

        // Hash guardado en la base de datos (copia literal del campo password)
        String storedHash = "$2a$10$DgS3rxNWH3oVbrfpGLalhOpuXx8PX3tW2mYp56NeHMo4f4fmKsN66"; // pega aquí la contraseña encriptada de la BD

        // Genera un nuevo hash del password real
        String newHash = encoder.encode(rawPassword);

        // Imprime ambos valores para comparar
        System.out.println("=== PRUEBA DE PASSWORD ===");
        System.out.println("Password real: " + rawPassword);
        System.out.println("Hash guardado BD: " + storedHash);
        System.out.println("Hash generado nuevo: " + newHash);

        // Comprobación de coincidencia
        boolean matches = encoder.matches(rawPassword, storedHash);
        System.out.println("¿Coinciden? " + matches);
    }
}
