package com.taskimed.service;
import com.taskimed.dto.RiskInput;
import com.taskimed.dto.RiskResult;

public interface RiskEvaluationService {
	RiskResult evaluate(RiskInput input);
}
