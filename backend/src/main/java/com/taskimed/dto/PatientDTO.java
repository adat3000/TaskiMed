package com.taskimed.dto;

import lombok.*;
import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatientDTO {
    private Long id;
    private String mrn;
    private String firstName;
    private String lastName;
    private String fullName;
    private Date dateOfBirth;
    private String gender;
    private String phoneNumber;
    private String email;
    private String address;
    private Date createdAt;
    private Date updatedAt;
    
    // Evita que al serializar el problema, este intente serializar sus pacientes
    @JsonIgnoreProperties("patients") 
    private Set<ProblemDTO> problems;
}