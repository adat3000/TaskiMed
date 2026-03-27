package com.taskimed.entity;

import lombok.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Patient {
    private Long id;  // Added to match the database

    private String mrn;
    private String firstName;
    private String lastName;
    private Date dateOfBirth;
    
    private String gender;
    private String phoneNumber;
    private String email;
    private String address;
    private Date createdAt;
    private Date updatedAt;

    private Set<Problem> problems = new HashSet<>();
    
    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedDateOfBirth() {
        if (dateOfBirth == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(dateOfBirth);
    }

    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedCreatedAt() {
        if (createdAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(createdAt);
    }

    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedUpdatedAt() {
        if (updatedAt == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(updatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Patient)) return false;
        Patient e = (Patient) o;
        return this.id != null && this.id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}