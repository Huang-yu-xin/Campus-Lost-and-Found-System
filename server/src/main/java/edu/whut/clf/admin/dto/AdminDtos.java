package edu.whut.clf.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminDtos {

    public record ReasonRequest(@NotBlank @Size(max = 255) String reason) {}
}
