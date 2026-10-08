package com.academia.banco.model;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// El saldo de una cuenta al cierre: un documento de la colección «saldos» de MongoDB.
// @Id: la cuenta ES el _id del documento. Así cada cuenta tiene un solo documento.
@Document("saldos")
public record SaldoCuenta(@Id String cuenta, BigDecimal saldo, long movimientos) {
}