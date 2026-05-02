package com.financial.operator.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;

    public OperatorController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/auth/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request) {
        List<OperatorItem> list = jdbcTemplate.query(
                "SELECT id, operator_code, operator_name, login_password_hash, status FROM sys_operator WHERE login_name = ?",
                (rs, rowNum) -> new OperatorItem(
                        rs.getLong("id"),
                        rs.getString("operator_code"),
                        rs.getString("operator_name"),
                        rs.getString("login_password_hash"),
                        rs.getString("status")
                ),
                request.loginName()
        );
        if (list.isEmpty()) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        OperatorItem item = list.get(0);
        if (!item.passwordHash().equals(request.password())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (!"1".equals(item.status())) {
            throw new IllegalArgumentException("账号已停用");
        }
        String token = createSessionToken(item.id());
        return new LoginResult(token, item.operatorCode(), item.operatorName());
    }

    @GetMapping("/opening/applications")
    public List<OpenApplyItem> listApplications(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String status
    ) {
        requireOperatorId(token);
        String importSql = """
                SELECT ir.id, ir.record_no, b.import_mode, ir.customer_name, ir.id_type, ir.id_no,
                       ir.acct_cls_code, ir.sh_shareholder_acct, ir.sz_shareholder_acct,
                       ir.review_status, ir.created_at, ir.created_customer_id,
                       COALESCE(oa.shareholder_market,
                                CASE
                                  WHEN ir.sh_shareholder_acct IS NOT NULL AND ir.sh_shareholder_acct <> '' THEN 'SH'
                                  WHEN ir.sz_shareholder_acct IS NOT NULL AND ir.sz_shareholder_acct <> '' THEN 'SZ'
                                  ELSE NULL
                                END) AS shareholder_market
                FROM cust_import_record ir
                JOIN cust_import_batch b ON b.id = ir.batch_id
                LEFT JOIN cust_open_apply oa ON oa.apply_no = ir.record_no
                ORDER BY ir.created_at DESC
                """;
        List<OpenApplyItem> all = new ArrayList<>();
        all.addAll(jdbcTemplate.query(importSql, openApplyMapper()));
        all.addAll(jdbcTemplate.query("""
                SELECT id, apply_no, customer_name, id_type, id_no, acct_cls_code, sh_account, sz_account,
                       apply_status, audit_comment, created_at, shareholder_market
                FROM cust_open_apply
                WHERE apply_status = 'WAIT_AUDIT'
                ORDER BY created_at DESC
                """, appApplyMapper()));
        all.sort(Comparator.comparing(OpenApplyItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        if (status == null || status.isBlank()) {
            return all;
        }
        return all.stream().filter(it -> status.equalsIgnoreCase(it.status())).toList();
    }

    @PostMapping("/opening/applications/{id}/claim")
    @Transactional
    public OpenApplyItem claimApplication(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long id
    ) {
        Long operatorId = requireOperatorId(token);
        OpenApplyItem item = mustGetApplyFromDb(id);
        if (!"SUBMITTED".equals(item.status())) {
            throw new IllegalArgumentException("仅已提交状态可接单");
        }
        jdbcTemplate.update("UPDATE cust_import_record SET review_status = ? WHERE id = ?", "IN_REVIEW", id);
        addAuditLog(id, item.applyNo(), operatorId, "SUBMITTED", "IN_REVIEW", "CLAIM", "操作员接单审核");
        return mustGetApplyFromDb(id);
    }

    @PostMapping("/opening/applications/import-app")
    @Transactional
    public Map<String, Object> importAllAppApplications(@RequestHeader("X-Operator-Token") String token) {
        Long operatorId = requireOperatorId(token);
        List<Long> appApplyIds = jdbcTemplate.queryForList(
                "SELECT id FROM cust_open_apply WHERE apply_status = 'WAIT_AUDIT' ORDER BY created_at ASC",
                Long.class
        );
        int imported = 0;
        int skipped = 0;
        Long latestImportId = null;
        for (Long appApplyId : appApplyIds) {
            OpenApplyItem item = importSingleAppApplication(operatorId, appApplyId);
            if (item == null) {
                skipped++;
                continue;
            }
            imported++;
            latestImportId = item.id();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", appApplyIds.size());
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("latestImportId", latestImportId);
        return result;
    }

    @PostMapping("/opening/applications/{id}/import-app")
    @Transactional
    public OpenApplyItem importOneAppApplication(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long id
    ) {
        Long operatorId = requireOperatorId(token);
        OpenApplyItem imported = importSingleAppApplication(operatorId, id);
        if (imported == null) {
            throw new IllegalArgumentException("该申请已导入或状态不可导入");
        }
        return imported;
    }

    @PostMapping("/opening/applications/{id}/approve")
    @Transactional
    public OpeningDecisionResult approveApplication(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long id,
            @Valid @RequestBody DecisionRequest request
    ) {
        Long operatorId = requireOperatorId(token);
        OpenApplyItem item = mustGetApplyFromDb(id);
        if (!"IN_REVIEW".equals(item.status())) {
            throw new IllegalArgumentException("仅审核中状态可通过");
        }
        validateApplyForApproval(item);

        Integer existed = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM cust_customer WHERE id_type = ? AND id_no = ?",
                Integer.class,
                item.idType(), item.idNo()
        );
        if (existed != null && existed > 0) {
            throw new IllegalArgumentException("证件已存在正式客户，禁止重复开户");
        }

        jdbcTemplate.update("UPDATE cust_import_record SET review_status = ? WHERE id = ?", "APPROVED", id);
        addAuditLog(id, item.applyNo(), operatorId, "IN_REVIEW", "APPROVED", "APPROVE", request.comment());

        TemplateItem template = mustGetTemplate(item.acctClsCode());

        long customerId = nextId();
        String customerCode = "C" + LocalDate.now().format(DATE_FMT) + String.format("%04d", customerId % 10000);
        jdbcTemplate.update(
                "INSERT INTO cust_customer (id, customer_code, customer_name, id_type, id_no, customer_type, acct_cls_code, customer_status, risk_level, open_date, source_record_id, created_by, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, 'INDIVIDUAL', ?, 'ACTIVE', 'R2', CURDATE(), ?, ?, NOW(), NOW())",
                customerId, customerCode, item.customerName(), item.idType(), item.idNo(), item.acctClsCode(), id, operatorId
        );

        long fundId = nextId();
        String fundAccountNo = "FA" + LocalDate.now().format(DATE_FMT) + String.format("%06d", fundId % 1000000);
        jdbcTemplate.update(
                "INSERT INTO acct_fund_account (id, fund_account_no, customer_id, currency_code, current_balance, available_balance, frozen_balance, status, version, opened_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0.0000, 'NORMAL', 0, NOW(), NOW())",
                fundId, fundAccountNo, customerId, template.currencyCode(), template.initCashBalance(), template.initCashBalance()
        );
        createAssetJournal(
                customerId, fundId, null, "CASH", "INIT_CASH", "I",
                BigDecimal.ZERO, template.initCashBalance(), template.initCashBalance(),
                "OPENING", id, item.applyNo(), operatorId, "开户初始化资金"
        );

        String market = item.shareholderMarket();
        if (isBlank(market)) {
            market = !isBlank(item.shShareholderAcct()) ? "SH" : (!isBlank(item.szShareholderAcct()) ? "SZ" : "SH");
        }
        if ("SH".equalsIgnoreCase(market)) {
            long shId = nextId();
            String shAcct = item.shShareholderAcct() == null || item.shShareholderAcct().isBlank()
                    ? "A" + String.format("%09d", shId % 1000000000)
                    : item.shShareholderAcct();
            jdbcTemplate.update(
                    "INSERT INTO acct_shareholder_account (id, customer_id, market_code, shareholder_account_no, account_status, opened_at, updated_at, remark) " +
                            "VALUES (?, ?, '1', ?, 'NORMAL', NOW(), NOW(), '开户自动生成')",
                    shId, customerId, shAcct
            );
        } else if ("SZ".equalsIgnoreCase(market)) {
            long szId = nextId();
            String szAcct = item.szShareholderAcct() == null || item.szShareholderAcct().isBlank()
                    ? "B" + String.format("%09d", szId % 1000000000)
                    : item.szShareholderAcct();
            jdbcTemplate.update(
                    "INSERT INTO acct_shareholder_account (id, customer_id, market_code, shareholder_account_no, account_status, opened_at, updated_at, remark) " +
                            "VALUES (?, ?, '0', ?, 'NORMAL', NOW(), NOW(), '开户自动生成')",
                    szId, customerId, szAcct
            );
        } else {
            throw new IllegalArgumentException("申请市场无效，仅支持SH/SZ");
        }

        List<TemplateHoldingItem> holdings = listTemplateHoldings(template.id());
        int positionCount = 0;
        for (TemplateHoldingItem h : holdings) {
            long positionId = nextId();
            BigDecimal costPrice = h.costPrice() == null ? BigDecimal.ZERO : h.costPrice();
            BigDecimal qty = BigDecimal.valueOf(h.initQty());
            BigDecimal marketValue = costPrice.multiply(qty);
            jdbcTemplate.update(
                    """
                    INSERT INTO acct_position
                    (id, fund_account_id, customer_id, security_id, market_code, security_code_snapshot,
                     total_qty, available_qty, frozen_qty, cost_price, last_price, market_value, position_status, version, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'NORMAL', 0, NOW())
                    """,
                    positionId, fundId, customerId, h.securityId(), h.marketCode(), h.securityCode(),
                    h.initQty(), h.initQty(), costPrice, costPrice, marketValue
            );
            createAssetJournal(
                    customerId, fundId, h.securityId(), "POSITION", "INIT_POSITION", "I",
                    BigDecimal.ZERO, qty, qty,
                    "OPENING", id, item.applyNo(), operatorId, "开户初始化持仓:" + h.securityCode()
            );
            positionCount++;
        }

        jdbcTemplate.update("UPDATE cust_import_record SET review_status = ?, created_customer_id = ? WHERE id = ?", "OPENED", customerId, id);
        addAuditLog(id, item.applyNo(), operatorId, "APPROVED", "OPENED", "OPEN_ACCOUNT", "生成客户与账户");

        OpenApplyItem opened = mustGetApplyFromDb(id);
        CustomerItem customer = jdbcTemplate.queryForObject(
                "SELECT id, customer_code, customer_name, id_type, id_no, acct_cls_code, customer_status, risk_level, open_date, source_record_id FROM cust_customer WHERE id = ?",
                customerMapper(), customerId
        );
        FundAccountItem fund = jdbcTemplate.queryForObject(
                "SELECT id, fund_account_no, customer_id, current_balance, available_balance, frozen_balance, status, opened_at FROM acct_fund_account WHERE id = ?",
                fundMapper(), fundId
        );
        List<ShareholderAccountItem> shList = jdbcTemplate.query(
                "SELECT id, customer_id, market_code, shareholder_account_no, account_status, opened_at FROM acct_shareholder_account WHERE customer_id = ? ORDER BY market_code DESC",
                shareholderMapper(), customerId
        );
        return new OpeningDecisionResult(opened, customer, fund, shList, template.initCashBalance(), positionCount);
    }

    @PostMapping("/opening/applications/{id}/reject")
    @Transactional
    public OpenApplyItem rejectApplication(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long id,
            @Valid @RequestBody DecisionRequest request
    ) {
        Long operatorId = requireOperatorId(token);
        OpenApplyItem item = mustGetApplyFromDb(id);
        if (!"IN_REVIEW".equals(item.status())) {
            throw new IllegalArgumentException("仅审核中状态可拒绝");
        }
        jdbcTemplate.update("UPDATE cust_import_record SET review_status = ?, validate_message = ? WHERE id = ?", "REJECTED", request.comment(), id);
        addAuditLog(id, item.applyNo(), operatorId, "IN_REVIEW", "REJECTED", "REJECT", request.comment());
        return mustGetApplyFromDb(id);
    }

    @GetMapping("/opening/applications/{id}/audit-logs")
    public List<OpenAuditLogItem> listAuditLogs(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long id
    ) {
        requireOperatorId(token);
        return jdbcTemplate.query(
                "SELECT id, record_id, audit_no, auditor_id, previous_status, current_status, audit_comment, created_at " +
                        "FROM cust_open_audit_log WHERE record_id = ? ORDER BY created_at ASC",
                (rs, rowNum) -> new OpenAuditLogItem(
                        rs.getLong("id"),
                        rs.getLong("record_id"),
                        "",
                        rs.getString("audit_no"),
                        rs.getLong("auditor_id"),
                        rs.getString("previous_status"),
                        rs.getString("current_status"),
                        "",
                        rs.getString("audit_comment"),
                        asLocalDateTime(rs.getString("created_at"))
                ),
                id
        );
    }

    @GetMapping("/opening/applications/{id}/result")
    public OpeningDecisionResult getOpeningResult(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long id
    ) {
        requireOperatorId(token);
        OpenApplyItem apply = mustGetApplyFromDb(id);
        if (!"OPENED".equals(apply.status()) || apply.customerId() == null) {
            throw new IllegalArgumentException("该申请尚未完成开户");
        }
        CustomerItem customer = jdbcTemplate.queryForObject(
                "SELECT id, customer_code, customer_name, id_type, id_no, acct_cls_code, customer_status, risk_level, open_date, source_record_id FROM cust_customer WHERE id = ?",
                customerMapper(), apply.customerId()
        );
        FundAccountItem fund = jdbcTemplate.queryForObject(
                """
                SELECT id, fund_account_no, customer_id, current_balance, available_balance, frozen_balance, status, opened_at
                FROM acct_fund_account
                WHERE customer_id = ?
                ORDER BY opened_at DESC
                LIMIT 1
                """,
                fundMapper(), apply.customerId()
        );
        List<ShareholderAccountItem> shList = jdbcTemplate.query(
                """
                SELECT id, customer_id, market_code, shareholder_account_no, account_status, opened_at
                FROM acct_shareholder_account
                WHERE customer_id = ?
                ORDER BY market_code DESC
                """,
                shareholderMapper(), apply.customerId()
        );
        BigDecimal initCash = jdbcTemplate.queryForObject(
                """
                SELECT change_amount
                FROM acct_asset_journal
                WHERE customer_id = ? AND change_type = 'INIT_CASH'
                ORDER BY occur_time ASC
                LIMIT 1
                """,
                BigDecimal.class,
                apply.customerId()
        );
        Integer initPositionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM acct_position WHERE customer_id = ?",
                Integer.class,
                apply.customerId()
        );
        return new OpeningDecisionResult(
                apply,
                customer,
                fund,
                shList,
                initCash == null ? BigDecimal.ZERO : initCash,
                initPositionCount == null ? 0 : initPositionCount
        );
    }

    @GetMapping("/opening/customers")
    public List<CustomerItem> listOpenedCustomers(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String keyword
    ) {
        requireOperatorId(token);
        String keywordLike = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.trim() + "%";
        return jdbcTemplate.query(
                """
                SELECT id, customer_code, customer_name, id_type, id_no, acct_cls_code, customer_status, risk_level, open_date, source_record_id
                FROM cust_customer
                WHERE (? IS NULL OR customer_code LIKE ? OR customer_name LIKE ? OR id_no LIKE ?)
                ORDER BY id DESC
                LIMIT 200
                """,
                customerMapper(),
                keywordLike, keywordLike, keywordLike, keywordLike
        );
    }

    @GetMapping("/opening/customers/{customerId}/assets")
    public Map<String, Object> customerAssets(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long customerId
    ) {
        requireOperatorId(token);
        List<CustomerItem> customers = jdbcTemplate.query(
                "SELECT id, customer_code, customer_name, id_type, id_no, acct_cls_code, customer_status, risk_level, open_date, source_record_id FROM cust_customer WHERE id = ?",
                customerMapper()
                , customerId
        );
        if (customers.isEmpty()) {
            throw new IllegalArgumentException("客户不存在");
        }

        List<FundAccountItem> funds = jdbcTemplate.query(
                """
                SELECT id, fund_account_no, customer_id, current_balance, available_balance, frozen_balance, status, opened_at
                FROM acct_fund_account
                WHERE customer_id = ?
                ORDER BY opened_at DESC
                """,
                fundMapper(),
                customerId
        );
        List<ShareholderAccountItem> shareholders = jdbcTemplate.query(
                """
                SELECT id, customer_id, market_code, shareholder_account_no, account_status, opened_at
                FROM acct_shareholder_account
                WHERE customer_id = ?
                ORDER BY market_code DESC, opened_at DESC
                """,
                shareholderMapper(),
                customerId
        );
        List<Map<String, Object>> positions = jdbcTemplate.queryForList(
                """
                SELECT p.id, p.market_code, p.security_code_snapshot, s.security_name,
                       p.total_qty, p.available_qty, p.frozen_qty, p.cost_price, p.last_price, p.market_value, p.position_status,
                       q.current_price AS quote_price
                FROM acct_position p
                LEFT JOIN md_security s ON s.id = p.security_id
                LEFT JOIN md_market_quote q
                  ON q.security_id = p.security_id
                 AND q.quote_time = (
                     SELECT MAX(q2.quote_time) FROM md_market_quote q2 WHERE q2.security_id = p.security_id
                 )
                WHERE p.customer_id = ?
                ORDER BY p.market_code ASC, p.security_code_snapshot ASC
                """,
                customerId
        );

        Map<String, Object> result = new HashMap<>();
        result.put("customer", customers.get(0));
        result.put("fundAccounts", funds);
        result.put("shareholderAccounts", shareholders);
        result.put("positions", positions);
        return result;
    }

    @GetMapping("/opening/customers/{customerId}/orders")
    public List<Map<String, Object>> customerOrders(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long customerId
    ) {
        requireOperatorId(token);
        return jdbcTemplate.queryForList(
                """
                SELECT o.order_no, o.source_type, fa.fund_account_no, o.market_code, o.security_code, o.security_name_snapshot, o.trade_direction,
                       order_price, order_qty, filled_qty, (order_qty - filled_qty) AS remain_qty, order_status, created_at
                FROM trd_order o
                LEFT JOIN acct_fund_account fa ON fa.id = o.fund_account_id
                WHERE o.customer_id = ?
                ORDER BY o.created_at DESC
                LIMIT 200
                """,
                customerId
        );
    }

    @GetMapping("/opening/customers/{customerId}/trades")
    public List<Map<String, Object>> customerTrades(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable Long customerId
    ) {
        requireOperatorId(token);
        return jdbcTemplate.queryForList(
                """
                SELECT trade_no, order_no, market_code, security_code, trade_direction,
                       trade_price, trade_qty, trade_amount, commission_amount, tax_amount, net_settle_amount, trade_time
                FROM trd_trade
                WHERE customer_id = ?
                ORDER BY trade_time DESC
                LIMIT 200
                """,
                customerId
        );
    }

    private Long requireOperatorId(String token) {
        Long opId = jdbcTemplate.queryForObject(
                "SELECT operator_id FROM op_login_session WHERE token = ? AND expire_at > NOW()",
                Long.class,
                token
        );
        if (opId == null) {
            throw new IllegalArgumentException("登录已失效，请重新登录");
        }
        Integer available = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_operator WHERE id = ? AND status = '1'",
                Integer.class,
                opId
        );
        if (available == null || available == 0) {
            throw new IllegalArgumentException("操作员不可用");
        }
        return opId;
    }

    private OpenApplyItem mustGetApplyFromDb(Long id) {
        List<OpenApplyItem> list = jdbcTemplate.query(
                """
                SELECT ir.id, ir.record_no, b.import_mode, ir.customer_name, ir.id_type, ir.id_no,
                       ir.acct_cls_code, ir.sh_shareholder_acct, ir.sz_shareholder_acct,
                       ir.review_status, ir.created_at, ir.created_customer_id,
                       COALESCE(oa.shareholder_market,
                                CASE
                                  WHEN ir.sh_shareholder_acct IS NOT NULL AND ir.sh_shareholder_acct <> '' THEN 'SH'
                                  WHEN ir.sz_shareholder_acct IS NOT NULL AND ir.sz_shareholder_acct <> '' THEN 'SZ'
                                  ELSE NULL
                                END) AS shareholder_market
                FROM cust_import_record ir
                JOIN cust_import_batch b ON b.id = ir.batch_id
                LEFT JOIN cust_open_apply oa ON oa.apply_no = ir.record_no
                WHERE ir.id = ?
                """,
                openApplyMapper(),
                id
        );
        if (list.isEmpty()) {
            throw new IllegalArgumentException("开户申请不存在: " + id);
        }
        return list.get(0);
    }

    private String createSessionToken(Long operatorId) {
        ensureSessionTable();
        String token = "op_" + UUID.randomUUID();
        long sid = nextId();
        jdbcTemplate.update(
                "INSERT INTO op_login_session (id, token, operator_id, expire_at, created_at) VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 8 HOUR), NOW())",
                sid, token, operatorId
        );
        return token;
    }

    private void ensureSessionTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS op_login_session (
                  id BIGINT PRIMARY KEY,
                  token VARCHAR(80) NOT NULL UNIQUE,
                  operator_id BIGINT NOT NULL,
                  expire_at DATETIME NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  KEY idx_op_login_session_operator (operator_id),
                  KEY idx_op_login_session_expire (expire_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private void validateApplyForApproval(OpenApplyItem item) {
        if (isBlank(item.customerName()) || isBlank(item.idType()) || isBlank(item.idNo()) || isBlank(item.acctClsCode())) {
            throw new IllegalArgumentException("申请资料不完整，无法通过审核");
        }
        if (!isBlank(item.shShareholderAcct()) && !item.shShareholderAcct().matches("^A\\d{9}$")) {
            throw new IllegalArgumentException("沪市股东账号格式不合法，应为A+9位数字");
        }
        if (!isBlank(item.szShareholderAcct()) && !item.szShareholderAcct().matches("^B\\d{9}$")) {
            throw new IllegalArgumentException("深市股东账号格式不合法，应为B+9位数字");
        }
        Integer existed = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM md_customer_template WHERE acct_cls_code = ? AND status = '1'",
                Integer.class,
                item.acctClsCode()
        );
        if (existed == null || existed == 0) {
            throw new IllegalArgumentException("账户类别不在有效字典中，无法开户");
        }
    }

    private TemplateItem mustGetTemplate(String acctClsCode) {
        List<TemplateItem> list = jdbcTemplate.query(
                """
                SELECT id, acct_cls_code, currency_code, init_cash_balance
                FROM md_customer_template
                WHERE acct_cls_code = ? AND status = '1'
                LIMIT 1
                """,
                (rs, rowNum) -> new TemplateItem(
                        rs.getLong("id"),
                        rs.getString("acct_cls_code"),
                        rs.getString("currency_code"),
                        rs.getBigDecimal("init_cash_balance")
                ),
                acctClsCode
        );
        if (list.isEmpty()) {
            throw new IllegalArgumentException("未配置账户类别模板: " + acctClsCode);
        }
        return list.get(0);
    }

    private List<TemplateHoldingItem> listTemplateHoldings(Long templateId) {
        return jdbcTemplate.query(
                """
                SELECT h.security_id, h.init_qty, h.cost_price, s.market_code, s.security_code
                FROM md_customer_template_holding h
                JOIN md_security s ON s.id = h.security_id
                WHERE h.template_id = ? AND h.status = '1' AND h.init_qty > 0
                ORDER BY h.sort_no ASC, h.id ASC
                """,
                (rs, rowNum) -> new TemplateHoldingItem(
                        rs.getLong("security_id"),
                        rs.getLong("init_qty"),
                        rs.getBigDecimal("cost_price"),
                        rs.getString("market_code"),
                        rs.getString("security_code")
                ),
                templateId
        );
    }

    private void createAssetJournal(
            Long customerId, Long fundAccountId, Long securityId, String assetType, String changeType, String direction,
            BigDecimal beforeAmount, BigDecimal changeAmount, BigDecimal afterAmount,
            String refType, Long refId, String refNo, Long operatorId, String remark
    ) {
        long id = nextId();
        String journalNo = "JNL" + LocalDate.now().format(DATE_FMT) + String.format("%06d", id % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO acct_asset_journal
                (id, journal_no, customer_id, fund_account_id, security_id, asset_type, change_type, direction,
                 before_amount, change_amount, after_amount, ref_type, ref_id, ref_no, operator_id, occur_time, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?)
                """,
                id, journalNo, customerId, fundAccountId, securityId, assetType, changeType, direction,
                beforeAmount, changeAmount, afterAmount, refType, refId, refNo, operatorId, remark
        );
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private OpenApplyItem importSingleAppApplication(Long operatorId, Long appApplyId) {
        List<AppApplyItem> appRows = jdbcTemplate.query(
                """
                SELECT id, apply_no, customer_name, id_type, id_no, mobile, acct_cls_code, sh_account, sz_account, apply_status, created_at
                FROM cust_open_apply
                WHERE id = ?
                """,
                (rs, rowNum) -> new AppApplyItem(
                        rs.getLong("id"),
                        rs.getString("apply_no"),
                        rs.getString("customer_name"),
                        rs.getString("id_type"),
                        rs.getString("id_no"),
                        rs.getString("mobile"),
                        rs.getString("acct_cls_code"),
                        rs.getString("sh_account"),
                        rs.getString("sz_account"),
                        rs.getString("apply_status"),
                        asLocalDateTime(rs.getString("created_at"))
                ),
                appApplyId
        );
        if (appRows.isEmpty()) {
            return null;
        }
        AppApplyItem app = appRows.get(0);
        if (!"WAIT_AUDIT".equalsIgnoreCase(app.status())) {
            return null;
        }

        Integer duplicate = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM cust_import_record
                WHERE id_type = ? AND id_no = ? AND review_status IN ('WAIT_AUDIT', 'IN_REVIEW', 'APPROVED', 'OPENED')
                """,
                Integer.class,
                app.idType(),
                app.idNo()
        );
        if (duplicate != null && duplicate > 0) {
            jdbcTemplate.update("UPDATE cust_open_apply SET apply_status = 'IMPORTED', updated_at = NOW() WHERE id = ?", app.id());
            return null;
        }

        long batchId = nextId();
        String batchNo = "IB" + LocalDate.now().format(DATE_FMT) + String.format("%05d", batchId % 100000);
        jdbcTemplate.update(
                """
                INSERT INTO cust_import_batch
                (id, batch_no, import_mode, source_file_name, total_count, pass_count, reject_count, batch_status, imported_by, imported_at, remark)
                VALUES (?, ?, 'APP', 'APP_OPEN_APPLY', 1, 1, 0, 'DONE', ?, NOW(), ?)
                """,
                batchId, batchNo, operatorId, "APP申请导入:" + app.applyNo()
        );

        long recordId = nextId();
        jdbcTemplate.update(
                """
                INSERT INTO cust_import_record
                (id, batch_id, record_no, row_no, customer_name, id_type, id_no, acct_cls_code,
                 sh_shareholder_acct, sz_shareholder_acct, import_status, validate_message, review_status, created_customer_id, created_at)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 'PASS', NULL, 'WAIT_AUDIT', NULL, NOW())
                """,
                recordId, batchId, app.applyNo(), app.customerName(), app.idType(), app.idNo(), app.acctClsCode(),
                app.shAccount(), app.szAccount()
        );
        jdbcTemplate.update("UPDATE cust_open_apply SET apply_status = 'IMPORTED', updated_at = NOW() WHERE id = ?", app.id());
        return mustGetApplyFromDb(recordId);
    }

    private void addAuditLog(Long applyId, String applyNo, Long operatorId, String preStatus, String currStatus, String action, String comment) {
        long id = nextId();
        String auditNo = "AUD" + LocalDate.now().format(DATE_FMT) + String.format("%05d", id % 100000);
        jdbcTemplate.update(
                "INSERT INTO cust_open_audit_log (id, record_id, audit_no, audit_result, audit_comment, auditor_id, previous_status, current_status, audited_at, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                id, applyId, auditNo, action, comment, operatorId, preStatus, currStatus
        );
    }

    private RowMapper<OpenApplyItem> openApplyMapper() {
        return (rs, rowNum) -> new OpenApplyItem(
                rs.getLong("id"),
                rs.getString("record_no"),
                rs.getString("import_mode"),
                rs.getString("customer_name"),
                rs.getString("id_type"),
                rs.getString("id_no"),
                rs.getString("acct_cls_code"),
                rs.getString("sh_shareholder_acct"),
                rs.getString("sz_shareholder_acct"),
                normalizeStatus(rs.getString("review_status")),
                null,
                null,
                asLocalDateTime(rs.getString("created_at")),
                null,
                rs.getObject("created_customer_id") == null ? null : rs.getLong("created_customer_id"),
                null,
                rs.getString("shareholder_market")
        );
    }

    private RowMapper<OpenApplyItem> appApplyMapper() {
        return (rs, rowNum) -> new OpenApplyItem(
                rs.getLong("id"),
                rs.getString("apply_no"),
                "APP_API",
                rs.getString("customer_name"),
                rs.getString("id_type"),
                rs.getString("id_no"),
                rs.getString("acct_cls_code"),
                rs.getString("sh_account"),
                rs.getString("sz_account"),
                normalizeStatus(rs.getString("apply_status")),
                null,
                null,
                asLocalDateTime(rs.getString("created_at")),
                rs.getString("audit_comment"),
                null,
                null,
                rs.getString("shareholder_market")
        );
    }

    private RowMapper<CustomerItem> customerMapper() {
        return (rs, rowNum) -> new CustomerItem(
                rs.getLong("id"),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getString("id_type"),
                rs.getString("id_no"),
                rs.getString("acct_cls_code"),
                rs.getString("customer_status"),
                rs.getString("risk_level"),
                rs.getDate("open_date").toLocalDate(),
                rs.getObject("source_record_id") == null ? null : rs.getLong("source_record_id")
        );
    }

    private RowMapper<FundAccountItem> fundMapper() {
        return (rs, rowNum) -> new FundAccountItem(
                rs.getLong("id"),
                rs.getString("fund_account_no"),
                rs.getLong("customer_id"),
                rs.getBigDecimal("current_balance"),
                rs.getBigDecimal("available_balance"),
                rs.getBigDecimal("frozen_balance"),
                rs.getString("status"),
                asLocalDateTime(rs.getString("opened_at"))
        );
    }

    private RowMapper<ShareholderAccountItem> shareholderMapper() {
        return (rs, rowNum) -> new ShareholderAccountItem(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("market_code"),
                rs.getString("shareholder_account_no"),
                rs.getString("account_status"),
                asLocalDateTime(rs.getString("opened_at"))
        );
    }

    private String normalizeStatus(String status) {
        if (status == null) return "SUBMITTED";
        return switch (status) {
            case "WAIT_AUDIT" -> "SUBMITTED";
            case "PASS" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> status;
        };
    }

    private LocalDateTime asLocalDateTime(String val) {
        if (val == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(val.replace(" ", "T"));
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    record OperatorItem(Long id, String operatorCode, String operatorName, String passwordHash, String status) {}
    record OpenApplyItem(
            Long id, String applyNo, String sourceType, String customerName, String idType, String idNo,
            String acctClsCode, String shShareholderAcct, String szShareholderAcct, String status,
            Long reviewerId, LocalDateTime reviewTime, LocalDateTime createdAt, String reviewComment,
            Long customerId, Long fundAccountId, String shareholderMarket
    ) {}
    record CustomerItem(
            Long id, String customerCode, String customerName, String idType, String idNo, String acctClsCode,
            String customerStatus, String riskLevel, LocalDate openDate, Long sourceApplyId
    ) {}
    record FundAccountItem(
            Long id, String fundAccountNo, Long customerId, BigDecimal currentBalance, BigDecimal availableBalance,
            BigDecimal frozenBalance, String status, LocalDateTime openedAt
    ) {}
    record ShareholderAccountItem(
            Long id, Long customerId, String marketCode, String shareholderAccountNo, String status, LocalDateTime openedAt
    ) {}
    record OpenAuditLogItem(
            Long id, Long applyId, String applyNo, String auditNo, Long operatorId, String previousStatus,
            String currentStatus, String action, String comment, LocalDateTime createdAt
    ) {}
    record AppApplyItem(
            Long id, String applyNo, String customerName, String idType, String idNo, String mobile,
            String acctClsCode, String shAccount, String szAccount, String status, LocalDateTime createdAt
    ) {}
    record TemplateItem(Long id, String acctClsCode, String currencyCode, BigDecimal initCashBalance) {}
    record TemplateHoldingItem(Long securityId, Long initQty, BigDecimal costPrice, String marketCode, String securityCode) {}
    record LoginResult(String token, String operatorCode, String operatorName) {}
    record OpeningDecisionResult(
            OpenApplyItem apply, CustomerItem customer, FundAccountItem fundAccount, List<ShareholderAccountItem> shareholderAccounts,
            BigDecimal initCashBalance, Integer initPositionCount
    ) {}

    record LoginRequest(@NotBlank String loginName, @NotBlank String password) {}
    record DecisionRequest(@NotBlank String comment) {}
}
