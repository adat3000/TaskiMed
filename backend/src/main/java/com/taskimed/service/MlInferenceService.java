package com.taskimed.service;

import com.taskimed.dto.MlInferenceRequest;
import com.taskimed.dto.MlInferenceResponse;

public interface MlInferenceService {
	MlInferenceResponse predict(MlInferenceRequest request);
}
