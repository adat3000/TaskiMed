package com.taskimed.dto;

import lombok.Data;

@Data
public class InviteResponse {
    private String inviteUrl;
    private String expiresIn;
}
