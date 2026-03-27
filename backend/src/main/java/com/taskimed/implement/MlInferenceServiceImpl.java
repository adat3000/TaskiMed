package com.taskimed.implement;

import org.springframework.web.reactive.function.client.WebClient;

import com.taskimed.dto.MlInferenceRequest;
import com.taskimed.dto.MlInferenceResponse;
import com.taskimed.service.MlInferenceService;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.Duration;
import reactor.core.publisher.Mono;

@Service
public class MlInferenceServiceImpl implements MlInferenceService {

    private final WebClient webClient;
	
	public MlInferenceServiceImpl(WebClient mlWebClient) {
		this.webClient = mlWebClient;
	}

	@Override
	public MlInferenceResponse predict(MlInferenceRequest request) {
        return webClient.post()
                .uri("/ml/inference/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MlInferenceResponse.class)
                .timeout(Duration.ofSeconds(3))        // ⏱ timeout
                .retry(2)                               // 🔁 retries
             // .onErrorResume(ex -> Mono.empty())      // 🛟 fallback
                .onErrorResume(ex -> Mono.just(
                	    MlInferenceResponse.builder()
                	        .prediction("INDETERMINADO")
                	        .score(0.0)
                	        .model_version("fallback")
                	        .build()
                	))
                .block();
	}

}
