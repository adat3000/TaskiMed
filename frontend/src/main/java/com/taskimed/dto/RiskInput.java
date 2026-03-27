package com.taskimed.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class RiskInput {
    private Double age;
    private Double cholesterol;
    private Double pressure;
}
