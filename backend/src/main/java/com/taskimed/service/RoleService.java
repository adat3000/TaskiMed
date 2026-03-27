package com.taskimed.service;

import java.util.List;

import com.taskimed.entity.Role;

public interface RoleService {
    List<Role> getRoles();
    Role getRoleById(Long id);
    Role saveRole(Role role);
}
