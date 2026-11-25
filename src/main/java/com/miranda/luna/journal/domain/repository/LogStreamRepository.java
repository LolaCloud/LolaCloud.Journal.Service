package com.miranda.luna.journal.domain.repository;

import com.miranda.luna.journal.domain.model.LogGroup;
import com.miranda.luna.journal.domain.model.LogStream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LogStreamRepository extends JpaRepository<LogStream, UUID> {

    Optional<LogStream> findByGroupAndName(LogGroup group, String name);

    @Query("SELECT s FROM LogStream s WHERE s.group.name = :groupName AND s.name = :name")
    Optional<LogStream> findByGroupNameAndName(@Param("groupName") String groupName, @Param("name") String name);

    List<LogStream> findByGroupId(UUID groupId);

    @Query("SELECT s FROM LogStream s WHERE s.group.name = :groupName ORDER BY s.name ASC")
    List<LogStream> findByGroupName(@Param("groupName") String groupName);

    boolean existsByGroupIdAndName(UUID groupId, String name);

    long countByGroupId(UUID groupId);
}
