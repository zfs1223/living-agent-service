package com.livingagent.core.autonomous.payout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, String> {

    List<PayoutAccount> findByOwnerId(String ownerId);

    List<PayoutAccount> findByOwnerType(String ownerType);

    Optional<PayoutAccount> findByAccountId(String accountId);

    Optional<PayoutAccount> findByIsDefaultTrueAndOwnerId(String ownerId);

    @Query("SELECT a FROM PayoutAccount a WHERE a.ownerId = :ownerId AND a.isDefault = true")
    List<PayoutAccount> findDefaultAccounts();

    boolean existsByAccountId(String accountId);
}
