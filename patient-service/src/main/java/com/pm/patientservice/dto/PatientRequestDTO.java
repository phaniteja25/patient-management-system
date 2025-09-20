package com.pm.patientservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PatientRequestDTO {

    @NotBlank(message = "Name is required")
    @Size(max=100, message = "Name cannot be more than 100 characters long ")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Address is required")
    private String address;

    @NotBlank(message = "Birth date is required")
    private String dateOfBirth;

    @NotBlank(message = "Name is required")
    @Size(max=100, message = "Name cannot be more than 100 characters long")
    public String getName() {
        return name;
    }


    public void setName(@NotBlank @Size(max=100) String name) {
        this.name = name;
    }

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    public String getEmail() {
        return email;
    }

   public void setEmail(@NotBlank @Email String email) {
        this.email = email;
    }

    @NotBlank(message = "Address is required")
    public String getAddress() {
        return address;
    }


    public void setAddress(@NotBlank String address) {
        this.address = address;
    }

    @NotBlank(message = "Birth date is required")
    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(@NotBlank String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
}
