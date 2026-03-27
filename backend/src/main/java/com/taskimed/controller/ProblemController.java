package com.taskimed.controller;

import com.taskimed.dto.ProblemDTO;
import com.taskimed.entity.Problem;
import com.taskimed.service.ProblemService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/problems")
@CrossOrigin(origins = "*")
public class ProblemController {

	private final ProblemService problemService;

	public ProblemController(ProblemService problemService){
		this.problemService = problemService;
	}

	@PostMapping
	public ResponseEntity<ProblemDTO> createProblem(@RequestBody ProblemDTO dto) {
	    ProblemDTO saved = problemService.saveProblem(dto);
	    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProblemDTO> getProblem(@PathVariable(name = "id") Long id) {
	    ProblemDTO updated = problemService.getProblemById(id);
	    return ResponseEntity.ok(updated);
	}

	@GetMapping
	public ResponseEntity<List<ProblemDTO>> getAllProblems() {
	    // Obtenemos las entidades (que ya traen los pacientes cargados gracias al size() en el service)
	    List<Problem> problems = problemService.getProblems();
	    
	    // Las convertimos a DTO para que Jackson incluya la lista de pacientes en el JSON
	    List<ProblemDTO> dtos = problems.stream()
	            .map(problemService::convertToDTO)
	            .collect(Collectors.toList());
	            
	    return ResponseEntity.ok(dtos);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
		problemService.deleteProblem(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/paginated")
	public ResponseEntity<Map<String, Object>> getProblemsPaginated(
			@RequestParam int pageNumber,
			@RequestParam int pageSize,
			@RequestParam(required = false) String filtro,
			@RequestParam(defaultValue = "id") String sortField,
			@RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam Map<String, String> allParams     // 👈 Nuevo
			) {
        if (allParams.containsKey("globalFilter")) {
            filtro = allParams.get("globalFilter");
        }
        // remover parámetros normales para quedarnos solo con filtros personalizados
        allParams.remove("pageNumber");
        allParams.remove("pageSize");
        allParams.remove("filtro");
        allParams.remove("sortField");
        allParams.remove("sortDir");
        allParams.remove("globalFilter");

		Page<ProblemDTO> page = problemService.getPage(pageNumber, pageSize, filtro, sortField, sortDir, allParams);

		Map<String, Object> response = new HashMap<>();
		response.put("data", page.getContent());
		response.put("total", page.getTotalElements());

		return ResponseEntity.ok(response);
	}
}