package com.taskimed.beans;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.entity.Role;

@Named
@SessionScoped
public class RoleBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Util util = new Util();
    private List<Role> roles;

    @PostConstruct
    public void init() {
        try {
            // Si en tus beans tienes un 'token' disponible, úsalo aquí en vez de util.obtenerToken()
            String token = util.obtenerToken();
            roles = util.getDataFromService("/api/roles", new TypeReference<List<Role>>() {}, token);
        } catch (Exception e) {
            roles = List.of();
        }
    }

    public List<Role> getRoles() {
        return roles;
    }
}
