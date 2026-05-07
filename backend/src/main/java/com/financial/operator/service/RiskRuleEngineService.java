package com.financial.operator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控规则引擎：从 risk_rule 加载规则配置，对 RiskContext 运行 PRE_ORDER / CANCEL_ORDER 检查，
 * 并将命中结果写入 risk_event 表。规则检查逻辑按 rule_code 硬编码；severity/block_flag 从 DB 读取，
 * 支持在不改代码的情况下调整规则的阻断策略与生效时间。
 */
@Service
public class RiskRuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(RiskRuleEngineService.class);

    private final JdbcTemplate jdbcTemplate;

    public RiskRuleEngineService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── 上下文与结果模型 ────────────────────────────────────────────────────────

    /** 下单前风控所需上下文（由调用方从 DB 预查后传入，避免引擎重复查询）。 */
    public record RiskContext(
            Long customerId,
            String customerStatus,
            Long fundAccountId,
            String fundAccountStatus,
            Long shareholderAccountId,
            String shareholderAccountStatus,
            Long securityId,
            String securityListedStatus,
            Integer lotSize,
            String direction,
            BigDecimal price,
            Long quantity,
            BigDecimal upperLimitPrice,
            BigDecimal lowerLimitPrice,
            BigDecimal availableCash,
            Long availableQty,
            BigDecimal estimatedTotalCost
    ) {}

    public record RiskViolation(
            long ruleId,
            String ruleCode,
            String ruleName,
            String hitMessage,
            String riskLevel,
            boolean blocking
    ) {}

    public record RiskCheckResult(
            boolean blocked,
            List<RiskViolation> violations
    ) {}

    private record RuleRow(
            long id,
            String ruleCode,
            String ruleName,
            String severity,
            boolean blockFlag,
            String paramJson
    ) {}

    // ── 公开方法 ────────────────────────────────────────────────────────────────

    /**
     * 运行 PRE_ORDER 阶段的所有有效规则，命中的规则写入 risk_event。
     * @param ctx     下单上下文（由调用方预查构建）
     * @param orderId 若已生成委托则传入，否则传 null
     */
    public RiskCheckResult check(RiskContext ctx, Long orderId) {
        List<RuleRow> rules = loadActiveRules("PRE_ORDER");
        List<RiskViolation> violations = new ArrayList<>();

        for (RuleRow rule : rules) {
            String msg = evaluate(rule, ctx);
            if (msg != null) {
                violations.add(new RiskViolation(rule.id(), rule.ruleCode(), rule.ruleName(),
                        msg, rule.severity(), rule.blockFlag()));
            }
        }

        for (RiskViolation v : violations) {
            writeRiskEvent(v, ctx.customerId(), ctx.fundAccountId(), orderId, ctx.securityId());
        }

        boolean blocked = violations.stream().anyMatch(RiskViolation::blocking);
        return new RiskCheckResult(blocked, violations);
    }

    /**
     * R010：检查当日撤单频率。block_flag=0 时只写预警事件，不阻断撤单。
     * @return true 表示阻断（当前 seed 中 block_flag=0，默认不阻断）
     */
    public boolean checkCancelFrequency(Long customerId, Long orderId) {
        List<RuleRow> rules = loadActiveRules("CANCEL_ORDER");
        RuleRow r010 = rules.stream()
                .filter(r -> "R010".equals(r.ruleCode()))
                .findFirst().orElse(null);
        if (r010 == null) return false;

        int threshold = parseIntParam(r010.paramJson(), "maxCancelPerDay", 10);
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM trd_cancel_request cr
                JOIN trd_order o ON o.id = cr.order_id
                WHERE o.customer_id = ?
                  AND DATE(cr.request_time) = CURDATE()
                  AND cr.cancel_status NOT IN ('REJECTED')
                """,
                Integer.class, customerId
        );

        int cnt = count == null ? 0 : count;
        if (cnt >= threshold) {
            RiskViolation v = new RiskViolation(r010.id(), r010.ruleCode(), r010.ruleName(),
                    "当日撤单次数(" + cnt + ")已达阈值(" + threshold + ")，触发行为预警",
                    r010.severity(), r010.blockFlag());
            writeRiskEvent(v, customerId, null, orderId, null);
            return r010.blockFlag();
        }
        return false;
    }

    // ── 私有方法 ────────────────────────────────────────────────────────────────

    private List<RuleRow> loadActiveRules(String stage) {
        String today = LocalDate.now().toString();
        return jdbcTemplate.query(
                """
                SELECT id, rule_code, rule_name, severity, block_flag, param_json
                FROM risk_rule
                WHERE rule_stage = ? AND status = '1'
                  AND effective_date <= ?
                  AND (expire_date IS NULL OR expire_date > ?)
                ORDER BY rule_code
                """,
                (rs, i) -> new RuleRow(
                        rs.getLong("id"),
                        rs.getString("rule_code"),
                        rs.getString("rule_name"),
                        rs.getString("severity"),
                        rs.getBoolean("block_flag"),
                        rs.getString("param_json")
                ),
                stage, today, today
        );
    }

    private String evaluate(RuleRow rule, RiskContext ctx) {
        return switch (rule.ruleCode()) {
            case "R001" -> {
                if (!"NORMAL".equalsIgnoreCase(ctx.customerStatus()))
                    yield "客户状态异常(" + ctx.customerStatus() + ")，禁止交易";
                yield null;
            }
            case "R002" -> {
                if (!"NORMAL".equalsIgnoreCase(ctx.fundAccountStatus()))
                    yield "资金账户状态异常(" + ctx.fundAccountStatus() + ")，禁止交易";
                yield null;
            }
            case "R003" -> {
                String ss = ctx.shareholderAccountStatus();
                if (ss != null && !"NORMAL".equalsIgnoreCase(ss))
                    yield "股东账户状态异常(" + ss + ")，禁止交易";
                yield null;
            }
            case "R004" -> {
                if (!"1".equals(ctx.securityListedStatus()))
                    yield "证券不可交易，上市状态：" + ctx.securityListedStatus();
                yield null;
            }
            case "R005" -> {
                if (ctx.lotSize() != null && ctx.lotSize() > 0 && ctx.quantity() % ctx.lotSize() != 0)
                    yield "委托数量(" + ctx.quantity() + ")不是交易单位(" + ctx.lotSize() + ")的整数倍";
                yield null;
            }
            case "R006" -> {
                String dir = ctx.direction();
                if ("B".equalsIgnoreCase(dir) && ctx.upperLimitPrice() != null
                        && ctx.price().compareTo(ctx.upperLimitPrice()) > 0)
                    yield "买入委托价(" + ctx.price() + ")超过涨停价(" + ctx.upperLimitPrice() + ")";
                if ("S".equalsIgnoreCase(dir) && ctx.lowerLimitPrice() != null
                        && ctx.price().compareTo(ctx.lowerLimitPrice()) < 0)
                    yield "卖出委托价(" + ctx.price() + ")低于跌停价(" + ctx.lowerLimitPrice() + ")";
                yield null;
            }
            case "R007" -> {
                if ("B".equalsIgnoreCase(ctx.direction())) {
                    BigDecimal avail = ctx.availableCash() == null ? BigDecimal.ZERO : ctx.availableCash();
                    if (avail.compareTo(ctx.estimatedTotalCost()) < 0)
                        yield "可用资金(" + avail + ")不足，预估所需：" + ctx.estimatedTotalCost();
                }
                yield null;
            }
            case "R008" -> {
                if ("S".equalsIgnoreCase(ctx.direction())) {
                    long avq = ctx.availableQty() == null ? 0L : ctx.availableQty();
                    if (avq < ctx.quantity())
                        yield "可卖持仓(" + avq + ")不足，委托数量：" + ctx.quantity();
                }
                yield null;
            }
            case "R009" -> {
                BigDecimal maxAmt = parseDecimalParam(rule.paramJson(), "maxAmount", new BigDecimal("5000000"));
                BigDecimal orderAmt = ctx.price().multiply(BigDecimal.valueOf(ctx.quantity()));
                if (orderAmt.compareTo(maxAmt) > 0)
                    yield "单笔委托金额(" + orderAmt + ")超过上限(" + maxAmt + ")，触发预警";
                yield null;
            }
            default -> null;
        };
    }

    private void writeRiskEvent(RiskViolation v, Long customerId, Long fundAccountId,
                                Long orderId, Long securityId) {
        try {
            long id = System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
            String eventNo = "RE" + LocalDate.now().toString().replace("-", "")
                    + String.format("%08d", id % 100_000_000L);
            jdbcTemplate.update(
                    """
                    INSERT INTO risk_event
                    (id, event_no, rule_id, event_type, risk_level, is_blocking,
                     customer_id, fund_account_id, order_id, security_id,
                     hit_message, event_status, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,'OPEN',NOW())
                    """,
                    id, eventNo, v.ruleId(), "PRE_ORDER", v.riskLevel(),
                    v.blocking() ? 1 : 0,
                    customerId, fundAccountId, orderId, securityId,
                    v.hitMessage().length() > 255 ? v.hitMessage().substring(0, 255) : v.hitMessage()
            );
        } catch (Exception e) {
            log.warn("写入 risk_event 失败: {}", e.getMessage());
        }
    }

    private int parseIntParam(String json, String key, int defaultVal) {
        if (json == null || json.isBlank()) return defaultVal;
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return defaultVal;
            int colon = json.indexOf(":", idx);
            if (colon < 0) return defaultVal;
            String rest = json.substring(colon + 1).trim();
            StringBuilder sb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isDigit(c)) sb.append(c);
                else if (!sb.isEmpty()) break;
            }
            return sb.isEmpty() ? defaultVal : Integer.parseInt(sb.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private BigDecimal parseDecimalParam(String json, String key, BigDecimal defaultVal) {
        if (json == null || json.isBlank()) return defaultVal;
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return defaultVal;
            int colon = json.indexOf(":", idx);
            if (colon < 0) return defaultVal;
            String rest = json.substring(colon + 1).trim();
            StringBuilder sb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isDigit(c) || c == '.' || (sb.isEmpty() && c == '-')) sb.append(c);
                else if (!sb.isEmpty()) break;
            }
            return sb.isEmpty() ? defaultVal : new BigDecimal(sb.toString());
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
