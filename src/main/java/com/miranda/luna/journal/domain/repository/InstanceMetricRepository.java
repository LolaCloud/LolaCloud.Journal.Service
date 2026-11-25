package com.miranda.luna.journal.domain.repository;

import com.miranda.luna.journal.domain.model.InstanceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstanceMetricRepository extends JpaRepository<InstanceMetric, UUID> {

    List<InstanceMetric> findByInstanceNameAndTimestampBetweenOrderByTimestampAsc(
            String instanceName,
            Instant from,
            Instant to
    );

    Optional<InstanceMetric> findTopByInstanceNameOrderByTimestampDesc(String instanceName);

    @Query("SELECT DISTINCT m.instanceName FROM InstanceMetric m ORDER BY m.instanceName ASC")
    List<String> findDistinctInstanceNames();

    @Modifying
    @Query("DELETE FROM InstanceMetric m WHERE m.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM InstanceMetric m WHERE LOWER(m.instanceName) = LOWER(:name) OR LOWER(m.instanceName) = LOWER(:altName)")
    int deleteByInstanceNameOrAlt(@Param("name") String name, @Param("altName") String altName);
}
