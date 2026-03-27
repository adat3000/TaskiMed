package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
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
 * Bean responsible for managing problems (Problem entity) in the JSF interface.
 * Includes CRUD methods connected to the backend through the REST service.
 */
@Data
@Named
@SessionScoped
@EqualsAndHashCode(callSuper = false)
public class ProblemBean extends EntityLazyBean<ProblemDTO> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Util util = new Util();

    private ProblemDTO selectedProblem = new ProblemDTO();
    private List<ProblemDTO> problems;
    private String action;

	private List<PatientDTO> allPatients; // Catálogo maestro
	private List<Long> selectedPatientIds = new ArrayList<>(); // IDs seleccionados en la UI
	private String newPatientName; // Para la creación rápida

    @Inject
    private LoginBean loginBean;

    @Override
    public String getEndpoint() {
        return "/api/problems/paginated";
    }

    @Override
    public Class<ProblemDTO> getEntityClass() {
        return ProblemDTO.class;
    }

    @PostConstruct
    public void initBean() {
        super.init();
        try {
            String token = util.obtenerToken();
            problems = util.getDataFromService("/api/problems", new TypeReference<List<ProblemDTO>>() {}, token);
			// NUEVO: Cargar catálogo de pacientes
			allPatients = util.getDataFromService("/api/patients", new TypeReference<List<PatientDTO>>() {
			}, token);
        } catch (Exception e) {
            e.printStackTrace();
            problems = List.of();
        }
    }

    public List<ProblemDTO> getProblems() {
        return problems;
    }

    public void ejecutar() {
        if ("Create".equals(action))
            create();
        else if ("Update".equals(action))
            update();
    }

    public void ejecutar(String accion) throws Exception {
        if ("eliminar".equals(accion)) {
            util.deleteDataFromService("/api/problems/", selectedProblem.getId(), token);
        } else if ("crear".equals(accion) || "actualizar".equals(accion)) {
        	syncPatients();
        	ProblemDTO problem = util.postDataToService(
                "/api/problems",
                selectedProblem,
                new TypeReference<ProblemDTO>() {},
                token
            );
            selectedProblem.setId(problem.getId());
			if ("actualizar".equals(accion)) {
				reloadTask();
			}
        }
    }

    public void clear() {
        selectedProblem = new ProblemDTO();
        action = null;
    }

    private void create() {
        try {
            ejecutar("crear");
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Success", "Problem created successfully. Status: " + util.getStatus()));
            PrimeFaces.current().ajax().update("form:datatable");
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "The problem could not be created."));
            e.printStackTrace();
        }
    }

    private void update() {
        try {
            ejecutar("actualizar");
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Success", "Problem updated successfully. Status: " + util.getStatus()));
            PrimeFaces.current().ajax().update("form:datatable");
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "The problem could not be updated."));
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            ejecutar("eliminar");
            if (util.getStatus() == 403) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN,
                        "Warning", "The authenticated problem cannot be deleted."));
                return;
            }
            if (util.getStatus() < 400) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "Success", "Problem deleted successfully. Status: " + util.getStatus()));
                adjustPaginatorAfterDeletion("tableEntity", lazyModel);
            }
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "There was a problem deleting the problem."));
            e.printStackTrace();
        }
    }

	// Antes de enviar al backend, convertimos los IDs de vuelta a objetos en el DTO
	public void syncPatients() {
		Set<PatientDTO> patientSet = allPatients.stream().filter(p -> selectedPatientIds.contains(p.getId()))
				.collect(Collectors.toSet());
		selectedProblem.setPatients(patientSet);
	}

	public String patientsAsTitle(ProblemDTO problem) {
		if (problem == null || problem.getPatients() == null || problem.getPatients().isEmpty()) {
			return "No patients";
		}

		return problem.getPatients().stream().map(p -> "• " + p.getFirstName() + ' ' + p.getLastName()).collect(Collectors.joining("\n"));
	}

	private void reloadTask() {
		try {
			String token = util.obtenerToken();
			List<TaskDTO> affectedTasks = util.getDataFromService("/api/tasks/byProblem/" + selectedProblem.getId(),
					new TypeReference<List<TaskDTO>>() {
					}, token);

			if (affectedTasks != null) {
				for (TaskDTO task : affectedTasks) {
					// Si la tarea tiene un paciente asignado, pero ese ID ya no está
					// en la lista de IDs seleccionados para el problema...
					if (task.getPatientId() != null && !selectedPatientIds.contains(task.getPatientId()) &&
						!task.getStatus().equals("COMPLETED") && !task.getStatus().equals("CANCELLED")) {

						task.setPatientId(null); // Desvincular

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
    
    public boolean canModify() {
        if (loginBean == null || loginBean.getUser() == null) {
            return false;
        }
        String loggedRole = loginBean.getUser().getRoleName();
        if ("ADMIN".equalsIgnoreCase(loggedRole)) {
            return true;
        }
        return false;
    }
}