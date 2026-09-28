package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Начисления по контейнеру (категория CONTAINER_FEES). В IMPLEMENTATION.md не описаны,
 * добавлены по фактической схеме OZON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerFees(
        @JsonProperty("fees") List<ContainerFee> fees
) {
    public List<ContainerFee> safeFees() {
        return fees == null ? List.of() : fees;
    }
}
