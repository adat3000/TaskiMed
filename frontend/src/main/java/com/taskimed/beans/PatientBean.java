package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.primefaces.PrimeFaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.PatientDTO;
import com.taskimed.dto.ProblemDTO;
import com.taskimed.dto.TaskDTO;

/**
 * Bean responsible for managing patients (Patient entity) in the JSF interface.
 * Includes CRUD methods connected to the backend through the REST service.
 */
@Data
@Named
@ViewScoped
@EqualsAndHashCode(callSuper = false)
public class PatientBean extends EntityLazyBean<PatientDTO> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Util util = new Util();

	private PatientDTO selectedPatient = new PatientDTO();
	private List<PatientDTO> patients;
	private String action;

	private List<ProblemDTO> allProblems; // Catálogo maestro
	private List<Long> selectedProblemIds = new ArrayList<>(); // IDs seleccionados en la UI
	private String newProblemName; // Para la creación rápida
	private Long filterProblemId; // ID para el filtro superior (selectOneMenu)

	// 🔥 NUEVO: Buscador dinámico
	private String problemSearchText;

    @Inject
    private LoginBean loginBean;

	@Inject
	private ProblemBean problemBean; // Inyectado para sincronizar selección si es necesario

	@Override
	public String getEndpoint() {
		return "/api/patients/paginated";
	}

	@Override
	public Class<PatientDTO> getEntityClass() {
		return PatientDTO.class;
	}

	@PostConstruct
	public void initBean() {
		super.init();
		try {
			String token = util.obtenerToken();
			patients = util.getDataFromService("/api/patients", new TypeReference<List<PatientDTO>>() {
			}, token);
			// NUEVO: Cargar catálogo de problemas
			allProblems = util.getDataFromService("/api/problems", new TypeReference<List<ProblemDTO>>() {
			}, token);
		} catch (Exception e) {
			e.printStackTrace();
			patients = List.of();
		}
	}

	public int getCurrentYear() {
		return java.time.LocalDate.now().getYear();
	}

	public List<PatientDTO> getPatients() {
		return patients;
	}

	// ============================================================
	// 🔥 NUEVA LÓGICA ESCALABLE PARA PROBLEMAS
	// ============================================================

	public List<ProblemDTO> getFilteredProblems() {
		if (allProblems == null) {
			return List.of();
		}
		if (problemSearchText == null || problemSearchText.isBlank()) {
			return allProblems;
		}
		String search = problemSearchText.toLowerCase();
		return allProblems.stream()
				.filter(p -> p.getName() != null &&
						p.getName().toLowerCase().contains(search))
				.collect(Collectors.toList());
	}

	public void toggleProblem(Long id) {
		if (selectedProblemIds.contains(id)) {
			selectedProblemIds.remove(id);
		} else {
			selectedProblemIds.add(id);
		}
	}

	public void removeProblem(Long id) {
		selectedProblemIds.remove(id);
	}

	public List<ProblemDTO> getSelectedProblems() {
		if (allProblems == null || selectedProblemIds == null) {
			return List.of();
		}
		return allProblems.stream()
				.filter(p -> selectedProblemIds.contains(p.getId()))
				.collect(Collectors.toList());
	}

	/**
	 * MÉTODOS DE FILTRADO (Basados en TaskBean)
	 * Filtra el datatable de pacientes por el problema seleccionado.
	 */
	public void onProblemSelectChange(ProblemDTO selectedProblem) {
		if (selectedProblem == null) {
			this.filterProblemId = null;
			removeCustomFilter("problemId");
		} else {
			this.filterProblemId = selectedProblem.getId();
			addCustomFilter("problemId", selectedProblem.getId());
			
			if (problemBean != null) {
				problemBean.setSelectedProblem(selectedProblem);
			}
		}
		// Refrescar el datatable con widgetVar="tableEntity"
		PrimeFaces.current().executeScript("PF('tableEntity').filter();");
	}

	public void ejecutar() {
		if ("Create".equals(action))
			create();
		else if ("Update".equals(action))
			update();
	}

	public void ejecutar(String accion) throws Exception {
		if ("eliminar".equals(accion)) {
			util.deleteDataFromService("/api/patients/", selectedPatient.getId(), token);
		} else if ("crear".equals(accion) || "actualizar".equals(accion)) {
			syncProblems();
			PatientDTO patient = util.postDataToService("/api/patients", selectedPatient,
					new TypeReference<PatientDTO>() {
					}, token);
			selectedPatient.setId(patient.getId());
			if ("actualizar".equals(accion)) {
				reloadTask();
			}
		}
	}

	public void clear() {
		selectedPatient = new PatientDTO();
		selectedProblemIds = new ArrayList<>();
		action = null;
	}

	private void create() {
		try {
			ejecutar("crear");
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Success",
					"Patient created successfully. Status: " + util.getStatus()));
			PrimeFaces.current().ajax().update("form:datatable");
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "The patient could not be created."));
			e.printStackTrace();
		}
	}

	private void update() {
		try {
			ejecutar("actualizar");
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Success",
					"Patient updated successfully. Status: " + util.getStatus()));
			PrimeFaces.current().ajax().update("form:datatable");
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "The patient could not be updated."));
			e.printStackTrace();
		}
	}

	public void delete() {
		try {
			ejecutar("eliminar");
			if (util.getStatus() == 403) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
						"Warning", "The authenticated patient cannot be deleted."));
				return;
			}
			if (util.getStatus() < 400) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
						"Success", "Patient deleted successfully. Status: " + util.getStatus()));
				adjustPaginatorAfterDeletion("tableEntity", lazyModel);
			}
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error",
					"There was a problem deleting the patient."));
			e.printStackTrace();
		}
	}

	public void validateEmail() {
		if (selectedPatient.getEmail() == null || selectedPatient.getEmail().trim().isEmpty()) {
			FacesContext.getCurrentInstance().addMessage("frmNewEdit:email",
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Email is required.", null));
			return;
		}

		String regex = "^[A-Za-z0-9+_.-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,6}$";

		if (!selectedPatient.getEmail().matches(regex)) {
			FacesContext.getCurrentInstance().addMessage("frmNewEdit:email",
					new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid email format", null));
		}
	}

	public void prepareNew() {
		this.action = "Create";
		this.selectedPatient = new PatientDTO();
		this.selectedProblemIds = new ArrayList<>();
		this.problemSearchText = null; // reset buscador
		this.newProblemName = null;
	}

	// Al preparar para editar, llenamos la lista de IDs desde los objetos
	public void prepareEdit(PatientDTO patient) {
		this.selectedPatient = patient;
		this.action = "Update";
		this.problemSearchText = null; // reset buscador
		if (patient.getProblems() != null) {
			this.selectedProblemIds = patient.getProblems().stream().map(ProblemDTO::getId)
					.collect(Collectors.toList());
		} else {
			this.selectedProblemIds = new ArrayList<>();
		}
		// Forzamos actualización del componente en el diálogo
		PrimeFaces.current().ajax().update("frmNewEdit:problems");
	}
	public void addProblem(Long id) {
	    if (id != null && !selectedProblemIds.contains(id)) {
	        selectedProblemIds.add(id);
	    }
	}
	// Método para guardar el nuevo problema rápidamente
	public void quickAddProblem() {
		System.out.println("DEBUG: Intentando guardar -> " + newProblemName);

		if (newProblemName == null || newProblemName.trim().isEmpty()) {
			System.out.println("DEBUG: El nombre llegó vacío.");
			return;
		}

		try {
			String token = util.obtenerToken();
			ProblemDTO nuevo = ProblemDTO.builder().name(newProblemName.trim()).build();

			// Llamada al servicio
			ProblemDTO guardado = util.postDataToService("/api/problems", nuevo, new TypeReference<ProblemDTO>() {
			}, token);

			if (guardado != null) {
				System.out.println("DEBUG: Guardado con éxito en DB con ID: " + guardado.getId());

				// 1. Actualizar catálogo maestro (allProblems es la lista que lee el
				// selectItems)
				this.allProblems.add(guardado);

				// 2. Marcarlo como seleccionado en la lista de IDs (selectedProblemIds)
				this.selectedProblemIds.add(guardado.getId());

				// 3. LIMPIAR la variable para el siguiente
				this.newProblemName = "";

				FacesContext.getCurrentInstance().addMessage(null,
						new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "Problem added"));
			} else {
				System.out.println("DEBUG: El servicio devolvió NULL (error en backend)");
			}
		} catch (Exception e) {
			System.err.println("DEBUG: Error catastrófico: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Antes de enviar al backend, convertimos los IDs de vuelta a objetos en el DTO
	public void syncProblems() {
		if (selectedProblemIds == null || selectedProblemIds.isEmpty()) {
			selectedPatient.setProblems(null);
			return;
		}
		// Convertimos los IDs seleccionados en la UI a objetos DTO del catálogo maestro
		Set<ProblemDTO> problemSet = allProblems.stream().filter(p -> selectedProblemIds.contains(p.getId()))
				.collect(Collectors.toSet());
		selectedPatient.setProblems(problemSet);
	}

	public String problemsAsTitle(PatientDTO patient) {
		if (patient == null || patient.getProblems() == null || patient.getProblems().isEmpty()) {
			return "No problems";
		}

		return patient.getProblems().stream().map(p -> "• " + p.getName()).collect(Collectors.joining("\n"));
	}

	private void reloadTask() {
		try {
			String token = util.obtenerToken();
			List<TaskDTO> affectedTasks = util.getDataFromService("/api/tasks/byPatient/" + selectedPatient.getId(),
					new TypeReference<List<TaskDTO>>() {
					}, token);

			if (affectedTasks != null) {
				for (TaskDTO task : affectedTasks) {
					// Si la tarea tiene un problema asignado, pero ese ID ya no está
					// en la lista de IDs seleccionados para el paciente...
					if (task.getProblemId() != null && !selectedProblemIds.contains(task.getProblemId()) &&
						!task.getStatus().equals("COMPLETED") && !task.getStatus().equals("CANCELLED")) {

						task.setProblemId(null); // Desvincular

						// 🔹 Build request object with the data expected by the backend
						Map<String, Object> request = new HashMap<>();
						request.put("id", task.getId());
						request.put("description", task.getDescription());
						request.put("status", task.getStatus());
						request.put("patientId", task.getPatientId());
						request.put("assignedToId", task.getAssignedToId());
						request.put("createdById", task.getCreatedById());
						request.put("dueDate", task.getDueDate());
						request.put("categoryId", task.getCategoryId());
						request.put("problemId", task.getProblemId());
						request.put("teamId", task.getTeamId());

						Map<String, Object> response = util.putDataToService("/api/tasks/" + task.getId(), request, token);
						
						if(response == null)  {
							throw new RuntimeException("The server returned a null response.");
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Limpia todos los filtros, variables de búsqueda y reinicia la tabla de pacientes.
	 */
	public void resetFilters() {
	    // 1. Limpiar variables de filtro local
	    this.filterProblemId = null;
	    
	    // 2. Limpiar el mapa de filtros de EntityLazyBean (heredado)
	    if (getCustomFilters() != null) {
	        getCustomFilters().clear();
	    }

	    // 3. Resetear el problema seleccionado en el bean inyectado (para que el combo visual vuelva a "All Problems")
	    if (problemBean != null) {
	        problemBean.setSelectedProblem(null);
	    }

	    // 4. Actualización de UI (Solo si es una petición AJAX)
	    if (FacesContext.getCurrentInstance().getPartialViewContext().isAjaxRequest()) {
		    // ⭐ CLAVE 2: Actualizar los contenedores de los filtros en la UI
		    // Sin esto, los números en los badges no cambiarán aunque el Bean los tenga
	        PrimeFaces.current().ajax().update(
	            "frmSidebar:problemListSidebar",
	            "frm:data"
	        );
		    // 4. Forzar el reinicio visual de la tabla
	        PrimeFaces.current().executeScript("PF('tableEntity').clearFilters();");
	    }
	}
	// Lista de problemas disponibles filtrada por selección y búsqueda manual
	public List<ProblemDTO> getAvailableProblems() {
	    if (allProblems == null) {
	        return new ArrayList<>();
	    }
	    
	    return allProblems.stream()
	            // 1. Que NO esté ya seleccionado (usando getter)
	            .filter(p -> !selectedProblemIds.contains(p.getId()))
	            // 2. Que coincida con la búsqueda manual (case-insensitive)
	            .filter(p -> {
	                if (problemSearchText == null || problemSearchText.trim().isEmpty()) {
	                    return true;
	                }
	                String search = problemSearchText.toLowerCase().trim();
	                return p.getName() != null && p.getName().toLowerCase().contains(search);
	            })
	            .collect(Collectors.toList());
	}

	// Lista de problemas seleccionados
	public List<ProblemDTO> getSelectedPatientProblems() {
	    if (allProblems == null || selectedProblemIds == null) {
	        return new ArrayList<>();
	    }

	    return allProblems.stream()
	        .filter(p -> selectedProblemIds.contains(p.getId()))
	        .collect(Collectors.toList());
	}
}