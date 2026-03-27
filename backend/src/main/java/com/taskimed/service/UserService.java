package com.taskimed.service;

import com.taskimed.dto.UserDTO;
import com.taskimed.dto.UserRegistrationResponse;
import com.taskimed.entity.User;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

public interface UserService {
	List<UserDTO> getUsers();
    UserDTO saveUser(UserDTO dto);
    User getUserById(Long id);
    User getUserByUsername(String username);
    void deleteUser(Long id);
    boolean changePassword(String username, String oldPassword, String newPassword);
    Page<UserDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);
    
    UserDTO convertToDTO(User user);
    
    UserRegistrationResponse linkUser(User user, Long userId);
    User registerUserFromInvite(User user);
    List<UserDTO> getAvailableUsers();
}
