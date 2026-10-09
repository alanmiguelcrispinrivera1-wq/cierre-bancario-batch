package com.academia.banco.batch;

import com.academia.banco.model.SaldoCuenta;
import java.math.BigDecimal;
import org.springframework.batch.infrastructure.item.ItemProcessor;

// El Procesador del step 3: solo deja pasar las cuentas con saldo positivo o cero.
public class SaldoPositivoProcessor implements ItemProcessor<SaldoCuenta, SaldoCuenta> {

    @Override
    public SaldoCuenta process(SaldoCuenta saldo) {
        if (saldo.saldo().compareTo(BigDecimal.ZERO) < 0) {
            return null;                    
        }
        return saldo;
    }
}