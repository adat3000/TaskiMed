package com.taskimed.controller;

import com.taskimed.dto.PatientDTO;
import com.taskimed.entity.Patient;
import com.taskimed.service.PatientService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.*;

@RestController
@RequestMapping("/api/patients")
@CrossOrigin(origins = "*")
public class PatientController {

	private final PatientService patientService;

	public PatientController(PatientService patientService){
		this.patientService = patientService;
	}

	@PostMapping
	public ResponseEntity<PatientDTO> createPatient(@RequestBody PatientDTO dto) {
	    PatientDTO saved = patientService.savePatient(dto);
	    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	@GetMapping("/{id}")
	public ResponseEntity<PatientDTO> getPatient(@PathVariable Long id) {
		PatientDTO dto = patientService.getPatientById(id);
		return ResponseEntity.ok(dto);
	}

    @GetMapping
    public ResponseEntity<List<Patient>> getAllPatients() {
        return ResponseEntity.ok(patientService.getPatients());
    }

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deletePatient(@PathVariable Long id) {
		patientService.deletePatient(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/paginated")
	public ResponseEntity<Map<String, Object>> getPatientsPaginated(
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

		Page<PatientDTO> page = patientService.getPage(pageNumber, pageSize, filtro, sortField, sortDir, allParams);

		Map<String, Object> response = new HashMap<>();
		response.put("data", page.getContent());
		response.put("total", page.getTotalElements());

		return ResponseEntity.ok(response);
	}
}