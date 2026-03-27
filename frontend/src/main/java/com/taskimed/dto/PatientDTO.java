package com.taskimed.dto;

import lombok.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PatientDTO {
    private Long id;

    private String mrn;
    private String firstName;
    private String lastName;
    private String fullName;
    private Date dateOfBirth;
    
    private String gender;
    private String phoneNumber;
    private String email;
    private String address;
    private Date createdAt;
    private Date updatedAt;
    
    private Set<ProblemDTO> problems;

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
        if (!(o instanceof PatientDTO)) return false;
        PatientDTO that = (PatientDTO) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
    	return id != null ? id.hashCode() : 0;
    }
}
