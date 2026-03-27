package com.taskimed.dto;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProblemDTO {
    private Long id;
    private String name;
    private String alias;
    
    @Builder.Default
    // Evita que al serializar el paciente, este intente serializar sus problemas
    @JsonIgnoreProperties("problems") 
    private Set<PatientDTO> patients = new HashSet<>();
}