package com.financial.operator.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.financial.operator.infra.cache.AppMarketQuoteQueryService;
import com.financial.operator.service.TradingFeeRuleService;
import com.financial.operator.service.TradingFeeRuleService.CommissionRule;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/app")
public class AppApiController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final JdbcTemplate jdbcTemplate;
    private final AppMarketQuoteQueryService marketQuoteQueryService;
    private final TradingFeeRuleService tradingFeeRuleService;

    public AppApiController(
            JdbcTemplate jdbcTemplate,
            AppMarketQuoteQueryService marketQuoteQueryService,
            TradingFeeRuleService tradingFeeRuleService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.marketQuoteQueryService = marketQuoteQueryService;
        this.tradingFeeRuleService = tradingFeeRuleService;
    }

    @PostMapping("/auth/register")
    @Transactional
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        if (isBlank(request.username()) || isBlank(request.password())) {
            return ApiResponse.fail(1001, "参数错误");
        }

        Integer existedByUsername = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE username = ?",
                Integer.class,
                request.username()
        );
        if (existedByUsername != null && existedByUsername > 0) {
            return ApiResponse.fail(1002, "用户名已存在");
        }

        if (!isBlank(request.mobile())) {
            Integer existedByMobile = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM app_user WHERE mobile = ?",
                    Integer.class,
                    request.mobile()
            );
            if (existedByMobile != null && existedByMobile > 0) {
                return ApiResponse.fail(1003, "手机号已存在");
            }
        }

        long id = nextId();
        String hash = PASSWORD_ENCODER.encode(request.password());
        jdbcTemplate.update(
                "INSERT INTO app_user (id, username, password_hash, mobile, status, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                id, request.username(), hash, emptyToNull(request.mobile())
        );

        return ApiResponse.ok(Map.of("userId", id, "username", request.username()));
    }

    @PostMapping("/auth/login")
    @Transactional
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String account = request.account();
        List<AppUserRow> users = jdbcTemplate.query(
                "SELECT id, username, mobile, password_hash, status FROM app_user WHERE username = ? OR mobile = ? LIMIT 1",
                (rs, rowNum) -> new AppUserRow(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("mobile"),
                        rs.getString("password_hash"),
                        rs.getString("status")
                ),
                account,
                account
        );
        if (users.isEmpty()) {
            return ApiResponse.fail(1004, "用户名或密码错误");
        }

        AppUserRow user = users.get(0);
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            return ApiResponse.fail(1004, "用户名或密码错误");
        }
        if (!PASSWORD_ENCODER.matches(request.password(), user.passwordHash())) {
            return ApiResponse.fail(1004, "用户名或密码错误");
        }

        String token = "app_" + UUID.randomUUID();
        long sid = nextId();
        jdbcTemplate.update(
                "INSERT INTO app_login_session (id, token, user_id, expire_at, created_at) VALUES (?, ?, ?, DATE_ADD(NOW(), INTERVAL 8 HOUR), NOW())",
                sid, token, user.id()
        );

        return ApiResponse.ok(Map.of(
                "token", token,
                "userId", user.id(),
                "username", user.username()
        ));
    }

    @PostMapping("/open-account/apply")
    @Transactional
    public ApiResponse<Map<String, Object>> openAccountApply(@Valid @RequestBody OpenAccountApplyRequest request) {
        if (request.userId() == null || isBlank(request.customerName()) || isBlank(request.idType())
                || isBlank(request.idNo()) || isBlank(request.mobile()) || isBlank(request.acctClsCode())
                || isBlank(request.shareholderMarket())) {
            return ApiResponse.fail(1001, "参数错误");
        }
        if (!("SH".equalsIgnoreCase(request.shareholderMarket()) || "SZ".equalsIgnoreCase(request.shareholderMarket()))) {
            return ApiResponse.fail(2004, "市场参数错误，仅支持SH/SZ");
        }
        if (!request.mobile().matches("^1\\d{10}$")) {
            return ApiResponse.fail(2002, "手机号格式错误，应为11位数字");
        }
        if (!request.idNo().matches("^(\\d{15}|\\d{17}[0-9Xx])$")) {
            return ApiResponse.fail(2003, "证件号格式错误");
        }

        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                request.userId()
        );
        if (userCount == null || userCount == 0) {
            return ApiResponse.fail(1005, "登录态失效");
        }

        Integer duplicate = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM cust_open_apply WHERE id_type = ? AND id_no = ? AND apply_status IN ('WAIT_AUDIT', 'APPROVED')",
                Integer.class,
                request.idType(),
                request.idNo()
        );
        if (duplicate != null && duplicate > 0) {
            return ApiResponse.fail(2001, "开户申请重复");
        }

        long applyId = nextId();
        String applyNo = "OA" + LocalDate.now().format(DATE_FMT) + String.format("%06d", applyId % 1000000);
        String autoShAccount = "A" + String.format("%09d", applyId % 1_000_000_000L);
        String autoSzAccount = "B" + String.format("%09d", (applyId + 7) % 1_000_000_000L);
        jdbcTemplate.update(
                "INSERT INTO cust_open_apply (id, apply_no, user_id, customer_name, id_type, id_no, mobile, acct_cls_code, shareholder_market, sh_account, sz_account, apply_status, audit_comment, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAIT_AUDIT', NULL, NOW(), NOW())",
                applyId, applyNo, request.userId(), request.customerName(), request.idType(), request.idNo(), request.mobile(),
                request.acctClsCode(), request.shareholderMarket(), autoShAccount, autoSzAccount
        );

        return ApiResponse.ok(Map.of(
                "applyId", applyId,
                "applyNo", applyNo,
                "status", "WAIT_AUDIT"
        ));
    }

    @GetMapping("/open-account/applications/latest")
    public ApiResponse<Map<String, Object>> latestOpenAccountApplication(@RequestParam Long userId) {
        if (userId == null) {
            return ApiResponse.fail(1001, "参数错误");
        }
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (userCount == null || userCount == 0) {
            return ApiResponse.fail(1005, "登录态失效");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT oa.id AS apply_id, oa.apply_no, oa.apply_status, ir.review_status
                FROM cust_open_apply oa
                LEFT JOIN cust_import_record ir ON ir.record_no = oa.apply_no
                WHERE oa.user_id = ?
                ORDER BY oa.created_at DESC
                LIMIT 1
                """,
                userId
        );
        if (rows.isEmpty()) {
            return ApiResponse.ok(Map.of());
        }
        Map<String, Object> row = rows.get(0);
        String status = finalStatus(asString(row.get("apply_status")), asString(row.get("review_status")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applyId", row.get("apply_id"));
        data.put("applyNo", row.get("apply_no"));
        data.put("status", status);
        data.put("statusText", statusText(status));
        return ApiResponse.ok(data);
    }

    @GetMapping("/open-account/applications/{applyId}/result")
    public ApiResponse<Map<String, Object>> openAccountResult(@PathVariable Long applyId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT oa.id AS apply_id, oa.apply_no, oa.apply_status, ir.review_status, ir.created_customer_id
                FROM cust_open_apply oa
                LEFT JOIN cust_import_record ir ON ir.record_no = oa.apply_no
                WHERE oa.id = ?
                LIMIT 1
                """,
                applyId
        );
        if (rows.isEmpty()) {
            return ApiResponse.fail(2001, "申请不存在");
        }
        Map<String, Object> row = rows.get(0);
        String status = finalStatus(asString(row.get("apply_status")), asString(row.get("review_status")));
        if (!"OPENED".equals(status)) {
            return ApiResponse.fail(2002, "申请尚未审核通过");
        }
        if (row.get("created_customer_id") == null) {
            return ApiResponse.fail(9999, "开户结果缺失");
        }
        Long customerId = toLong(row.get("created_customer_id"));

        Map<String, Object> customerRow = jdbcTemplate.queryForMap(
                """
                SELECT customer_code, customer_name, acct_cls_code
                FROM cust_customer
                WHERE id = ?
                LIMIT 1
                """,
                customerId
        );
        Map<String, Object> fundRow = jdbcTemplate.queryForMap(
                """
                SELECT fund_account_no, current_balance
                FROM acct_fund_account
                WHERE customer_id = ?
                ORDER BY opened_at DESC
                LIMIT 1
                """,
                customerId
        );
        List<Map<String, Object>> shareholderRows = new ArrayList<>(jdbcTemplate.queryForList(
                """
                SELECT market_code, shareholder_account_no, account_status
                FROM acct_shareholder_account
                WHERE customer_id = ?
                ORDER BY market_code DESC
                """,
                customerId
        ));
        BigDecimal initCashBalance = jdbcTemplate.queryForObject(
                """
                SELECT change_amount
                FROM acct_asset_journal
                WHERE customer_id = ? AND change_type = 'INIT_CASH'
                ORDER BY occur_time ASC
                LIMIT 1
                """,
                BigDecimal.class,
                customerId
        );
        Integer initPositionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM acct_position WHERE customer_id = ?",
                Integer.class,
                customerId
        );

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customerCode", customerRow.get("customer_code"));
        customer.put("customerName", customerRow.get("customer_name"));
        customer.put("acctClsCode", customerRow.get("acct_cls_code"));

        Map<String, Object> fund = new LinkedHashMap<>();
        fund.put("fundAccountNo", fundRow.get("fund_account_no"));
        fund.put("currentBalance", fundRow.get("current_balance"));

        List<Map<String, Object>> shareholderAccounts = new ArrayList<>();
        for (Map<String, Object> rowItem : shareholderRows) {
            String marketCode = asString(rowItem.get("market_code"));
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("marketCode", marketCode);
            account.put("marketName", "1".equals(marketCode) ? "沪A" : "深A");
            account.put("shareholderAccountNo", rowItem.get("shareholder_account_no"));
            account.put("status", rowItem.get("account_status"));
            shareholderAccounts.add(account);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applyId", row.get("apply_id"));
        data.put("applyNo", row.get("apply_no"));
        data.put("status", status);
        data.put("customer", customer);
        data.put("fundAccount", fund);
        data.put("initCashBalance", initCashBalance == null ? fundRow.get("current_balance") : initCashBalance);
        data.put("initPositionCount", initPositionCount == null ? 0 : initPositionCount);
        data.put("shareholderAccounts", shareholderAccounts);
        return ApiResponse.ok(data);
    }

    @GetMapping("/trade/fund-accounts")
    public ApiResponse<List<Map<String, Object>>> listTradeFundAccounts(@RequestParam Long userId) {
        if (userId == null) {
            return ApiResponse.fail(1001, "参数错误");
        }
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (userCount == null || userCount == 0) {
            return ApiResponse.fail(1005, "登录态失效");
        }
        Long customerId = findLatestOpenedCustomerIdByUser(userId);
        if (customerId == null) {
            return ApiResponse.ok(List.of());
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT fund_account_no, current_balance, available_balance, frozen_balance, status, opened_at
                FROM acct_fund_account
                WHERE customer_id = ?
                ORDER BY opened_at DESC
                """,
                customerId
        );
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fundAccountNo", asString(row.get("fund_account_no")));
            BigDecimal av = asDecimal(row.get("available_balance"));
            BigDecimal fr = asDecimal(row.get("frozen_balance"));
            // 与总资产口径一致：对外「当前余额」= 可用+冻结，避免 current_balance 字段滞后导致与可用/冻结展示矛盾
            item.put("currentBalance", fundCashTotal(av, fr));
            item.put("availableBalance", av);
            item.put("frozenBalance", fr);
            item.put("status", asString(row.get("status")));
            item.put("openedAt", row.get("opened_at"));
            list.add(item);
        }
        return ApiResponse.ok(list);
    }

    @PostMapping("/trade/risk/precheck")
    public ApiResponse<Map<String, Object>> tradeRiskPrecheck(@Valid @RequestBody TradeRiskPrecheckRequest request) {
        List<Map<String, Object>> violations = new ArrayList<>();
        if (request.userId() == null || isBlank(request.fundAccountNo()) || isBlank(request.marketCode()) || isBlank(request.securityCode())
                || isBlank(request.tradeDirection()) || request.price() == null || request.quantity() == null) {
            return ApiResponse.fail(1001, "参数错误");
        }
        if (request.price().compareTo(BigDecimal.ZERO) <= 0 || request.quantity() <= 0) {
            return ApiResponse.fail(1001, "价格和数量必须大于0");
        }

        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                request.userId()
        );
        if (userCount == null || userCount == 0) {
            addViolation(violations, "AUTH_INVALID", "用户未登录或已失效");
            return precheckResult(violations, null);
        }

        Long customerId = findLatestOpenedCustomerIdByUser(request.userId());
        if (customerId == null) {
            addViolation(violations, "NOT_OPENED", "客户未开户，禁止交易");
            return precheckResult(violations, null);
        }

        List<Map<String, Object>> fundRows = jdbcTemplate.queryForList(
                """
                SELECT id, available_balance, status
                FROM acct_fund_account
                WHERE customer_id = ? AND fund_account_no = ?
                LIMIT 1
                """,
                customerId, request.fundAccountNo().trim()
        );
        if (fundRows.isEmpty()) {
            addViolation(violations, "FUND_ACCOUNT_INVALID", "资金账户不存在或不属于当前客户");
            return precheckResult(violations, customerId);
        }
        Map<String, Object> fund = fundRows.get(0);
        Long fundAccountId = toLong(fund.get("id"));
        if (!"NORMAL".equalsIgnoreCase(asString(fund.get("status")))) {
            addViolation(violations, "ACCOUNT_FROZEN", "资金账户状态异常，禁止交易");
            return precheckResult(violations, customerId);
        }

        String marketCodeNorm = normalizeMarketCode(request.marketCode());
        if (marketCodeNorm == null) {
            addViolation(violations, "MARKET_INVALID", "市场参数错误，仅支持SH/SZ或1/0");
            return precheckResult(violations, customerId);
        }
        String securityCodeNorm = request.securityCode().trim();
        List<Map<String, Object>> securityRows = jdbcTemplate.queryForList(
                """
                SELECT id, lot_size, listed_status, security_type
                FROM md_security
                WHERE market_code = ? AND security_code = ?
                LIMIT 1
                """,
                marketCodeNorm, securityCodeNorm
        );
        if (securityRows.isEmpty()) {
            addViolation(violations, "SECURITY_NOT_FOUND", "证券代码不存在");
            return precheckResult(violations, customerId);
        }
        Map<String, Object> security = securityRows.get(0);
        Long securityId = toLong(security.get("id"));
        if (!"1".equals(asString(security.get("listed_status")))) {
            addViolation(violations, "SECURITY_NOT_LISTED", "证券不可交易");
        }

        Integer lotSize = ((Number) security.get("lot_size")).intValue();
        if (request.quantity() % lotSize != 0) {
            addViolation(violations, "LOT_SIZE_INVALID", "委托数量不符合交易单位");
        }

        List<Map<String, Object>> quoteRows = jdbcTemplate.queryForList(
                """
                SELECT upper_limit_price, lower_limit_price
                FROM md_market_quote
                WHERE security_id = ?
                ORDER BY quote_time DESC
                LIMIT 1
                """,
                securityId
        );
        if (!quoteRows.isEmpty()) {
            BigDecimal upper = (BigDecimal) quoteRows.get(0).get("upper_limit_price");
            BigDecimal lower = (BigDecimal) quoteRows.get(0).get("lower_limit_price");
            if (upper != null && request.price().compareTo(upper) > 0) {
                addViolation(violations, "PRICE_UPPER_LIMIT", "委托价格超过涨停价");
            }
            if (lower != null && request.price().compareTo(lower) < 0) {
                addViolation(violations, "PRICE_LOWER_LIMIT", "委托价格低于跌停价");
            }
        }

        List<String> acctClsRows = jdbcTemplate.queryForList(
                "SELECT acct_cls_code FROM cust_customer WHERE id = ? LIMIT 1",
                String.class,
                customerId
        );
        String acctClsCode = acctClsRows.isEmpty() || isBlank(acctClsRows.get(0)) ? "NORMAL" : acctClsRows.get(0).trim();
        String securityType = asString(security.get("security_type"));
        if (isBlank(securityType)) {
            securityType = "COMMON";
        }

        String direction = request.tradeDirection().toUpperCase();
        if ("BUY".equals(direction) || "B".equals(direction)) {
            BigDecimal available = (BigDecimal) fund.get("available_balance");
            BigDecimal turnover = request.price().multiply(BigDecimal.valueOf(request.quantity())).setScale(4, RoundingMode.HALF_UP);
            CommissionRule commRule = tradingFeeRuleService.loadCommission(acctClsCode, marketCodeNorm, securityType);
            BigDecimal estComm = tradingFeeRuleService.commissionOnTurnover(turnover, commRule);
            BigDecimal estStampBuy = tradingFeeRuleService.buyStampOnTurnover(turnover, marketCodeNorm, securityType);
            BigDecimal need = turnover.add(estComm).add(estStampBuy).setScale(4, RoundingMode.HALF_UP);
            if (available == null || available.compareTo(need) < 0) {
                addViolation(violations, "INSUFFICIENT_CASH", "可用资金不足（含预估佣金/税费）");
            }
        } else if ("SELL".equals(direction) || "S".equals(direction)) {
            List<Map<String, Object>> positionRows = jdbcTemplate.queryForList(
                    """
                    SELECT available_qty
                    FROM acct_position
                    WHERE fund_account_id = ? AND security_id = ?
                    LIMIT 1
                    """,
                    fundAccountId, securityId
            );
            long availableQty = positionRows.isEmpty() ? 0L : ((Number) positionRows.get(0).get("available_qty")).longValue();
            if (availableQty < request.quantity()) {
                addViolation(violations, "INSUFFICIENT_POSITION", "可卖持仓不足");
            }
        } else {
            addViolation(violations, "DIRECTION_INVALID", "买卖方向仅支持BUY/SELL或B/S");
        }

        return precheckResult(violations, customerId);
    }

    @GetMapping("/market/quotes")
    public ApiResponse<Map<String, Object>> marketQuotes(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        int offset = (pageNo - 1) * size;

        String marketCode = normalizeMarketCode(market);
        String keywordLike = isBlank(keyword) ? null : "%" + keyword.trim() + "%";
        Map<String, Object> data = marketQuoteQueryService.loadQuotesPage(marketCode, keywordLike, pageNo, size, offset);
        return ApiResponse.ok(data);
    }

    @GetMapping("/market/quotes/{market}/{securityCode}")
    public ApiResponse<Map<String, Object>> marketQuoteDetail(
            @PathVariable String market,
            @PathVariable String securityCode
    ) {
        String marketCode = normalizeMarketCode(market);
        if (isBlank(marketCode) || isBlank(securityCode)) {
            return ApiResponse.fail(1001, "参数错误");
        }

        Map<String, Object> item = marketQuoteQueryService.loadQuoteDetail(marketCode, securityCode.trim());
        if (item == null) {
            return ApiResponse.fail(2001, "证券不存在");
        }

        return ApiResponse.ok(item);
    }

    /**
     * App 资产总览：现金以资金户「可用+冻结」合计（与 {@link #fundCashTotal} 一致），证券以持仓市值合计；
     * {@code availableCash} 为各户 available_balance 之和（不含冻结，仅表示可下单现金）。
     */
    @GetMapping("/assets/overview")
    public ApiResponse<Map<String, Object>> assetOverview(@RequestParam Long userId) {
        if (userId == null) {
            return ApiResponse.fail(1001, "参数错误");
        }
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (userCount == null || userCount == 0) {
            return ApiResponse.fail(1005, "登录态失效");
        }
        Long customerId = findLatestOpenedCustomerIdByUser(userId);
        if (customerId == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("availableCash", 0.0);
            empty.put("totalAsset", 0.0);
            empty.put("marketValue", 0.0);
            empty.put("profitLoss", 0.0);
            return ApiResponse.ok(empty);
        }
        BigDecimal sumAvail = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(available_balance),0) FROM acct_fund_account WHERE customer_id = ?",
                BigDecimal.class,
                customerId
        );
        BigDecimal sumCashTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(available_balance + frozen_balance),0) FROM acct_fund_account WHERE customer_id = ?",
                BigDecimal.class,
                customerId
        );
        BigDecimal sumMv = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(market_value),0) FROM acct_position WHERE customer_id = ?",
                BigDecimal.class,
                customerId
        );
        if (sumAvail == null) {
            sumAvail = BigDecimal.ZERO;
        }
        if (sumCashTotal == null) {
            sumCashTotal = BigDecimal.ZERO;
        }
        if (sumMv == null) {
            sumMv = BigDecimal.ZERO;
        }
        BigDecimal totalAsset = sumCashTotal.add(sumMv);
        BigDecimal profitLoss = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(
                    (COALESCE(p.last_price, p.cost_price, 0) - COALESCE(p.cost_price, 0)) * p.total_qty
                ), 0)
                FROM acct_position p
                WHERE p.customer_id = ?
                """,
                BigDecimal.class,
                customerId
        );
        if (profitLoss == null) {
            profitLoss = BigDecimal.ZERO;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("availableCash", sumAvail.doubleValue());
        data.put("totalAsset", totalAsset.doubleValue());
        data.put("marketValue", sumMv.doubleValue());
        data.put("profitLoss", profitLoss.doubleValue());
        return ApiResponse.ok(data);
    }

    /**
     * App 持仓明细列表（按证券）：数量、成本/最新/行情价、市值等；资产页「查看持仓」按钮对接此接口。
     */
    @GetMapping("/assets/positions")
    public ApiResponse<List<Map<String, Object>>> assetPositions(@RequestParam Long userId) {
        if (userId == null) {
            return ApiResponse.fail(1001, "参数错误");
        }
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (userCount == null || userCount == 0) {
            return ApiResponse.fail(1005, "登录态失效");
        }
        Long customerId = findLatestOpenedCustomerIdByUser(userId);
        if (customerId == null) {
            return ApiResponse.ok(List.of());
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT p.market_code, p.security_code_snapshot AS security_code, s.security_name,
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
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            String mc = asString(row.get("market_code"));
            item.put("marketCode", mc);
            item.put("market", "1".equals(mc) ? "SH" : "SZ");
            item.put("securityCode", asString(row.get("security_code")));
            item.put("securityName", row.get("security_name") == null ? "" : asString(row.get("security_name")));
            item.put("totalQty", toLong(row.get("total_qty")));
            item.put("availableQty", toLong(row.get("available_qty")));
            item.put("frozenQty", toLong(row.get("frozen_qty")));
            item.put("costPrice", row.get("cost_price") == null ? null : asDecimal(row.get("cost_price")).doubleValue());
            item.put("lastPrice", row.get("last_price") == null ? null : asDecimal(row.get("last_price")).doubleValue());
            item.put("quotePrice", row.get("quote_price") == null ? null : asDecimal(row.get("quote_price")).doubleValue());
            item.put("marketValue", row.get("market_value") == null ? null : asDecimal(row.get("market_value")).doubleValue());
            item.put("positionStatus", row.get("position_status") == null ? "" : asString(row.get("position_status")));
            list.add(item);
        }
        return ApiResponse.ok(list);
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Long findLatestOpenedCustomerIdByUser(Long userId) {
        List<Map<String, Object>> custRows = jdbcTemplate.queryForList(
                """
                SELECT ir.created_customer_id AS customer_id
                FROM cust_open_apply oa
                JOIN cust_import_record ir ON ir.record_no = oa.apply_no
                WHERE oa.user_id = ?
                  AND ir.created_customer_id IS NOT NULL
                  AND ir.review_status = 'OPENED'
                ORDER BY oa.updated_at DESC
                LIMIT 1
                """,
                userId
        );
        if (custRows.isEmpty() || custRows.get(0).get("customer_id") == null) {
            return null;
        }
        return toLong(custRows.get(0).get("customer_id"));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    private String finalStatus(String applyStatus, String reviewStatus) {
        if (reviewStatus == null || reviewStatus.isBlank()) {
            return normalizeApplyStatus(applyStatus);
        }
        return normalizeReviewStatus(reviewStatus);
    }

    private String normalizeApplyStatus(String status) {
        if (status == null) return "WAIT_AUDIT";
        return switch (status) {
            case "WAIT_AUDIT", "IMPORTED", "IN_REVIEW", "REJECTED", "OPENED" -> status;
            case "APPROVED" -> "OPENED";
            default -> status;
        };
    }

    private String normalizeReviewStatus(String status) {
        if (status == null) return "WAIT_AUDIT";
        return switch (status) {
            case "WAIT_AUDIT" -> "WAIT_AUDIT";
            case "SUBMITTED" -> "WAIT_AUDIT";
            case "IN_REVIEW" -> "IN_REVIEW";
            case "APPROVED" -> "OPENED";
            case "OPENED" -> "OPENED";
            case "REJECTED" -> "REJECTED";
            default -> status;
        };
    }

    private String statusText(String status) {
        return switch (status) {
            case "OPENED" -> "审核通过";
            case "REJECTED" -> "审核未通过";
            default -> "审核中";
        };
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private void addViolation(List<Map<String, Object>> violations, String ruleCode, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ruleCode", ruleCode);
        item.put("message", message);
        violations.add(item);
    }

    private ApiResponse<Map<String, Object>> precheckResult(List<Map<String, Object>> violations, Long customerId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pass", violations.isEmpty());
        data.put("result", violations.isEmpty() ? "PASS" : "REJECT");
        data.put("customerId", customerId);
        data.put("violations", violations);
        return ApiResponse.ok(data);
    }

    private String normalizeMarketCode(String market) {
        if (isBlank(market)) return null;
        String v = market.trim().toUpperCase();
        return switch (v) {
            case "SH", "1" -> "1";
            case "SZ", "0" -> "0";
            default -> null;
        };
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal d) return d;
        return new BigDecimal(String.valueOf(value));
    }

    /** 资金户对外展示用现金总额（可用+冻结），与总资产中的现金部分口径一致 */
    private static BigDecimal fundCashTotal(BigDecimal availableBalance, BigDecimal frozenBalance) {
        BigDecimal a = availableBalance == null ? BigDecimal.ZERO : availableBalance;
        BigDecimal f = frozenBalance == null ? BigDecimal.ZERO : frozenBalance;
        return a.add(f).setScale(4, RoundingMode.HALF_UP);
    }

    record AppUserRow(Long id, String username, String mobile, String passwordHash, String status) {
    }

    record RegisterRequest(
            @NotBlank String username,
            @NotBlank String password,
            String mobile
    ) {
    }

    record LoginRequest(
            @NotBlank String account,
            @NotBlank String password
    ) {
    }

    record OpenAccountApplyRequest(
            @NotNull Long userId,
            @NotBlank String customerName,
            @NotBlank String idType,
            @NotBlank String idNo,
            @NotBlank String mobile,
            @NotBlank String acctClsCode,
            @NotBlank String shareholderMarket
    ) {
    }

    record TradeRiskPrecheckRequest(
            @NotNull Long userId,
            @NotBlank String fundAccountNo,
            @NotBlank String marketCode,
            @NotBlank String securityCode,
            @NotBlank String tradeDirection,
            @NotNull BigDecimal price,
            @NotNull Long quantity
    ) {
    }

    record ApiResponse<T>(int code, String message, T data) {
        static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(0, "success", data);
        }

        static <T> ApiResponse<T> fail(int code, String message) {
            return new ApiResponse<>(code, message, null);
        }
    }
}
