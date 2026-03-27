package com.taskimed.implement;

import com.taskimed.dto.UserDTO;
import com.taskimed.dto.UserRegistrationResponse;
import com.taskimed.entity.Team;
import com.taskimed.entity.User;
import com.taskimed.repository.RoleRepository;
import com.taskimed.repository.TeamRepository;
import com.taskimed.repository.UserRepository;
import com.taskimed.service.EmailService;
import com.taskimed.service.UserService;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TeamRepository teamRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    @Transactional
    public UserDTO saveUser(UserDTO dto) {
        if (dto == null) {
            throw new RuntimeException("UserDTO no puede ser null");
        }

        User user;

        // 1) Si es actualización → cargar usuario existente
        if (dto.getId() != null) {
            user = userRepository.findById(dto.getId())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + dto.getId()));
        } else {
            user = new User();
        }

        // 2) Mapear campos simples DTO -> Entidad
        user.setUsername(dto.getUsername());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setJobPosition(dto.getJobPosition());
        user.setEntryDate(dto.getEntryDate());
        user.setActive(dto.getActive());

        // ==========================================================
        // 3) Manejo CORRECTO de contraseña
        // ==========================================================
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {

            // 🔹 Caso 1: Nuevo usuario → siempre hashear si es texto plano
            // 🔹 Caso 2: Edición → hashear SOLO si NO es un hash ya existente
            if (!dto.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(dto.getPassword()));
            }
            // else → ya viene hasheada: mantenerla tal cual
        }
        // Si viene null o vacía en modo edición → conservar la que ya tiene la entidad

        // 4) Asignar rol
        if (dto.getRoleId() != null) {
            var role = roleRepository.findById(dto.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Rol no encontrado con ID: " + dto.getRoleId()));
            user.setRole(role);
        } else {
            user.setRole(null);
        }
        if (dto.getTeamId() != null) {
            Team team = teamRepository.findById(dto.getTeamId()).orElse(null);
            user.setTeam(team);
        } else {
        	user.setTeam(null);
        }
        // 5) Guardar y devolver DTO
        User saved = userRepository.save(user);
        return convertToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true) // Agregado para permitir acceso al Proxy de Team
    public User getUserById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // FORZADO MANUAL: Si hay un equipo, accedemos a un campo para inicializarlo
        if (user.getTeam() != null) {
            user.getTeam().getName(); // Esto obliga a Hibernate a cargar los datos AHORA
        }
        
        return user;
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return false;

        // Validar contraseña anterior
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return false;
        }

        // Guardar nueva contraseña encriptada
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }

    @Override
    @Transactional
    public Page<UserDTO> getPage(
            int pageNumber,
            int pageSize,
            String filtro,
            String sortField,
            String sortDir,
            Map<String, String> customFilters
    ){
        try {

            // ─────────────────────────────────────────────
            // 1️⃣ Normalizar campos de ordenamiento
            // ─────────────────────────────────────────────
            if (sortField != null) {
                switch (sortField) {
                    case "roleName"         -> sortField = "role.name";
                    case "teamName" -> sortField = "team.name";
                    case "teamAlias" -> sortField = "team.alias";
                    case "fullName" -> sortField = "firstName"; // Ordenar por nombre si piden fullName
                    default -> {}
                }
            }

            Sort sort = sortDir.equalsIgnoreCase("asc")
                    ? Sort.by(sortField).ascending()
                    : Sort.by(sortField).descending();

            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);


            // ─────────────────────────────────────────────
            // 2️⃣ SPEC BASE – FILTRO GLOBAL
            // ─────────────────────────────────────────────
            Specification<User> specGlobal = (root, query, builder) -> {

                if (filtro == null || filtro.trim().isEmpty()) {
                    return builder.conjunction();
                }

                String pattern = "%" + filtro.toLowerCase() + "%";

                var roleJoin = root.join("role", JoinType.LEFT);
                var teamJoin = root.join("team", JoinType.LEFT);

                Expression<String> entryDateStr = builder.function("DATE_FORMAT", String.class,
                        root.get("entryDate"), builder.literal("%Y-%m-%d"));

                Expression<String> userFullName = builder.concat(
                        builder.concat(builder.lower(root.get("firstName")), " "),
                        builder.lower(root.get("lastName"))
                );

                return builder.or(
                        builder.like(builder.lower(root.get("username")), pattern),
                        builder.like(builder.lower(entryDateStr), pattern),
                        builder.like(builder.lower(root.get("firstName")), pattern),
                        builder.like(builder.lower(root.get("lastName")), pattern),
                        builder.like(userFullName, pattern),
                        builder.like(builder.lower(root.get("phone")), pattern),
                        builder.like(builder.lower(root.get("jobPosition")), pattern),
                        // Campos de Role
                        builder.like(builder.lower(roleJoin.get("name")), pattern),
                        // Campos de Team (Nuevos)
                        builder.like(builder.lower(teamJoin.get("name")), pattern),
                        builder.like(builder.lower(teamJoin.get("alias")), pattern)
                );
            };


            // ─────────────────────────────────────────────
            // 3️⃣ SPEC PARA FILTROS PERSONALIZADOS
            // ─────────────────────────────────────────────
            Specification<User> specCustom = (root, query, builder) -> {

                List<Predicate> predicates = new ArrayList<>();

                if (customFilters != null) {

                    // 🔹 Filtrar por rol
                    if (customFilters.containsKey("roleId")) {
                        Long id = Long.valueOf(customFilters.get("roleId"));
                        predicates.add(builder.equal(root.get("role").get("id"), id));
                    }
                    if (customFilters.containsKey("teamId")) {
                        predicates.add(builder.equal(root.get("team").get("id"), Long.valueOf(customFilters.get("teamId"))));
                    }
                    if (customFilters.containsKey("active")) {
                        predicates.add(builder.equal(root.get("active"), Boolean.valueOf(customFilters.get("active"))));
                    }
                }

                return predicates.isEmpty()
                        ? builder.conjunction()
                        : builder.and(predicates.toArray(new Predicate[0]));
            };


            // ─────────────────────────────────────────────
            // 4️⃣ Combinar Specs
            // ─────────────────────────────────────────────
            Specification<User> finalSpec =
                    Specification.where(specGlobal).and(specCustom);


            // ─────────────────────────────────────────────
            // 5️⃣ Ejecutar consulta
            // ─────────────────────────────────────────────
            Page<User> page = userRepository.findAll(finalSpec, pageable);

            List<UserDTO> dtoList = page.getContent()
                    .stream()
                    .map(this::convertToDTO)
                    .toList();

            return new PageImpl<>(dtoList, pageable, page.getTotalElements());

        } catch (Exception e) {
            throw new RuntimeException("Unexpected error in getPage: " + e.getMessage(), e);
        }
    }

    /**
     * Convierte una entidad Task a un DTO incluyendo nombres completos.
     */
    @Override
    public UserDTO convertToDTO(User user) {
        if (user == null) return null;
        String roleName = null;
		String fullName = null;
        if (user.getRole() != null) {
            String firstName = Optional.ofNullable(user.getFirstName()).orElse("");
            String lastName = Optional.ofNullable(user.getLastName()).orElse("");
            fullName = (firstName + " " + lastName).trim();
        	roleName = Optional.ofNullable(user.getRole().getName()).orElse("");
        }
        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .roleName(roleName)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(fullName)
                .email(user.getEmail())
                .phone(user.getPhone())
                .jobPosition(user.getJobPosition())
                .entryDate(user.getEntryDate())
                .active(user.getActive())
                .teamId(user.getTeam() != null ? user.getTeam().getId() : null)
                .teamName(user.getTeam() != null ? user.getTeam().getName() : null)
                .teamAlias(user.getTeam() != null ? user.getTeam().getAlias() : null)
                .build();
    }

    @Override
    public UserRegistrationResponse linkUser(User user, Long userId) {
        String tempPassword = null;

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            tempPassword = generateSecurePassword(12);
            user.setPassword(passwordEncoder.encode(tempPassword));

            // ✅ Enviar la contraseña temporal por email al empleado
            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                String subject = "Acceso temporal a la plataforma TaskiMed";
                String message = String.format(
                    "Hola %s %s,\n\nSe ha creado tu cuenta en TaskiMed.\n\nUsuario: %s\nContraseña temporal: %s\n\nPor favor, cambia tu contraseña al iniciar sesión.",
                    user.getFirstName(),
                    user.getLastName(),
                    user.getUsername(),
                    tempPassword
                );
                emailService.sendSimpleMessage(user.getEmail(), subject, message);
            }
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        User savedUser = userRepository.save(user);

        // 🔒 En producción ya no devolvemos la contraseña en la API
        return new UserRegistrationResponse(savedUser, null);
    }

    /**
     * Genera una contraseña aleatoria segura.
     */
    private String generateSecurePassword(int length) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
    @Override
    public User registerUserFromInvite(User user) {
        String pwd = user.getPassword();
        if (pwd != null && !pwd.startsWith("$2a$")) { // Solo encripta si no está ya cifrada
            user.setPassword(passwordEncoder.encode(pwd));
        }
        return userRepository.save(user);
    }
    @Override
    public List<UserDTO> getAvailableUsers() {
        // Retorna usuarios que no tienen equipo asignado (team_id IS NULL)
        return userRepository.findAll()
                .stream()
                .filter(u -> u.getTeam() == null)
                .map(this::convertToDTO)
                .toList();
    }

    // Nueva función para asignar equipo a múltiples usuarios
    @Transactional
    public void assignTeamToUsers(Long teamId, List<Long> userIds) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Equipo no encontrado"));
                
        for (Long userId : userIds) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setTeam(team);
                userRepository.save(user);
            }
        }
    }
}
