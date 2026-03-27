package com.taskimed.implement;

import java.util.List;

import org.springframework.stereotype.Service;

import com.taskimed.dto.MlInferenceRequest;
import com.taskimed.dto.MlInferenceResponse;
import com.taskimed.dto.RiskInput;
import com.taskimed.dto.RiskResult;
import com.taskimed.service.MlInferenceService;
import com.taskimed.service.RiskEvaluationService;

@Service
public class RiskEvaluationServiceImpl implements RiskEvaluationService{
	private final MlInferenceService mlInferenceService;
	
	public RiskEvaluationServiceImpl(MlInferenceService mlInferenceService) {
		this.mlInferenceService = mlInferenceService;
	}
    public RiskResult evaluate(RiskInput input) {

        MlInferenceRequest mlRequest = new MlInferenceRequest(
            List.of(input.getAge(), input.getCholesterol(), input.getPressure())
        );

        MlInferenceResponse mlResponse = mlInferenceService.predict(mlRequest);

        if (mlResponse == null) {
            return RiskResult.fallback();
        }

        return RiskResult.fromMl(mlResponse);
    }

}
