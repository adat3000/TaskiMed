package com.taskimed.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class MlInferenceResponse {
    private String prediction;
    private Double score;
    private String model_version;
}
