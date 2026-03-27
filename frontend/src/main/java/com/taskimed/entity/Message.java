package com.taskimed.entity;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Message {
    private Long id;
    private Long senderId;
    private String subject;
    private String body;
    private Date createdAt;
    private Date updatedAt;

    // Lista de IDs de destinatarios
    private List<Long> recipientIds;

    @JsonIgnore
    public String getFormattedCreatedAt() {
        if (createdAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy HH:mm").format(createdAt);
    }

    @JsonIgnore
    public String getFormattedUpdatedAt() {
        if (updatedAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy HH:mm").format(updatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message e = (Message) o;
        return this.id != null && this.id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
