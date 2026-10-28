package com.nazjara.model;

import java.math.BigDecimal;

public record InsuranceQuote(String quoteId, String tier, BigDecimal priceEur) {
}
