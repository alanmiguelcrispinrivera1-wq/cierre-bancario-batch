package com.academia.banco.batch;

import com.academia.banco.model.Movimiento;
import java.math.BigDecimal;
import org.springframework.batch.infrastructure.item.ItemProcessor;

// El Procesador: recibe UN movimiento como lo leyó el Lector y devuelve el que se va a escribir.
public class MovimientoProcessor implements ItemProcessor<Movimiento, Movimiento> {

    // Arriba de este monto, el movimiento va a revisión manual y no se carga.
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("10000");

    @Override
    public Movimiento process(Movimiento movimiento) {
        String tipo = movimiento.tipo().trim().toUpperCase();   // " retiro" → "RETIRO"
        if (!tipo.equals("DEPOSITO") && !tipo.equals("RETIRO")) {
            return null;                                         // null = «este no se escribe» (se FILTRA)
        }
        if (movimiento.monto().compareTo(MONTO_MAXIMO) > 0) {   // monto > 10,000
            return null;                                         // también se FILTRA: va a revisión manual
        }
        return new Movimiento(movimiento.cuenta().trim(), tipo, movimiento.monto());
    }
}