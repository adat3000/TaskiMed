package com.taskimed.dto;

import lombok.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class MlInferenceRequest {
	private List<Integer> features;
}
