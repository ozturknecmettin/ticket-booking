package com.workshop.projection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.dto.PriceZone;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class PriceZoneConverter implements AttributeConverter<List<PriceZone>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<PriceZone> zones) {
        if (zones == null || zones.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(zones);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize price zones", e);
        }
    }

    @Override
    public List<PriceZone> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not deserialize price zones", e);
        }
    }
}
