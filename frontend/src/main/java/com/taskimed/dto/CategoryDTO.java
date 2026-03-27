package com.taskimed.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryDTO {
    private Long id;
    private String name;
    private String alias;

    // Sobrescribir toString es útil para que PrimeFaces lo muestre en ciertos componentes
    @Override
    public String toString() {
        return name;
    }
}