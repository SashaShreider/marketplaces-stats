package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Тело ошибки OZON (схема default в ответах).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OzonErrorResponse(
        @JsonProperty("code") Integer code,
        @JsonProperty("message") String message,
        @JsonProperty("details") List<Object> details
) {
}
