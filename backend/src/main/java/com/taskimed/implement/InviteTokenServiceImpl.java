package com.taskimed.implement;

import com.taskimed.entity.InviteToken;
import com.taskimed.repository.InviteTokenRepository;
import com.taskimed.service.InviteTokenService;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class InviteTokenServiceImpl implements InviteTokenService {

    private final InviteTokenRepository inviteTokenRepository;

    public InviteTokenServiceImpl(InviteTokenRepository inviteTokenRepository) {
        this.inviteTokenRepository = inviteTokenRepository;
    }

    @Override
    public String generateToken(String email) {
        String token = UUID.randomUUID().toString();
        InviteToken inviteToken = new InviteToken();
        inviteToken.setEmail(email);
        inviteToken.setToken(token);
        inviteToken.setCreatedAt(LocalDateTime.now());
        inviteToken.setUsed(false);
        inviteTokenRepository.save(inviteToken);
        return token;
    }

    @Override
    public String validateToken(String token) {
        InviteToken inviteToken = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token inválido o no encontrado"));

        if (inviteToken.isUsed()) {
            throw new RuntimeException("El token ya fue usado");
        }

        if (inviteToken.getCreatedAt().isBefore(LocalDateTime.now().minusDays(7))) {
            throw new RuntimeException("El token ha expirado");
        }

        return inviteToken.getEmail();
    }

    @Override
    public void validateTokenByEmail(String email) {
        Optional<InviteToken> tokenOpt = inviteTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);

        if (tokenOpt.isEmpty()) {
            throw new RuntimeException("No se encontró una invitación activa para este correo");
        }

        InviteToken token = tokenOpt.get();

        // Verificar expiración (3 minutos)
        if (token.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
            throw new RuntimeException("El enlace ha expirado, solicita una nueva invitación");
        }

        if (token.isUsed()) {
            throw new RuntimeException("Este enlace ya fue utilizado");
        }
    }

    @Override
    public void markTokenAsUsed(String email) {
        Optional<InviteToken> tokenOpt = inviteTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email);

        tokenOpt.ifPresent(t -> {
            t.setUsed(true);
            inviteTokenRepository.save(t);
        });
    }
}
