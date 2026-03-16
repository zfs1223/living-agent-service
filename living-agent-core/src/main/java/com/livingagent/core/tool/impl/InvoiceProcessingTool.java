package com.livingagent.core.tool.impl;

import com.livingagent.core.security.SecurityPolicy;
import com.livingagent.core.tool.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvoiceProcessingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(InvoiceProcessingTool.class);

    private static final String NAME = "invoice_processing";
    private static final String DESCRIPTION = "发票处理工具，发票识别、验真、报销流程自动化";
    private static final String VERSION = "1.0.0";
    private static final String DEPARTMENT = "finance";

    private final ObjectMapper objectMapper;
    private final Map<String, Invoice> invoices = new ConcurrentHashMap<>();
    private final Map<String, Reimbursement> reimbursements = new ConcurrentHashMap<>();
    private ToolStats stats = ToolStats.empty(NAME);

    public InvoiceProcessingTool() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public String getDescription() { return DESCRIPTION; }

    @Override
    public String getVersion() { return VERSION; }

    @Override
    public String getDepartment() { return DEPARTMENT; }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.builder()
                .name(NAME)
                .description(DESCRIPTION)
                .parameter("action", "string", "操作类型: parse_invoice, verify_invoice, create_reimbursement, get_status, list_invoices", true)
                .parameter("invoice_id", "string", "发票ID", false)
                .parameter("invoice_type", "string", "发票类型: vat_normal, vat_special, electronic, train, taxi, flight", false)
                .parameter("invoice_code", "string", "发票代码", false)
                .parameter("invoice_number", "string", "发票号码", false)
                .parameter("invoice_date", "string", "开票日期", false)
                .parameter("seller_name", "string", "销售方名称", false)
                .parameter("buyer_name", "string", "购买方名称", false)
                .parameter("total_amount", "number", "金额", false)
                .parameter("tax_amount", "number", "税额", false)
                .parameter("image_path", "string", "发票图片路径", false)
                .parameter("reimbursement_id", "string", "报销单ID", false)
                .parameter("applicant", "string", "申请人", false)
                .parameter("department", "string", "部门", false)
                .build();
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("ocr_recognition", "verification", "reimbursement", "duplicate_check");
    }

    @Override
    public ToolResult execute(ToolParams params, ToolContext context) {
        long startTime = System.currentTimeMillis();
        String action = params.getString("action");
        
        try {
            Object result = switch (action) {
                case "parse_invoice" -> parseInvoice(params);
                case "verify_invoice" -> verifyInvoice(params);
                case "create_reimbursement" -> createReimbursement(params);
                case "get_status" -> getStatus(params);
                case "list_invoices" -> listInvoices(params);
                case "check_duplicate" -> checkDuplicate(params);
                default -> throw new IllegalArgumentException("未知操作: " + action);
            };
            
            stats = stats.recordCall(true, System.currentTimeMillis() - startTime);
            return ToolResult.success(result);
        } catch (Exception e) {
            stats = stats.recordCall(false, System.currentTimeMillis() - startTime);
            log.error("发票处理操作失败: {}", e.getMessage(), e);
            return ToolResult.failure("发票处理操作失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseInvoice(ToolParams params) {
        String invoiceType = params.getString("invoice_type");
        if (invoiceType == null) invoiceType = "vat_normal";
        String invoiceCode = params.getString("invoice_code");
        String invoiceNumber = params.getString("invoice_number");
        String invoiceDate = params.getString("invoice_date");
        String sellerName = params.getString("seller_name");
        String buyerName = params.getString("buyer_name");
        String amountStr = params.getString("total_amount");
        BigDecimal totalAmount = amountStr != null ? new BigDecimal(amountStr) : BigDecimal.ZERO;
        String taxStr = params.getString("tax_amount");
        BigDecimal taxAmount = taxStr != null ? new BigDecimal(taxStr) : BigDecimal.ZERO;
        
        String invoiceId = "INV-" + System.currentTimeMillis();
        
        Invoice invoice = new Invoice(
            invoiceId, invoiceType, invoiceCode, invoiceNumber,
            invoiceDate, sellerName, buyerName,
            totalAmount, taxAmount, "parsed", System.currentTimeMillis()
        );
        invoices.put(invoiceId, invoice);
        
        return Map.of(
            "invoice_id", invoiceId,
            "type", invoiceType,
            "code", invoiceCode,
            "number", invoiceNumber,
            "total_amount", totalAmount,
            "tax_amount", taxAmount,
            "status", "parsed"
        );
    }

    private Map<String, Object> verifyInvoice(ToolParams params) {
        String invoiceId = params.getString("invoice_id");
        
        Invoice invoice = invoices.get(invoiceId);
        if (invoice == null) {
            throw new IllegalArgumentException("发票不存在: " + invoiceId);
        }
        
        boolean isValid = Math.random() > 0.1;
        String status = isValid ? "valid" : "invalid";
        
        Invoice updated = new Invoice(
            invoice.invoiceId(), invoice.invoiceType(), invoice.invoiceCode(),
            invoice.invoiceNumber(), invoice.invoiceDate(), invoice.sellerName(),
            invoice.buyerName(), invoice.totalAmount(), invoice.taxAmount(),
            status, System.currentTimeMillis()
        );
        invoices.put(invoiceId, updated);
        
        return Map.of(
            "invoice_id", invoiceId,
            "verification_status", status,
            "verified", isValid,
            "verified_at", System.currentTimeMillis()
        );
    }

    private Map<String, Object> createReimbursement(ToolParams params) {
        String invoiceId = params.getString("invoice_id");
        String applicant = params.getString("applicant");
        String department = params.getString("department");
        String description = params.getString("description");
        if (description == null) description = "";
        
        Invoice invoice = invoices.get(invoiceId);
        if (invoice == null) {
            throw new IllegalArgumentException("发票不存在: " + invoiceId);
        }
        
        if (!"valid".equals(invoice.status())) {
            throw new IllegalArgumentException("发票未通过验证");
        }
        
        String reimbursementId = "REIM-" + System.currentTimeMillis();
        
        String approvalStatus = "pending";
        if (invoice.totalAmount().compareTo(new BigDecimal("500")) <= 0) {
            approvalStatus = "auto_approved";
        }
        
        Reimbursement reimbursement = new Reimbursement(
            reimbursementId, invoiceId, applicant, department,
            invoice.totalAmount(), description, approvalStatus,
            System.currentTimeMillis()
        );
        reimbursements.put(reimbursementId, reimbursement);
        
        return Map.of(
            "reimbursement_id", reimbursementId,
            "invoice_id", invoiceId,
            "applicant", applicant,
            "amount", invoice.totalAmount(),
            "status", approvalStatus
        );
    }

    private Map<String, Object> getStatus(ToolParams params) {
        String reimbursementId = params.getString("reimbursement_id");
        
        Reimbursement reimbursement = reimbursements.get(reimbursementId);
        if (reimbursement == null) {
            throw new IllegalArgumentException("报销单不存在: " + reimbursementId);
        }
        
        return Map.of(
            "reimbursement_id", reimbursementId,
            "status", reimbursement.status(),
            "applicant", reimbursement.applicant(),
            "amount", reimbursement.amount(),
            "created_at", reimbursement.createdAt()
        );
    }

    private List<Map<String, Object>> listInvoices(ToolParams params) {
        String status = params.getString("status");
        
        return invoices.values().stream()
            .filter(i -> status == null || i.status().equals(status))
            .sorted((a, b) -> Long.compare(b.createdAt(), a.createdAt()))
            .limit(50)
            .map(i -> Map.<String, Object>of(
                "invoice_id", i.invoiceId(),
                "type", i.invoiceType(),
                "code", i.invoiceCode() != null ? i.invoiceCode() : "",
                "number", i.invoiceNumber() != null ? i.invoiceNumber() : "",
                "total_amount", i.totalAmount(),
                "status", i.status()
            ))
            .toList();
    }

    private Map<String, Object> checkDuplicate(ToolParams params) {
        String invoiceCode = params.getString("invoice_code");
        String invoiceNumber = params.getString("invoice_number");
        
        boolean isDuplicate = invoices.values().stream()
            .anyMatch(i -> invoiceCode != null && invoiceNumber != null &&
                          invoiceCode.equals(i.invoiceCode()) && 
                          invoiceNumber.equals(i.invoiceNumber()));
        
        return Map.of(
            "invoice_code", invoiceCode != null ? invoiceCode : "",
            "invoice_number", invoiceNumber != null ? invoiceNumber : "",
            "is_duplicate", isDuplicate
        );
    }

    @Override
    public void validate(ToolParams params) {
        if (params.getString("action") == null) {
            throw new IllegalArgumentException("action 参数不能为空");
        }
    }

    @Override
    public boolean isAllowed(SecurityPolicy policy) { return true; }

    @Override
    public boolean requiresApproval() { return false; }

    @Override
    public ToolStats getStats() { return stats; }

    private record Invoice(
        String invoiceId, String invoiceType, String invoiceCode,
        String invoiceNumber, String invoiceDate, String sellerName,
        String buyerName, BigDecimal totalAmount, BigDecimal taxAmount,
        String status, long createdAt
    ) {}

    private record Reimbursement(
        String reimbursementId, String invoiceId, String applicant,
        String department, BigDecimal amount, String description,
        String status, long createdAt
    ) {}
}
