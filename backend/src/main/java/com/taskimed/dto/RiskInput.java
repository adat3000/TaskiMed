package com.taskimed.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class RiskInput {
    private Integer age;
    private Integer cholesterol;
    private Integer pressure;
}
