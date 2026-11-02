package com.project.ChatProject.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateStatusMessage(

        @Size(max = 50)
        String newStatusMessage
) {
}
