package edu.whut.clf.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminDtos {

    public record ReasonRequest(@NotBlank String reason) {}
}
