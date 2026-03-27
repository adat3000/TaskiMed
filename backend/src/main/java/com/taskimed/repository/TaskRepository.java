package com.taskimed.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskimed.entity.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	/**
     * Busca una tarea por ID cargando todas sus relaciones de forma ansiosa (Eager)
     * para evitar LazyInitializationException durante la conversión a DTO.
     */
    @EntityGraph(attributePaths = {"patient", "assignedTo", "createdBy", "createdBy.role", "category", "problem", "team"})
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdCustom(@Param("id") Long id);
	
    /**
     * Obtiene todas las tareas asociadas a un paciente por su ID.
     *
     * @param patientId ID del paciente
     * @return Lista de tareas asociadas al paciente
     */
	@EntityGraph(attributePaths = {"patient", "assignedTo", "createdBy", "createdBy.role"})
    List<Task> findByPatient_Id(Long patientId);
	
    /**
     * Obtiene todas las tareas asociadas a un paciente por su ID.
     *
     * @param patientId ID del paciente
     * @return Lista de tareas asociadas al paciente
     */
	@EntityGraph(attributePaths = {"problem", "assignedTo", "createdBy", "createdBy.role"})
    List<Task> findByProblem_Id(Long problemId);

    /**
     * Obtiene todas las tareas asignadas a un usuario por su ID.
     *
     * @param userId ID del usuario asignado
     * @return Lista de tareas asignadas al usuario
     */
	// Usamos @EntityGraph para forzar la carga de las relaciones y evitar el error de sesión
	@EntityGraph(attributePaths = {"patient", "assignedTo", "createdBy", "createdBy.role"})
    List<Task> findByAssignedTo_Id(Long userId);

	@EntityGraph(attributePaths = {"patient", "assignedTo", "createdBy", "createdBy.role"})
    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId " +
           "AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Task> findActiveTasksByUserId(@Param("userId") Long userId);

	@Modifying
	@Query("UPDATE Task t SET t.assignedTo = NULL WHERE t.team.id = :teamId")
	void unassignAllByTeamId(@Param("teamId") Long teamId);

	@Modifying
	@Query("UPDATE Task t SET t.assignedTo = NULL WHERE t.team.id = :teamId AND t.assignedTo.id = :userId")
	void unassignUserFromTeamTasks(@Param("teamId") Long teamId, @Param("userId") Long userId);	

	@Query("""
		    SELECT t.patient.id, COUNT(t)
		    FROM Task t
		    WHERE t.patient IS NOT NULL
		    GROUP BY t.patient.id
		""")
	List<Object[]> countTasksGroupedByPatient();

	@Query("""
		    SELECT t.problem.id, COUNT(t)
		    FROM Task t
		    WHERE t.problem IS NOT NULL
		    GROUP BY t.problem.id
		""")
	List<Object[]> countTasksGroupedByProblem();

	@Query("""
		    SELECT t.category.id, COUNT(t)
		    FROM Task t
		    WHERE t.category IS NOT NULL
		    GROUP BY t.category.id
		""")
	List<Object[]> countTasksGroupedByCategory();
}
