package com.livingagent.core.autonomous.payout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PayoutService {

    PayoutResult collectGitHubSponsors(String sponsorEventId, String accountId);

    PayoutResult collectGitHubBounty(String issueId, String pullRequestId, String accountId);

    PayoutResult collectPayPal(String transactionId, String accountId);

    PayoutResult collectCrypto(String txHash, String accountId);

    PayoutResult collectAlipay(String tradeNo, String accountId);

    PayoutResult collectWechatPay(String transactionId, String accountId);

    PayoutResult collectStripe(String paymentIntentId, String accountId);

    PayoutStatus checkPayoutStatus(String payoutId);

    List<PayoutRecord> getPayoutHistory(String ownerId, Instant from, Instant to);

    List<PayoutRecord> getPendingPayouts();

    PayoutAccount createAccount(CreateAccountRequest request);

    Optional<PayoutAccount> getAccount(String accountId);

    List<PayoutAccount> getAccountsByOwner(String ownerId);

    PayoutAccount setDefaultAccount(String ownerId, String accountId);

    PayoutAccount verifyAccount(String accountId);

    void deactivateAccount(String accountId);

    BigDecimal getTotalCollected(String ownerId, Instant from, Instant to);

    BigDecimal getPendingAmount(String ownerId);

    PayoutSummary getSummary(String ownerId);

    record PayoutResult(
            boolean success,
            String payoutId,
            BigDecimal amount,
            String currency,
            String status,
            String error,
            Instant collectedAt
    ) {
        public static PayoutResult success(String payoutId, BigDecimal amount, String currency) {
            return new PayoutResult(true, payoutId, amount, currency, "COMPLETED", null, Instant.now());
        }

        public static PayoutResult pending(String payoutId, BigDecimal amount, String currency) {
            return new PayoutResult(true, payoutId, amount, currency, "PENDING", null, Instant.now());
        }

        public static PayoutResult failed(String error) {
            return new PayoutResult(false, null, BigDecimal.ZERO, "USD", "FAILED", error, null);
        }
    }

    record PayoutStatus(
            String payoutId,
            String status,
            BigDecimal amount,
            String currency,
            Instant createdAt,
            Instant completedAt,
            String transactionHash
    ) {}

    record CreateAccountRequest(
            String accountName,
            String accountType,
            String provider,
            String accountIdentifier,
            String ownerId,
            String ownerType,
            boolean isDefault
    ) {}

    record PayoutSummary(
            BigDecimal totalCollected,
            BigDecimal pendingAmount,
            int totalPayouts,
            int successfulPayouts,
            int pendingPayouts,
            int failedPayouts,
            BigDecimal thisMonthCollected,
            BigDecimal lastMonthCollected
    ) {}
}
