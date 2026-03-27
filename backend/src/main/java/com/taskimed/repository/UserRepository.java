package com.taskimed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.taskimed.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
	List<User> findByTeamId(Long teamId);

	@Query("SELECT u FROM User u WHERE u.team IS NULL OR u.team.id = :currentTeamId")
	List<User> findAvailableUsersForTeam(@Param("currentTeamId") Long currentTeamId);

	@Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.team WHERE u.username = :username")
	Optional<User> findByUsername(@Param("username") String username);
	Optional<User> findByEmail(String email);
    User findByUsernameAndPassword(String username, String password);
}
