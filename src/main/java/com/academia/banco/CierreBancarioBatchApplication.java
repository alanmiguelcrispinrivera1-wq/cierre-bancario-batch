package com.academia.banco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CierreBancarioBatchApplication {

    public static void main(String[] args) {
        // SpringApplication.exit(…) traduce cómo terminó el Job a un código de salida (FAILED → 5),
        // y System.exit(…) se lo devuelve a quien lanzó el programa.
        System.exit(SpringApplication.exit(SpringApplication.run(CierreBancarioBatchApplication.class, args)));
    }

}
