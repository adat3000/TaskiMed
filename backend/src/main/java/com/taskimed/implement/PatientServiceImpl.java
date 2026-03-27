package com.taskimed.implement;

import com.taskimed.dto.PatientDTO;
import com.taskimed.dto.ProblemDTO;
import com.taskimed.entity.Patient;
import com.taskimed.entity.Problem;
import com.taskimed.repository.PatientRepository;
import com.taskimed.repository.ProblemRepository;
import com.taskimed.service.PatientService;

import jakarta.persistence.PersistenceException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
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
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final ProblemRepository problemRepository;

    public PatientServiceImpl(PatientRepository patientRepository, ProblemRepository problemRepository) {
        this.patientRepository = patientRepository;
        this.problemRepository = problemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Patient> getPatients() {
    	List<Patient> patients = patientRepository.findAll();
        patients.forEach(p -> p.getProblems().size()); // Forzar inicialización del proxy
        return patients;
    }
    @Override
    @Transactional // Es vital que sea transaccional para manejar la relación many-to-many
    public PatientDTO savePatient(PatientDTO dto) {
        if (dto == null) throw new RuntimeException("PatientDTO no puede ser null");

        Patient patient;
        if (dto.getId() != null) {
            patient = patientRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Paciente no encontrado con ID: " + dto.getId()));
        } else {
            patient = new Patient();
        }

        // Mapear campos simples...
        patient.setMrn(dto.getMrn());
        patient.setFirstName(dto.getFirstName());
        patient.setLastName(dto.getLastName());
        patient.setDateOfBirth(dto.getDateOfBirth());
        patient.setGender(dto.getGender());
        patient.setPhoneNumber(dto.getPhoneNumber());
        patient.setEmail(dto.getEmail());
        patient.setAddress(dto.getAddress());
        //patient.setCreatedAt(dto.getCreatedAt());
        //patient.setUpdatedAt(dto.getUpdatedAt());
        
     // --- NUEVO: Mapear Problems de forma segura ---
        if (dto.getProblems() != null) {
            // Obtenemos las entidades reales desde la DB
            Set<Problem> nuevasEntidades = dto.getProblems().stream()
                    .map(pDto -> problemRepository.findById(pDto.getId()).orElse(null))
                    .filter(p -> p != null)
                    .collect(Collectors.toSet());
            
            patient.getProblems().clear();
            patient.getProblems().addAll(nuevasEntidades);
        }

        Patient saved = patientRepository.saveAndFlush(patient);
        return convertToDTO(saved);
    }

    @Override
    public PatientDTO getPatientById(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found with id: " + id));
        return convertToDTO(patient);
    }

	@Override
	public void deletePatient(Long id) {
		patientRepository.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<PatientDTO> getPage(
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

            // 1. SPEC BASE - FILTRO GLOBAL (Tu lógica existente)
            Specification<Patient> specGlobal = (root, query, builder) -> {
                if (filtro == null || filtro.trim().isEmpty()) {
                    return builder.conjunction();
                }

                String pattern = "%" + filtro.toLowerCase() + "%";


                Expression<String> userFullName = builder.concat(
                        builder.concat(builder.lower(root.get("firstName")), " "),
                        builder.lower(root.get("lastName"))
                );
                Expression<String> dateOfBirthStr = builder.function("DATE_FORMAT", String.class,
                        root.get("dateOfBirth"), builder.literal("%Y-%m-%d"));

                Expression<String> createdAtStr = builder.function("DATE_FORMAT", String.class,
                        root.get("createdAt"), builder.literal("%Y-%m-%d"));

                Expression<String> updatedAtStr = builder.function("DATE_FORMAT", String.class,
                        root.get("updatedAt"), builder.literal("%Y-%m-%d"));
                return builder.or(
                    builder.like(builder.lower(root.get("mrn")), pattern),
                    builder.like(userFullName, pattern),
                    builder.like(builder.lower(dateOfBirthStr), pattern),
                    builder.like(builder.lower(root.get("gender")), pattern),
                    builder.like(builder.lower(root.get("phoneNumber")), pattern),
                    builder.like(builder.lower(root.get("email")), pattern),
                    builder.like(builder.lower(root.get("address")), pattern),
                    builder.like(builder.lower(createdAtStr), pattern),
                    builder.like(builder.lower(updatedAtStr), pattern)
                );
            };
            // 2. NUEVA SPEC PARA FILTROS PERSONALIZADOS (Lo que faltaba)
            Specification<Patient> specCustom = (root, query, builder) -> {
            	// Si la consulta es para contar elementos (count), no aplicamos distinct de esta forma
                if (query.getResultType() != Long.class) {
                    query.distinct(true); 
                }

                List<Predicate> predicates = new ArrayList<>();

                if (customFilters != null) {
                    // 🔹 Filtrar por Problema (Relación Many-to-Many)
                    if (customFilters.containsKey("problemId") && customFilters.get("problemId") != null) {
                        try {
                            Long pId = Long.valueOf(customFilters.get("problemId"));
                            // Hacemos un join con la colección "problems" de la entidad Patient
                            var problemsJoin = root.join("problems", JoinType.INNER);
                            predicates.add(builder.equal(problemsJoin.get("id"), pId));
                        } catch (NumberFormatException e) {
                            // ID inválido
                        }
                    }
                    
                    // Aquí puedes agregar más filtros en el futuro (ej. gender, ageRange, etc.)
                }

                return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
            };

            // 3. Combinar las especificaciones
            Specification<Patient> finalSpec = Specification.where(specGlobal).and(specCustom);            

            // 4. Ejecutar consulta con la especificación combinada
            Page<Patient> page = patientRepository.findAll(finalSpec, pageable);

            List<PatientDTO> dtoList = page.getContent()
                    .stream()
                    .map(this::convertToDTO)
                    .toList();

            return new PageImpl<>(dtoList, pageable, page.getTotalElements());
        } catch (DataAccessException | PersistenceException e) {
            throw new RuntimeException("Error getting paginated patients: " + e.getMessage(), e);
        }
	}

    /**
     * Convierte una entidad Patient a un DTO incluyendo nombres completos.
     */
    @Override
    public PatientDTO convertToDTO(Patient patient) {
        if (patient == null) return null;
		String fullName = null;
        if (patient.getFirstName() != null && patient.getLastName() != null) {
        	fullName = (patient.getFirstName() + " " + patient.getLastName()).trim();
        }
     // --- NUEVO: Convertir Problems de Entidad a DTO ---
        Set<ProblemDTO> problemDtos = new HashSet<>();
        if (patient.getProblems() != null) {
            problemDtos = patient.getProblems().stream()
                    .map(p -> ProblemDTO.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .alias(p.getAlias())
                            .build())
                    .collect(Collectors.toSet());
        }
        return PatientDTO.builder()
                .id(patient.getId())
                .mrn(patient.getMrn())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .fullName(fullName)
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .phoneNumber(patient.getPhoneNumber())
                .email(patient.getEmail())
                .address(patient.getAddress())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .problems(problemDtos)
                .build();
    }
}