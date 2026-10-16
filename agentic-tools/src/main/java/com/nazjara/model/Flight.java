package com.nazjara.model;

import java.math.BigDecimal;

public record Flight(String flightNumber, String from, String to, String date, String departure, String arrival,
                     BigDecimal priceEur) {
}
