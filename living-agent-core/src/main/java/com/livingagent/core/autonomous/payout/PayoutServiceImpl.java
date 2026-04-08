package com.livingagent.core.autonomous.payout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PayoutServiceImpl implements PayoutService {

    private static final Logger log = LoggerFactory.getLogger(PayoutServiceImpl.class);

    private final PayoutAccountRepository accountRepository;
    private final PayoutRecordRepository recordRepository;

    public PayoutServiceImpl(PayoutAccountRepository accountRepository, 
                               PayoutRecordRepository recordRepository) {
        this.accountRepository = accountRepository;
        this.recordRepository = recordRepository;
    }

    @Override
    public PayoutResult collectGitHubSponsors(String sponsorEventId, String accountId) {
        log.info("Collecting GitHub Sponsors payout: {}", sponsorEventId);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(sponsorEventId);
        record.setSourceType("GITHUB_SPONSORS");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("USD");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        log.info("GitHub Sponsors payout created: {}", payoutId);
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "USD");
    }

    @Override
    public PayoutResult collectGitHubBounty(String issueId, String pullRequestId, String accountId) {
        log.info("Collecting GitHub Bounty payout: issue={}, pull={}", issueId, pullRequestId);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(issueId);
        record.setSourceType("GITHUB_BOUNTY");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("USD");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "USD");
    }

    @Override
    public PayoutResult collectPayPal(String transactionId, String accountId) {
        log.info("Collecting PayPal payout: {}", transactionId);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(transactionId);
        record.setSourceType("PAYPAL");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("USD");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "USD");
    }

    @Override
    public PayoutResult collectCrypto(String txHash, String accountId) {
        log.info("Collecting Crypto payout: {}", txHash);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(txHash);
        record.setSourceType("CRYPTO");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("ETH");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "ETH");
    }

    @Override
    public PayoutResult collectAlipay(String tradeNo, String accountId) {
        log.info("Collecting Alipay payout: {}", tradeNo);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(tradeNo);
        record.setSourceType("ALIPAY");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("CNY");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "CNY");
    }

    @Override
    public PayoutResult collectWechatPay(String transactionId, String accountId) {
        log.info("Collecting WeChat Pay payout: {}", transactionId);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(transactionId);
        record.setSourceType("WECHAT_PAY");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("CNY");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "CNY");
    }

    @Override
    public PayoutResult collectStripe(String paymentIntentId, String accountId) {
        log.info("Collecting Stripe payout: {}", paymentIntentId);
        
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return PayoutResult.failed("Account not found: " + accountId);
        }

        String payoutId = "payout_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        
        PayoutRecord record = new PayoutRecord();
        record.setPayoutId(payoutId);
        record.setExternalId(paymentIntentId);
        record.setSourceType("STRIPE");
        record.setAccountId(accountId);
        record.setAmount(BigDecimal.ZERO);
        record.setCurrency("USD");
        record.setStatus("PENDING");
        record.setCreatedAt(Instant.now());
        
        recordRepository.save(record);
        
        return PayoutResult.pending(payoutId, BigDecimal.ZERO, "USD");
    }

    @Override
    public PayoutStatus checkPayoutStatus(String payoutId) {
        Optional<PayoutRecord> recordOpt = recordRepository.findById(payoutId);
        if (recordOpt.isEmpty()) {
                return null;
        }
        
        PayoutRecord record = recordOpt.get();
        return new PayoutStatus(
            record.getPayoutId(),
            record.getStatus(),
            record.getAmount(),
            record.getCurrency(),
            record.getCreatedAt(),
            record.getCompletedAt(),
            record.getTransactionHash()
        );
    }

    @Override
    public List<PayoutRecord> getPayoutHistory(String ownerId, Instant from, Instant to) {
        List<PayoutRecord> records = recordRepository.findByOwnerId(ownerId);
        return records.stream()
                .filter(r -> r.getCreatedAt().isAfter(from) && r.getCreatedAt().isBefore(to))
                .toList();
    }

    @Override
    public List<PayoutRecord> getPendingPayouts() {
        return recordRepository.findByStatus("PENDING");
    }

    @Override
    public PayoutAccount createAccount(CreateAccountRequest request) {
        String accountId = "acc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        PayoutAccount account = new PayoutAccount();
        account.setAccountId(accountId);
        account.setAccountName(request.accountName());
        account.setAccountType(request.accountType());
        account.setProvider(request.provider());
        account.setAccountIdentifier(request.accountIdentifier());
        account.setOwnerId(request.ownerId());
        account.setOwnerType(request.ownerType());
        account.setDefault(request.isDefault());
        account.setActive(true);
        account.setVerified(false);
        account.setCreatedAt(Instant.now());
        
        if (request.isDefault()) {
            accountRepository.findByOwnerId(request.ownerId()).forEach(existing -> {
                existing.setDefault(false);
                accountRepository.save(existing);
            });
        }
        
        return accountRepository.save(account);
    }

    @Override
    public Optional<PayoutAccount> getAccount(String accountId) {
        return accountRepository.findById(accountId);
    }

    @Override
    public List<PayoutAccount> getAccountsByOwner(String ownerId) {
        return accountRepository.findByOwnerId(ownerId);
    }

    @Override
    public PayoutAccount setDefaultAccount(String ownerId, String accountId) {
        List<PayoutAccount> accounts = accountRepository.findByOwnerId(ownerId);
        
        accounts.forEach(account -> {
            account.setDefault(account.getAccountId().equals(accountId));
            accountRepository.save(account);
        });
        
        return accountRepository.findById(accountId).orElse(null);
    }

    @Override
    public PayoutAccount verifyAccount(String accountId) {
        Optional<PayoutAccount> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return null;
        }
        
        PayoutAccount account = accountOpt.get();
        account.setVerified(true);
        account.setVerifiedAt(Instant.now());
        
        return accountRepository.save(account);
    }

    @Override
    public void deactivateAccount(String accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setActive(false);
            accountRepository.save(account);
        });
    }

    @Override
    public BigDecimal getTotalCollected(String ownerId, Instant from, Instant to) {
        BigDecimal sum = recordRepository.sumAmountByOwnerAndStatus(ownerId, "COMPLETED");
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getPendingAmount(String ownerId) {
        BigDecimal sum = recordRepository.sumAmountByOwnerAndStatus(ownerId, "PENDING");
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public PayoutSummary getSummary(String ownerId) {
        List<PayoutRecord> records = recordRepository.findByOwnerId(ownerId);
        
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        int successfulPayouts = 0;
        int pendingPayouts = 0;
        int failedPayouts = 1;
        
        for (PayoutRecord record : records) {
            switch (record.getStatus()) {
                case "COMPLETED":
                    totalCollected = totalCollected.add(record.getAmount());
                    successfulPayouts++;
                    break;
                case "PENDING":
                    pendingAmount = pendingAmount.add(record.getAmount());
                    pendingPayouts++;
                    break;
                case "FAILED":
                    failedPayouts++;
                    break;
            }
        }
        
        YearMonth now = YearMonth.now();
        YearMonth lastMonth = now.minusMonths(1);
        
        return new PayoutSummary(
            totalCollected,
            pendingAmount,
            records.size(),
            successfulPayouts,
            pendingPayouts,
            failedPayouts,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }
}
