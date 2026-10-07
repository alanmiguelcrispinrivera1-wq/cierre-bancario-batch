package com.academia.banco.model;

import java.math.BigDecimal;

// Un renglón del archivo del banco: cuenta, tipo (DEPOSITO o RETIRO) y monto.
public record Movimiento(String cuenta, String tipo, BigDecimal monto) {
}
