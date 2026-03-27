package com.taskimed.service;

import com.taskimed.dto.TaskDTO;
import com.taskimed.dto.TaskRequestDTO;
import com.taskimed.entity.Task;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.Map;

public interface TaskService {

    // 🟢 CRUD básico
    Task saveTask(Task task);
    List<Task> getTasks();
    Task getTaskById(Long id);
    void deleteTask(Long id);

    // 🔍 Búsquedas personalizadas
    List<TaskDTO> getTasksByPatientId(Long patientId);
    List<TaskDTO> getTasksByProblemId(Long problemId);
    List<TaskDTO> getTasksByUserId(Long userId);

    // 📄 Soporte para paginación y filtrado (devuelve DTOs)
    Page<TaskDTO> getPage(int pageNumber, int pageSize, String filtro, String sortField, String sortDir, Map<String, String> customFilters);

    // ✳️ Nuevos métodos usados por el controlador
    TaskDTO convertToDTO(Task task); // 👈 ahora público
    Task createTask(TaskRequestDTO requestDTO);
    Task updateTask(Long id, TaskRequestDTO requestDTO);
    
    void unassignTasks(Long teamId, Long userId);
    Map<Long, Long> countTasksByPatient();
    Map<Long, Long> countTasksByProblem();
    Map<Long, Long> countTasksByCategory();
    Map<Long, Integer> getDynamicCountsFor(String entityType, Map<String, String> customFilters);
}
