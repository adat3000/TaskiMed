package com.taskimed.beans;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import lombok.Data;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.taskimed.config.Util;
import com.taskimed.dto.UserDTO;

@Data
@Named
@SessionScoped
public class LoginBean implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username, password;
    private UserDTO user;
    private Util util = new Util();

    public String login() {
        String errorTitle = "Error";
        String errorDescription = "Could not be processed";
        String warningTitle = "Warning";
        String warningDescription = "Incorrect username or password";
        String redirection = null;

        try {
            // Crear el mapa con las credenciales
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("username", username);
            requestBody.put("password", password);

            // Llamar al backend unificado: devuelve token + user
            Map<String, Object> response = util.postDataToService("/api/users/login", requestBody, new TypeReference<Map<String, Object>>() {});
            // 2. Verificar si la respuesta contiene un error explícito del backend
            if (response != null && response.containsKey("error")) {
                String serverError = (String) response.get("error");
                FacesContext.getCurrentInstance().addMessage("frmLogin:loginMessages",
                    new FacesMessage(FacesMessage.SEVERITY_WARN, "Attention", serverError));
                return null;
            }
            if (response != null && response.get("token") != null) {
                String token = (String) response.get("token");

                // Guardar token en la sesión
                FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put("token", token);

                // Extraer datos de usuario del response
                @SuppressWarnings("unchecked")
                Map<String, Object> userMap = (Map<String, Object>) response.get("user");
                if (userMap != null) {
                    user = new UserDTO();
                    if (userMap.get("id") != null) {
                        user.setId(((Number) userMap.get("id")).longValue());
                    }
                    user.setUsername((String) userMap.get("username"));
                    String usernameForFetch = user.getUsername();
                    if (usernameForFetch != null && !usernameForFetch.isEmpty()) {
                        String url = "/api/users/" + usernameForFetch;
                        UserDTO fullUser = util.getDataFromService(url, new TypeReference<UserDTO>() {}, token);
                        if (fullUser != null) {
                            user = fullUser; // reemplazar por el usuario completo
                        }
                    }

                    // Guardar usuario en sesión
                    FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put("user", user);
                    FacesContext.getCurrentInstance().getExternalContext().getSessionMap().put("username", user.getUsername());

                    redirection = "index?faces-redirect=true";
                } else {
                    FacesContext.getCurrentInstance().addMessage("frmLogin:loginMessages",
                        new FacesMessage(FacesMessage.SEVERITY_WARN, warningTitle, "No user data found"));
                }
            } else {
                FacesContext.getCurrentInstance().addMessage("frmLogin:loginMessages",
                    new FacesMessage(FacesMessage.SEVERITY_WARN, warningTitle, warningDescription));
            }
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage("frmLogin:loginMessages",
                    new FacesMessage(FacesMessage.SEVERITY_WARN, warningTitle, warningDescription));
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage("frmLogin:loginMessages",
                new FacesMessage(FacesMessage.SEVERITY_FATAL, errorTitle, errorDescription));
        }
        return redirection;
    }

    public void validateSession() {
        FacesContext context = FacesContext.getCurrentInstance();

        try {
            // Evitar redirección durante postback (submit de formularios)
            if (context.isPostback()) {
                return;
            }

            // Obtener la URI de la solicitud actual
            String requestURI = context.getExternalContext().getRequestServletPath();

            // Excluir páginas públicas con token de invitación
            String tokenInvitacion = context.getExternalContext()
                                          .getRequestParameterMap()
                                          .get("token");

            boolean esProfileInvitacion = "/profile.jsf".equals(requestURI) 
                                           && tokenInvitacion != null 
                                           && !tokenInvitacion.isEmpty();

            // Aquí se pueden agregar más páginas públicas si es necesario
            if (esProfileInvitacion) {
                return; // Permitir acceso sin estar logueado
            }

            // Obtener token de sesión
            String token = (String) context.getExternalContext().getSessionMap().get("token");

            // Si no hay token, redirigir al login
            if (token == null || token.isEmpty()) {
                context.getExternalContext().redirect(context.getExternalContext().getRequestContextPath());
                context.responseComplete();
                return;
            }

            // Validar token directamente
            boolean esValido = util.verificarToken(token);
            if (!esValido) {
                context.getExternalContext().redirect(context.getExternalContext().getRequestContextPath());
                context.responseComplete();
            }

        } catch (Exception e) {
            e.printStackTrace();
            // En caso de error inesperado, también redirigir al login
            try {
                context.getExternalContext().redirect(context.getExternalContext().getRequestContextPath());
                context.responseComplete();
            } catch (IOException ioEx) {
                ioEx.printStackTrace();
            }
        }
    }

    public void logout() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            context.getExternalContext().invalidateSession();
            context.getExternalContext().getSessionMap().clear();
            context.getExternalContext().redirect(context.getExternalContext().getRequestContextPath() + "/");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isAdmin() {
        return user != null && user.getRoleName().equals("ADMIN");
    }
}