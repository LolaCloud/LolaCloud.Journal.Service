package com.miranda.luna.journal.domain.repository;

import com.miranda.luna.journal.domain.model.LogGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LogGroupRepository extends JpaRepository<LogGroup, UUID> {
    Optional<LogGroup> findByName(String name);
    boolean existsByName(String name);
}
