package com.workshop.projection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.dto.SeatItem;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class SeatItemListConverter implements AttributeConverter<List<SeatItem>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SeatItem> seats) {
        if (seats == null || seats.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(seats);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize seats", e);
        }
    }

    @Override
    public List<SeatItem> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize seats", e);
        }
    }
}
