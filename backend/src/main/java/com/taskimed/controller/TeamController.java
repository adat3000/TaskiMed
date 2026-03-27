package com.taskimed.controller;

import com.taskimed.dto.TeamDTO;
import com.taskimed.service.TeamService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import java.util.*;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = "*")
public class TeamController {

	private final TeamService teamService;

	public TeamController(TeamService teamService){
		this.teamService = teamService;
	}

	@PostMapping
	public ResponseEntity<TeamDTO> createTeam(@RequestBody TeamDTO dto) {
	    TeamDTO saved = teamService.saveTeam(dto);
	    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	@GetMapping("/{id}")
	public ResponseEntity<TeamDTO> getTeam(@PathVariable Long id) {
	    TeamDTO team = teamService.getTeamById(id); // O la lógica que uses para obtenerlo
	    return ResponseEntity.ok(team);
	}

    @GetMapping
    public ResponseEntity<List<TeamDTO>> getAllTeams() {
        return ResponseEntity.ok(teamService.getTeams());
    }

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
		teamService.deleteTeam(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/paginated")
	public ResponseEntity<Map<String, Object>> getTeamsPaginated(
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

		Page<TeamDTO> page = teamService.getPage(pageNumber, pageSize, filtro, sortField, sortDir, allParams);

		Map<String, Object> response = new HashMap<>();
		response.put("data", page.getContent());
		response.put("total", page.getTotalElements());

		return ResponseEntity.ok(response);
	}
}