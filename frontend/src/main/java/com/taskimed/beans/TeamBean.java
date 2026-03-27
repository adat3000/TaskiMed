package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.inject.Inject;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.primefaces.PrimeFaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.TeamDTO;
import com.taskimed.dto.UserDTO;

/**
 * Bean responsible for managing teams (Team entity) in the JSF interface.
 * Includes CRUD methods connected to the backend through the REST service.
 */
@Data
@Named
@ViewScoped
@EqualsAndHashCode(callSuper = false)
public class TeamBean extends EntityLazyBean<TeamDTO> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Util util = new Util();

    private TeamDTO selectedTeam = new TeamDTO();
    private List<TeamDTO> teams;
    private String action;
    
    private List<UserDTO> allUsers;

    @Inject
    private LoginBean loginBean;
    
    @Inject
    private TaskBean taskBean;
    
    @Override
    public String getEndpoint() {
        return "/api/teams/paginated";
    }

    @Override
    public Class<TeamDTO> getEntityClass() {
        return TeamDTO.class;
    }

    @PostConstruct
    public void initBean() {
        super.init();
        try {
            reloadTeams();
            reloadUsers();
        } catch (Exception e) {
            e.printStackTrace();
            teams = List.of();
            allUsers = new ArrayList<>();
        }
    }

    public void reloadUsers() {
        try {
            String token = util.obtenerToken();
            this.allUsers = util.getDataFromService("/api/users", new TypeReference<List<UserDTO>>() {}, token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reloadTeams() {
        try {
            String token = util.obtenerToken();
            teams = util.getDataFromService("/api/teams", new TypeReference<List<TeamDTO>>() {}, token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Retorna la lista de usuarios que pertenecen a un equipo específico.
     * Utilizado para el OverlayPanel en la vista.
     */
    public List<UserDTO> getMembersByTeam(Long teamId) {
        if (allUsers == null || teamId == null) return new ArrayList<>();
        return allUsers.stream()
                .filter(u -> teamId.equals(u.getTeamId()))
                .collect(Collectors.toList());
    }

    public List<UserDTO> getAllUsersFiltered() {
        if (allUsers == null) return new ArrayList<>();
        
        return allUsers.stream()
            .filter(u -> u.getTeamId() == null || 
                    (selectedTeam != null && selectedTeam.getId() != null && selectedTeam.getId().equals(u.getTeamId())))
            .collect(Collectors.toList());
    }

    public List<TeamDTO> getTeams() {
        return teams;
    }
    
    public void ejecutar() {
        if ("Create".equals(action)) {
            create();
            this.teams = null; 
            reloadTeams();
        }
        else if ("Update".equals(action))
            update();
    }

    /**
     * Centralized execution method for database operations.
     * Logic added to handle task de-assignment when teams or memberships change.
     */
    public void ejecutar(String accion) throws Exception {
        String token = util.obtenerToken();
        
        if ("eliminar".equals(accion)) {
            // 1. Antes de eliminar el equipo, desasignar tareas de sus miembros
            handleTaskCleanupForTeamMembers(selectedTeam.getId(), null, token);
            
            // 2. Proceder con la eliminación del equipo
            util.deleteDataFromService("/api/teams/", selectedTeam.getId(), token);
            
        } else if ("crear".equals(accion) || "actualizar".equals(accion)) {
            
            // Si es actualización, identificar usuarios que salen del equipo
            if ("actualizar".equals(accion) && selectedTeam.getId() != null) {
                handleUserMovementCleanup(token);
            }

            TeamDTO team = util.postDataToService(
                "/api/teams",
                selectedTeam,
                new TypeReference<TeamDTO>() {},
                token
            );
            selectedTeam.setId(team.getId());
        }
        reloadUsers();
        // 2. Notificamos al TaskBean que la estructura de miembros cambió
        if (taskBean != null) {
            // Llamamos a la función de recarga del TaskBean (la añadiremos abajo)
            taskBean.reloadUserLists(); 
            FacesContext.getCurrentInstance().addMessage(null, 
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Sync", "Task assignments updated."));        }
    }

    /**
     * Logic to handle "Assigned To = NULL" when a user is moved to another team 
     * or removed from the current team.
     */
    private void handleUserMovementCleanup(String token) {
        try {
            // Obtenemos el equipo de la DB
            TeamDTO currentInDb = util.getDataFromService("/api/teams/" + selectedTeam.getId(), 
                                    new TypeReference<TeamDTO>() {}, token);
            
            // Verificamos que traiga miembros para evitar el proxy error
            if (currentInDb != null && currentInDb.getUserIds() != null) {
                List<Long> oldMembers = currentInDb.getUserIds();
                List<Long> newMembers = (selectedTeam.getUserIds() != null) 
                                        ? selectedTeam.getUserIds() 
                                        : new ArrayList<>();

                // Identificar quiénes salieron del equipo
                List<Long> removedUsers = oldMembers.stream()
                        .filter(id -> !newMembers.contains(id))
                        .collect(Collectors.toList());

                for (Long userId : removedUsers) {
                    handleTaskCleanupForTeamMembers(selectedTeam.getId(), userId, token);
                }
            }
        } catch (Exception e) {
            // Logueamos el error pero no bloqueamos la actualización del equipo
            System.err.println("Note: Task cleanup skipped due to: " + e.getMessage());
        }
    }

    /**
     * Service call to clean up tasks. 
     * This calls a specific endpoint or uses a generic update to set assigned_to = null.
     */
    private void handleTaskCleanupForTeamMembers(Long teamId, Long userId, String token) {
        try {
            // Construimos la URL de limpieza. 
            // Si userId es null, limpia para TODOS los miembros del equipo (caso eliminación).
            String cleanupUrl = "/api/tasks/cleanup-assignment?teamId=" + teamId;
            if (userId != null) {
                cleanupUrl += "&userId=" + userId;
            }
            
            // Llamada al servicio para ejecutar el UPDATE tasks SET assigned_to = NULL...
            util.executeAction(cleanupUrl, null, token);
        } catch (Exception e) {
        	// Ignoramos el error de Jackson si la respuesta es exitosa (200 OK) pero vacía
            if (!e.getMessage().contains("No content to map")) {
                e.printStackTrace();
            }
        }
    }

    public void clear() {
        selectedTeam = new TeamDTO();
        action = null;
    }

    private void create() {
        try {
            ejecutar("crear");
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Success", "Team created successfully."));
            PrimeFaces.current().ajax().update("frm:data", "messageBox");
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "The team could not be created."));
            e.printStackTrace();
        }
    }

    private void update() {
        try {
            ejecutar("actualizar");
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Success", "Team updated successfully."));
            PrimeFaces.current().ajax().update("frm:data", "messageBox");
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "The team could not be updated."));
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            ejecutar("eliminar");
            if (util.getStatus() == 403) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN,
                        "Warning", "The team cannot be deleted."));
                return;
            }
            if (util.getStatus() < 400) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO,
                        "Success", "Team deleted successfully."));
                adjustPaginatorAfterDeletion("tableEntity", lazyModel);
            }
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "There was an error deleting the team."));
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
 // --- NUEVAS FUNCIONES PARA REUTILIZAR LÓGICA EXISTENTE ---

    /**
     * Reutiliza la lista allUsers para devolver los objetos UserDTO 
     * seleccionados actualmente en el equipo.
     */
    public List<UserDTO> getSelectedUsers() {
        if (selectedTeam == null || selectedTeam.getUserIds() == null || allUsers == null) {
            return new ArrayList<>();
        }
        return allUsers.stream()
                .filter(u -> selectedTeam.getUserIds().contains(u.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Actualiza directamente la lista de IDs en el DTO a partir de 
     * los objetos seleccionados en el componente.
     */
    public void setSelectedUsers(List<UserDTO> users) {
        if (selectedTeam != null) {
            if (users == null) {
                selectedTeam.setUserIds(new ArrayList<>());
            } else {
                selectedTeam.setUserIds(users.stream()
                        .map(UserDTO::getId)
                        .collect(Collectors.toList()));
            }
        }
    }
}