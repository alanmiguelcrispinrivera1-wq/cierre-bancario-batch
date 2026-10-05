package com.academia.banco;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// spring.batch.job.enabled=false: la prueba revisa que la aplicación arranque, SIN correr el cierre de verdad.
@SpringBootTest(properties = "spring.batch.job.enabled=false")
class CierreBancarioBatchApplicationTests {

    @Test
    void contextLoads() {
    }

}