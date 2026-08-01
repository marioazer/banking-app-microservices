package com.example.profileservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDailySummaryRequestDto(

        @NotNull(message = "Daily summary preference must be specified")
        Boolean dailySummaryEnabled,

        @NotBlank(message = "Timezone cannot be blank")
        // We expect standard IANA timezone formats like "America/New_York" or "UTC"
        String timezone

) {}
