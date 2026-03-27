package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.inject.Inject;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.TaskDTO;

import lombok.Data;

@Data
@Named
@ViewScoped
public class NotificationBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Util util = new Util();
    private List<TaskDTO> allTasks;

    @Inject
    private LoginBean loginBean;

    @PostConstruct
    public void initBean() {
        try {
            if (loginBean.getUser() == null) return;
            
            Long userId = loginBean.getUser().getId();
            String token = util.obtenerToken();
            
            String endpoint = "/api/tasks/byUser/" + userId;
            List<TaskDTO> result = util.getDataFromService(
                endpoint, 
                new TypeReference<List<TaskDTO>>() {}, 
                token
            );
            
            this.allTasks = (result != null) ? result : new ArrayList<>();
        } catch (Exception e) {
            this.allTasks = new ArrayList<>();
        }
    }
    
    public void reloadNotifications() {
    	this.allTasks = new ArrayList<>(); // Limpiamos la lista actual
        initBean(); // Reutilizamos la lógica de carga que ya tienes
    }
    
 // Método auxiliar para convertir Date a LocalDateTime
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant()
                   .atZone(ZoneId.systemDefault())
                   .toLocalDateTime();
    }

    public String getTimeRemaining(Date dueDate) {
        if (dueDate == null) return "";

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = convertToLocalDateTime(dueDate);
        
        Duration duration = Duration.between(now, target);

        if (duration.isNegative() || duration.isZero()) {
            return "Expired";
        }

        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");

        return sb.toString();
    }

    public List<TaskDTO> getUrgentTasks() {
        if (allTasks == null) {
            return new ArrayList<>();
        }

        // Filtramos en una nueva lista para no perder los datos originales de allTasks
        return allTasks.stream()
            .filter(t -> t.getDueDate() != null)
            .filter(t -> {
                String status = t.getStatus();
                // SOLO filtramos para excluir COMPLETED y CANCELLED
                return !"COMPLETED".equals(status) && !"CANCELLED".equals(status);
            })
            .collect(Collectors.toList());
    }
}
