package com.taskimed.implement;

import com.taskimed.dto.TeamDTO;
import com.taskimed.entity.Team;
import com.taskimed.entity.User;
import com.taskimed.repository.TeamRepository;
import com.taskimed.repository.UserRepository;
import com.taskimed.service.TeamService;

import jakarta.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class TeamServiceImpl implements TeamService {

	private final TeamRepository teamRepository;
	private final UserRepository userRepository;
	
	public TeamServiceImpl(TeamRepository teamRepository, UserRepository userRepository) {
		this.teamRepository = teamRepository;
		this.userRepository = userRepository;
	}

	@Override
    @Transactional(readOnly = true)
	public List<TeamDTO> getTeams() {
		return teamRepository.findAll().stream()
	            .map(this::convertToDTO)
	            .collect(Collectors.toList());
	}
	
	@Override
	@Transactional
	public TeamDTO saveTeam(TeamDTO dto) {
	    if (dto == null) throw new RuntimeException("TeamDTO no puede ser null");

	    Team team;
	    if (dto.getId() != null) {
	        team = teamRepository.findById(dto.getId())
	                .orElseThrow(() -> new RuntimeException("Team not found with ID: " + dto.getId()));
	        
	        // 1. DESASIGNAR usuarios antiguos (opcional, dependiendo de si quieres reemplazo total)
	        // Buscamos usuarios que antes estaban en este equipo y les ponemos el team en null
	        List<User> currentMembers = userRepository.findByTeamId(team.getId());
	        for (User user : currentMembers) {
	            user.setTeam(null);
	        }
	    } else {
	        team = new Team();
	    }

	    // 2. Mapear campos simples
	    team.setName(dto.getName());
	    team.setAlias(dto.getAlias());

	    // Guardamos el equipo primero para asegurar que tenga ID si es nuevo
	    Team saved = teamRepository.saveAndFlush(team);

	    // 3. ASIGNAR nuevos usuarios seleccionados en el userIds del DTO
	    if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
	        List<User> newMembers = userRepository.findAllById(dto.getUserIds());
	        for (User user : newMembers) {
	            user.setTeam(saved); // Establecemos la relación en el lado del Usuario (FK)
	        }
	        // No es necesario llamar a userRepository.save ya que @Transactional 
	        // detectará los cambios en las entidades gestionadas.
	        
	        // Actualizamos la lista interna para que el convertToDTO la vea reflejada
	        saved.setMembers(newMembers);
	    } else {
	        saved.setMembers(new ArrayList<>());
	    }

	    return convertToDTO(saved);
	}
	@Override
	@Transactional(readOnly = true)
	public TeamDTO getTeamById(Long id) {
	    Team team = teamRepository.findById(id).orElseThrow();
	    team.getMembers().size(); // Esto fuerza la carga de la colección dentro de la sesión
	    return convertToDTO(team);
	}

	@Override
	public void deleteTeam(Long id) {
		teamRepository.deleteById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<TeamDTO> getPage(
			int pageNumber,
			int pageSize,
			String filtro,
			String sortField,
			String sortDir,
            Map<String, String> customFilters
		) {
        try {
            Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
            Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

            Specification<Team> spec = (root, query, builder) -> {
                if (filtro == null || filtro.trim().isEmpty()) {
                    return builder.conjunction();
                }

                String pattern = "%" + filtro.toLowerCase() + "%";

                return builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("alias")), pattern)
                );
            };
            Page<Team> page = teamRepository.findAll(spec, pageable);

            List<TeamDTO> dtoList = page.getContent()
                    .stream()
                    .map(this::convertToDTO)
                    .toList();

            return new PageImpl<>(dtoList, pageable, page.getTotalElements());
        } catch (DataAccessException | PersistenceException e) {
            throw new RuntimeException("Error getting paginated teams: " + e.getMessage(), e);
        }
	}

	@Override
	public TeamDTO convertToDTO(Team team) {
	    if (team == null) return null;

	    // Extraemos IDs y Nombres mientras la sesión está activa
	    List<Long> ids = new ArrayList<>();
	    List<String> names = new ArrayList<>();
	    List<Boolean> actives = new ArrayList<>();

	    if (team.getMembers() != null) {
	        for (User user : team.getMembers()) {
	            ids.add(user.getId());
	            // Concatenamos nombre completo para mostrar en los chips del frontend
	            names.add(user.getFirstName() + " " + user.getLastName());
	            actives.add(user.getActive());
	        }
	    }

	    return TeamDTO.builder()
	            .id(team.getId())
	            .name(team.getName())
	            .alias(team.getAlias())
	            .userIds(ids)      // Se usa en el p:selectManyMenu (Edición)
	            .userNames(names)  // Se usa en el ui:repeat (Vista)
	            .userActives(actives)
	            .build();
	}
}