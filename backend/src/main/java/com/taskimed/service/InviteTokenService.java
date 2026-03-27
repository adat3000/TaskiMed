package com.taskimed.service;

public interface InviteTokenService {
    String generateToken(String email);
    String validateToken(String token);
    void validateTokenByEmail(String email);
    void markTokenAsUsed(String email);
}
