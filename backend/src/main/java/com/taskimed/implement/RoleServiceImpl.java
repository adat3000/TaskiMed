package com.taskimed.implement;

import com.taskimed.entity.Role;
import com.taskimed.repository.RoleRepository;
import com.taskimed.service.RoleService;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    public RoleServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<Role> getRoles() {
        return roleRepository.findAll();
    }

    @Override
    public Role getRoleById(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new RuntimeException("Role not found"));
    }

    @Override
    public Role saveRole(Role role) {
        return roleRepository.save(role);
    }
}
