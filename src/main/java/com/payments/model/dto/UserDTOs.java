package com.payments.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDTOs {
    private UserDTOs() {}

    public record UserResponse(
            String id,
            String email,
            String firstName,
            String lastName,
            String otherName
    ) { }

    public record UserRequest(
            @NotBlank
            @Email
            String email,

            @NotBlank
            @Size(min = 2, max = 255)
            @Pattern(
                    regexp = "^[A-Za-zÀ-ÖØ-öø-ÿ'\\- ]+$",
                    message = "First name must contain only letters, spaces, apostrophes, or hyphens")
            String firstName,

            @NotBlank
            @Size(min = 2, max = 255)
            @Pattern(
                    regexp = "^[A-Za-zÀ-ÖØ-öø-ÿ'\\- ]+$",
                    message = "Last name must contain only letters, spaces, apostrophes, or hyphens")
            String lastName,

            @NotBlank
            @Size(min = 2, max = 255)
            @Pattern(
                    regexp = "^[A-Za-zÀ-ÖØ-öø-ÿ'\\- ]+$",
                    message = "Other name must contain only letters, spaces, apostrophes, or hyphens")
            String otherName,

            @NotBlank
            @Size(min = 2, max = 50)
            String password
    ) { }


}
