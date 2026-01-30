package com.company.qa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@Converter
public class JsonTypeConverter implements AttributeConverter<Map<String, Object>, PGobject> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PGobject convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(attribute));
            return pgObject;
        } catch (JsonProcessingException | SQLException e) {
            throw new IllegalArgumentException("Error converting Map to JSONB", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToEntityAttribute(PGobject dbData) {
        if (dbData == null || dbData.getValue() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData.getValue(), Map.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSONB to Map", e);
        }
    }
}