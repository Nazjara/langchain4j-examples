package com.nazjara.model;

import java.math.BigDecimal;

public record Hotel(String hotelId, String name, String city, int stars, BigDecimal pricePerNightEur) {
}
