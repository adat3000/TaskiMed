package com.taskimed.controller;

import com.taskimed.config.JwtTokenUtil;
import com.taskimed.dto.UserDTO;
import com.taskimed.dto.UserLoginDTO;
import com.taskimed.dto.UserRegistrationResponse;
import com.taskimed.entity.JwtRequest;
import com.taskimed.entity.JwtResponse;
import com.taskimed.entity.User;
import com.taskimed.repository.UserRepository;
import com.taskimed.service.EmailService;
import com.taskimed.service.InviteTokenService;
import com.taskimed.service.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import jakarta.servlet.http.HttpServletRequest;

import java.util.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailService emailService; // servicio que ya usabas en UserServiceImpl

	@Autowired
	private InviteTokenService inviteTokenService;

	private final UserService userService;

	@Value("${app.base.url:http://localhost:8080}")
	private String appBaseUrl;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO dto) {
	    try {
	        UserDTO newUser = userService.saveUser(dto);
	        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
	    } catch (Exception e) {
	        return ResponseEntity.badRequest().body(null);
	    }
	}

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> userDTOs = userService.getUsers();
        return ResponseEntity.ok(userDTOs);
    }

	@GetMapping("/{param}")
	public ResponseEntity<UserDTO> getUser(@PathVariable String param) {
		User user = null;
		UserDTO dto = null;
		try {
			Long id = Long.parseLong(param);
			user = userService.getUserById(id);
			dto = userService.convertToDTO(user);
			return ResponseEntity.ok(dto);
		} catch (NumberFormatException e) {
			user = userService.getUserByUsername(param);
			dto = userService.convertToDTO(user);
			return ResponseEntity.ok(dto);
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteUser(@PathVariable Long id,
	                                    HttpServletRequest request) {

	    try {
	        // Obtener username del token JWT
	        String authHeader = request.getHeader("Authorization");
	        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body(Map.of("error", "Token inválido"));
	        }

	        String token = authHeader.substring(7);
	        String usernameFromToken = jwtTokenUtil.extractUsername(token);

	        // Obtener usuario actual desde DB
	        User loggedUser = userService.getUserByUsername(usernameFromToken);

	        // Si intenta eliminarse a sí mismo → Prohibido
	        if (loggedUser != null && loggedUser.getId().equals(id)) {
	            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	        }

	        // Seguir con la eliminación normal
	        userService.deleteUser(id);
	        return ResponseEntity.noContent().build();

	    } catch (Exception e) {
	        logger.error("Error al eliminar usuario: {}", id, e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("error", "No se pudo eliminar el usuario."));
	    }
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody UserLoginDTO loginDTO) {
	    try {
	        // 🔹 1. Autenticar directamente con Spring Security
	        // Esto hace que el AuthenticationManager use automáticamente tu PasswordEncoder (BCrypt)
	        authenticationManager.authenticate(
	            new UsernamePasswordAuthenticationToken(
	                loginDTO.getUsername(),
	                loginDTO.getPassword()
	            )
	        );

	        // 🔹 2. Si no lanza excepción, cargar el usuario y generar el token JWT
	        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginDTO.getUsername());
	        final String token = jwtTokenUtil.generateToken(userDetails);

	        // 🔹 3. Obtener el usuario de la base de datos
	        User user = userService.getUserByUsername(loginDTO.getUsername());
	        // 🔴 NUEVA VALIDACIÓN: Si el usuario existe pero no está activo
	        if (user != null && Boolean.FALSE.equals(user.getActive())) {
	            logger.warn("Intento de login de usuario inactivo: {}", loginDTO.getUsername());
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body(Map.of("error", "User is inactive"));
	        }
	        
	        // 4. CONVERTIR A DTO ANTES DE ENVIAR (Solución al error de Proxy)
	        UserDTO userDTO = userService.convertToDTO(user);
	        // 🔹 4. Retornar token + datos del usuario
	        return ResponseEntity.ok(Map.of(
	            "token", token,
	            "user", userDTO
	        ));

	    } catch (BadCredentialsException e) {
	        logger.warn("Intento de login con credenciales inválidas: {}", loginDTO.getUsername());
	        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                .body(Map.of("error", "Nombre de usuario o contraseña incorrecta"));
	    } catch (DisabledException e) {
	        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                .body(Map.of("error", "La cuenta está deshabilitada."));
	    } catch (Exception e) {
	        logger.error("Error autenticando usuario", e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("error", "Error al autenticar usuario."));
	    }
	}

	@PostMapping("/auth")
	public ResponseEntity<?> createAuthenticationToken(@RequestBody JwtRequest authenticationRequest) throws Exception {
		authenticate(authenticationRequest.getUsername(), authenticationRequest.getPassword());

		final UserDetails userDetails = userDetailsService.loadUserByUsername(authenticationRequest.getUsername());
		final String token = jwtTokenUtil.generateToken(userDetails);

		return ResponseEntity.ok(new JwtResponse(token));
	}

	private void authenticate(String username, String password) throws Exception {
		try {
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
		} catch (BadCredentialsException e) {
			throw new Exception("INVALID_CREDENTIALS", e);
		} catch (DisabledException e) {
			throw new Exception("USER_DISABLED", e);
		}
	}

	@PutMapping("/change-password")
	public ResponseEntity<?> changePassword(@RequestBody UserLoginDTO dto) {
		try {
			boolean updated = userService.changePassword(dto.getUsername(), dto.getOldPassword(), dto.getNewPassword());
			if (updated) {
				return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente."));
			} else {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body(Map.of("error", "La contraseña anterior no es válida o el usuario no existe."));
			}
		} catch (Exception e) {
			logger.error("Error cambiando contraseña", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Error al actualizar la contraseña."));
		}
	}

	@GetMapping("/verify-token")
	public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String authHeader) {
		try {
			if (authHeader == null || !authHeader.startsWith("Bearer ")) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body(Map.of("error", "Token no proporcionado o inválido."));
			}

			String token = authHeader.substring(7);
			String username = jwtTokenUtil.extractUsername(token);
			UserDetails userDetails = userDetailsService.loadUserByUsername(username);

			if (jwtTokenUtil.validateToken(token, userDetails)) {
				return ResponseEntity.ok(Map.of("valid", true));
			} else {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("error", "Token inválido o expirado."));
			}
		} catch (Exception e) {
			logger.error("Error verificando token", e);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token inválido o expirado."));
		}
	}

	@GetMapping("/paginated")
	public ResponseEntity<Map<String, Object>> getUsersPaginated(
            @RequestParam int pageNumber,
            @RequestParam int pageSize,
            @RequestParam(required = false) String filtro,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam Map<String, String> allParams     // 👈 Nuevo
    ) {
        if (allParams.containsKey("globalFilter")) {
            filtro = allParams.get("globalFilter");
        }
        // remover parámetros normales para quedarnos solo con filtros personalizados
        allParams.remove("pageNumber");
        allParams.remove("pageSize");
        allParams.remove("filtro");
        allParams.remove("sortField");
        allParams.remove("sortDir");
        allParams.remove("globalFilter");

        Page<UserDTO> page = userService.getPage(pageNumber, pageSize, filtro, sortField, sortDir, allParams);

        Map<String, Object> response = new HashMap<>();
        response.put("data", page.getContent());
        response.put("total", page.getTotalElements());

        return ResponseEntity.ok(response);
	}

	@PostMapping("/invite/{userId}")
	public ResponseEntity<?> generateInvite(@PathVariable Long userId, HttpServletRequest request) {
	    try {
	        Optional<User> userOpt = userRepository.findById(userId);
	        if (userOpt.isEmpty()) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                    .body(Map.of("error", "User not found."));
	        }

	        User user = userOpt.get();
	        if (user.getEmail() == null || user.getEmail().isBlank()) {
	            return ResponseEntity.badRequest()
	                    .body(Map.of("error", "The user does not have a registered email address."));
	        }

	        // Generate invitation token (valid for 3 minutes)
	        String token = inviteTokenService.generateToken(user.getEmail());

	        // 🔹 Base URL controlled by configuration
	        String baseUrl = appBaseUrl;

	        // 🔹 Build the invitation URL (without /backend in the final path)
	        String inviteUrl = baseUrl + "/profile.jsf?token=" + token;

	        // 🔹 Send the invitation email
	        try {
	            String subject = "Invitation to create an account in TaskiMed";
	            String message = String.format(
	                    "Hello %s %s,\n\n"
	                            + "You have been invited to create an account in TaskiMed.\n"
	                            + "Please use the following link (valid for 10 minutes):\n\n%s\n\n"
	                            + "If you did not request this invitation, please ignore this message.\n\nBest regards,\nTaskiMed Team",
	                    user.getFirstName() == null ? "" : user.getFirstName(),
	                    user.getLastName() == null ? "" : user.getLastName(),
	                    inviteUrl
	            );

	            emailService.sendSimpleMessage(user.getEmail(), subject, message);
	        } catch (Exception e) {
	            logger.warn("Failed to send email to {}: {}", user.getEmail(), e.getMessage());
	        }

	        return ResponseEntity.ok(Map.of(
	                "inviteUrl", inviteUrl,
	                "expiresIn", "3 minutes"
	        ));

	    } catch (Exception e) {
	        logger.error("Error generating invitation for user ID {}", userId, e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("error", "Could not generate invitation."));
	    }
	}

	@PostMapping("/register")
	public ResponseEntity<?> registerUser(@RequestParam String token, @RequestBody User user) {
		try {
			if (!jwtTokenUtil.validateInviteToken(token)) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("error", "Token inválido o expirado."));
			}

			Long userId = jwtTokenUtil.extractUserId(token);

			if (userId == null) {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body(Map.of("error", "El token no contiene un userId válido."));
			}

			// Enviar tempPassword por correo (si
			// linkUser lo hace)
			UserRegistrationResponse response = userService.linkUser(user, userId);

			return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", response.getUser(), "message",
					"Usuario registrado correctamente. La contraseña temporal (si fue generada) fue enviada por email."));

		} catch (Exception e) {
			logger.error("Error registrando usuario desde invitación", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "No se pudo registrar el usuario."));
		}
	}
	@GetMapping("/invite-data")
	public ResponseEntity<?> getInviteData(@RequestParam String token) {
	    try {
	        if (!jwtTokenUtil.validateInviteToken(token)) {
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body(Map.of("error", "Token inválido o expirado."));
	        }

	        Long userId = jwtTokenUtil.extractUserId(token);
	        if (userId == null) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                    .body(Map.of("error", "El token no contiene un userId válido."));
	        }

	        Optional<User> maybeEmp = userRepository.findById(userId);
	        if (maybeEmp.isEmpty()) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                    .body(Map.of("error", "Empleado no encontrado."));
	        }

	        return ResponseEntity.ok(maybeEmp.get());
	    } catch (Exception e) {
	        logger.error("Error obteniendo datos del empleado por invitación", e);
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body(Map.of("error", "No se pudieron obtener los datos del empleado."));
	    }
	}
}
