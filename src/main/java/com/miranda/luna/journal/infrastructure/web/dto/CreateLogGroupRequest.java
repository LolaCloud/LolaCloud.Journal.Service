package com.miranda.luna.journal.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLogGroupRequest(
        @NotBlank(message = "Log group name is required")
        @Size(min = 2, max = 100, message = "Log group name must be between 2 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9/_.-]+$", message = "Log group name must contain only alphanumeric characters, slashes, dashes, dots, or underscores")
        String name,

        String description,

        @Min(value = 1, message = "Retention days must be at least 1 day")
        @Max(value = 3650, message = "Retention days cannot exceed 3650 days")
        Integer retentionDays
) {
    public int resolvedRetentionDays() {
        return (retentionDays != null && retentionDays > 0) ? retentionDays : 7;
    }
}
