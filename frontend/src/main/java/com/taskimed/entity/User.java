package com.taskimed.entity;

import lombok.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class User {
    private Long id;  // Added to match the database
    private String username;
    private String password;
    private Role role;  // Changed from int role to List<Role> to reflect the ManyToMany relationship
    
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String jobPosition;
    private Date entryDate;
    private Boolean active;
    private Long teamId;

    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedEntryDate() {
        if (entryDate == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(entryDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User e = (User) o;
        return this.id != null && this.id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
