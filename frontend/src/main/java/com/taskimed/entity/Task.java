package com.taskimed.entity;

import lombok.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Task {
    private Long id;
    private String description;
    private Status status = Status.NEW;
    @JsonIgnore
    private Patient patient;
    private Date dueDate;
    private Date createdAt = new Date();
    private User assignedTo;
    private User createdBy;

    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedDueDate() {
        if (dueDate == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(dueDate);
    }

    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedCreatedAt() {
        if (createdAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task)) return false;
        Task e = (Task) o;
        return this.id != null && this.id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    // Enum para el estado de la tarea
    public enum Status {
        NEW,
        IN_PROGRESS,
        CANCELLED,
        COMPLETED
    }
}