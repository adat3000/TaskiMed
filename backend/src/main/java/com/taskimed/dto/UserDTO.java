package com.taskimed.dto;

import lombok.*;
import java.util.Date;

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
    private String teamName; // Para mostrar en la tabla de usuarios
    private String teamAlias; // Para mostrar en la tabla de usuarios
}
