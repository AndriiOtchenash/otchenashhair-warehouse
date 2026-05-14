package com.hairmony.warehouse.repository;

import com.hairmony.warehouse.domain.followup.FollowUp;
import com.hairmony.warehouse.domain.followup.FollowUpAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    @Query("""
        SELECT f FROM FollowUp f
        WHERE f.client.id IN :clientIds
          AND f.createdAt = (
              SELECT MAX(f2.createdAt) FROM FollowUp f2
              WHERE f2.client.id = f.client.id
          )
        """)
    List<FollowUp> findLatestPerClient(@Param("clientIds") Collection<Long> clientIds);

    List<FollowUp> findAllByClientIdOrderByCreatedAtDesc(Long clientId);

    Optional<FollowUp> findFirstByClientIdOrderByCreatedAtDesc(Long clientId);

    /** Deletes all active-hiding records (SNOOZE or DONE with future dueDate) for a client */
    @Modifying
    @Query("DELETE FROM FollowUp f WHERE f.client.id = :clientId AND f.action IN :actions AND f.dueDate > :today")
    void deleteActiveHidingRecords(@Param("clientId") Long clientId,
                                   @Param("actions") List<FollowUpAction> actions,
                                   @Param("today") LocalDate today);

    /** Returns [clientId, count] pairs for all clients in the given set */
    @Query("SELECT f.client.id, COUNT(f) FROM FollowUp f WHERE f.client.id IN :clientIds GROUP BY f.client.id")
    List<Object[]> countPerClient(@Param("clientIds") Collection<Long> clientIds);

    @Modifying
    @Query("DELETE FROM FollowUp f WHERE f.client.id = :clientId")
    void deleteAllByClientId(@Param("clientId") Long clientId);
}
