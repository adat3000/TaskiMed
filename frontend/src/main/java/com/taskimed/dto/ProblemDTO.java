package com.taskimed.dto;

import java.util.HashSet;
import java.util.Set;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProblemDTO {
    @EqualsAndHashCode.Include
    private Long id;
    private String name;
    private String alias;
    
    @Builder.Default
    private Set<PatientDTO> patients = new HashSet<>();

    // Sobrescribir toString es útil para que PrimeFaces lo muestre en ciertos componentes
    @Override
    public String toString() {
        return name;
    }
}