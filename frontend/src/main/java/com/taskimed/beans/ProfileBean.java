package com.taskimed.beans;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.primefaces.PrimeFaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskimed.config.Util;
import com.taskimed.dto.UserDTO;

import lombok.Data;

@Data
@Named
@ViewScoped
public class ProfileBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Util util = new Util();
    private UserDTO user;
    private String token;
    private String username;

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;
    private boolean modoInvitacion;

    // ============================================================
    // Profile initialization or invitation mode
    // ============================================================
    @PostConstruct
    public void init() {
        FacesContext context = FacesContext.getCurrentInstance();
        var params = context.getExternalContext().getRequestParameterMap();
        var session = context.getExternalContext().getSession(false);

        try {
            if (params.containsKey("token")) {
                token = params.get("token");
                System.out.println("Case 1: token = " + token);

                modoInvitacion = true;
                String url = "/api/users/validate-invite?token=" + token;
                Map<String, Object> response = util.getDataFromService(
                    url, new TypeReference<Map<String, Object>>() {}, null);

                System.out.println("Validation response: " + response);

                // If the backend returns an "error" field, show message and stop execution
                if (response != null && response.containsKey("error")) {
                    String errorMessage = response.get("error").toString();
                    System.out.println("Backend error: " + errorMessage);

                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", errorMessage));

                    modoInvitacion = false;
                    PrimeFaces.current().ajax().update("growl");
                    return;
                }

                // Normal validation of "valid" message
                if (response != null 
                        && response.get("message") != null 
                        && response.get("message").toString().toLowerCase().contains("válido")) {

                    System.out.println("Valid invitation.");

                    try {
                        Object userObj = response.get("user");
                        ObjectMapper mapper = new ObjectMapper();
                        user = mapper.convertValue(userObj, UserDTO.class);
                        user.setUsername(user.getEmail()); // Default: email as username
                        user.setEntryDate(new Date()); // Default: today as entryDate
                        System.out.println("User successfully loaded: " + user.getFirstName());

                    } catch (Exception e) {
                        System.out.println("Error assigning user data: " + e.getMessage());
                        e.printStackTrace();
                    }

                } else {
                    System.out.println("Invalid or expired invitation.");
                    modoInvitacion = false;
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_WARN,
                            "Invalid or expired invitation.",
                            "The link is no longer valid or has already been used."));
                }

            } else if (session != null
                    && context.getExternalContext().getSessionMap().get("username") != null) {

                username = (String) context.getExternalContext().getSessionMap().get("username");
                token = (String) context.getExternalContext().getSessionMap().get("token");
                modoInvitacion = false;
                cargarProfile();

            } else {
                System.out.println("Case 3: No token or session. Redirecting to login...");
                modoInvitacion = false;
                String contextPath = context.getExternalContext().getRequestContextPath();
                if (contextPath == null || contextPath.isEmpty()) {
                    contextPath = "";
                }
                context.getExternalContext().redirect(contextPath + "/index.xhtml");
            }

        } catch (Exception e) {
            System.out.println("Error in init(): " + e.getMessage());
            e.printStackTrace();
            modoInvitacion = false;
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL,
                    "Error", "An error occurred while loading the profile."));
            PrimeFaces.current().ajax().update("growl");
        }
    }

    // ============================================================
    // Load authenticated user's profile
    // ============================================================
    public void cargarProfile() {
        if (user != null) return;
        if (username != null) {
            try {
                String url = "/api/users/" + username;
                user = util.getDataFromService(url, new TypeReference<UserDTO>() {}, token);
            } catch (Exception e) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_FATAL,
                        "Error", "Unable to load profile."));
            }
        }
    }

    // ============================================================
    // Save profile changes or registration via invitation
    // ============================================================
    public void guardar() {
        FacesContext context = FacesContext.getCurrentInstance();
        String ctx = context.getExternalContext().getRequestContextPath();
        try {
            if (modoInvitacion) {
                // Registration of a new user from an invitation
                Map<String, Object> registro = new HashMap<>();
                registro.put("token", token);
                registro.put("username", user.getUsername());
                registro.put("entryDate", user.getEntryDate());
                registro.put("password", newPassword);

                Map<String, Object> response = util.postDataToService(
                    "/api/users/register-invite",
                    registro,
                    new TypeReference<Map<String, Object>>() {},
                    null
                );

                if (response != null && response.containsKey("message")) {
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", response.get("message").toString()));
                } else if (response != null && response.containsKey("error")) {
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", response.get("error").toString()));
                }

                // Redirigir a /login.jsf o raíz
                context.getExternalContext().redirect(ctx + "/");
                return;

            } else {
                // Normal profile update
                UserDTO updatedUser = util.postDataToService(
                    "/api/users",
                    user,
                    new TypeReference<UserDTO>() {},
                    token
                );

                if (updatedUser != null) {
                    user = updatedUser;
                    FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", "User data updated successfully."));
                    PrimeFaces.current().ajax().update("formProfile");

                    context.getExternalContext().redirect(ctx + "/index.jsf");
                    return;
                }
            }

        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "An error occurred while saving the data."));
        }
    }

    // ============================================================
    // Password change
    // ============================================================
    public void cambiarPassword() {
        try {
            if (newPassword == null || !newPassword.equals(confirmPassword)) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "Passwords do not match."));
                return;
            }

            Map<String, Object> cambio = new HashMap<>();
            cambio.put("username", user.getUsername());
            cambio.put("oldPassword", currentPassword);
            cambio.put("newPassword", newPassword);

            Map<String, Object> response = util.putDataToService("/api/users/change-password", cambio, token);

            if (response != null && response.containsKey("message")) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Success", response.get("message").toString()));
                currentPassword = null;
                newPassword = null;
                confirmPassword = null;
                PrimeFaces.current().ajax().update("formProfile");
            } else if (response != null && response.containsKey("error")) {
                FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", response.get("error").toString()));
            }
        } catch (Exception e) {
            FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_FATAL, "Error", "Unable to change password."));
        }
    }

    // ============================================================
    // Cancel editing
    // ============================================================
    public String cancelar() {
        return "index?faces-redirect=true";
    }
}