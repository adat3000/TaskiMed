package com.taskimed.implement;

import com.taskimed.dto.PatientDTO;
import com.taskimed.dto.ProblemDTO;
import com.taskimed.entity.Problem;
import com.taskimed.repository.PatientRepository;
import com.taskimed.repository.ProblemRepository;
import com.taskimed.service.ProblemService;

import jakarta.persistence.PersistenceException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class ProblemServiceImpl implements ProblemService {

	private final ProblemRepository problemRepository;
	
	public ProblemServiceImpl(ProblemRepository problemRepository, PatientRepository patientRepository) {
		this.problemRepository = problemRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Problem> getProblems() {
	    List<Problem> problems = problemRepository.findAll();
	    // Al tener un hashCode limpio basado en ID, esto ya no fallará
	    problems.forEach(p -> {
	        if (p.getPatients() != null) p.getPatients().size();
	    });
	    return problems;
	}
	
	@Override
    @Transactional
	public ProblemDTO saveProblem(ProblemDTO dto) {
        if (dto == null) throw new RuntimeException("ProblemDTO no puede ser null");

        Problem problem;
        if (dto.getId() != null) {
            problem = problemRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Problem not found with ID: " + dto.getId()));
        } else {
            problem = new Problem();
        }

        // Mapear campos simples...
        problem.setName(dto.getName());
        problem.setAlias(dto.getAlias());

        Problem saved = problemRepository.saveAndFlush(problem);
        return convertToDTO(saved);
	}

	@Override
	public ProblemDTO getProblemById(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Problem not found with id: " + id));
        return convertToDTO(problem);
	}

	@Override
	public void deleteProblem(Long id) {
		problemRepository.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ProblemDTO> getPage(
			int pageNumber,
			int pageSize,
			String filtro,
			String sortField,
			String sortDir,
            Map<String, String> customFilters
		) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

            Specification<Problem> spec = (root, query, builder) -> {
                if (filtro == null || filtro.trim().isEmpty()) {
                    return builder.conjunction();
                }

                String pattern = "%" + filtro.toLowerCase() + "%";

                return builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("alias")), pattern)
                );
            };
            Page<Problem> page = problemRepository.findAll(spec, pageable);

            List<ProblemDTO> dtoList = page.getContent()
                    .stream()
                    .map(this::convertToDTO)
                    .toList();

            return new PageImpl<>(dtoList, pageable, page.getTotalElements());
        } catch (DataAccessException | PersistenceException e) {
            throw new RuntimeException("Error getting paginated problems: " + e.getMessage(), e);
        }
	}

	@Override
	@Transactional(readOnly = true)
	public ProblemDTO convertToDTO(Problem problem) {
	    if (problem == null) return null;

	    // Convertimos los pacientes asociados a este problema
	    Set<PatientDTO> patientDtos = new HashSet<>();
	    if (problem.getPatients() != null) {
	        patientDtos = problem.getPatients().stream()
	                .map(p -> PatientDTO.builder()
	                        .id(p.getId())
	                        .mrn(p.getMrn())
	                        .firstName(p.getFirstName())
	                        .lastName(p.getLastName())
	                        .fullName((p.getFirstName() + " " + p.getLastName()).trim())
	                        .dateOfBirth(p.getDateOfBirth())
	                        .gender(p.getGender())
	                        .phoneNumber(p.getPhoneNumber())
	                        .email(p.getEmail())
	                        .address(p.getAddress())
	                        .createdAt(p.getCreatedAt())
	                        .updatedAt(p.getUpdatedAt())
	                        // NO mapeamos el set de 'problems' aquí para evitar recursividad
	                        .build())
	                .collect(Collectors.toSet());
	    }

	    return ProblemDTO.builder()
	            .id(problem.getId())
	            .name(problem.getName())
	            .alias(problem.getAlias())
	            .patients(patientDtos) // --- NUEVO: Lista de pacientes en el DTO ---
	            .build();
	}
}