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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

import org.primefaces.PrimeFaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.CategoryDTO;
import com.taskimed.dto.PatientDTO;
import com.taskimed.dto.ProblemDTO;
import com.taskimed.dto.TaskDTO;
import com.taskimed.dto.UserDTO;

/**
 * Bean responsible for managing tasks (TaskDTO) in the JSF interface. Includes
 * CRUD methods connected to the backend through the REST service.
 */
@Data
@Named
@ViewScoped
@EqualsAndHashCode(callSuper = false)
public class TaskBean extends EntityLazyBean<TaskDTO> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Util util = new Util();
	private Date today;
	private boolean filterMyTasks = false;
	private TaskDTO selectedTask = new TaskDTO();
	private String action;
	private List<String> statusList = List.of("NEW", "IN_PROGRESS", "CANCELLED", "COMPLETED");
	// 1. Añade la propiedad para rastrear el filtro seleccionado
	private String currentFilter = "ALL";
	private Long filterProblemId; // Variable dedicada al filtro
	private Long filterPatientId; // Variable dedicada al filtro
	private PatientDTO localPatient;
	private ProblemDTO localProblem;

	// Propiedad nueva para manejar el objeto Usuario en el combo (permite ver
	// .active)
	private UserDTO selectedUser;

	@Inject
	private LoginBean loginBean; // ✅ Inyección directa del bean de sesión

	@Inject
	private PatientBean patientBean; // ✅ Inyectado para sincronizar el combo multi-columna

	@Inject
	private ProblemBean problemBean; // ✅ Inyectado para sincronizar el combo multi-columna

	@Inject
	private CategoryBean categoryBean; // ✅ Inyectado para sincronizar el combo multi-columna

	@Inject
	private TeamBean teamBean;

	@Inject
	private UserBean userBean;

	@Inject
	private NotificationBean notificationBean; // Inyectamos el bean de notificaciones

	// Lista filtrada de usuarios que se mostrará en el combo
	private List<UserDTO> filteredUsers;

	// Conteo cacheado de tareas por paciente
	private Map<Long, Integer> taskCountByPatient = new HashMap<>();

	// Conteo cacheado de tareas por problema
	private Map<Long, Integer> taskCountByProblem = new HashMap<>();

	// 1. Declarar el nuevo mapa de conteo (cerca de taskCountByPatient)
	private Map<Long, Integer> taskCountByCategory = new HashMap<>();

	@Override
	public String getEndpoint() {
		return "/api/tasks/paginated";
	}

	@Override
	public Class<TaskDTO> getEntityClass() {
		return TaskDTO.class;
	}

	@PostConstruct
	public void initBean() {
		today = new Date();
		super.init();
		selectedTask = new TaskDTO();

		reloadTaskCounts(); // 👈 AÑADIR
	}

	/**
	 * Prepara la visualización de la tarea cargando datos frescos desde el
	 * servicio. Esto evita que cambios temporales realizados en el diálogo (sin
	 * guardar) se muestren como si fueran reales.
	 */
	public void prepareView(TaskDTO task) {
		try {
			String url = "/api/tasks/" + task.getId();

			// Consultamos al servicio para obtener la versión real de la base de datos
			TaskDTO freshTask = util.getDataFromService(url, new TypeReference<TaskDTO>() {
			}, token);

			if (freshTask != null) {
				this.selectedTask = freshTask;
			} else {
				this.selectedTask = task;
			}
		} catch (Exception e) {
			this.selectedTask = task;
			e.printStackTrace();
		}

		this.action = "Update";
	}

	/**
	 * Sincroniza el objeto PatientDTO seleccionado al abrir el diálogo de edición.
	 * Esto permite que el selectOneMenu con OmniFaces marque al paciente correcto.
	 */
	public void prepareEdit(TaskDTO task) {
		try {
			// Consultamos al API para obtener la versión "limpia" de la base de datos
			String url = "/api/tasks/" + task.getId();
			TaskDTO freshTask = util.getDataFromService(url, new TypeReference<TaskDTO>() {
			}, token);

			if (freshTask != null) {
				this.selectedTask = freshTask;
			} else {
				// Fallback: si el servicio falla, usamos el objeto que viene del datatable
				this.selectedTask = task;
			}
		} catch (Exception e) {
			this.selectedTask = task;
			e.printStackTrace();
		}

		this.action = "Update";

		// 1. Forzamos al UserBean a vaciar su lista interna por completo
		if (userBean != null) {
			userBean.setUsers(new ArrayList<>()); // En lugar de null, lista vacía para evitar NPE
			userBean.init();
		}

		// 2. Si el Bean no carga, TaskBean descarga la data directamente
		ensureUsersLoadedManual();

		// Sincronizamos el objeto usuario ANTES de filtrar
		syncSelectedUserFromId();

		// 3. Ejecutamos el filtrado
		handleTeamChange();

		// Sincronización de pacientes
		if (this.selectedTask.getPatientId() != null && patientBean != null && patientBean.getPatients() != null) {
			this.localPatient = patientBean.getPatients().stream()
					.filter(p -> p.getId().equals(this.selectedTask.getPatientId())).findFirst().orElse(null);
		}

		// Sincronización de problemas
		if (this.selectedTask.getProblemId() != null && problemBean != null && problemBean.getProblems() != null) {
			this.localProblem = problemBean.getProblems().stream()
					.filter(p -> p.getId().equals(this.selectedTask.getProblemId())).findFirst().orElse(null);
		}
	}

	private void syncSelectedUserFromId() {
		if (this.selectedTask.getAssignedToId() != null && userBean.getUsers() != null) {
			this.selectedUser = userBean.getUsers().stream()
					.filter(u -> u.getId().equals(this.selectedTask.getAssignedToId())).findFirst().orElse(null);
		} else {
			this.selectedUser = null;
		}
	}

	private void ensureUsersLoadedManual() {
		try {
			// Si el UserBean sigue reportando 0 o la data es vieja, cargamos directo
			// Nota: Añadimos un timestamp a la URL para romper cualquier caché del
			// navegador/proxy
			String token = util.obtenerToken();
			String url = "/api/users?t=" + System.currentTimeMillis();

			List<UserDTO> apiUsers = util.getDataFromService(url, new TypeReference<List<UserDTO>>() {
			}, token);

			if (apiUsers != null && !apiUsers.isEmpty()) {
				userBean.setUsers(apiUsers);
			}
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "Manual loading assigned users error."));
			e.printStackTrace();
		}
	}

	public void handleTeamChange() {
		List<UserDTO> allUsers = (userBean != null) ? userBean.getUsers() : new ArrayList<>();

		if (selectedTask.getTeamId() == null) {
			// Si el equipo es null, la lista de usuarios debe estar VACÍA
			// (o mostrar todos, pero según tu lógica, el usuario debe limpiarse)
			this.filteredUsers = new ArrayList<>();
			this.selectedUser = null; // LIMPIAMOS EL USUARIO SELECCIONADO
			this.selectedTask.setAssignedToId(null);
		} else {
			Long targetTeamId = selectedTask.getTeamId();

			// CREAMOS UNA NUEVA LISTA (No reutilizamos la anterior)
			this.filteredUsers = allUsers.stream().filter(u -> targetTeamId.equals(u.getTeamId()))
					.collect(Collectors.toList());

			// Verificamos si el usuario actual sigue perteneciendo al nuevo equipo
			if (selectedUser != null && !targetTeamId.equals(selectedUser.getTeamId())) {
				this.selectedUser = null;
				this.selectedTask.setAssignedToId(null);
			}
		}

		// IMPORTANTE: Forzar el update del ID exacto del componente
		// Revisa si en tu .xhtml el id es 'assignedTo' o ':frmNewEdit:assignedTo'
		PrimeFaces.current().ajax().update("assignedTo");
	}

	/**
	 * CASO 2: Al cambiar el Usuario, seleccionamos su equipo automáticamente.
	 */
	public void handleUserChange() {
		if (this.selectedUser == null) {
			selectedTask.setAssignedToId(null);
			return;
		}

		// Sincronizamos el ID del DTO con el objeto seleccionado en el combo
		selectedTask.setAssignedToId(this.selectedUser.getId());

		// Asignamos el teamId del usuario a la tarea
		selectedTask.setTeamId(this.selectedUser.getTeamId());

		// Actualizamos la lista de usuarios para que coincida con este equipo
		handleTeamChange();
	}

	// Inicializar la lista al preparar edición o creación
	public void prepareNewTask() {
		clear();
		this.action = "Create";
		this.selectedUser = null;
		if (userBean != null) {
			this.filteredUsers = userBean.getUsers();
		}
	}

	/**
	 * NUEVA FUNCIÓN: Permite que TeamBean notifique cambios. Esta función actualiza
	 * la lista global de usuarios y la lista filtrada del diálogo.
	 */
	public void reloadUserLists() {
		if (userBean != null) {
			userBean.init();
			this.filteredUsers = userBean.getUsers();

			// Si el diálogo está abierto, re-filtramos por el equipo de la tarea actual
			if (selectedTask != null && selectedTask.getTeamId() != null) {
				handleTeamChange();
			}
			syncSelectedUserFromId();
			// Forzamos actualización del combo en la vista
			PrimeFaces.current().ajax().update("assignedTo");
		}
	}

	public void onPatientSelectNewEdit(PatientDTO selectedPatient) {
	    if (this.selectedTask == null) {
	        this.selectedTask = new TaskDTO();
	    }

	    if (selectedPatient != null) {
	        this.localPatient = selectedPatient;
	        this.selectedTask.setPatientId(selectedPatient.getId());
	    } else {
	        this.localPatient = null;
	        this.selectedTask.setPatientId(null);
	    }

	    // CRUCIAL: Resetear el problema seleccionado en la tarea
	    // ya que el nuevo paciente no tiene el problema del paciente anterior.
	    if (this.selectedTask != null) {
	        this.selectedTask.setProblemId(null);
	    }

	    this.filterProblemId = null;
	    removeCustomFilter("problemId");
	}

	public void onPatientSelectInTasks(PatientDTO selectedPatient) {
		// 1. Aseguramos que selectedTask no sea nulo para evitar el NPE
		if (this.selectedTask == null) {
			this.selectedTask = new TaskDTO();
		}

		if (selectedPatient != null) {
			// Sincronizamos el objeto de edición (si existiera)
			this.selectedTask.setPatientId(selectedPatient.getId());

			// Aplicamos el FILTRO a la tabla
			addCustomFilter("patientId", selectedPatient.getId());

			// Sincronizamos el bean de pacientes para los combos dependientes
			patientBean.setSelectedPatient(selectedPatient);
		} else {
			this.selectedTask.setPatientId(null);
			removeCustomFilter("patientId");
			patientBean.setSelectedPatient(null);
		}

		// 2. IMPORTANTE: Resetear el filtro de problema siempre que cambie el paciente
		this.filterProblemId = null;
		removeCustomFilter("problemId");

		reloadTaskCounts(); // 👈 AÑADIR
	}

	public void onProblemSelectInTasks(ProblemDTO selectedProblem) {
		// 1. Aseguramos que selectedTask no sea nulo para evitar el NPE
		if (this.selectedTask == null) {
			this.selectedTask = new TaskDTO();
		}

		if (selectedProblem != null) {
			// Sincronizamos el objeto de edición (si existiera)
			this.selectedTask.setProblemId(selectedProblem.getId());

			// Aplicamos el FILTRO a la tabla
			addCustomFilter("problemId", selectedProblem.getId());

			// Sincronizamos el bean de problemas para los combos dependientes
			problemBean.setSelectedProblem(selectedProblem);
		} else {
			this.selectedTask.setProblemId(null);
			removeCustomFilter("problemId");
			problemBean.setSelectedProblem(null);
		}

		// 2. IMPORTANTE: Resetear el filtro de paciente siempre que cambie el problema
		this.filterPatientId = null;
		removeCustomFilter("patientId");

		reloadTaskCounts(); // 👈 AÑADIR
	}

	// 2. Método principal de filtrado
	public void applyFilter() {
		// Limpiamos filtros anteriores del LazyModel para no acumularlos
		removeCustomFilter("assignedToId");
		removeCustomFilter("createdById");
		removeCustomFilter("status");
		removeCustomFilter("expiredOnly");
		removeCustomFilter("notExpired");

		Long currentUserId = loginBean.getUser().getId();

		switch (currentFilter) {
		case "ASSIGNED_TO":
			addCustomFilter("assignedToId", currentUserId);
			break;
		case "CREATED_BY":
			addCustomFilter("createdById", currentUserId);
			break;
		case "EXPIRED":
			// Enviamos un flag o parámetro que el Backend/DAO sepa interpretar
			// como: status IN ('NEW', 'IN_PROGRESS') AND dueDate < NOW()
			addCustomFilter("expiredOnly", true);
			break;
		case "NOT_EXPIRED":
			addCustomFilter("notExpired", "true"); // Nuevo filtro
			break;
		case "NEW":
		case "IN_PROGRESS":
		case "COMPLETED":
		case "CANCELLED":
			addCustomFilter("status", currentFilter);
			break;
		default: // "ALL"
			// No se añaden filtros extra
			break;
		}
		// Nota: El update se hace desde el p:ajax del xhtml
	}

	public void ejecutar() {
		if ("Create".equalsIgnoreCase(action))
			create();
		else if ("Update".equalsIgnoreCase(action))
			update();
	}

	public void ejecutar(String accion) throws Exception {
		try {
			if ("eliminar".equalsIgnoreCase(accion)) {
				util.deleteDataFromService("/api/tasks/", selectedTask.getId(), token);
			} else if ("crear".equalsIgnoreCase(accion) || "actualizar".equalsIgnoreCase(accion)) {

				// 🔹 Build request object with the data expected by the backend
				Map<String, Object> req = new HashMap<>();
				req.put("id", selectedTask.getId());
				req.put("description", selectedTask.getDescription());
				req.put("status", selectedTask.getStatus());
				// Dentro de ejecutar(String accion)
				if (this.localPatient != null) {
					selectedTask.setPatientId(localPatient.getId());
				}
				req.put("patientId", selectedTask.getPatientId());
				req.put("assignedToId", selectedTask.getAssignedToId());
				req.put("createdById", selectedTask.getCreatedById());
				req.put("dueDate", selectedTask.getDueDate());
				req.put("categoryId", selectedTask.getCategoryId());
				req.put("problemId", selectedTask.getProblemId());
				req.put("teamId", selectedTask.getTeamId());

				Map<String, Object> response = null;

				if ("crear".equalsIgnoreCase(accion)) {
					// 🟢 POST to create
					response = util.postDataToService("/api/tasks", req, new TypeReference<Map<String, Object>>() {
					}, token);
				} else {
					// 🟣 PUT to update
					response = util.putDataToService("/api/tasks/" + selectedTask.getId(), req, token);
				}

				// 🔹 Validate response
				if (response != null) {
					if (response.containsKey("id"))
						selectedTask.setId(Long.valueOf(response.get("id").toString()));
					if (response.containsKey("description"))
						selectedTask.setDescription(response.get("description").toString());
					if (response.containsKey("status"))
						selectedTask.setStatus(response.get("status").toString());
					if (response.containsKey("patientId"))
						selectedTask.setPatientId(Long.valueOf(response.get("patientId").toString()));
					if (response.containsKey("assignedToId"))
						selectedTask.setAssignedToId(Long.valueOf(response.get("assignedToId").toString()));
					if (response.containsKey("createdById"))
						selectedTask.setCreatedById(Long.valueOf(response.get("createdById").toString()));
					if (response.containsKey("categoryId"))
						selectedTask.setCategoryId(Long.valueOf(response.get("categoryId").toString()));
					if (response.containsKey("problemId"))
						selectedTask.setProblemId(Long.valueOf(response.get("problemId").toString()));
					if (response.containsKey("teamId"))
						selectedTask.setTeamId(Long.valueOf(response.get("teamId").toString()));
					if (response.containsKey("patientName"))
						selectedTask.setPatientName((String) response.get("patientName"));
					if (response.containsKey("assignedToActive"))
						selectedTask.setAssignedToActive((Boolean) response.get("assignedToActive"));
					if (response.containsKey("createdByActive"))
						selectedTask.setCreatedByActive((Boolean) response.get("createdByActive"));
					if (response.containsKey("assignedToName"))
						selectedTask.setAssignedToName((String) response.get("assignedToName"));
					if (response.containsKey("createdByName"))
						selectedTask.setCreatedByName((String) response.get("createdByName"));
					if (response.containsKey("categoryAlias"))
						selectedTask.setCategoryAlias((String) response.get("categoryAlias"));
					if (response.containsKey("categoryName"))
						selectedTask.setCategoryName((String) response.get("categoryName"));
					if (response.containsKey("problemAlias"))
						selectedTask.setProblemAlias((String) response.get("problemAlias"));
					if (response.containsKey("problemName"))
						selectedTask.setProblemName((String) response.get("problemName"));
					if (response.containsKey("teamAlias"))
						selectedTask.setTeamAlias((String) response.get("teamAlias"));
					if (response.containsKey("teamName"))
						selectedTask.setTeamName((String) response.get("teamName"));
					if (response.containsKey("roleName"))
						selectedTask.setRoleName((String) response.get("roleName"));
				} else {
					throw new RuntimeException("The server returned a null response.");
				}
			}
			reloadTaskCounts();
		    PrimeFaces.current().ajax().update("frm:patientList","frm:problemList","frmSidebar");
		} catch (Exception e) {
			util.setStatus(400);
			throw e;
		}
	}

	public void clear() {
		// Reiniciamos el objeto para romper el vínculo con la tabla
		this.selectedTask = new TaskDTO();
		this.action = null;
		this.selectedUser = null;
		this.localPatient = null;
		this.filteredUsers = new ArrayList<>();

		if (patientBean != null) {
			patientBean.setSelectedPatient(null);
		}

		if (problemBean != null) {
			problemBean.setSelectedProblem(null);
		}

		if (categoryBean != null) {
			categoryBean.setSelectedCategory(null);
		}

		// Valores por defecto para nueva tarea (sin causar recursión)
		if (loginBean != null && loginBean.getUser() != null) {
			UserDTO user = loginBean.getUser();
			this.selectedTask.setCreatedById(user.getId());
			this.selectedTask.setCreatedByName(user.getFullName());
			this.selectedTask.setCreatedByActive(user.getActive());
		}
		this.selectedTask.setStatus("NEW");
	}

	private void create() {
		try {
			ejecutar("crear");
			if (util.getStatus() < 400) {
				syncNotification();
				syncAfterTaskChange(); // 👈

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
						"Success", "Task created successfully. Status: " + util.getStatus()));
			}
			PrimeFaces.current().ajax().update("frm:data");
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "The task could not be created."));
			e.printStackTrace();
		}
	}

	private void update() {
		try {
			ejecutar("actualizar");
			if (util.getStatus() < 400) {
				syncNotification();
				syncAfterTaskChange(); // 👈

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
						"Success", "Task updated successfully. Status: " + util.getStatus()));
			}
			PrimeFaces.current().ajax().update("frm:data");
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "The task could not be updated."));
			e.printStackTrace();
		}
	}

	public void delete() {
		try {
			ejecutar("eliminar");
			if (util.getStatus() == 403) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
						"Warning", "You do not have permission to delete this task."));
				return;
			}
			if (util.getStatus() < 400) {
				syncNotification();
				syncAfterTaskChange(); // 👈

				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
						"Success", "Task deleted successfully. Status: " + util.getStatus()));
				adjustPaginatorAfterDeletion("tableEntity", lazyModel);
			}
		} catch (Exception e) {
			FacesContext.getCurrentInstance().addMessage(null,
					new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "There was a problem deleting the task."));
			e.printStackTrace();
		}
	}

	private void syncNotification() {
		if (notificationBean != null) {
			try {
				notificationBean.reloadNotifications();
				PrimeFaces.current().ajax().update("notificationWrapper");
				// Nota: Verifica si el ID es "frm:notificationWrapper" o "notificationWrapper"
				// según cómo esté en tu plantilla principal.
			} catch (Exception e) {
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
						"Notification", "It is not available in this view."));
			}
		}
	}

	/**
	 * Returns true if the logged-in user is allowed to edit/delete the given task.
	 *
	 * Rules: - ADMIN can modify any task. - STAFF can modify only tasks they
	 * created. - Additionally, STAFF is NOT allowed to modify tasks created by
	 * ADMIN (defensive check using Task.roleName).
	 */
	public boolean canModify(TaskDTO task) {
		if (loginBean == null || loginBean.getUser() == null || task == null) {
			return false;
		}

		// logged user info
		Long loggedUserId = loginBean.getUser().getId();
		String loggedRole = null;
		if (loginBean.getUser().getRoleId() != null) {
			loggedRole = loginBean.getUser().getRoleName();
		}

		// 1) Admin can do everything
		if ("ADMIN".equalsIgnoreCase(loggedRole)) {
			return true;
		}

		// 2) Defensive: if task has roleName and it's ADMIN, prevent non-admin from
		// modifying it
		if (task.getRoleName() != null && "ADMIN".equalsIgnoreCase(task.getRoleName())) {
			return false;
		}

		// 3) STAFF (or others) can only modify their own tasks
		return task.getCreatedById() != null && task.getCreatedById().equals(loggedUserId);
	}

	public boolean canModifySelectedTask() {
		if (selectedTask == null || selectedTask.getId() == null) {
			return false;
		}

		if (loginBean.getUser() == null) {
			return false;
		}

		Long currentId = loginBean.getUser().getId();
		Long creatorId = selectedTask.getCreatedById();
		Long assignedTo = selectedTask.getAssignedToId();

		return (currentId != null && (currentId.equals(creatorId) || currentId.equals(assignedTo)));
	}

	/**
	 * Returns true if the selected task is in a final, non-editable state
	 * (Completed or Cancelled).
	 */
	public boolean isTaskFinalized() {
		if (selectedTask == null || selectedTask.getStatus() == null) {
			return false;
		}
		String status = selectedTask.getStatus();
		return "COMPLETED".equals(status) || "CANCELLED".equals(status);
	}

	// Dentro de TaskBean.java
	// ...
	/**
	 * Returns the list of possible statuses for the current selected task. Excludes
	 * 'NEW' if the task is already 'IN_PROGRESS'.
	 */
	public List<String> getEditableStatusList() {
		try {
			// Si no hay tarea o no hay estado, devolvemos la lista completa por defecto
			if (selectedTask == null || selectedTask.getStatus() == null) {
				return statusList;
			}

			if ("IN_PROGRESS".equals(selectedTask.getStatus())) {
				return List.of("IN_PROGRESS", "CANCELLED", "COMPLETED");
			}

			return statusList;
		} catch (Exception e) {
			// Log para ver el error real en la consola del servidor (Tomcat/Payara)
			e.printStackTrace();
			return statusList;
		}
	}

	/**
	 * Asegura que el objeto selectedTask esté inicializado para evitar errores de
	 * 'Objetivo inalcanzable' en la vista.
	 */
	public void ensureSelectedTaskInitialized() {
		if (this.selectedTask == null) {
			this.selectedTask = new TaskDTO();
		}
	}

	/**
	 * Limpia todos los filtros, variables de búsqueda y reinicia la tabla.
	 */
	public void resetFilters() {
	    // 1. Limpiar variables de filtro local
	    this.currentFilter = "ALL";
	    this.filterProblemId = null;
	    this.filterPatientId = null;
	    this.filterMyTasks = false;

	    // 2. Limpiar el mapa de filtros de EntityLazyBean
	    if (getCustomFilters() != null) {
	        getCustomFilters().clear();
	    }

	    // 3. Resetear beans inyectados
	    if (categoryBean != null) categoryBean.setSelectedCategory(null);
	    if (patientBean != null) patientBean.setSelectedPatient(null);
	    if (problemBean != null) problemBean.setSelectedProblem(null);

	    // ⭐ CAMBIO SUSTANCIAL: Usar reloadTaskCounts() en lugar de reloadDynamicCounts()
	    // Esto asegura que al entrar a la página o resetear, los números 
	    // muestren el TOTAL global y no dependan de filtros previos.
	    reloadTaskCounts();

	    // 4. Actualización de UI (Solo si es una petición AJAX)
	    if (FacesContext.getCurrentInstance().getPartialViewContext().isAjaxRequest()) {
		    // ⭐ CLAVE 2: Actualizar los contenedores de los filtros en la UI
		    // Sin esto, los números en los badges no cambiarán aunque el Bean los tenga
	        PrimeFaces.current().ajax().update(
	            "frm:activeFilters", 
	            "frm:problemList", 
	            "frm:patientList", 
	            "frmSidebar:categoryListSidebar",
	            "frm:data"
	        );
		    // 4. Forzar el reinicio visual de la tabla
	        PrimeFaces.current().executeScript("PF('tableEntity').clearFilters();");
	    }
	}

	/**
	 * Aplica filtro combinado: Paciente + Problema Usado exclusivamente por el
	 * selectOneMenu embebido en la tabla de pacientes
	 */
	public void filterByPatientAndProblem(Long patientId, Long problemId) {

		// ===== PATIENT FILTER =====
		if (patientId != null) {
			this.filterPatientId = patientId;
			addCustomFilter("patientId", patientId);
		} else {
			this.filterPatientId = null;
			removeCustomFilter("patientId");
		}

		// ===== PROBLEM FILTER =====
		if (problemId != null) {
			this.filterProblemId = problemId;
			addCustomFilter("problemId", problemId);
		} else {
			this.filterProblemId = null;
			removeCustomFilter("problemId");
		}

		// Refrescamos la tabla
		PrimeFaces.current().executeScript("PF('tableEntity').filter();");
	}

	public int getTaskCountForPatient(Long patientId) {
		if (patientId == null)
			return 0;
		return taskCountByPatient.getOrDefault(patientId, 0);
	}

	public int getTaskCountForProblem(Long problemId) {
		if (problemId == null)
			return 0;
		return taskCountByProblem.getOrDefault(problemId, 0);
	}

	public int getTaskCountForCategory(Long categoryId) {
	    if (categoryId == null) return 0;
	    return taskCountByCategory.getOrDefault(categoryId, 0);
	}

	public void reloadTaskCounts() {
		try {
			String token = util.obtenerToken();
			Map<Long, Integer> response = null;

			patientBean.setSelectedPatient(null);
			problemBean.setSelectedProblem(null);
			categoryBean.setSelectedCategory(null);

			// 🔹 Endpoint recomendado (simple y rápido)
			response = util.getDataFromService("/api/tasks/count-by-patient", new TypeReference<Map<Long, Integer>>() {
			}, token);

			taskCountByPatient.clear();
			if (response != null) {
				taskCountByPatient.putAll(response);
			}

			// 🔹 Endpoint recomendado (simple y rápido)
			response = util.getDataFromService("/api/tasks/count-by-problem", new TypeReference<Map<Long, Integer>>() {
			}, token);

			taskCountByProblem.clear();
			if (response != null) {
				taskCountByProblem.putAll(response);
			}

			// --- NUEVO: Conteo por Categoría ---
	        response = util.getDataFromService("/api/tasks/count-by-category", new TypeReference<Map<Long, Integer>>() {}, token);
	        taskCountByCategory.clear();
	        if (response != null) {
	            taskCountByCategory.putAll(response);
	        }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void syncAfterTaskChange() {
		reloadTaskCounts();
		PrimeFaces.current().ajax().update("frm:patientList");
		PrimeFaces.current().ajax().update("frm:problemList");
	}

	// Filtrado cruzado: Paciente → Problemas
	public void onPatientSelectChange(PatientDTO selectedPatient) {
		if (selectedPatient == null) {
			// All Patients selected
			filterByPatientAndProblem(null, null);
			// Mostrar todos los problemas
			PrimeFaces.current().ajax().update("frm:problemList");
			return;
		}

		filterByPatientAndProblem(selectedPatient.getId(), null);

		// Filtramos problemas que tiene este paciente
		PrimeFaces.current().ajax().update("frm:problemList");
	}

	public void onCategorySelectChange(CategoryDTO selectedCategory) {
	    if (selectedCategory == null) {
	        // Si seleccionas "All" o deseleccionas, quitamos el filtro
	        removeCustomFilter("categoryId");
	    } else {
	        // Agregamos el ID al mapa que usa el lazyModel
	        addCustomFilter("categoryId", selectedCategory.getId().toString());
	    }
	    // No es necesario llamar a initBean si solo quieres filtrar, 
	    // pero si lo usas para resetear otros valores, está bien.
	}

	// Filtrado cruzado: Problema → Pacientes
	public void onProblemSelectChange(ProblemDTO selectedProblem) {
	    if (selectedProblem == null) {
	        // All Problems selected
	        filterByPatientAndProblem(null, null);
	        // Mostrar todos los pacientes
	        PrimeFaces.current().ajax().update("frm:patientList");
	        return;
	    }

	    filterByPatientAndProblem(null, selectedProblem.getId());

	    // Refrescamos pacientes según este problema
	    PrimeFaces.current().ajax().update("frm:patientList");
	}

	// Total Tasks
	public long getTotalTaskCountByPatient() {
		return taskCountByPatient.values().stream().mapToLong(Integer::longValue).sum();
	}

	// Total Problems
	public long getTotalTaskCountByProblem() {
		return taskCountByProblem.values().stream().mapToLong(Integer::longValue).sum();
	}

	// 2. Patrón uniforme para los listeners (Repite este esquema para los 3)
	public void onFilterChange() {

	    // 1. Detectar si el clic viene del botón de borrar paciente
	    String source = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("javax.faces.source");
	    if (source != null && source.contains("btnRemovePatientChip")) {
	        patientBean.setSelectedPatient(null);
	    }

	    // 2. Sincronización (Tu lógica actual)
	    if (categoryBean.getSelectedCategory() != null) {
	        addCustomFilter("categoryId", categoryBean.getSelectedCategory().getId().toString());
	    } else {
	        getCustomFilters().remove("categoryId");
	    }

	    if (problemBean.getSelectedProblem() != null) {
	        addCustomFilter("problemId", problemBean.getSelectedProblem().getId().toString());
	    } else {
	        getCustomFilters().remove("problemId");
	    }

	    // ⭐ Aquí es donde Bob se quedaba pegado, ahora la intercepción de arriba lo habrá limpiado
	    if (patientBean.getSelectedPatient() != null) {
	        addCustomFilter("patientId", patientBean.getSelectedPatient().getId().toString());
	    } else {
	        getCustomFilters().remove("patientId");
	    }

	    // 2. Forzar recarga de conteos
	    //reloadDynamicCounts();
	    
	    // 3. Si el mapa está vacío, resetear el estado visual de la tabla
	    if (getCustomFilters().isEmpty()) {
	        PrimeFaces.current().executeScript("PF('tableEntity').clearFilters();");
	    }

	    // 4. ACTUALIZACIÓN DE UI (Clave para que Bob se vaya de la pantalla)
	    PrimeFaces.current().ajax().update(
	        "frm:activeFilters", 
	        "frm:data", 
	        "frm:patientList", 
	        "frm:problemList", 
	        "frmSidebar"
	    );
	}

	public void reloadDynamicCounts() {
	    try {
	        String token = util.obtenerToken();
	        Map<String, String> stringFilters = getFormattedFilters(); // 👈 Uso de la función formateadora

	        // Construcción de URLs y llamadas al servicio
	        String catUrl = buildParamsUrl("/api/tasks/counts-dynamic/category", stringFilters);
	        String proUrl = buildParamsUrl("/api/tasks/counts-dynamic/problem", stringFilters);
	        String patUrl = buildParamsUrl("/api/tasks/counts-dynamic/patient", stringFilters);

	        this.taskCountByCategory = util.getDataFromService(catUrl, new TypeReference<Map<Long, Integer>>(){}, token);
	        this.taskCountByProblem = util.getDataFromService(proUrl, new TypeReference<Map<Long, Integer>>(){}, token);
	        this.taskCountByPatient = util.getDataFromService(patUrl, new TypeReference<Map<Long, Integer>>(){}, token);
	        
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	private String buildParamsUrl(String baseEndpoint, Map<String, String> filters) {
	    if (filters == null || filters.isEmpty()) {
	        return baseEndpoint;
	    }
	    StringBuilder sb = new StringBuilder(baseEndpoint);
	    sb.append("?");
	    filters.forEach((k, v) -> {
	        if (v != null) {
	            sb.append(k).append("=").append(v).append("&");
	        }
	    });
	    // Eliminar el último '&' o '?'
	    return sb.substring(0, sb.length() - 1);
	}
	/**
	 * Añade un filtro al mapa asegurando la conversión a String.
	 */
	// Función auxiliar para simplificar la lógica
	/**
	 * Esta es la función que reemplaza a updateFilterState.
	 * Se encarga de convertir el ID a String para que sea compatible con el API.
	 */
	public void addStringFilter(String key, Object value) {
	    if (value != null) {
	        addCustomFilter(key, value.toString());
	    } else {
	        removeCustomFilter(key);
	    }
	}

	/**
	 * Obtiene una copia de los filtros actuales convertidos a String para el API.
	 */
	// Esta es la función que el IDE decía que no se usaba. Ahora está conectada arriba.
	/**
	 * Prepara los filtros para ser enviados como parámetros de URL.
	 */
	private Map<String, String> getFormattedFilters() {
	    Map<String, Object> filters = getCustomFilters();
	    if (filters == null) return new HashMap<>();
	    
	    return filters.entrySet().stream()
	            .filter(e -> e.getValue() != null)
	            .collect(Collectors.toMap(
	                Map.Entry::getKey, 
	                e -> e.getValue().toString()
	            ));
	}
	// Nueva función para asegurar la limpieza antes de recalcular
	public void removePatientFilter() {
	    System.out.println("   [!] Ejecutando removePatientFilter() explícito");
	    this.patientBean.setSelectedPatient(null);
	    onFilterChange(); // Llamamos al recálculo después de asegurar el null
	}

	// Repetir para los otros si es necesario
	public void removeProblemFilter() {
	    this.problemBean.setSelectedProblem(null);
	    onFilterChange();
	}

	public void removeCategoryFilter() {
	    this.categoryBean.setSelectedCategory(null);
	    onFilterChange();
	}
}