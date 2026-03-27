package com.taskimed.controller;

import com.taskimed.dto.UserDTO;
import com.taskimed.entity.InviteToken;
import com.taskimed.entity.User;
import com.taskimed.repository.InviteTokenRepository;
import com.taskimed.repository.UserRepository;
import com.taskimed.service.InviteTokenService;
import com.taskimed.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserInviteController {

	@Autowired
	private InviteTokenRepository inviteTokenRepository;

	@Autowired
    private InviteTokenService inviteTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    /**
     * Valida un token de invitación (UUID) y retorna los datos del empleado asociado.
     */
    @GetMapping("/validate-invite")
    public ResponseEntity<?> validateInvite(@RequestParam String token) {
        try {
            String email = inviteTokenService.validateToken(token);
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se encontró un usuario con ese correo."));
            }

            Optional<InviteToken> optionalInvite = inviteTokenRepository.findByToken(token);
            if (optionalInvite.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Token de invitación inválido o inexistente."));
            }

            InviteToken invite = optionalInvite.get();

            if (invite.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
                return ResponseEntity.badRequest().body(Map.of("error", "El enlace de invitación ha expirado."));
            }

            // ✅ Mapear User → UserDTO
            User user = userOpt.get();
            UserDTO dto = UserDTO.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .fullName(user.getFirstName() + " " + user.getLastName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .jobPosition(user.getJobPosition())
                    .entryDate(user.getEntryDate())
                    .active(user.getActive())
                    .roleId(user.getRole() != null ? user.getRole().getId() : null)
                    .roleName(user.getRole() != null ? user.getRole().getName() : null)
                    .build();

            return ResponseEntity.ok(Map.of(
                    "message", "Token válido.",
                    "user", dto
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Registra un nuevo usuario a partir de una invitación válida.
     */
    @PostMapping("/register-invite")
    public ResponseEntity<?> registerFromInvite(@RequestBody Map<String, Object> payload) {
        try {
            String token = (String) payload.get("token");
            String username = (String) payload.get("username");
            String password = (String) payload.get("password");
            Long entryDateMillis = ((Number) payload.get("entryDate")).longValue();
            Date entryDate = new Date(entryDateMillis);

            if (token == null || token.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Token de invitación faltante."));
            }

            // 1️⃣ Buscar token en base de datos
            Optional<InviteToken> optionalInvite = inviteTokenRepository.findByToken(token);
            if (optionalInvite.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Token de invitación inválido o inexistente."));
            }

            InviteToken invite = optionalInvite.get();

            // 2️⃣ Verificar expiración del token (más de 3 minutos)
            if (invite.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {
                return ResponseEntity.badRequest().body(Map.of("error", "El enlace de invitación ha expirado."));
            }

            // 3️⃣ Verificar si el token ya fue usado
            if (invite.isUsed()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El enlace ya fue utilizado."));
            }


            // 5️⃣ Validar duplicidad de usuario
            if (userRepository.findByUsername(username).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El usuario ya existe."));
            }

            // 6️⃣ Crear usuario sin cifrar la contraseña aquí
            User user = userRepository.findByEmail(username).orElse(null);
            user.setUsername(username);
            user.setPassword(password); // ⚠️ El cifrado se hace en UserServiceImpl
            user.setEntryDate(entryDate);

            // 7️⃣ Guardar usuario (el servicio aplicará el encode)
            User savedUser = userService.registerUserFromInvite(user);

            // 8️⃣ Marcar token como usado
            invite.setUsed(true);
            inviteTokenRepository.save(invite);

            return ResponseEntity.ok(Map.of(
                    "message", "User created successfully.",
                    "user", savedUser
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
