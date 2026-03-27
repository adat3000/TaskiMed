package com.taskimed.dto;

import lombok.Data;
import java.util.Date;

@Data
public class TaskRequestDTO {
    private Long id;
    private String description;
    private String status;
    private Long patientId;
    private Long assignedToId;
    private Long createdById;
    private Date dueDate;
    private Long categoryId;
    private Long problemId;
    private Long teamId;
}
