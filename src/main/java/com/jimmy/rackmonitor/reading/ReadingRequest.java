package com.jimmy.rackmonitor.reading;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 感測器回報的一筆資料。 */
public record ReadingRequest(
        @NotBlank String rackId,
        @NotNull @PositiveOrZero Double powerKw,
        @NotNull Double inletTemp) {
}
