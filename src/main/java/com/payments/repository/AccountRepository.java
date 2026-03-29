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
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Account a SET a.status = :status WHERE a.accountNumber = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") AccountStatus status);
}
