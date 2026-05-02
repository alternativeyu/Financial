package com.financial.operator.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api")
public class TradeController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 双边佣金率（演示用固定值，可后续接 md_commission_rule） */
    private static final BigDecimal DEFAULT_COMM_RATE = new BigDecimal("0.00025");
    /** 卖出印花税率（演示用） */
    private static final BigDecimal DEFAULT_STAMP_SELL_RATE = new BigDecimal("0.001");

    private final JdbcTemplate jdbcTemplate;

    public TradeController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/operator/trade/orders")
    public ApiResponse<Map<String, Object>> listOrdersByOperator(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String orderStatusGroup,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        try {
            requireOperatorId(token);
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
            int offset = (pageNo - 1) * size;
            String sourceFilter = isBlank(sourceType) ? null : sourceType.trim().toUpperCase();
            String statusFilter = isBlank(orderStatus) ? null : orderStatus.trim().toUpperCase();
            String group = isBlank(orderStatusGroup) ? null : orderStatusGroup.trim().toUpperCase();
            if (group != null && !"ACTIVE".equals(group) && !"CANCELED".equals(group) && !"COMPLETED".equals(group)) {
                throw new IllegalArgumentException("orderStatusGroup 仅支持 ACTIVE、COMPLETED 或 CANCELED");
            }

            String where = """
                    WHERE (? IS NULL OR o.source_type = ?)
                      AND (? IS NULL OR o.order_status = ?)
                      AND (? IS NULL OR (
                           (? = 'ACTIVE' AND o.order_status IN ('INIT','REPORTED','PART_FILLED'))
                        OR (? = 'COMPLETED' AND (o.order_status = 'FILLED' OR (o.order_status = 'PART_CANCELED' AND o.filled_qty > 0)))
                        OR (? = 'CANCELED' AND o.order_status IN ('CANCELED','PART_CANCELED'))
                      ))
                    """;

            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM trd_order o " + where,
                    Integer.class,
                    sourceFilter, sourceFilter, statusFilter, statusFilter, group, group, group, group
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT
                      o.id,
                      o.order_no,
                      o.source_type,
                      o.customer_id,
                      fa.fund_account_no,
                      o.market_code,
                      o.security_code,
                      o.security_name_snapshot,
                      o.trade_direction,
                      o.order_price,
                      o.order_qty,
                      o.filled_qty,
                      o.order_status,
                      o.created_at,
                      c.cancel_status AS last_cancel_status
                    FROM trd_order o
                    LEFT JOIN acct_fund_account fa ON fa.id = o.fund_account_id
                    LEFT JOIN trd_cancel_request c
                      ON c.order_id = o.id
                     AND c.request_time = (
                       SELECT MAX(c2.request_time) FROM trd_cancel_request c2 WHERE c2.order_id = o.id
                     )
                    """ + where + """
                    ORDER BY o.created_at DESC
                    LIMIT ? OFFSET ?
                    """,
                    sourceFilter, sourceFilter, statusFilter, statusFilter, group, group, group, group, size, offset
            );

            List<Map<String, Object>> list = rows.stream().map(this::toOrderListItem).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("page", pageNo);
            data.put("pageSize", size);
            data.put("total", total == null ? 0 : total);
            data.put("list", list);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    /**
     * App 端委托列表（标准流程：列表展示 + 客户端发起撤单，无柜台人工审核环节）。
     */
    @GetMapping("/app/trade/orders")
    public ApiResponse<Map<String, Object>> listOrdersByApp(
            @RequestParam @NotNull Long userId,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String orderStatus,
            /**
             * 与「未完成 / 已完成 / 已撤单」三 Tab 对齐：
             * {@code ONGOING} 进行中；{@code COMPLETED} 有成交且可展示在「已完成」（全成 + 部撤中有成交量）；
             * {@code CANCELED} 含撤单（全撤 + 部撤）。部撤 {@code PART_CANCELED} 同时属于 COMPLETED 与 CANCELED。
             * 传 {@code FILLED} 时与 {@code COMPLETED} 同义（兼容旧参数）。
             * 不传则返回全部；分桶以每条 {@code orderListBuckets} 为准。
             */
            @RequestParam(required = false) String orderListCategory,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        try {
            ensureActiveAppUser(userId);
            Long customerId = findLatestOpenedCustomerByUser(userId);
            if (customerId == null) {
                return ApiResponse.fail(2001, "客户未开户");
            }
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
            int offset = (pageNo - 1) * size;
            String fundFilter = isBlank(fundAccountNo) ? null : fundAccountNo.trim();
            String statusFilter = isBlank(orderStatus) ? null : orderStatus.trim().toUpperCase();
            String listCat = isBlank(orderListCategory) ? null : orderListCategory.trim().toUpperCase();
            if ("FILLED".equals(listCat)) {
                listCat = "COMPLETED";
            }
            if (listCat != null && !"ONGOING".equals(listCat) && !"COMPLETED".equals(listCat) && !"CANCELED".equals(listCat)) {
                throw new IllegalArgumentException("orderListCategory 仅支持 ONGOING、COMPLETED（或别名 FILLED）、CANCELED");
            }

            String where = """
                    WHERE o.customer_id = ?
                      AND o.source_type = 'APP'
                      AND (? IS NULL OR fa.fund_account_no = ?)
                      AND (? IS NULL OR o.order_status = ?)
                      AND (? IS NULL OR (
                           (? = 'ONGOING' AND o.order_status IN ('INIT','REPORTED','PART_FILLED'))
                        OR (? = 'COMPLETED' AND (o.order_status = 'FILLED' OR (o.order_status = 'PART_CANCELED' AND o.filled_qty > 0)))
                        OR (? = 'CANCELED' AND o.order_status IN ('CANCELED','PART_CANCELED'))
                      ))
                    """;

            Integer total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM trd_order o "
                            + "LEFT JOIN acct_fund_account fa ON fa.id = o.fund_account_id "
                            + where,
                    Integer.class,
                    customerId, fundFilter, fundFilter, statusFilter, statusFilter,
                    listCat, listCat, listCat, listCat
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT
                      o.id,
                      o.order_no,
                      o.source_type,
                      o.customer_id,
                      fa.fund_account_no,
                      o.market_code,
                      o.security_code,
                      o.security_name_snapshot,
                      o.trade_direction,
                      o.order_price,
                      o.order_qty,
                      o.filled_qty,
                      o.order_status,
                      o.created_at,
                      c.cancel_status AS last_cancel_status
                    FROM trd_order o
                    LEFT JOIN acct_fund_account fa ON fa.id = o.fund_account_id
                    LEFT JOIN trd_cancel_request c
                      ON c.order_id = o.id
                     AND c.request_time = (
                       SELECT MAX(c2.request_time) FROM trd_cancel_request c2 WHERE c2.order_id = o.id
                     )
                    """
                            + where
                            + """
                    ORDER BY o.created_at DESC
                    LIMIT ? OFFSET ?
                    """,
                    customerId, fundFilter, fundFilter, statusFilter, statusFilter,
                    listCat, listCat, listCat, listCat, size, offset
            );

            List<Map<String, Object>> list = rows.stream().map(this::toOrderListItem).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("page", pageNo);
            data.put("pageSize", size);
            data.put("total", total == null ? 0 : total);
            data.put("list", list);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @PostMapping("/app/trade/orders")
    @Transactional
    public ApiResponse<Map<String, Object>> submitOrderByApp(@Valid @RequestBody AppOrderSubmitRequest request) {
        try {
            ensureActiveAppUser(request.userId());
            Long customerId = findLatestOpenedCustomerByUser(request.userId());
            if (customerId == null) {
                return ApiResponse.fail(2001, "客户未开户，禁止交易");
            }
            return ApiResponse.ok(submitOrderInternal(
                    customerId,
                    request.fundAccountNo(),
                    request.marketCode(),
                    request.securityCode(),
                    request.tradeDirection(),
                    request.price(),
                    request.quantity(),
                    request.requestSeqNo(),
                    "APP",
                    null
            ));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @PostMapping("/operator/trade/orders")
    @Transactional
    public ApiResponse<Map<String, Object>> submitOrderByOperator(
            @RequestHeader("X-Operator-Token") String token,
            @Valid @RequestBody OperatorOrderSubmitRequest request
    ) {
        try {
            Long operatorId = requireOperatorId(token);
            return ApiResponse.ok(submitOrderInternal(
                    request.customerId(),
                    request.fundAccountNo(),
                    request.marketCode(),
                    request.securityCode(),
                    request.tradeDirection(),
                    request.price(),
                    request.quantity(),
                    request.requestSeqNo(),
                    "OPERATOR",
                    operatorId
            ));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @PostMapping("/app/trade/orders/{orderNo}/cancel")
    @Transactional
    public ApiResponse<Map<String, Object>> cancelOrderByApp(
            @PathVariable String orderNo,
            @Valid @RequestBody AppCancelRequest request
    ) {
        try {
            ensureActiveAppUser(request.userId());
            Long customerId = findLatestOpenedCustomerByUser(request.userId());
            if (customerId == null) {
                return ApiResponse.fail(2001, "客户未开户，禁止撤单");
            }
            return ApiResponse.ok(cancelOrderInternal(
                    orderNo,
                    request.requestSeqNo(),
                    request.cancelReason(),
                    null,
                    customerId
            ));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @PostMapping("/operator/trade/orders/{orderNo}/cancel")
    @Transactional
    public ApiResponse<Map<String, Object>> cancelOrderByOperator(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable String orderNo,
            @Valid @RequestBody OperatorCancelRequest request
    ) {
        try {
            Long operatorId = requireOperatorId(token);
            return ApiResponse.ok(cancelOrderInternal(
                    orderNo,
                    request.requestSeqNo(),
                    request.cancelReason(),
                    operatorId,
                    null
            ));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    /**
     * 模拟交易所成交（演示）：成交后按限价冻结口径解冻多冻资金/解冻卖出冻结股数，并写入成交表。
     * 生产环境可由撮合回报回调替代本接口。
     */
    @PostMapping("/operator/trade/orders/{orderNo}/simulate-match")
    @Transactional
    public ApiResponse<Map<String, Object>> simulateTradeMatch(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable String orderNo,
            @Valid @RequestBody SimulateMatchRequest request
    ) {
        try {
            Long operatorId = requireOperatorId(token);
            return ApiResponse.ok(applyTradeSettlementInternal(operatorId, orderNo, request.fillQty(), request.fillPrice(),
                    request.requestSeqNo(), request.externalTradeNo()));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    /**
     * 手工录入成交回报（与模拟报盘同一清算逻辑）。幂等键优先使用 {@code externalTradeNo}（成交单号），须全局唯一。
     */
    @PostMapping("/operator/trade/orders/{orderNo}/fill-reports")
    @Transactional
    public ApiResponse<Map<String, Object>> manualTradeFillReport(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable String orderNo,
            @Valid @RequestBody SimulateMatchRequest request
    ) {
        try {
            Long operatorId = requireOperatorId(token);
            return ApiResponse.ok(applyTradeSettlementInternal(operatorId, orderNo, request.fillQty(), request.fillPrice(),
                    request.requestSeqNo(), request.externalTradeNo()));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    private static final int MAX_REQUEST_SEQ_NO_LEN = 128;

    private static void ensureRequestSeqNoLength(String requestSeqNo) {
        String s = requestSeqNo == null ? "" : requestSeqNo.trim();
        if (s.length() > MAX_REQUEST_SEQ_NO_LEN) {
            throw new IllegalArgumentException("requestSeqNo 长度不能超过 " + MAX_REQUEST_SEQ_NO_LEN);
        }
    }

    private Map<String, Object> submitOrderInternal(
            Long customerId,
            String fundAccountNoInput,
            String marketInput,
            String securityCodeInput,
            String directionInput,
            BigDecimal price,
            Long quantity,
            String requestSeqNo,
            String sourceType,
            Long operatorId
    ) {
        if (customerId == null || isBlank(fundAccountNoInput) || isBlank(marketInput) || isBlank(securityCodeInput) || isBlank(directionInput)
                || price == null || quantity == null || quantity <= 0 || price.compareTo(BigDecimal.ZERO) <= 0
                || isBlank(requestSeqNo)) {
            throw new IllegalArgumentException("参数错误");
        }
        ensureRequestSeqNoLength(requestSeqNo);

        String marketCode = normalizeMarketCode(marketInput);
        if (marketCode == null) {
            throw new IllegalArgumentException("市场参数错误，仅支持SH/SZ");
        }
        String direction = normalizeDirection(directionInput);
        if (direction == null) {
            throw new IllegalArgumentException("买卖方向仅支持BUY/SELL或B/S");
        }
        String fundAccountNo = fundAccountNoInput.trim();
        String securityCode = securityCodeInput.trim();

        List<Map<String, Object>> idemRows = jdbcTemplate.queryForList(
                """
                SELECT o.id, o.order_no, o.order_status, o.frozen_cash, o.frozen_qty, d.dispatch_no
                FROM rpt_order_dispatch d
                JOIN trd_order o ON o.id = d.order_id
                WHERE d.dispatch_type = 'ORDER'
                  AND d.request_seq_no = ?
                LIMIT 1
                """,
                requestSeqNo.trim()
        );
        if (!idemRows.isEmpty()) {
            Map<String, Object> row = idemRows.get(0);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", toLong(row.get("id")));
            data.put("orderNo", row.get("order_no"));
            data.put("orderStatus", row.get("order_status"));
            data.put("dispatchNo", row.get("dispatch_no"));
            data.put("frozenCash", asDecimal(row.get("frozen_cash")));
            data.put("frozenQty", toLong(row.get("frozen_qty")));
            data.put("idempotent", true);
            return data;
        }

        Map<String, Object> fund = queryOne(
                """
                SELECT id, customer_id, fund_account_no, available_balance, frozen_balance, status
                FROM acct_fund_account
                WHERE customer_id = ? AND fund_account_no = ?
                LIMIT 1
                """,
                customerId, fundAccountNo
        );
        if (fund == null) {
            throw new IllegalArgumentException("资金账户不存在或不属于该客户");
        }
        if (!"NORMAL".equalsIgnoreCase(asString(fund.get("status")))) {
            throw new IllegalArgumentException("资金账户状态异常，禁止交易");
        }
        Long fundAccountId = toLong(fund.get("id"));

        Map<String, Object> shareholder = queryOne(
                """
                SELECT id, shareholder_account_no, account_status
                FROM acct_shareholder_account
                WHERE customer_id = ? AND market_code = ?
                LIMIT 1
                """,
                customerId, marketCode
        );
        if (shareholder == null) {
            throw new IllegalArgumentException("股东账户不存在");
        }
        if (!"NORMAL".equalsIgnoreCase(asString(shareholder.get("account_status")))) {
            throw new IllegalArgumentException("股东账户状态异常");
        }
        Long shareholderAccountId = toLong(shareholder.get("id"));

        Map<String, Object> security = queryOne(
                """
                SELECT id, security_name, lot_size, listed_status
                FROM md_security
                WHERE market_code = ? AND security_code = ?
                LIMIT 1
                """,
                marketCode, securityCode
        );
        if (security == null) {
            throw new IllegalArgumentException("证券代码不存在");
        }
        if (!"1".equals(asString(security.get("listed_status")))) {
            throw new IllegalArgumentException("证券不可交易");
        }
        int lotSize = ((Number) security.get("lot_size")).intValue();
        if (quantity % lotSize != 0) {
            throw new IllegalArgumentException("委托数量不符合交易单位");
        }
        Long securityId = toLong(security.get("id"));
        String securityName = asString(security.get("security_name"));

        Map<String, Object> quote = queryOne(
                """
                SELECT upper_limit_price, lower_limit_price
                FROM md_market_quote
                WHERE security_id = ?
                ORDER BY quote_time DESC
                LIMIT 1
                """,
                securityId
        );
        if (quote != null) {
            BigDecimal upper = asDecimal(quote.get("upper_limit_price"));
            BigDecimal lower = asDecimal(quote.get("lower_limit_price"));
            if (upper != null && price.compareTo(upper) > 0) {
                throw new IllegalArgumentException("委托价格超过涨停价");
            }
            if (lower != null && price.compareTo(lower) < 0) {
                throw new IllegalArgumentException("委托价格低于跌停价");
            }
        }

        BigDecimal orderAmount = price.multiply(BigDecimal.valueOf(quantity)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal frozenCash = BigDecimal.ZERO;
        long frozenQty = 0L;

        if ("B".equals(direction)) {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE acct_fund_account
                    SET available_balance = available_balance - ?,
                        frozen_balance = frozen_balance + ?,
                        current_balance = (available_balance - ?) + (frozen_balance + ?),
                        version = version + 1,
                        updated_at = NOW()
                    WHERE id = ? AND available_balance >= ?
                    """,
                    orderAmount, orderAmount, orderAmount, orderAmount, fundAccountId, orderAmount
            );
            if (updated == 0) {
                throw new IllegalArgumentException("可用资金不足");
            }
            frozenCash = orderAmount;
        } else {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE acct_position
                    SET available_qty = available_qty - ?,
                        frozen_qty = frozen_qty + ?,
                        version = version + 1,
                        updated_at = NOW()
                    WHERE fund_account_id = ? AND security_id = ? AND available_qty >= ?
                    """,
                    quantity, quantity, fundAccountId, securityId, quantity
            );
            if (updated == 0) {
                throw new IllegalArgumentException("可卖持仓不足");
            }
            frozenQty = quantity;
        }

        long orderId = nextId();
        String orderNo = "OD" + LocalDate.now().format(DATE_FMT) + String.format("%06d", orderId % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO trd_order
                (id, order_no, customer_id, fund_account_id, shareholder_account_id, security_id, market_code,
                 security_code, security_name_snapshot, trade_direction, order_price, order_qty, order_amount,
                 filled_qty, filled_amount, avg_fill_price, frozen_cash, frozen_qty, order_status, risk_check_result,
                 dispatch_status, source_type, created_by, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?, 'INIT', 'PASS', 'WAIT', ?, ?, NOW(), NOW(), 0)
                """,
                orderId, orderNo, customerId, fundAccountId, shareholderAccountId, securityId, marketCode,
                securityCode, securityName, direction, price, quantity, orderAmount, frozenCash, frozenQty,
                sourceType, operatorId
        );

        long dispatchId = nextId();
        String dispatchNo = "DP" + LocalDate.now().format(DATE_FMT) + String.format("%06d", dispatchId % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO rpt_order_dispatch
                (id, dispatch_no, order_id, dispatch_type, request_seq_no, request_payload, dispatch_status, sent_at, reply_deadline, created_at)
                VALUES (?, ?, ?, 'ORDER', ?, ?, 'SENT', NOW(), DATE_ADD(NOW(), INTERVAL 30 SECOND), NOW())
                """,
                dispatchId, dispatchNo, orderId, requestSeqNo.trim(), buildOrderRequestPayload(marketCode, securityCode, direction, price, quantity)
        );

        long replyId = nextId();
        String replyNo = "RP" + LocalDate.now().format(DATE_FMT) + String.format("%06d", replyId % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO rpt_order_reply
                (id, reply_no, dispatch_id, order_id, cancel_request_id, reply_type, external_order_no, external_trade_no,
                 accept_code, accept_message, reply_payload, received_at)
                VALUES (?, ?, ?, ?, NULL, 'ORDER_ACCEPT', ?, NULL, '0', '受理成功', ?, NOW())
                """,
                replyId, replyNo, dispatchId, orderId, "EX" + orderNo, "{\"result\":\"ACCEPTED\"}"
        );

        jdbcTemplate.update("UPDATE rpt_order_dispatch SET dispatch_status = 'ACCEPTED' WHERE id = ?", dispatchId);
        jdbcTemplate.update("UPDATE trd_order SET order_status = 'REPORTED', dispatch_status = 'ACCEPTED', updated_at = NOW() WHERE id = ?", orderId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", orderId);
        data.put("orderNo", orderNo);
        data.put("orderStatus", "REPORTED");
        data.put("dispatchNo", dispatchNo);
        data.put("fundAccountNo", fundAccountNo);
        data.put("frozenCash", frozenCash);
        data.put("frozenQty", frozenQty);
        data.put("idempotent", false);
        return data;
    }

    /**
     * @param requiredCustomerId 非空时表示 App 撤单：仅允许撤销属于该客户的委托
     */
    private Map<String, Object> cancelOrderInternal(
            String orderNoInput,
            String requestSeqNo,
            String cancelReason,
            Long requestedBy,
            Long requiredCustomerId
    ) {
        if (isBlank(orderNoInput) || isBlank(requestSeqNo)) {
            throw new IllegalArgumentException("参数错误");
        }
        ensureRequestSeqNoLength(requestSeqNo);
        String orderNo = orderNoInput.trim();

        List<Map<String, Object>> idemRows = jdbcTemplate.queryForList(
                """
                SELECT c.id, c.cancel_no, c.cancel_status, o.order_no, o.order_status
                FROM rpt_order_dispatch d
                JOIN trd_cancel_request c ON c.dispatch_id = d.id
                JOIN trd_order o ON o.id = c.order_id
                WHERE d.dispatch_type = 'CANCEL'
                  AND d.request_seq_no = ?
                LIMIT 1
                """,
                requestSeqNo.trim()
        );
        if (!idemRows.isEmpty()) {
            Map<String, Object> row = idemRows.get(0);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cancelId", toLong(row.get("id")));
            data.put("cancelNo", row.get("cancel_no"));
            data.put("cancelStatus", row.get("cancel_status"));
            data.put("orderNo", row.get("order_no"));
            data.put("orderStatus", row.get("order_status"));
            data.put("idempotent", true);
            return data;
        }

        Map<String, Object> order = queryOne(
                """
                SELECT id, order_no, customer_id, order_status, trade_direction, order_qty, filled_qty, frozen_cash, frozen_qty,
                       fund_account_id, security_id
                FROM trd_order
                WHERE order_no = ?
                LIMIT 1
                """,
                orderNo
        );
        if (order == null) {
            throw new IllegalArgumentException("委托单不存在");
        }
        if (requiredCustomerId != null && toLong(order.get("customer_id")) != requiredCustomerId) {
            throw new IllegalArgumentException("无权撤销该委托");
        }

        String orderStatus = asString(order.get("order_status"));
        long orderQty = toLong(order.get("order_qty"));
        long filledQty = toLong(order.get("filled_qty"));
        long remainQty = Math.max(0L, orderQty - filledQty);
        if (!(("REPORTED".equals(orderStatus) || "PART_FILLED".equals(orderStatus)) && remainQty > 0)) {
            throw new IllegalArgumentException("当前委托状态不允许撤单");
        }

        Integer pendingCancel = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_cancel_request WHERE order_id = ? AND cancel_status IN ('INIT','SENT')",
                Integer.class,
                toLong(order.get("id"))
        );
        if (pendingCancel != null && pendingCancel > 0) {
            throw new IllegalArgumentException("该委托已有撤单申请处理中");
        }

        long cancelId = nextId();
        String cancelNo = "CX" + LocalDate.now().format(DATE_FMT) + String.format("%06d", cancelId % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO trd_cancel_request
                (id, cancel_no, order_id, order_no_snapshot, request_qty, cancel_reason, cancel_status, dispatch_id, requested_by, request_time, confirmed_time, reject_message)
                VALUES (?, ?, ?, ?, ?, ?, 'INIT', NULL, ?, NOW(), NULL, NULL)
                """,
                cancelId, cancelNo, toLong(order.get("id")), orderNo, remainQty, emptyToNull(cancelReason), requestedBy
        );

        long dispatchId = nextId();
        String dispatchNo = "DP" + LocalDate.now().format(DATE_FMT) + String.format("%06d", dispatchId % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO rpt_order_dispatch
                (id, dispatch_no, order_id, dispatch_type, request_seq_no, request_payload, dispatch_status, sent_at, reply_deadline, created_at)
                VALUES (?, ?, ?, 'CANCEL', ?, ?, 'SENT', NOW(), DATE_ADD(NOW(), INTERVAL 30 SECOND), NOW())
                """,
                dispatchId, dispatchNo, toLong(order.get("id")), requestSeqNo.trim(), "{\"cancelNo\":\"" + cancelNo + "\"}"
        );
        jdbcTemplate.update("UPDATE trd_cancel_request SET dispatch_id = ?, cancel_status = 'SENT' WHERE id = ?", dispatchId, cancelId);

        long replyId = nextId();
        String replyNo = "RP" + LocalDate.now().format(DATE_FMT) + String.format("%06d", replyId % 1000000);
        jdbcTemplate.update(
                """
                INSERT INTO rpt_order_reply
                (id, reply_no, dispatch_id, order_id, cancel_request_id, reply_type, external_order_no, external_trade_no,
                 accept_code, accept_message, reply_payload, received_at)
                VALUES (?, ?, ?, ?, ?, 'CANCEL_ACCEPT', ?, NULL, '0', '撤单成功', ?, NOW())
                """,
                replyId, replyNo, dispatchId, toLong(order.get("id")), cancelId, "EX" + orderNo, "{\"result\":\"CANCELLED\"}"
        );

        BigDecimal releasedCash = BigDecimal.ZERO;
        long releasedQty = 0L;
        String direction = asString(order.get("trade_direction"));
        if ("B".equalsIgnoreCase(direction)) {
            // frozen_cash 在委托生命周期内表示「当前仍为该笔委托冻结的资金」，成交模拟会按笔扣减；撤单时一次性全部退回可用
            releasedCash = asDecimal(order.get("frozen_cash"));
            if (releasedCash == null) {
                releasedCash = BigDecimal.ZERO;
            }
            if (releasedCash.compareTo(BigDecimal.ZERO) > 0) {
                jdbcTemplate.update(
                        """
                        UPDATE acct_fund_account
                        SET available_balance = available_balance + ?,
                            frozen_balance = frozen_balance - ?,
                            current_balance = (available_balance + ?) + (frozen_balance - ?),
                            version = version + 1,
                            updated_at = NOW()
                        WHERE id = ? AND frozen_balance >= ?
                        """,
                        releasedCash, releasedCash, releasedCash, releasedCash,
                        toLong(order.get("fund_account_id")), releasedCash
                );
            }
            jdbcTemplate.update(
                    "UPDATE trd_order SET frozen_cash = frozen_cash - ? WHERE id = ?",
                    releasedCash, toLong(order.get("id"))
            );
        } else {
            releasedQty = toLong(order.get("frozen_qty"));
            if (releasedQty <= 0L) {
                releasedQty = remainQty;
            }
            if (releasedQty > 0) {
                jdbcTemplate.update(
                        """
                        UPDATE acct_position
                        SET available_qty = available_qty + ?,
                            frozen_qty = frozen_qty - ?,
                            version = version + 1,
                            updated_at = NOW()
                        WHERE fund_account_id = ? AND security_id = ?
                        """,
                        releasedQty, releasedQty, toLong(order.get("fund_account_id")), toLong(order.get("security_id"))
                );
            }
            jdbcTemplate.update(
                    "UPDATE trd_order SET frozen_qty = frozen_qty - ? WHERE id = ?",
                    releasedQty, toLong(order.get("id"))
            );
        }

        String newOrderStatus = filledQty > 0 ? "PART_CANCELED" : "CANCELED";
        jdbcTemplate.update(
                "UPDATE trd_order SET order_status = ?, dispatch_status = 'ACCEPTED', updated_at = NOW() WHERE id = ?",
                newOrderStatus, toLong(order.get("id"))
        );
        jdbcTemplate.update(
                "UPDATE trd_cancel_request SET cancel_status = 'CANCELED', confirmed_time = NOW() WHERE id = ?",
                cancelId
        );
        jdbcTemplate.update("UPDATE rpt_order_dispatch SET dispatch_status = 'ACCEPTED' WHERE id = ?", dispatchId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cancelId", cancelId);
        data.put("cancelNo", cancelNo);
        data.put("cancelStatus", "CANCELED");
        data.put("orderNo", orderNo);
        data.put("orderStatus", newOrderStatus);
        data.put("releasedCash", releasedCash);
        data.put("releasedQty", releasedQty);
        data.put("idempotent", false);
        return data;
    }

    private void insertTradeAssetJournal(
            Long customerId,
            Long fundAccountId,
            Long securityId,
            String assetType,
            String changeType,
            String direction,
            BigDecimal beforeAmount,
            BigDecimal changeAmount,
            BigDecimal afterAmount,
            String refType,
            Long refId,
            String refNo,
            Long operatorId,
            String remark
    ) {
        long id = nextId();
        String journalNo = "JNL" + LocalDate.now().format(DATE_FMT) + String.format("%06d", id % 1_000_000);
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

    private void insertTradeFeeDetail(
            long feeRowId,
            long tradeId,
            long orderId,
            String feeType,
            BigDecimal calcBase,
            BigDecimal rate,
            BigDecimal amount,
            String remark
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO trd_fee_detail
                (id, biz_type, biz_id, order_id, trade_id, fee_type, calc_base, fee_rate, fee_amount, currency_code, created_at, remark)
                VALUES (?, 'TRADE', ?, ?, ?, ?, ?, ?, ?, '0', NOW(), ?)
                """,
                feeRowId, tradeId, orderId, tradeId, feeType, calcBase, rate, amount, remark
        );
    }

    private Map<String, Object> tradeSettlementIdempotentPayload(Map<String, Object> tradeRow) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tradeNo", tradeRow.get("trade_no"));
        data.put("orderNo", tradeRow.get("order_no"));
        data.put("orderStatus", tradeRow.get("order_status"));
        data.put("filledQty", toLong(tradeRow.get("filled_qty")));
        data.put("filledAmount", asDecimal(tradeRow.get("filled_amount")));
        data.put("avgFillPrice", asDecimal(tradeRow.get("avg_fill_price")));
        data.put("fillQty", toLong(tradeRow.get("trade_qty")));
        data.put("fillPrice", asDecimal(tradeRow.get("trade_price")));
        data.put("tradeAmount", asDecimal(tradeRow.get("trade_amount")));
        data.put("commissionAmount", asDecimal(tradeRow.get("commission_amount")));
        data.put("taxAmount", asDecimal(tradeRow.get("tax_amount")));
        data.put("otherFeeAmount", asDecimal(tradeRow.get("other_fee_amount")));
        data.put("netSettleAmount", asDecimal(tradeRow.get("net_settle_amount")));
        data.put("idempotent", true);
        return data;
    }

    /**
     * 统一清算：幂等（成交单号 {@code trd_trade.trade_no} 优先；其次报盘 request_seq_no）、费用、成交表、委托累计、资产流水。
     */
    private Map<String, Object> applyTradeSettlementInternal(
            Long operatorId,
            String orderNoInput,
            long fillQty,
            BigDecimal fillPrice,
            String requestSeqNo,
            String externalTradeNoOpt
    ) {
        if (isBlank(orderNoInput) || fillQty <= 0 || fillPrice == null || fillPrice.compareTo(BigDecimal.ZERO) <= 0 || isBlank(requestSeqNo)) {
            throw new IllegalArgumentException("参数错误");
        }
        ensureRequestSeqNoLength(requestSeqNo);
        String orderNo = orderNoInput.trim();
        String externalTradeNo = isBlank(externalTradeNoOpt) ? null : externalTradeNoOpt.trim();
        if (externalTradeNo != null) {
            if (externalTradeNo.length() > 32) {
                throw new IllegalArgumentException("成交单号长度不能超过32");
            }
        }

        if (externalTradeNo != null) {
            Map<String, Object> existTrade = queryOne(
                    """
                    SELECT t.id, t.trade_no, t.trade_qty, t.trade_price, t.trade_amount, t.commission_amount, t.tax_amount,
                           t.other_fee_amount, t.net_settle_amount, o.order_no, o.order_status, o.filled_qty, o.filled_amount, o.avg_fill_price
                    FROM trd_trade t
                    JOIN trd_order o ON o.id = t.order_id
                    WHERE t.trade_no = ?
                    LIMIT 1
                    """,
                    externalTradeNo
            );
            if (existTrade != null) {
                if (!orderNo.equalsIgnoreCase(asString(existTrade.get("order_no")))) {
                    throw new IllegalArgumentException("成交单号已用于其他委托，无法重复清算");
                }
                return tradeSettlementIdempotentPayload(existTrade);
            }
        }

        List<Map<String, Object>> idemRows = jdbcTemplate.queryForList(
                """
                SELECT o.order_no, o.order_status, o.filled_qty, o.filled_amount, o.avg_fill_price,
                       d.external_order_no AS trade_no, t.trade_qty, t.trade_price, t.trade_amount,
                       t.commission_amount, t.tax_amount, t.other_fee_amount, t.net_settle_amount
                FROM rpt_order_dispatch d
                JOIN trd_order o ON o.id = d.order_id
                LEFT JOIN trd_trade t ON t.trade_no = d.external_order_no
                WHERE d.dispatch_type = 'MATCH' AND d.request_seq_no = ?
                LIMIT 1
                """,
                requestSeqNo.trim()
        );
        if (!idemRows.isEmpty()) {
            Map<String, Object> row = idemRows.get(0);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderNo", row.get("order_no"));
            data.put("orderStatus", row.get("order_status"));
            data.put("filledQty", toLong(row.get("filled_qty")));
            data.put("filledAmount", asDecimal(row.get("filled_amount")));
            data.put("avgFillPrice", asDecimal(row.get("avg_fill_price")));
            data.put("tradeNo", row.get("trade_no"));
            data.put("fillQty", row.get("trade_qty") != null ? toLong(row.get("trade_qty")) : null);
            data.put("fillPrice", row.get("trade_price") != null ? asDecimal(row.get("trade_price")) : null);
            data.put("tradeAmount", asDecimal(row.get("trade_amount")));
            data.put("commissionAmount", asDecimal(row.get("commission_amount")));
            data.put("taxAmount", asDecimal(row.get("tax_amount")));
            data.put("otherFeeAmount", asDecimal(row.get("other_fee_amount")));
            data.put("netSettleAmount", asDecimal(row.get("net_settle_amount")));
            data.put("idempotent", true);
            return data;
        }

        Map<String, Object> order = queryOne(
                """
                SELECT o.id, o.order_no, o.customer_id, o.fund_account_id, o.security_id, o.market_code, o.security_code,
                       o.trade_direction, o.order_price, o.order_qty, o.filled_qty, o.filled_amount, o.avg_fill_price,
                       o.frozen_cash, o.frozen_qty, o.order_status
                FROM trd_order o
                WHERE o.order_no = ?
                LIMIT 1
                """,
                orderNo
        );
        if (order == null) {
            throw new IllegalArgumentException("委托单不存在");
        }
        String orderStatus = asString(order.get("order_status"));
        if (!"REPORTED".equals(orderStatus) && !"PART_FILLED".equals(orderStatus)) {
            throw new IllegalArgumentException("仅已报或部成委托可成交");
        }
        long orderQty = toLong(order.get("order_qty"));
        long filledBefore = toLong(order.get("filled_qty"));
        long remainQty = Math.max(0L, orderQty - filledBefore);
        if (fillQty > remainQty) {
            throw new IllegalArgumentException("成交量超过剩余委托数量");
        }
        long orderId = toLong(order.get("id"));
        long fundAccountId = toLong(order.get("fund_account_id"));
        long securityId = toLong(order.get("security_id"));
        long customerId = toLong(order.get("customer_id"));
        String marketCode = asString(order.get("market_code"));
        String securityCode = asString(order.get("security_code"));
        String direction = asString(order.get("trade_direction"));
        BigDecimal orderPrice = asDecimal(order.get("order_price"));
        if (orderPrice == null || orderPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("委托价格异常");
        }
        // 限价委托与成交价关系（模拟成交 / 手工回报共用）
        if ("B".equalsIgnoreCase(direction)) {
            if (fillPrice.compareTo(orderPrice) > 0) {
                throw new IllegalArgumentException("买入限价委托：成交价不能高于委托价");
            }
        } else if ("S".equalsIgnoreCase(direction)) {
            if (fillPrice.compareTo(orderPrice) < 0) {
                throw new IllegalArgumentException("卖出限价委托：成交价不能低于委托价");
            }
        }

        Map<String, Object> sec = queryOne(
                "SELECT lot_size FROM md_security WHERE id = ? LIMIT 1",
                securityId
        );
        if (sec == null) {
            throw new IllegalArgumentException("证券主数据缺失");
        }
        int lotSize = ((Number) sec.get("lot_size")).intValue();
        if (lotSize > 0 && fillQty % lotSize != 0) {
            throw new IllegalArgumentException("成交数量须满足最小交易单位");
        }

        BigDecimal fillValue = fillPrice.multiply(BigDecimal.valueOf(fillQty)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal filledAmountBefore = asDecimal(order.get("filled_amount"));
        if (filledAmountBefore == null) {
            filledAmountBefore = BigDecimal.ZERO;
        }

        BigDecimal commRate = DEFAULT_COMM_RATE;
        BigDecimal stampRate = "S".equalsIgnoreCase(direction) ? DEFAULT_STAMP_SELL_RATE : BigDecimal.ZERO;
        BigDecimal feeComm = fillValue.multiply(commRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal feeStamp = fillValue.multiply(stampRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal feeOther = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal feeTotal = feeComm.add(feeStamp).add(feeOther);

        long tradeId = nextId();
        String tradeNo = externalTradeNo != null ? externalTradeNo
                : ("TF" + LocalDate.now().format(DATE_FMT) + String.format("%06d", tradeId % 1_000_000));

        Map<String, Object> fundBefore = queryOne(
                "SELECT current_balance, available_balance, frozen_balance FROM acct_fund_account WHERE id = ? LIMIT 1",
                fundAccountId
        );
        if (fundBefore == null) {
            throw new IllegalArgumentException("资金账户不存在");
        }
        BigDecimal curBefore = nz(asDecimal(fundBefore.get("current_balance")));
        BigDecimal availBefore = nz(asDecimal(fundBefore.get("available_balance")));

        if ("B".equalsIgnoreCase(direction)) {
            BigDecimal lockChunk = orderPrice.multiply(BigDecimal.valueOf(fillQty)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal frozenCash = nz(asDecimal(order.get("frozen_cash")));
            if (lockChunk.compareTo(frozenCash) > 0) {
                throw new IllegalArgumentException("委托冻结资金不足，无法成交该数量");
            }
            BigDecimal surplus = lockChunk.subtract(fillValue).max(BigDecimal.ZERO);
            if (availBefore.add(surplus).compareTo(feeTotal) < 0) {
                throw new IllegalArgumentException("释放后可用资金不足以支付佣金与税费");
            }
            int rows = jdbcTemplate.update(
                    """
                    UPDATE acct_fund_account
                    SET frozen_balance = frozen_balance - ?,
                        available_balance = available_balance + ? - ?,
                        current_balance = (frozen_balance - ?) + (available_balance + ? - ?),
                        version = version + 1,
                        updated_at = NOW()
                    WHERE id = ? AND frozen_balance >= ?
                    """,
                    lockChunk, surplus, feeTotal,
                    lockChunk, surplus, feeTotal,
                    fundAccountId, lockChunk
            );
            if (rows == 0) {
                throw new IllegalArgumentException("资金冻结余额不足，无法成交");
            }
            jdbcTemplate.update(
                    "UPDATE trd_order SET frozen_cash = frozen_cash - ? WHERE id = ? AND frozen_cash >= ?",
                    lockChunk, orderId, lockChunk
            );

            BigDecimal curAfterPrincipal = curBefore.subtract(fillValue).setScale(4, RoundingMode.HALF_UP);
            insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_BUY_PRINCIPAL", "O",
                    curBefore, fillValue.negate(), curAfterPrincipal,
                    "TRADE", tradeId, tradeNo, operatorId, "买入成交：冻结价款转实际扣减");

            if (feeComm.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal curAfterComm = curAfterPrincipal.subtract(feeComm).setScale(4, RoundingMode.HALF_UP);
                insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_COMMISSION", "O",
                        curAfterPrincipal, feeComm.negate(), curAfterComm,
                        "TRADE", tradeId, tradeNo, operatorId, "买入佣金");
                curAfterPrincipal = curAfterComm;
            }
            if (feeStamp.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal curAfterStamp = curAfterPrincipal.subtract(feeStamp).setScale(4, RoundingMode.HALF_UP);
                insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_STAMP", "O",
                        curAfterPrincipal, feeStamp.negate(), curAfterStamp,
                        "TRADE", tradeId, tradeNo, operatorId, "印花税");
                curAfterPrincipal = curAfterStamp;
            }
            if (feeOther.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal curAfterOther = curAfterPrincipal.subtract(feeOther).setScale(4, RoundingMode.HALF_UP);
                insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_OTHER_FEE", "O",
                        curAfterPrincipal, feeOther.negate(), curAfterOther,
                        "TRADE", tradeId, tradeNo, operatorId, "其他费用");
            }

            Map<String, Object> pos = queryOne(
                    "SELECT id, total_qty, available_qty, frozen_qty, cost_price FROM acct_position WHERE fund_account_id = ? AND security_id = ? LIMIT 1",
                    fundAccountId, securityId
            );
            if (pos == null) {
                insertTradeAssetJournal(customerId, fundAccountId, securityId, "POSITION", "TRADE_BUY_SHARES", "I",
                        BigDecimal.ZERO, BigDecimal.valueOf(fillQty), BigDecimal.valueOf(fillQty),
                        "TRADE", tradeId, tradeNo, operatorId, "买入成交增加持仓");
                long positionId = nextId();
                jdbcTemplate.update(
                        """
                        INSERT INTO acct_position
                        (id, fund_account_id, customer_id, security_id, market_code, security_code_snapshot,
                         total_qty, available_qty, frozen_qty, cost_price, last_price, market_value, position_status, version, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'NORMAL', 0, NOW())
                        """,
                        positionId, fundAccountId, customerId, securityId, marketCode, securityCode,
                        fillQty, fillQty, fillPrice, fillPrice, fillValue
                );
            } else {
                long oldTot = toLong(pos.get("total_qty"));
                BigDecimal qtyBdBefore = BigDecimal.valueOf(oldTot);
                insertTradeAssetJournal(customerId, fundAccountId, securityId, "POSITION", "TRADE_BUY_SHARES", "I",
                        qtyBdBefore, BigDecimal.valueOf(fillQty), BigDecimal.valueOf(oldTot + fillQty),
                        "TRADE", tradeId, tradeNo, operatorId, "买入成交增加持仓");
                long posId = toLong(pos.get("id"));
                BigDecimal oldCost = nz(asDecimal(pos.get("cost_price")));
                long newTot = oldTot + fillQty;
                BigDecimal newCost = fillPrice;
                if (newTot > 0) {
                    newCost = oldCost.multiply(BigDecimal.valueOf(oldTot))
                            .add(fillValue)
                            .divide(BigDecimal.valueOf(newTot), 4, RoundingMode.HALF_UP);
                }
                BigDecimal mv = newCost.multiply(BigDecimal.valueOf(newTot)).setScale(4, RoundingMode.HALF_UP);
                jdbcTemplate.update(
                        """
                        UPDATE acct_position
                        SET total_qty = total_qty + ?,
                            available_qty = available_qty + ?,
                            cost_price = ?,
                            last_price = ?,
                            market_value = ?,
                            version = version + 1,
                            updated_at = NOW()
                        WHERE id = ?
                        """,
                        fillQty, fillQty, newCost, fillPrice, mv, posId
                );
            }
        } else if ("S".equalsIgnoreCase(direction)) {
            long frozenQtyOnOrder = toLong(order.get("frozen_qty"));
            if (fillQty > frozenQtyOnOrder) {
                throw new IllegalArgumentException("委托冻结数量不足，无法成交");
            }
            Map<String, Object> posBefore = queryOne(
                    "SELECT id, total_qty, available_qty, frozen_qty, cost_price FROM acct_position WHERE fund_account_id = ? AND security_id = ? LIMIT 1",
                    fundAccountId, securityId
            );
            if (posBefore == null) {
                throw new IllegalArgumentException("持仓不存在");
            }
            long oldTot = toLong(posBefore.get("total_qty"));
            long oldFrz = toLong(posBefore.get("frozen_qty"));
            BigDecimal frzQtyBd = BigDecimal.valueOf(oldFrz);
            BigDecimal totQtyBd = BigDecimal.valueOf(oldTot);

            insertTradeAssetJournal(customerId, fundAccountId, securityId, "POSITION", "TRADE_SELL_UNFREEZE", "O",
                    frzQtyBd, BigDecimal.valueOf(-fillQty), BigDecimal.valueOf(oldFrz - fillQty),
                    "TRADE", tradeId, tradeNo, operatorId, "卖出成交扣减冻结股份");
            insertTradeAssetJournal(customerId, fundAccountId, securityId, "POSITION", "TRADE_SELL_DELIVER", "O",
                    totQtyBd, BigDecimal.valueOf(-fillQty), BigDecimal.valueOf(oldTot - fillQty),
                    "TRADE", tradeId, tradeNo, operatorId, "卖出成交减少持仓");

            int rows = jdbcTemplate.update(
                    """
                    UPDATE acct_position
                    SET frozen_qty = frozen_qty - ?,
                        total_qty = total_qty - ?,
                        version = version + 1,
                        updated_at = NOW()
                    WHERE fund_account_id = ? AND security_id = ? AND frozen_qty >= ?
                    """,
                    fillQty, fillQty, fundAccountId, securityId, fillQty
            );
            if (rows == 0) {
                throw new IllegalArgumentException("持仓冻结数量不足，无法成交");
            }
            BigDecimal netProceed = fillValue.subtract(feeTotal).setScale(4, RoundingMode.HALF_UP);
            int fundRows = jdbcTemplate.update(
                    """
                    UPDATE acct_fund_account
                    SET available_balance = available_balance + ?,
                        current_balance = frozen_balance + (available_balance + ?),
                        version = version + 1,
                        updated_at = NOW()
                    WHERE id = ?
                    """,
                    netProceed, netProceed, fundAccountId
            );
            if (fundRows == 0) {
                throw new IllegalArgumentException("资金账户更新失败");
            }

            BigDecimal cashRun = curBefore;
            BigDecimal curAfterGross = cashRun.add(fillValue).setScale(4, RoundingMode.HALF_UP);
            insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_SELL_PRINCIPAL", "I",
                    cashRun, fillValue, curAfterGross,
                    "TRADE", tradeId, tradeNo, operatorId, "卖出成交价款入账");
            cashRun = curAfterGross;
            if (feeComm.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal n = cashRun.subtract(feeComm).setScale(4, RoundingMode.HALF_UP);
                insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_COMMISSION", "O",
                        cashRun, feeComm.negate(), n,
                        "TRADE", tradeId, tradeNo, operatorId, "卖出佣金");
                cashRun = n;
            }
            if (feeStamp.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal n = cashRun.subtract(feeStamp).setScale(4, RoundingMode.HALF_UP);
                insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_STAMP", "O",
                        cashRun, feeStamp.negate(), n,
                        "TRADE", tradeId, tradeNo, operatorId, "卖出印花税");
                cashRun = n;
            }
            if (feeOther.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal n = cashRun.subtract(feeOther).setScale(4, RoundingMode.HALF_UP);
                insertTradeAssetJournal(customerId, fundAccountId, null, "CASH", "TRADE_OTHER_FEE", "O",
                        cashRun, feeOther.negate(), n,
                        "TRADE", tradeId, tradeNo, operatorId, "卖出其他费用");
            }

            jdbcTemplate.update(
                    "UPDATE trd_order SET frozen_qty = frozen_qty - ? WHERE id = ? AND frozen_qty >= ?",
                    fillQty, orderId, fillQty
            );
        } else {
            throw new IllegalArgumentException("委托方向异常");
        }

        long newFilledQty = filledBefore + fillQty;
        BigDecimal newFilledAmount = filledAmountBefore.add(fillValue);
        BigDecimal avgFill = fillPrice;
        if (newFilledQty > 0) {
            avgFill = newFilledAmount.divide(BigDecimal.valueOf(newFilledQty), 4, RoundingMode.HALF_UP);
        }
        String newStatus = newFilledQty >= orderQty ? "FILLED" : "PART_FILLED";
        jdbcTemplate.update(
                """
                UPDATE trd_order
                SET filled_qty = ?,
                    filled_amount = ?,
                    avg_fill_price = ?,
                    order_status = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                newFilledQty, newFilledAmount, avgFill, newStatus, orderId
        );

        BigDecimal netSettle = "S".equalsIgnoreCase(direction)
                ? fillValue.subtract(feeTotal)
                : fillValue.add(feeTotal).negate();

        jdbcTemplate.update(
                """
                INSERT INTO trd_trade
                (id, trade_no, order_id, order_no, customer_id, fund_account_id, security_id, market_code, security_code,
                 trade_direction, trade_price, trade_qty, trade_amount, commission_amount, tax_amount, other_fee_amount,
                 net_settle_amount, trade_time, settle_status, source_reply_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), 'SETTLED', NULL)
                """,
                tradeId, tradeNo, orderId, orderNo, customerId, fundAccountId, securityId, marketCode, securityCode,
                direction, fillPrice, fillQty, fillValue, feeComm, feeStamp, feeOther, netSettle
        );

        if (feeComm.compareTo(BigDecimal.ZERO) > 0) {
            insertTradeFeeDetail(nextId(), tradeId, orderId, "COMMISSION", fillValue, commRate, feeComm, "佣金");
        }
        if (feeStamp.compareTo(BigDecimal.ZERO) > 0) {
            insertTradeFeeDetail(nextId(), tradeId, orderId, "STAMP", fillValue, stampRate, feeStamp, "印花税");
        }
        if (feeOther.compareTo(BigDecimal.ZERO) > 0) {
            insertTradeFeeDetail(nextId(), tradeId, orderId, "OTHER", fillValue, BigDecimal.ZERO, feeOther, "其他");
        }

        long matchDispatchId = nextId();
        String matchDispatchNo = "DP" + LocalDate.now().format(DATE_FMT) + String.format("%06d", matchDispatchId % 1_000_000);
        String payload = "{\"tradeNo\":\"" + tradeNo.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"fillQty\":" + fillQty + ",\"fillPrice\":" + fillPrice + "}";
        jdbcTemplate.update(
                """
                INSERT INTO rpt_order_dispatch
                (id, dispatch_no, order_id, dispatch_type, request_seq_no, request_payload, dispatch_status, external_order_no, sent_at, reply_deadline, created_at)
                VALUES (?, ?, ?, 'MATCH', ?, ?, 'ACCEPTED', ?, NOW(), NULL, NOW())
                """,
                matchDispatchId, matchDispatchNo, orderId, requestSeqNo.trim(), payload, tradeNo
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tradeNo", tradeNo);
        data.put("orderNo", orderNo);
        data.put("orderStatus", newStatus);
        data.put("filledQty", newFilledQty);
        data.put("filledAmount", newFilledAmount);
        data.put("avgFillPrice", avgFill);
        data.put("fillQty", fillQty);
        data.put("fillPrice", fillPrice);
        data.put("tradeAmount", fillValue);
        data.put("commissionAmount", feeComm);
        data.put("taxAmount", feeStamp);
        data.put("otherFeeAmount", feeOther);
        data.put("netSettleAmount", netSettle);
        data.put("idempotent", false);
        return data;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * App 三 Tab 分桶：部撤 {@code PART_CANCELED} 同时含「有成交」与「有撤单」，返回 {@code COMPLETED} 与 {@code CANCELED} 两个桶。
     */
    private static List<String> computeOrderListBuckets(String orderStatus, long filledQty) {
        if (orderStatus == null) {
            return List.of("ONGOING");
        }
        return switch (orderStatus) {
            case "FILLED" -> List.of("COMPLETED");
            case "CANCELED" -> List.of("CANCELED");
            case "PART_CANCELED" -> {
                if (filledQty > 0L) {
                    yield List.of("COMPLETED", "CANCELED");
                }
                yield List.of("CANCELED");
            }
            default -> List.of("ONGOING");
        };
    }

    private String buildOrderRequestPayload(String marketCode, String securityCode, String direction, BigDecimal price, Long quantity) {
        return "{\"marketCode\":\"" + marketCode + "\",\"securityCode\":\"" + securityCode + "\",\"tradeDirection\":\""
                + direction + "\",\"price\":" + price + ",\"quantity\":" + quantity + "}";
    }

    private Map<String, Object> toOrderListItem(Map<String, Object> row) {
        long orderQty = toLong(row.get("order_qty"));
        long filledQty = toLong(row.get("filled_qty"));
        String status = asString(row.get("order_status"));
        boolean terminal = "FILLED".equals(status) || "CANCELED".equals(status) || "PART_CANCELED".equals(status);
        long remainQty = terminal ? 0L : Math.max(0L, orderQty - filledQty);
        String lastCancelStatus = asString(row.get("last_cancel_status"));
        boolean canceling = "INIT".equals(lastCancelStatus) || "SENT".equals(lastCancelStatus);
        boolean canCancel = ("REPORTED".equals(status) || "PART_FILLED".equals(status)) && !terminal && (orderQty - filledQty) > 0 && !canceling;

        String marketCode = asString(row.get("market_code"));
        String direction = asString(row.get("trade_direction"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", toLong(row.get("id")));
        item.put("orderNo", asString(row.get("order_no")));
        item.put("sourceType", asString(row.get("source_type")));
        item.put("customerId", toLong(row.get("customer_id")));
        item.put("fundAccountNo", asString(row.get("fund_account_no")));
        item.put("marketCode", marketCode);
        item.put("market", "1".equals(marketCode) ? "SH" : "SZ");
        item.put("securityCode", asString(row.get("security_code")));
        item.put("securityName", asString(row.get("security_name_snapshot")));
        item.put("tradeDirection", direction);
        item.put("tradeDirectionText", "B".equals(direction) ? "买入" : "卖出");
        BigDecimal orderPrice = asDecimal(row.get("order_price"));
        item.put("orderPrice", orderPrice);
        // 与部分移动端字段名对齐（Gson 默认映射 price / quantity，避免未配置 @SerializedName 时显示为 0）
        item.put("price", orderPrice);
        item.put("orderQty", orderQty);
        item.put("quantity", orderQty);
        item.put("filledQty", filledQty);
        item.put("remainQty", remainQty);
        item.put("orderStatus", status);
        item.put("orderListBuckets", computeOrderListBuckets(status, filledQty));
        item.put("createdAt", row.get("created_at"));
        item.put("lastCancelStatus", lastCancelStatus);
        item.put("canCancel", canCancel);
        return item;
    }

    private void ensureActiveAppUser(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("登录态失效");
        }
    }

    private Long findLatestOpenedCustomerByUser(Long userId) {
        List<Long> list = jdbcTemplate.queryForList(
                """
                SELECT ir.created_customer_id
                FROM cust_open_apply oa
                JOIN cust_import_record ir ON ir.record_no = oa.apply_no
                WHERE oa.user_id = ?
                  AND ir.review_status = 'OPENED'
                  AND ir.created_customer_id IS NOT NULL
                ORDER BY oa.updated_at DESC
                LIMIT 1
                """,
                Long.class,
                userId
        );
        return list.isEmpty() ? null : list.get(0);
    }

    private Long requireOperatorId(String token) {
        List<Long> operatorIds = jdbcTemplate.queryForList(
                "SELECT operator_id FROM op_login_session WHERE token = ? AND expire_at > NOW() LIMIT 1",
                Long.class,
                token
        );
        if (operatorIds.isEmpty()) {
            throw new IllegalArgumentException("登录已过期，请重新登录");
        }
        return operatorIds.get(0);
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String normalizeMarketCode(String market) {
        if (market == null) return null;
        String m = market.trim().toUpperCase();
        if ("SH".equals(m) || "1".equals(m)) return "1";
        if ("SZ".equals(m) || "0".equals(m)) return "0";
        return null;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null) return null;
        String d = direction.trim().toUpperCase();
        if ("BUY".equals(d) || "B".equals(d)) return "B";
        if ("SELL".equals(d) || "S".equals(d)) return "S";
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal d) return d;
        return new BigDecimal(String.valueOf(value));
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }

    record AppOrderSubmitRequest(
            @NotNull Long userId,
            @NotBlank String fundAccountNo,
            @NotBlank String marketCode,
            @NotBlank String securityCode,
            @NotBlank String tradeDirection,
            @NotNull BigDecimal price,
            @NotNull Long quantity,
            @NotBlank String requestSeqNo
    ) {
    }

    record OperatorOrderSubmitRequest(
            @NotNull Long customerId,
            @NotBlank String fundAccountNo,
            @NotBlank String marketCode,
            @NotBlank String securityCode,
            @NotBlank String tradeDirection,
            @NotNull BigDecimal price,
            @NotNull Long quantity,
            @NotBlank String requestSeqNo
    ) {
    }

    record AppCancelRequest(
            @NotNull Long userId,
            @NotBlank String requestSeqNo,
            String cancelReason
    ) {
    }

    record OperatorCancelRequest(
            @NotBlank String requestSeqNo,
            String cancelReason
    ) {
    }

    record SimulateMatchRequest(
            @NotNull Long fillQty,
            @NotNull BigDecimal fillPrice,
            @NotBlank String requestSeqNo,
            /** 成交回报单号；空则后台生成。已存在则整笔幂等返回，用于防重复清算 */
            String externalTradeNo
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
