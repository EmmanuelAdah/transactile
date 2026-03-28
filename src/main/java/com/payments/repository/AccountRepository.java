package com.payments.repository;

import com.payments.model.entity.Account;
import com.payments.model.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByExternalId(String externalId);

    boolean existsByEmail(String email);

    boolean existsByExternalId(String externalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Account a SET a.status = :status WHERE a.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") AccountStatus status);
}
