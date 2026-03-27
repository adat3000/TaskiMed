package com.taskimed.implement;

import com.taskimed.dto.TaskDTO;
import com.taskimed.dto.TaskRequestDTO;
import com.taskimed.entity.Task;
import com.taskimed.repository.CategoryRepository;
import com.taskimed.repository.PatientRepository;
import com.taskimed.repository.ProblemRepository;
import com.taskimed.repository.TaskRepository;
import com.taskimed.repository.TeamRepository;
import com.taskimed.repository.UserRepository;
import com.taskimed.service.TaskService;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskServiceImpl implements TaskService {

	private final TaskRepository taskRepository;
	private final PatientRepository patientRepository;
	private final UserRepository userRepository;
	private final CategoryRepository categoryRepository;
	private final ProblemRepository problemRepository;
	private final TeamRepository teamRepository;

	public TaskServiceImpl(TaskRepository taskRepository, PatientRepository patientRepository,
			UserRepository userRepository, CategoryRepository categoryRepository,
			ProblemRepository problemRepository, TeamRepository teamRepository) {
		this.taskRepository = taskRepository;
		this.patientRepository = patientRepository;
		this.userRepository = userRepository;
		this.categoryRepository = categoryRepository;
		this.problemRepository = problemRepository;
		this.teamRepository = teamRepository;
	}

	@Override
	public Task saveTask(Task task) {
		return taskRepository.save(task);
	}

	@Override
	public List<Task> getTasks() {
		return taskRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Task getTaskById(Long id) {
		// Usamos findByIdCustom para que cargue los objetos relacionados (Proxy -> Real Object)
	    return taskRepository.findByIdCustom(id)
	        .orElseThrow(() -> new RuntimeException("Task not found with ID: " + id));
	}

	@Override
	public void deleteTask(Long id) {
		taskRepository.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TaskDTO> getTasksByPatientId(Long patientId) {
		List<Task> tasks = taskRepository.findByPatient_Id(patientId);
	    return tasks.stream()
	                .map(this::convertToDTO) // Aquí la sesión SIGUE abierta
	                .toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TaskDTO> getTasksByProblemId(Long problemId) {
		List<Task> tasks = taskRepository.findByProblem_Id(problemId);
	    return tasks.stream()
	                .map(this::convertToDTO) // Aquí la sesión SIGUE abierta
	                .toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<TaskDTO> getTasksByUserId(Long userId) {
		List<Task> tasks = taskRepository.findActiveTasksByUserId(userId);
		// Al estar dentro de @Transactional, la sesión sigue viva aquí
		return tasks.stream().map(this::convertToDTO).collect(Collectors.toList());
	}

	/**
	 * Devuelve una página de tareas convertidas a DTO. Corrige el error de
	 * ordenamiento por 'patient.name' y 'assignedTo.name'.
	 */
	@Override
	@Transactional
	public Page<TaskDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir,
			Map<String, String> customFilters // 👈 createdById, assignedToId, status, etc.
	) {
		try {

			// ─────────────────────────────────────────────
			// 1️⃣ Normalizar campos de ordenamiento
			// ─────────────────────────────────────────────
			if (sortField != null) {
				switch (sortField) {
				case "patientName" -> sortField = "patient.lastName";
				case "assignedToName" -> sortField = "assignedTo.lastName";
				case "createdByName" -> sortField = "createdBy.lastName";
				case "roleName" -> sortField = "createdBy.role.name";
				case "categoryAlias" -> sortField = "category.alias";
				case "categoryName" -> sortField = "category.name";
				case "problemAlias" -> sortField = "problem.alias";
				case "problemName" -> sortField = "problem.name";
				case "teamAlias" -> sortField = "team.alias";
				case "teamName" -> sortField = "team.name";
				default -> {
				}
				}
			}

			Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending()
					: Sort.by(sortField).descending();

			Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

			// ─────────────────────────────────────────────
			// 2️⃣ SPEC BASE – FILTRO GLOBAL
			// ─────────────────────────────────────────────
			Specification<Task> specGlobal = (root, query, builder) -> {

				if (filtro == null || filtro.trim().isEmpty()) {
					return builder.conjunction();
				}

				String pattern = "%" + filtro.toLowerCase() + "%";

				var patientJoin = root.join("patient", JoinType.LEFT);
				var userJoin1 = root.join("assignedTo", JoinType.LEFT);
				var userJoin2 = root.join("createdBy", JoinType.LEFT);
				var roleJoin = userJoin2.join("role", JoinType.LEFT);
				var categoryJoin = root.join("category", JoinType.LEFT);
				var problemJoin = root.join("problem", JoinType.LEFT);
				var teamJoin = root.join("team", JoinType.LEFT);

				Expression<String> dueDateStr = builder.function("DATE_FORMAT", String.class, root.get("dueDate"),
						builder.literal("%Y-%m-%d"));

				Expression<String> createdAtStr = builder.function("DATE_FORMAT", String.class, root.get("createdAt"),
						builder.literal("%Y-%m-%d"));

				Expression<String> patientFullName = builder.concat(
						builder.concat(builder.lower(patientJoin.get("firstName")), " "),
						builder.lower(patientJoin.get("lastName")));

				Expression<String> userFullName1 = builder.concat(
						builder.concat(builder.lower(userJoin1.get("firstName")), " "),
						builder.lower(userJoin1.get("lastName")));

				Expression<String> userFullName2 = builder.concat(
						builder.concat(builder.lower(userJoin2.get("firstName")), " "),
						builder.lower(userJoin2.get("lastName")));

				return builder.or(builder.like(builder.lower(root.get("description")), pattern),
						builder.like(builder.lower(root.get("status").as(String.class)), pattern),
						builder.like(builder.lower(dueDateStr), pattern),
						builder.like(builder.lower(createdAtStr), pattern),
						builder.like(builder.lower(patientJoin.get("firstName")), pattern),
						builder.like(builder.lower(patientJoin.get("lastName")), pattern),
						builder.like(patientFullName, pattern),
						builder.like(builder.lower(userJoin1.get("firstName")), pattern),
						builder.like(builder.lower(userJoin1.get("lastName")), pattern),
						builder.like(userFullName1, pattern),
						builder.like(builder.lower(userJoin2.get("firstName")), pattern),
						builder.like(builder.lower(userJoin2.get("lastName")), pattern),
						builder.like(userFullName2, pattern),
						builder.like(builder.upper(roleJoin.get("name")), pattern),
						builder.like(builder.upper(categoryJoin.get("alias")), pattern),
						builder.like(builder.upper(categoryJoin.get("name")), pattern),
						builder.like(builder.upper(problemJoin.get("alias")), pattern),
						builder.like(builder.upper(problemJoin.get("name")), pattern),
						builder.like(builder.upper(teamJoin.get("alias")), pattern),
						builder.like(builder.upper(teamJoin.get("name")), pattern));

			};

			// ─────────────────────────────────────────────
			// 3️⃣ SPEC PARA FILTROS PERSONALIZADOS
			// ─────────────────────────────────────────────
			Specification<Task> specCustom = (root, query, builder) -> {

				List<Predicate> predicates = new ArrayList<>();

				if (customFilters != null) {

					// 🔹 Filtrar por paciente
					if (customFilters.containsKey("patientId")) {
						Long id = Long.valueOf(customFilters.get("patientId"));
						predicates.add(builder.equal(root.get("patient").get("id"), id));
					}

					// 🔹 Filtrar por usuario creador
					if (customFilters.containsKey("createdById")) {
						Long id = Long.valueOf(customFilters.get("createdById"));
						predicates.add(builder.equal(root.get("createdBy").get("id"), id));
					}

					// 🔹 Filtrar por asignado
					if (customFilters.containsKey("assignedToId")) {
						Long id = Long.valueOf(customFilters.get("assignedToId"));
						predicates.add(builder.equal(root.get("assignedTo").get("id"), id));
					}

					// 🔹 Filtrar por categoría
					if (customFilters.containsKey("categoryId")) {
						Long id = Long.valueOf(customFilters.get("categoryId"));
						predicates.add(builder.equal(root.get("category").get("id"), id));
					}

					// 🔹 Filtrar por problema
					if (customFilters.containsKey("problemId") && customFilters.get("problemId") != null) {
			            try {
			                Long id = Long.valueOf(customFilters.get("problemId").toString());
			                predicates.add(builder.equal(root.get("problem").get("id"), id));
			            } catch (NumberFormatException e) {
			                // Silencioso o log: id inválido
			            }
			        }

					// 🔹 Filtrar por equipo
					if (customFilters.containsKey("teamId") && customFilters.get("teamId") != null) {
			            try {
			                Long id = Long.valueOf(customFilters.get("teamId").toString());
			                predicates.add(builder.equal(root.get("team").get("id"), id));
			            } catch (NumberFormatException e) {
			                // Silencioso o log: id inválido
			            }
			        }
					// 🔹 Filtrar por estado
					if (customFilters.containsKey("status")) {
						predicates.add(builder.equal(root.get("status"), customFilters.get("status")));
					}
					// ⭐ NUEVO: Filtrar por Tareas Expiradas (Vencidas y Pendientes)
					if (customFilters.containsKey("expiredOnly")) {
						// 1. Consideramos expiradas solo las que NO están completadas ni canceladas
						// Usamos el enum o String según cómo esté definido en tu entidad Task
						CriteriaBuilder.In<Object> statusIn = builder.in(root.get("status"));
						statusIn.value("NEW").value("IN_PROGRESS");

						predicates.add(statusIn);

						// 2. La fecha de vencimiento (dueDate) es menor a la fecha actual (ahora)
						predicates.add(builder.lessThan(root.get("dueDate"), new java.util.Date()));
					}
					// ─────────────────────────────────────────────
					// 3️⃣ SPEC PARA FILTROS PERSONALIZADOS
					// ─────────────────────────────────────────────
					// ... dentro del if (customFilters != null) ...

					// ⭐ REVISIÓN: Tareas Expiradas (Strictly before today)
					// ⭐ Lógica Unificada para Expirados (Evita duplicados)
			        Calendar cal = Calendar.getInstance();
			        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
			        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
			        java.util.Date todayStart = cal.getTime();

			        if (customFilters.containsKey("expiredOnly")) {
			            Predicate isPending = root.get("status").in("NEW", "IN_PROGRESS");
			            Predicate isOverdue = builder.lessThan(root.get("dueDate"), todayStart);
			            predicates.add(builder.and(isPending, isOverdue));
			        }

			        if (customFilters.containsKey("notExpired")) {
			            Predicate isPending = root.get("status").in("NEW", "IN_PROGRESS");
			            Predicate isFutureOrToday = builder.greaterThanOrEqualTo(root.get("dueDate"), todayStart);
			            predicates.add(builder.and(isPending, isFutureOrToday));
			        }				}

				return predicates.isEmpty() ? builder.conjunction() : builder.and(predicates.toArray(new Predicate[0]));
			};

			// ─────────────────────────────────────────────
			// 4️⃣ Combinar Specs
			// ─────────────────────────────────────────────
			Specification<Task> finalSpec = Specification.where(specGlobal).and(specCustom);

			// ─────────────────────────────────────────────
			// 5️⃣ Ejecutar consulta
			// ─────────────────────────────────────────────
			Page<Task> page = taskRepository.findAll(finalSpec, pageable);

			List<TaskDTO> dtoList = page.getContent().stream().map(this::convertToDTO).toList();

			return new PageImpl<>(dtoList, pageable, page.getTotalElements());

		} catch (Exception e) {
			throw new RuntimeException("Unexpected error in getPage: " + e.getMessage(), e);
		}
	}

	/**
	 * Convierte una entidad Task a un DTO incluyendo nombres completos.
	 */
	@Override
	public TaskDTO convertToDTO(Task task) {
		if (task == null)
			return null;

		// ---------------------------
		// PATIENT NAME
		// ---------------------------
		String patientName = null;
		if (task.getPatient() != null) {
			String firstName = Optional.ofNullable(task.getPatient().getFirstName()).orElse("");
			String lastName = Optional.ofNullable(task.getPatient().getLastName()).orElse("");
			patientName = (firstName + " " + lastName).trim();
		}

		// ---------------------------
		// ASSIGNED TO NAME
		// ---------------------------
		String assignedToName = null;
		if (task.getAssignedTo() != null) {
			String firstName = Optional.ofNullable(task.getAssignedTo().getFirstName()).orElse("");
			String lastName = Optional.ofNullable(task.getAssignedTo().getLastName()).orElse("");
			assignedToName = (firstName + " " + lastName).trim();
		}

		// ---------------------------
		// CREATED BY NAME + ROLE
		// ---------------------------
		String createdByName = null;
		String roleName = null;
		if (task.getCreatedBy() != null) {
			var created = task.getCreatedBy();

			String first = Optional.ofNullable(created.getFirstName()).orElse("");
			String last = Optional.ofNullable(created.getLastName()).orElse("");
			createdByName = (first + " " + last).trim();

			roleName = Optional.ofNullable(created.getRole()).map(r -> r.getName()).orElse("");
		}

		// ---------------------------
		// CATEGORY ALIAS
		// ---------------------------
		String categoryAlias = null;
		String categoryName = null;
		if (task.getCategory() != null) {
			categoryAlias = Optional.ofNullable(task.getCategory()).map(r -> r.getAlias()).orElse("");
			categoryName = Optional.ofNullable(task.getCategory()).map(r -> r.getName()).orElse("");
		}

		// ---------------------------
		// PROBLEM ALIAS
		// ---------------------------
		String problemAlias = null;
		String problemName = null;
		if (task.getProblem() != null) {
			problemAlias = Optional.ofNullable(task.getProblem()).map(r -> r.getAlias()).orElse("");
			problemName = Optional.ofNullable(task.getProblem()).map(r -> r.getName()).orElse("");
		}

		// ---------------------------
		// TEAM ALIAS
		// ---------------------------
		String teamAlias = null;
		String teamName = null;
		if (task.getTeam() != null) {
			teamAlias = Optional.ofNullable(task.getTeam()).map(r -> r.getAlias()).orElse("");
			teamName = Optional.ofNullable(task.getTeam()).map(r -> r.getName()).orElse("");
		}

		// ---------------------------
		// BUILD DTO
		// ---------------------------
		return TaskDTO.builder().id(task.getId()).description(task.getDescription())
				.status(task.getStatus() != null ? task.getStatus().name() : null)

				.patientId(task.getPatient() != null ? task.getPatient().getId() : null)
				.patientName(patientName)

				.assignedToId(task.getAssignedTo() != null ? task.getAssignedTo().getId() : null)
				.assignedToActive(task.getAssignedTo() != null ? task.getAssignedTo().getActive() : null)
				.assignedToName(assignedToName)

				.createdById(task.getCreatedBy() != null ? task.getCreatedBy().getId() : null)
				.createdByActive(task.getCreatedBy() != null ? task.getCreatedBy().getActive() : null)
				.createdByName(createdByName)

				.dueDate(task.getDueDate()).createdAt(task.getCreatedAt())

				.categoryId(task.getCategory() != null ? task.getCategory().getId() : null)
				.categoryAlias(categoryAlias)
				.categoryName(categoryName)

				.problemId(task.getProblem() != null ? task.getProblem().getId() : null)
				.problemAlias(problemAlias)
				.problemName(problemName)

				.teamId(task.getTeam() != null ? task.getTeam().getId() : null)
				.teamAlias(teamAlias)
				.teamName(teamName)

				.roleName(roleName).build();
	}

	@Override
	@Transactional
	public Task createTask(TaskRequestDTO dto) {
		Task task = new Task();
		task.setDescription(dto.getDescription());
		task.setStatus(Task.Status.valueOf(dto.getStatus()));

		if (dto.getPatientId() != null) {
			task.setPatient(patientRepository.findById(dto.getPatientId()).orElse(null));
		} else {
	        task.setPatient(null);
	    }

		if (dto.getAssignedToId() != null) {
			task.setAssignedTo(userRepository.findById(dto.getAssignedToId()).orElse(null));
		} else {
	        task.setAssignedTo(null); 
	    }

		if (dto.getCreatedById() != null) {
			task.setCreatedBy(userRepository.findById(dto.getCreatedById()).orElse(null));
		} else {
	        task.setCreatedBy(null);
	    }

		if (dto.getCategoryId() != null) {
			task.setCategory(categoryRepository.findById(dto.getCategoryId()).orElse(null));
		} else {
	        task.setCategory(null); 
	    }

		if (dto.getProblemId() != null) {
			task.setProblem(problemRepository.findById(dto.getProblemId()).orElse(null));
		} else {
	        task.setProblem(null); 
	    }

		if (dto.getTeamId() != null) {
			task.setTeam(teamRepository.findById(dto.getTeamId()).orElse(null));
		} else {
	        task.setTeam(null); 
	    }

		task.setDueDate(dto.getDueDate());
		return taskRepository.save(task);
	}

	@Override
	@Transactional
	public Task updateTask(Long id, TaskRequestDTO dto) {
		Task task = getTaskById(id);
		task.setDescription(dto.getDescription());
		task.setStatus(Task.Status.valueOf(dto.getStatus()));

		if (dto.getPatientId() != null) {
			task.setPatient(patientRepository.findById(dto.getPatientId()).orElse(null));
		} else {
	        task.setPatient(null);
	    }

		if (dto.getAssignedToId() != null) {
			task.setAssignedTo(userRepository.findById(dto.getAssignedToId()).orElse(null));
		} else {
	        task.setAssignedTo(null); 
	    }

		if (dto.getCreatedById() != null) {
			task.setCreatedBy(userRepository.findById(dto.getCreatedById()).orElse(null));
		} else {
	        task.setCreatedBy(null);
	    }

		if (dto.getCategoryId() != null) {
			task.setCategory(categoryRepository.findById(dto.getCategoryId()).orElse(null));
		} else {
	        task.setCategory(null); 
	    }

		if (dto.getProblemId() != null) {
			task.setProblem(problemRepository.findById(dto.getProblemId()).orElse(null));
		} else {
	        task.setProblem(null); 
	    }

		if (dto.getTeamId() != null) {
			task.setTeam(teamRepository.findById(dto.getTeamId()).orElse(null));
		} else {
	        task.setTeam(null); 
	    }

		task.setDueDate(dto.getDueDate());
		return taskRepository.save(task);
	}
	@Transactional
	public void unassignTasks(Long teamId, Long userId) {
	    if (userId == null) {
	        // Caso: Eliminar equipo (Desasignar a todos los del equipo)
	        taskRepository.unassignAllByTeamId(teamId);
	    } else {
	        // Caso: Usuario sale del equipo (Desasignar solo a ese usuario en tareas de ese equipo)
	        taskRepository.unassignUserFromTeamTasks(teamId, userId);
	    }
	}

	// Ejemplo de cómo quedarían tus métodos actuales usando la nueva lógica
	@Override
	public Map<Long, Long> countTasksByPatient() {
	    // Si no hay filtros, se comporta como antes, pero ahora es capaz de recibir filtros
	    return getDynamicCountsFor("patient", new HashMap<>())
	           .entrySet().stream()
	           .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
	}

	// Ejemplo de cómo quedarían tus métodos actuales usando la nueva lógica
	@Override
	public Map<Long, Long> countTasksByProblem() {
	    // Si no hay filtros, se comporta como antes, pero ahora es capaz de recibir filtros
	    return getDynamicCountsFor("problem", new HashMap<>())
	           .entrySet().stream()
	           .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
	}

	// Ejemplo de cómo quedarían tus métodos actuales usando la nueva lógica
	@Override
	public Map<Long, Long> countTasksByCategory() {
	    // Si no hay filtros, se comporta como antes, pero ahora es capaz de recibir filtros
	    return getDynamicCountsFor("category", new HashMap<>())
	           .entrySet().stream()
	           .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().longValue()));
	}
	@Override
	@Transactional(readOnly = true)
	public Map<Long, Integer> getDynamicCountsFor(String entityType, Map<String, String> customFilters) {
	    // 1. Crear Specification basada en los filtros actuales
	    Specification<Task> spec = (root, query, builder) -> {
	        List<Predicate> predicates = new ArrayList<>();
	        if (customFilters != null) {
	            if (customFilters.get("categoryId") != null) 
	                predicates.add(builder.equal(root.get("category").get("id"), Long.valueOf(customFilters.get("categoryId"))));
	            if (customFilters.get("problemId") != null) 
	                predicates.add(builder.equal(root.get("problem").get("id"), Long.valueOf(customFilters.get("problemId"))));
	            if (customFilters.get("patientId") != null) 
	                predicates.add(builder.equal(root.get("patient").get("id"), Long.valueOf(customFilters.get("patientId"))));
	        }
	        return builder.and(predicates.toArray(new Predicate[0]));
	    };

	    // 2. Obtener solo las tareas que cumplen los filtros cruzados
	    List<Task> filteredTasks = taskRepository.findAll(spec);

	    // 3. Agrupar y contar por la entidad solicitada
	    Map<Long, Long> counts;
	    switch (entityType.toLowerCase()) {
	        case "category" -> counts = filteredTasks.stream().filter(t -> t.getCategory() != null)
	                                    .collect(Collectors.groupingBy(t -> t.getCategory().getId(), Collectors.counting()));
	        case "problem" -> counts = filteredTasks.stream().filter(t -> t.getProblem() != null)
	                                    .collect(Collectors.groupingBy(t -> t.getProblem().getId(), Collectors.counting()));
	        case "patient" -> counts = filteredTasks.stream().filter(t -> t.getPatient() != null)
	                                    .collect(Collectors.groupingBy(t -> t.getPatient().getId(), Collectors.counting()));
	        default -> counts = new HashMap<>();
	    }

	    // Convertir a Map<Long, Integer> para el Bean
	    return counts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().intValue()));
	}
}