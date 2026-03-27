package com.taskimed.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class RiskResult {
    private String risk;
    private String confidence;
    private String source;

    public static RiskResult fallback() {
        return new RiskResult(
            "INDETERMINADO",
            "N/A",
            "RULE_BASED"
        );
    }
    public static RiskResult fromMl(MlInferenceResponse ml) {
        return new RiskResult(
            ml.getPrediction(),
            Math.round(ml.getScore() * 100) + "%",
            ml.getModel_version()
        );
    }
}
