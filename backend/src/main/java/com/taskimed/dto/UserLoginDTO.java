package com.taskimed.dto;

import java.io.Serializable;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class UserLoginDTO implements Serializable {

	private static final long serialVersionUID = 1L;
	private String username;
    private String password;
    private String oldPassword;
    private String newPassword;
}
