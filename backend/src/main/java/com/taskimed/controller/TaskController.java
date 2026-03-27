package com.taskimed.controller;

import com.taskimed.dto.TaskDTO;
import com.taskimed.dto.TaskRequestDTO;
import com.taskimed.entity.Task;
import com.taskimed.service.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // 🟢 Crear una nueva tarea
    @PostMapping
    public ResponseEntity<TaskDTO> createTask(@RequestBody TaskRequestDTO dto) {
        Task task = taskService.createTask(dto);
        TaskDTO response = taskService.convertToDTO(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 🟣 Actualizar una tarea existente
    @PutMapping("/{id}")
    public ResponseEntity<TaskDTO> updateTask(@PathVariable Long id, @RequestBody TaskRequestDTO dto) {
        Task updatedTask = taskService.updateTask(id, dto);
        TaskDTO response = taskService.convertToDTO(updatedTask);
        return ResponseEntity.ok(response);
    }

    // 🔵 Obtener una tarea por ID
    @GetMapping("/{id}")
    public ResponseEntity<TaskDTO> getTask(@PathVariable Long id) {
        Task task = taskService.getTaskById(id);
        TaskDTO dto = taskService.convertToDTO(task);
        return ResponseEntity.ok(dto);
    }

    // 🟣 Obtener todas las tareas
    @GetMapping
    public ResponseEntity<List<TaskDTO>> getAllTasks() {
        List<TaskDTO> taskDTOs = taskService.getTasks()
                .stream()
                .map(taskService::convertToDTO)
                .toList();
        return ResponseEntity.ok(taskDTOs);
    }

    // 🔴 Eliminar tarea
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    // 🟠 Paginación + filtro dinámico (devuelve DTOs con nombres completos)
    @GetMapping("/paginated")
    public ResponseEntity<Map<String, Object>> getTasksPaginated(
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

        Page<TaskDTO> page = taskService.getPage(pageNumber, pageSize, filtro, sortField, sortDir, allParams);

        Map<String, Object> response = new HashMap<>();
        response.put("data", page.getContent());
        response.put("total", page.getTotalElements());

        return ResponseEntity.ok(response);
    }

    // 🧩 Buscar tareas por paciente
    @GetMapping("/byPatient/{patientId}")
    public ResponseEntity<List<TaskDTO>> getTasksByPatient(@PathVariable Long patientId) {
        List<TaskDTO> taskDTOs = taskService.getTasksByPatientId(patientId);
        return ResponseEntity.ok(taskDTOs);
    }

    // 🧩 Buscar tareas por problema
    @GetMapping("/byProblem/{problemId}")
    public ResponseEntity<List<TaskDTO>> getTasksByProblem(@PathVariable Long problemId) {
        List<TaskDTO> taskDTOs = taskService.getTasksByProblemId(problemId);
        return ResponseEntity.ok(taskDTOs);
    }

    // 👤 Buscar tareas por usuario asignado
    @GetMapping("/byUser/{userId}")
    public ResponseEntity<List<TaskDTO>> getTasksByUser(@PathVariable Long userId) {
        List<TaskDTO> taskDTOs = taskService.getTasksByUserId(userId);
        return ResponseEntity.ok(taskDTOs);
    }
    /**
     * 🧹 Limpiar asignaciones de tareas.
     * Invocado por TeamBean cuando un equipo se elimina o un usuario cambia de equipo.
     */
    @PostMapping("/cleanup-assignment")
    public ResponseEntity<Void> cleanupAssignment(
            @RequestParam Long teamId,
            @RequestParam(required = false) Long userId) {
        
        taskService.unassignTasks(teamId, userId);
        return ResponseEntity.ok().build();
    }
    // 📊 Conteo de tareas por paciente
    @GetMapping("/count-by-patient")
    public ResponseEntity<Map<Long, Long>> getTaskCountByPatient() {
        Map<Long, Long> counts = taskService.countTasksByPatient();
        return ResponseEntity.ok(counts);
    }
    // 📊 Conteo de tareas por problema
    @GetMapping("/count-by-problem")
    public ResponseEntity<Map<Long, Long>> getTaskCountByProblem() {
        Map<Long, Long> counts = taskService.countTasksByProblem();
        return ResponseEntity.ok(counts);
    }
    // 📊 Conteo de tareas por problema
    @GetMapping("/count-by-category")
    public ResponseEntity<Map<Long, Long>> getTaskCountByCategory() {
        Map<Long, Long> counts = taskService.countTasksByCategory();
        return ResponseEntity.ok(counts);
    }
 // 📊 Conteo Dinámico Unificado
    @GetMapping("/counts-dynamic/{entityType}")
    public ResponseEntity<Map<Long, Integer>> getDynamicCounts(
            @PathVariable String entityType,
            @RequestParam Map<String, String> allParams) {
        
        // Filtramos parámetros de paginación si los hubiera
        Map<Long, Integer> counts = taskService.getDynamicCountsFor(entityType, allParams);
        return ResponseEntity.ok(counts);
    }
}
