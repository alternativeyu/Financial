package com.financial.operator.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一业务查询服务：所有报表类 SQL 在此集中维护，按 App 本人 / 柜台多条件解析访问边界，避免各端自行拼接 SQL。
 */
@Service
public class UnifiedBusinessQueryService {

    private final JdbcTemplate jdbcTemplate;

    public UnifiedBusinessQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureActiveAppUser(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("登录态失效");
        }
    }

    public Long findLatestOpenedCustomerByUser(long userId) {
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

    public Long requireOperatorId(String token) {
        if (isBlank(token)) {
            throw new IllegalArgumentException("缺少操作员令牌");
        }
        List<Long> sessionRows = jdbcTemplate.queryForList(
                "SELECT operator_id FROM op_login_session WHERE token = ? AND expire_at > NOW() LIMIT 1",
                Long.class,
                token
        );
        if (sessionRows.isEmpty()) {
            throw new IllegalArgumentException("登录已失效，请重新登录");
        }
        Long opId = sessionRows.get(0);
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

    public Map<String, Object> listOrdersByAppPaged(
            long userId,
            String fundAccountNo,
            String orderStatus,
            String orderListCategory,
            int page,
            int pageSize
    ) {
        ensureActiveAppUser(userId);
        Long customerId = findLatestOpenedCustomerByUser(userId);
        if (customerId == null) {
            throw new IllegalArgumentException("客户未开户");
        }
        int pageNo = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = (pageNo - 1) * size;
        String fundFilter = emptyToNull(fundAccountNo);
        String statusFilter = emptyToNull(orderStatus) == null ? null : orderStatus.trim().toUpperCase();
        String listCat = emptyToNull(orderListCategory) == null ? null : orderListCategory.trim().toUpperCase();
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
        return data;
    }

    public Map<String, Object> listOrdersByOperatorPaged(
            String token,
            String sourceType,
            String orderStatus,
            String orderStatusGroup,
            int page,
            int pageSize
    ) {
        requireOperatorId(token);
        int pageNo = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 100);
        int offset = (pageNo - 1) * size;
        String sourceFilter = emptyToNull(sourceType) == null ? null : sourceType.trim().toUpperCase();
        String statusFilter = emptyToNull(orderStatus) == null ? null : orderStatus.trim().toUpperCase();
        String group = emptyToNull(orderStatusGroup) == null ? null : orderStatusGroup.trim().toUpperCase();
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
                """
                        + where
                        + """
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
        return data;
    }

    public Map<String, Object> appCustomerProfile(long userId) {
        ensureActiveAppUser(userId);
        Long customerId = findLatestOpenedCustomerByUser(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (customerId == null) {
            data.put("opened", false);
            data.put("customer", null);
            return data;
        }
        Map<String, Object> cust = jdbcTemplate.queryForMap(
                """
                SELECT customer_code, customer_name, id_type, id_no, acct_cls_code, customer_status, risk_level, open_date
                FROM cust_customer
                WHERE id = ?
                LIMIT 1
                """,
                customerId
        );
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customerCode", cust.get("customer_code"));
        customer.put("customerName", cust.get("customer_name"));
        customer.put("idType", cust.get("id_type"));
        customer.put("idNoMasked", maskIdNo(asString(cust.get("id_no"))));
        customer.put("acctClsCode", cust.get("acct_cls_code"));
        customer.put("customerStatus", cust.get("customer_status"));
        customer.put("riskLevel", cust.get("risk_level"));
        customer.put("openDate", cust.get("open_date"));
        data.put("opened", true);
        data.put("customer", customer);
        return data;
    }

    public Map<String, Object> pageAssetJournalForApp(long userId, LocalDate from, LocalDate to, int page, int pageSize) {
        ensureActiveAppUser(userId);
        Long customerId = findLatestOpenedCustomerByUser(userId);
        if (customerId == null) {
            return emptyPage(page, pageSize);
        }
        return pageAssetJournal(customerId, null, from, to, page, pageSize);
    }

    public Map<String, Object> pageTradesForApp(long userId, LocalDate from, LocalDate to, int page, int pageSize) {
        ensureActiveAppUser(userId);
        Long customerId = findLatestOpenedCustomerByUser(userId);
        if (customerId == null) {
            return emptyPage(page, pageSize);
        }
        return pageTrades(customerId, null, from, to, page, pageSize);
    }

    public Map<String, Object> pageRiskEventsForApp(long userId, LocalDate from, LocalDate to, int page, int pageSize) {
        ensureActiveAppUser(userId);
        Long customerId = findLatestOpenedCustomerByUser(userId);
        if (customerId == null) {
            return emptyPage(page, pageSize);
        }
        return pageRiskEvents(customerId, null, null, null, from, to, page, pageSize, true);
    }

    public List<Map<String, Object>> listNotificationsForApp(long userId, int limit) {
        ensureActiveAppUser(userId);
        int cap = Math.min(Math.max(limit, 1), 100);
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map<String, Object>> applies = jdbcTemplate.queryForList(
                """
                SELECT oa.apply_no, oa.apply_status, oa.updated_at, ir.review_status
                FROM cust_open_apply oa
                LEFT JOIN cust_import_record ir ON ir.record_no = oa.apply_no
                WHERE oa.user_id = ?
                ORDER BY oa.updated_at DESC
                LIMIT ?
                """,
                userId, cap
        );
        for (Map<String, Object> row : applies) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("channel", "OPEN_ACCOUNT");
            n.put("title", "开户申请");
            n.put("refNo", row.get("apply_no"));
            n.put("status", row.get("review_status") != null ? row.get("review_status") : row.get("apply_status"));
            n.put("createdAt", row.get("updated_at"));
            out.add(n);
        }
        Long customerId = findLatestOpenedCustomerByUser(userId);
        if (customerId != null) {
            List<Map<String, Object>> risks = jdbcTemplate.queryForList(
                    """
                    SELECT e.event_no, e.event_type, e.risk_level, e.hit_message, e.event_status, e.created_at
                    FROM risk_event e
                    WHERE e.customer_id = ?
                    ORDER BY e.created_at DESC
                    LIMIT ?
                    """,
                    customerId, cap
            );
            for (Map<String, Object> row : risks) {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("channel", "RISK");
                n.put("title", "风险提示");
                n.put("refNo", row.get("event_no"));
                n.put("riskLevel", row.get("risk_level"));
                n.put("message", row.get("hit_message"));
                n.put("status", row.get("event_status"));
                n.put("createdAt", row.get("created_at"));
                out.add(n);
            }
        }
        out.sort((a, b) -> {
            Object ta = a.get("createdAt");
            Object tb = b.get("createdAt");
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return String.valueOf(tb).compareTo(String.valueOf(ta));
        });
        if (out.size() > cap) {
            return out.subList(0, cap);
        }
        return out;
    }

    public ResolvedOperatorBusinessContext resolveOperatorBusinessContext(OperatorReportFilter f) {
        Long customerId = null;
        Long fundAccountId = null;
        Long orderId = null;
        Long tradeDbId = null;

        String customerCode = trimToNull(f.customerCode());
        String idNo = trimToNull(f.idNo());
        String fundAccountNo = trimToNull(f.fundAccountNo());
        String shareholderAccountNo = trimToNull(f.shareholderAccountNo());
        String orderNo = trimToNull(f.orderNo());
        String tradeNo = trimToNull(f.tradeNo());

        if (customerCode != null) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM cust_customer WHERE customer_code = ? LIMIT 1",
                    Long.class,
                    customerCode
            );
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("客户代码不存在");
            }
            customerId = mergeCustomer(customerId, ids.get(0), "customerCode");
        }
        if (idNo != null) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM cust_customer WHERE id_no = ? LIMIT 1",
                    Long.class,
                    idNo
            );
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("证件号不存在");
            }
            customerId = mergeCustomer(customerId, ids.get(0), "idNo");
        }
        if (fundAccountNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, customer_id FROM acct_fund_account WHERE fund_account_no = ? LIMIT 1",
                    fundAccountNo
            );
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("资金账户不存在");
            }
            Map<String, Object> row = rows.get(0);
            customerId = mergeCustomer(customerId, toLong(row.get("customer_id")), "fundAccountNo");
            fundAccountId = toLong(row.get("id"));
        }
        if (shareholderAccountNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, customer_id FROM acct_shareholder_account WHERE shareholder_account_no = ? LIMIT 1",
                    shareholderAccountNo
            );
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("证券账户不存在");
            }
            Map<String, Object> row = rows.get(0);
            customerId = mergeCustomer(customerId, toLong(row.get("customer_id")), "shareholderAccountNo");
        }
        if (orderNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, customer_id FROM trd_order WHERE order_no = ? LIMIT 1",
                    orderNo
            );
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("委托编号不存在");
            }
            Map<String, Object> row = rows.get(0);
            customerId = mergeCustomer(customerId, toLong(row.get("customer_id")), "orderNo");
            orderId = toLong(row.get("id"));
        }
        if (tradeNo != null) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, customer_id FROM trd_trade WHERE trade_no = ? LIMIT 1",
                    tradeNo
            );
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("成交编号不存在");
            }
            Map<String, Object> row = rows.get(0);
            customerId = mergeCustomer(customerId, toLong(row.get("customer_id")), "tradeNo");
            tradeDbId = toLong(row.get("id"));
        }

        return new ResolvedOperatorBusinessContext(customerId, fundAccountId, orderId, tradeDbId);
    }

    public Map<String, Object> pageOrdersForOperatorReport(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        ResolvedOperatorBusinessContext ctx = resolveOperatorBusinessContext(filter);
        int pageNo = filter.page();
        int size = filter.pageSize();
        int offset = (pageNo - 1) * size;

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        appendOrderScope(where, args, ctx);
        String sourceFilter = trimToNull(filter.sourceType());
        if (sourceFilter != null) {
            where.append(" AND o.source_type = ? ");
            args.add(sourceFilter.toUpperCase());
        }
        String statusFilter = trimToNull(filter.orderStatus());
        if (statusFilter != null) {
            where.append(" AND o.order_status = ? ");
            args.add(statusFilter.toUpperCase());
        }
        String group = trimToNull(filter.orderStatusGroup());
        if (group != null) {
            String g = group.toUpperCase();
            if (!"ACTIVE".equals(g) && !"CANCELED".equals(g) && !"COMPLETED".equals(g)) {
                throw new IllegalArgumentException("orderStatusGroup 仅支持 ACTIVE、COMPLETED 或 CANCELED");
            }
            where.append(" AND (");
            where.append(switch (g) {
                case "ACTIVE" -> " o.order_status IN ('INIT','REPORTED','PART_FILLED') ";
                case "COMPLETED" -> " (o.order_status = 'FILLED' OR (o.order_status = 'PART_CANCELED' AND o.filled_qty > 0)) ";
                default -> " o.order_status IN ('CANCELED','PART_CANCELED') ";
            });
            where.append(") ");
        }
        appendDateRange(where, args, "o.created_at", filter.orderDateFrom(), filter.orderDateTo());

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_order o " + where,
                Integer.class,
                args.toArray()
        );

        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add(offset);
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
                        + " ORDER BY o.created_at DESC LIMIT ? OFFSET ? ",
                argsPage.toArray()
        );

        List<Map<String, Object>> list = rows.stream().map(this::toOrderListItem).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", list);
        return data;
    }

    public Map<String, Object> pageTradesForOperatorReport(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        ResolvedOperatorBusinessContext ctx = resolveOperatorBusinessContext(filter);
        int pageNo = filter.page();
        int size = filter.pageSize();
        int offset = (pageNo - 1) * size;

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (ctx.tradeDbId() != null) {
            where.append(" AND t.id = ? ");
            args.add(ctx.tradeDbId());
        }
        if (ctx.customerId() != null) {
            where.append(" AND t.customer_id = ? ");
            args.add(ctx.customerId());
        }
        if (ctx.fundAccountId() != null) {
            where.append(" AND t.fund_account_id = ? ");
            args.add(ctx.fundAccountId());
        }
        appendDateRange(where, args, "t.trade_time", filter.tradeDateFrom(), filter.tradeDateTo());

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_trade t " + where,
                Integer.class,
                args.toArray()
        );

        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.trade_no, t.order_no, t.market_code, t.security_code, t.trade_direction,
                       t.trade_price, t.trade_qty, t.trade_amount, t.commission_amount, t.tax_amount, t.net_settle_amount,
                       t.trade_time, t.settle_status, fa.fund_account_no, cc.customer_code
                FROM trd_trade t
                LEFT JOIN acct_fund_account fa ON fa.id = t.fund_account_id
                LEFT JOIN cust_customer cc ON cc.id = t.customer_id
                """
                        + where
                        + " ORDER BY t.trade_time DESC LIMIT ? OFFSET ? ",
                argsPage.toArray()
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", rows);
        return data;
    }

    public Map<String, Object> pageAssetJournalForOperatorReport(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        ResolvedOperatorBusinessContext ctx = resolveOperatorBusinessContext(filter);
        Long customerId = ctx.customerId();
        Long fundAccountId = ctx.fundAccountId();
        if (customerId == null && fundAccountId == null) {
            throw new IllegalArgumentException("资产流水查询请至少提供 customerCode、idNo、fundAccountNo、orderNo 或 tradeNo 之一以定位客户或资金户");
        }
        return pageAssetJournal(customerId, fundAccountId, filter.journalDateFrom(), filter.journalDateTo(), filter.page(), filter.pageSize());
    }

    public Map<String, Object> pageRiskEventsForOperatorReport(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        ResolvedOperatorBusinessContext ctx = resolveOperatorBusinessContext(filter);
        String status = trimToNull(filter.riskEventStatus());
        return pageRiskEvents(ctx.customerId(), ctx.fundAccountId(), ctx.orderId(), status, filter.riskDateFrom(), filter.riskDateTo(), filter.page(), filter.pageSize(), false);
    }

    public Map<String, Object> pageOperationLogsForOperator(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        int pageNo = filter.page();
        int size = filter.pageSize();
        int offset = (pageNo - 1) * size;
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        String module = trimToNull(filter.opLogModule());
        if (module != null) {
            where.append(" AND l.module_name = ? ");
            args.add(module);
        }
        String bizType = trimToNull(filter.opLogBizType());
        if (bizType != null) {
            where.append(" AND l.biz_type = ? ");
            args.add(bizType);
        }
        String bizNo = trimToNull(filter.opLogBizNo());
        if (bizNo != null) {
            where.append(" AND l.biz_no = ? ");
            args.add(bizNo);
        }
        if (filter.opLogOperatorId() != null) {
            where.append(" AND l.operator_id = ? ");
            args.add(filter.opLogOperatorId());
        }
        appendDateRange(where, args, "l.occurred_at", filter.opLogDateFrom(), filter.opLogDateTo());

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_operation_log l " + where,
                Integer.class,
                args.toArray()
        );
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT l.log_no, l.module_name, l.biz_type, l.biz_no, l.operation_type, l.operation_result,
                       l.operation_content, l.occurred_at, o.operator_code, o.operator_name
                FROM sys_operation_log l
                JOIN sys_operator o ON o.id = l.operator_id
                """
                        + where
                        + " ORDER BY l.occurred_at DESC LIMIT ? OFFSET ? ",
                argsPage.toArray()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", rows);
        return data;
    }

    public List<Map<String, Object>> listOrderDispatchesForOperator(String token, String orderNo) {
        requireOperatorId(token);
        if (isBlank(orderNo)) {
            throw new IllegalArgumentException("请提供 orderNo");
        }
        return jdbcTemplate.queryForList(
                """
                SELECT d.dispatch_no, d.dispatch_type, d.request_seq_no, d.dispatch_status, d.external_order_no,
                       d.sent_at, d.created_at
                FROM rpt_order_dispatch d
                JOIN trd_order o ON o.id = d.order_id
                WHERE o.order_no = ?
                ORDER BY d.created_at DESC
                LIMIT 500
                """,
                orderNo.trim()
        );
    }

    public List<Map<String, Object>> listCancelRequestsForOperator(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        ResolvedOperatorBusinessContext ctx = resolveOperatorBusinessContext(filter);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        appendOrderJoinScope(where, args, ctx, "o");
        appendDateRange(where, args, "c.request_time", filter.orderDateFrom(), filter.orderDateTo());
        return jdbcTemplate.queryForList(
                """
                SELECT c.cancel_no, c.order_no_snapshot, c.cancel_status, c.request_qty, c.cancel_reason,
                       c.request_time, c.confirmed_time, c.reject_message, o.order_status, o.customer_id
                FROM trd_cancel_request c
                JOIN trd_order o ON o.id = c.order_id
                """
                        + where
                        + " ORDER BY c.request_time DESC LIMIT 500 ",
                args.toArray()
        );
    }

    public List<Map<String, Object>> listFeeDetailsForOperator(String token, OperatorReportFilter filter) {
        requireOperatorId(token);
        ResolvedOperatorBusinessContext ctx = resolveOperatorBusinessContext(filter);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (ctx.tradeDbId() != null) {
            where.append(" AND f.trade_id = ? ");
            args.add(ctx.tradeDbId());
        } else if (ctx.orderId() != null) {
            where.append(" AND f.order_id = ? ");
            args.add(ctx.orderId());
        } else if (ctx.customerId() != null) {
            where.append(" AND (f.order_id IN (SELECT id FROM trd_order WHERE customer_id = ?) ");
            args.add(ctx.customerId());
            where.append(" OR f.trade_id IN (SELECT id FROM trd_trade WHERE customer_id = ?)) ");
            args.add(ctx.customerId());
        } else {
            throw new IllegalArgumentException("费用明细查询请至少提供 customerCode、idNo、fundAccountNo、orderNo 或 tradeNo 之一");
        }
        return jdbcTemplate.queryForList(
                """
                SELECT f.id, f.biz_type, f.biz_id, f.order_id, f.trade_id, f.fee_type, f.fee_amount, f.calc_base, f.fee_rate, f.created_at, f.remark
                FROM trd_fee_detail f
                """
                        + where
                        + " ORDER BY f.created_at DESC LIMIT 500 ",
                args.toArray()
        );
    }

    public List<Map<String, Object>> searchCustomersForOperator(String token, String keyword, String idNo) {
        requireOperatorId(token);
        String kw = trimToNull(keyword);
        String id = trimToNull(idNo);
        String like = kw == null ? null : "%" + kw + "%";
        return jdbcTemplate.queryForList(
                """
                SELECT id, customer_code, customer_name, id_type, id_no, acct_cls_code, customer_status, risk_level, open_date
                FROM cust_customer
                WHERE (? IS NULL OR customer_code LIKE ? OR customer_name LIKE ? OR id_no LIKE ?)
                  AND (? IS NULL OR id_no = ?)
                ORDER BY id DESC
                LIMIT 200
                """,
                like, like, like, like, id, id
        );
    }

    private Map<String, Object> pageAssetJournal(Long customerId, Long fundAccountId, LocalDate from, LocalDate to, int page, int pageSize) {
        int pageNo = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 200);
        int offset = (pageNo - 1) * size;
        if (customerId == null && fundAccountId == null) {
            return emptyPage(pageNo, size);
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (customerId != null) {
            where.append(" AND j.customer_id = ? ");
            args.add(customerId);
        }
        if (fundAccountId != null) {
            where.append(" AND j.fund_account_id = ? ");
            args.add(fundAccountId);
        }
        appendDateRange(where, args, "j.occur_time", from, to);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM acct_asset_journal j " + where,
                Integer.class,
                args.toArray()
        );
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT j.journal_no, j.asset_type, j.change_type, j.direction, j.before_amount, j.change_amount, j.after_amount,
                       j.ref_type, j.ref_no, j.occur_time, j.remark, fa.fund_account_no
                FROM acct_asset_journal j
                LEFT JOIN acct_fund_account fa ON fa.id = j.fund_account_id
                """
                        + where
                        + " ORDER BY j.occur_time DESC LIMIT ? OFFSET ? ",
                argsPage.toArray()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", rows);
        return data;
    }

    private Map<String, Object> pageTrades(Long customerId, Long fundAccountId, LocalDate from, LocalDate to, int page, int pageSize) {
        int pageNo = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 200);
        int offset = (pageNo - 1) * size;
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE t.customer_id = ? ");
        args.add(customerId);
        if (fundAccountId != null) {
            where.append(" AND t.fund_account_id = ? ");
            args.add(fundAccountId);
        }
        appendDateRange(where, args, "t.trade_time", from, to);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_trade t " + where,
                Integer.class,
                args.toArray()
        );
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT t.trade_no, t.order_no, t.market_code, t.security_code, t.trade_direction,
                       t.trade_price, t.trade_qty, t.trade_amount, t.commission_amount, t.tax_amount, t.net_settle_amount, t.trade_time
                FROM trd_trade t
                """
                        + where
                        + " ORDER BY t.trade_time DESC LIMIT ? OFFSET ? ",
                argsPage.toArray()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", rows);
        return data;
    }

    private Map<String, Object> pageRiskEvents(
            Long customerId,
            Long fundAccountId,
            Long orderId,
            String eventStatus,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize,
            boolean requireEntityScope
    ) {
        int pageNo = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 200);
        int offset = (pageNo - 1) * size;
        if (requireEntityScope && customerId == null && fundAccountId == null && orderId == null) {
            return emptyPage(pageNo, size);
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (customerId != null) {
            where.append(" AND e.customer_id = ? ");
            args.add(customerId);
        }
        if (fundAccountId != null) {
            where.append(" AND e.fund_account_id = ? ");
            args.add(fundAccountId);
        }
        if (orderId != null) {
            where.append(" AND e.order_id = ? ");
            args.add(orderId);
        }
        if (eventStatus != null) {
            where.append(" AND e.event_status = ? ");
            args.add(eventStatus.toUpperCase());
        }
        appendDateRange(where, args, "e.created_at", from, to);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM risk_event e " + where,
                Integer.class,
                args.toArray()
        );
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT e.event_no, e.event_type, e.risk_level, e.is_blocking, e.hit_message, e.event_status,
                       e.handled_at, e.created_at, e.customer_id, e.order_id,
                       r.rule_code, r.rule_name,
                       c.customer_code, c.customer_name
                FROM risk_event e
                JOIN risk_rule r ON r.id = e.rule_id
                LEFT JOIN cust_customer c ON c.id = e.customer_id
                """
                        + where
                        + " ORDER BY e.created_at DESC LIMIT ? OFFSET ? ",
                argsPage.toArray()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", rows);
        return data;
    }

    private void appendOrderScope(StringBuilder where, List<Object> args, ResolvedOperatorBusinessContext ctx) {
        appendOrderJoinScope(where, args, ctx, "o");
    }

    private void appendOrderJoinScope(StringBuilder where, List<Object> args, ResolvedOperatorBusinessContext ctx, String alias) {
        if (ctx.orderId() != null) {
            where.append(" AND ").append(alias).append(".id = ? ");
            args.add(ctx.orderId());
            return;
        }
        if (ctx.customerId() != null) {
            where.append(" AND ").append(alias).append(".customer_id = ? ");
            args.add(ctx.customerId());
        }
        if (ctx.fundAccountId() != null) {
            where.append(" AND ").append(alias).append(".fund_account_id = ? ");
            args.add(ctx.fundAccountId());
        }
    }

    private void appendDateRange(StringBuilder where, List<Object> args, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            where.append(" AND ").append(column).append(" >= ? ");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            where.append(" AND ").append(column).append(" < ? ");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
    }

    private static Long mergeCustomer(Long current, Long next, String label) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        if (!current.equals(next)) {
            throw new IllegalArgumentException("查询条件指向不一致的客户实体: " + label);
        }
        return current;
    }

    private static Map<String, Object> emptyPage(int page, int size) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", page);
        data.put("pageSize", size);
        data.put("total", 0);
        data.put("list", List.of());
        return data;
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

    private static String maskIdNo(String idNo) {
        if (idNo == null || idNo.isBlank()) {
            return "";
        }
        String s = idNo.trim();
        if (s.length() <= 8) {
            return "****";
        }
        return s.substring(0, 4) + "****" + s.substring(s.length() - 4);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /** 报表看板汇总数据（全局统计，不依赖客户/账户过滤）。 */
    public Map<String, Object> getReportSummary(String token) {
        requireOperatorId(token);

        long totalOrders = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_order", Long.class));
        long todayOrders = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_order WHERE DATE(created_at) = CURDATE()", Long.class));
        long canceledOrders = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_order WHERE order_status IN ('CANCELED','PART_CANCELED')", Long.class));
        long totalTrades = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_trade", Long.class));
        long todayTrades = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM trd_trade WHERE DATE(trade_time) = CURDATE()", Long.class));
        BigDecimal totalTradeAmount = nzDecimal(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(trade_amount),0) FROM trd_trade", BigDecimal.class));
        BigDecimal todayTradeAmount = nzDecimal(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(trade_amount),0) FROM trd_trade WHERE DATE(trade_time) = CURDATE()", BigDecimal.class));
        long openRiskEvents = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM risk_event WHERE event_status = 'OPEN'", Long.class));
        long activeCustomers = nzLong(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM cust_customer WHERE customer_status = 'NORMAL'", Long.class));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalOrders", totalOrders);
        data.put("todayOrders", todayOrders);
        data.put("canceledOrders", canceledOrders);
        data.put("totalTrades", totalTrades);
        data.put("todayTrades", todayTrades);
        data.put("totalTradeAmount", totalTradeAmount);
        data.put("todayTradeAmount", todayTradeAmount);
        data.put("openRiskEvents", openRiskEvents);
        data.put("activeCustomers", activeCustomers);
        return data;
    }

    private static long nzLong(Long v) { return v == null ? 0L : v; }
    private static BigDecimal nzDecimal(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
