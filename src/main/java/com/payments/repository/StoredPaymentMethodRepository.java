package com.payments.repository;

import com.payments.model.entity.StoredPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StoredPaymentMethodRepository extends JpaRepository<StoredPaymentMethod, UUID> {

    List<StoredPaymentMethod> findByAccountId(UUID accountId);

    @Modifying
    @Query("UPDATE StoredPaymentMethod s SET s.isDefault = false WHERE s.account.id = :accountId")
    void clearDefaultForAccount(@Param("accountId") UUID accountId);
}
