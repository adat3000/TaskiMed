package com.taskimed.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.taskimed.dto.RiskInput;
import com.taskimed.dto.RiskResult;
import com.taskimed.service.RiskEvaluationService;

@RestController
@RequestMapping("/api")
public class RiskController {

    private final RiskEvaluationService service;

    public RiskController(RiskEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/risk")
    public ResponseEntity<RiskResult> evaluate(@RequestBody RiskInput input) {
        return ResponseEntity.ok(service.evaluate(input));
    }
}
