package com.academia.banco.batch;

import com.academia.banco.model.Movimiento;
import org.springframework.batch.infrastructure.item.ItemProcessor;

// El Procesador: recibe UN movimiento como lo leyó el Lector y devuelve el que se va a escribir.
public class MovimientoProcessor implements ItemProcessor<Movimiento, Movimiento> {

    @Override
    public Movimiento process(Movimiento movimiento) {
        String tipo = movimiento.tipo().trim().toUpperCase();   // " retiro" → "RETIRO"
        if (!tipo.equals("DEPOSITO") && !tipo.equals("RETIRO")) {
            return null;                                         // null = «este no se escribe» (se FILTRA)
        }
        return new Movimiento(movimiento.cuenta().trim(), tipo, movimiento.monto());
    }
}
