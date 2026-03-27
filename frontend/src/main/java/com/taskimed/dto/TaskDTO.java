package com.taskimed.dto;

import lombok.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaskDTO {

    private Long id;
    private String description;
    private String status;

    // 🔹 Estos nombres solo sirven para mostrar en frontend, no deben enviarse al backend
    private String patientName;

    private Long patientId;

    private String assignedToName;
    private String createdByName;
    private String roleName;

    private Long assignedToId;
    private Long createdById;
    private Long categoryId;
    private Long problemId;
    private Long teamId;
    
    private Boolean assignedToActive;
    private Boolean createdByActive;

    private Date dueDate;
    private Date createdAt;

    private String categoryAlias;
    private String categoryName;

    private String problemAlias;
    private String problemName;

    private String teamAlias;
    private String teamName;

    @JsonIgnore
    public String getFormattedDueDate() {
        if (dueDate == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(dueDate);
    }

    @JsonIgnore
    public String getFormattedCreatedAt() {
        if (createdAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(createdAt);
    }
}
