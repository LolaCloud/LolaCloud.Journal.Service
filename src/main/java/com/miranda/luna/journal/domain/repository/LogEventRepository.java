package com.miranda.luna.journal.domain.repository;

import com.miranda.luna.journal.domain.model.LogEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LogEventRepository extends JpaRepository<LogEvent, UUID> {

    @Query("SELECT e FROM LogEvent e WHERE e.stream.id = :streamId " +
            "AND (CAST(:since AS timestamp) IS NULL OR e.timestamp >= :since) " +
            "AND (CAST(:until AS timestamp) IS NULL OR e.timestamp <= :until) " +
            "AND (:query IS NULL OR LOWER(e.message) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY e.timestamp DESC")
    Page<LogEvent> findEvents(
            @Param("streamId") UUID streamId,
            @Param("since") Instant since,
            @Param("until") Instant until,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("SELECT e FROM LogEvent e WHERE e.stream.group.name = :groupName " +
            "AND (CAST(:since AS timestamp) IS NULL OR e.timestamp >= :since) " +
            "AND (CAST(:until AS timestamp) IS NULL OR e.timestamp <= :until) " +
            "AND (:query IS NULL OR LOWER(e.message) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY e.timestamp DESC")
    Page<LogEvent> findGroupEvents(
            @Param("groupName") String groupName,
            @Param("since") Instant since,
            @Param("until") Instant until,
            @Param("query") String query,
            Pageable pageable
    );

    List<LogEvent> findTop50ByStreamIdOrderByTimestampDesc(UUID streamId);

    @Query("SELECT e FROM LogEvent e WHERE e.stream.group.name = :groupName AND e.timestamp < :before ORDER BY e.timestamp ASC")
    List<LogEvent> findEventsForArchive(@Param("groupName") String groupName, @Param("before") Instant before);

    @Modifying
    @Query("DELETE FROM LogEvent e WHERE e.stream.id IN (SELECT s.id FROM LogStream s WHERE s.group.id = :groupId) AND e.timestamp < :cutoff")
    int deleteByGroupAndTimestampBefore(@Param("groupId") UUID groupId, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM LogEvent e WHERE e.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") Instant cutoff);
}
