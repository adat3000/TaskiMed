package com.taskimed.dto;

import lombok.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {

    private Long id;
    private String username;
    private String password;
    
    private Long roleId;
    private String roleName;
    
    private String firstName;
    private String lastName;
    private String fullName;

    private String email;
    private String phone;
    private String jobPosition;
    private Date entryDate;
    private Boolean active;

    private Long teamId;
    private String teamName;
    private String teamAlias;
    // Additional method if you need the date formatted for display
    @JsonIgnore
    public String getFormattedEntryDate() {
        if (entryDate == null) return "";
        return new SimpleDateFormat("MM/dd/yyyy").format(entryDate);
    }
}
