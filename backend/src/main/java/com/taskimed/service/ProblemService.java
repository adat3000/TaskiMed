package com.taskimed.service;

import java.util.List;
import java.util.Map;

import com.taskimed.dto.ProblemDTO;
import com.taskimed.entity.Problem;

import org.springframework.data.domain.Page;

public interface ProblemService {
	ProblemDTO saveProblem(ProblemDTO dto);
	List<Problem> getProblems();
    ProblemDTO getProblemById(Long id);
    void deleteProblem(Long id);
    Page<ProblemDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);
    
    ProblemDTO convertToDTO(Problem problem);
}