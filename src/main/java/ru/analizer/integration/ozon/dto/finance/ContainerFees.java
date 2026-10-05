package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Начисления по контейнеру (категория CONTAINER_FEES). Категория есть в актуальной
 * схеме ответов OZON, хотя в первоначальном описании её не было.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerFees(
        @JsonProperty("fees") List<ContainerFee> fees
) {
    public List<ContainerFee> safeFees() {
        return fees == null ? List.of() : fees;
    }
}
