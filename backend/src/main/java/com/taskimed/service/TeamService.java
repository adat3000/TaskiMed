package com.taskimed.service;

import java.util.List;
import java.util.Map;

import com.taskimed.dto.TeamDTO;
import com.taskimed.entity.Team;

import org.springframework.data.domain.Page;

public interface TeamService {
	TeamDTO saveTeam(TeamDTO dto);
	List<TeamDTO> getTeams();
    TeamDTO getTeamById(Long id);
    void deleteTeam(Long id);
    Page<TeamDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);
    
    TeamDTO convertToDTO(Team team);
}