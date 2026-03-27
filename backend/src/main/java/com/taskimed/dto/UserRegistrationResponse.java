package com.taskimed.dto;

import com.taskimed.entity.User;

public class UserRegistrationResponse {
    private User user;
    private String tempPassword;

    public UserRegistrationResponse(User user, String tempPassword) {
        this.user = user;
        this.tempPassword = tempPassword;
    }

    public User getUser() {
        return user;
    }

    public String getTempPassword() {
        return tempPassword;
    }
}
