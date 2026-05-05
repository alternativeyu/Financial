package com.financial.operator.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 从 {@code md_commission_rule}、{@code md_tax_rule} 解析费率；无匹配规则时回退与历史演示一致的默认值。
 * 与概要设计「佣金/印花税从规则表读取、买入冻结含预估费用」对齐。
 */
@Service
public class TradingFeeRuleService {

    static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.00025");
    static final BigDecimal DEFAULT_MIN_COMMISSION = new BigDecimal("0.0100");
    static final BigDecimal DEFAULT_STAMP_SELL_RATE = new BigDecimal("0.001");

    private final JdbcTemplate jdbcTemplate;

    public TradingFeeRuleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record CommissionRule(BigDecimal rate, BigDecimal minFee, BigDecimal maxFee) {
        static CommissionRule defaults() {
            return new CommissionRule(DEFAULT_COMMISSION_RATE, DEFAULT_MIN_COMMISSION, null);
        }
    }

    /**
     * 按成交金额计算佣金（已含 min/max 夹逼）。
     */
    public BigDecimal commissionOnTurnover(BigDecimal turnover, CommissionRule rule) {
        if (turnover == null || turnover.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal raw = turnover.multiply(rule.rate()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal v = raw.max(nzDecimal(rule.minFee()));
        if (rule.maxFee() != null) {
            v = v.min(rule.maxFee());
        }
        return v.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 买入侧预估印花税（一般为 0）；若规则表配置了 B 方向 STAMP 则使用。
     */
    public BigDecimal buyStampOnTurnover(BigDecimal turnover, String marketCode, String securityType) {
        BigDecimal rate = loadTaxRate("STAMP", marketCode, securityType, "B");
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return turnover.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    public CommissionRule loadCommission(String acctClsCode, String marketCode, String securityType) {
        CommissionRule row = queryCommissionRow(acctClsCode, marketCode, securityType);
        if (row != null) {
            return row;
        }
        if (securityType != null && !"COMMON".equalsIgnoreCase(securityType)) {
            CommissionRule common = queryCommissionRow(acctClsCode, marketCode, "COMMON");
            if (common != null) {
                return common;
            }
        }
        return CommissionRule.defaults();
    }

    /**
     * 卖出印花税率（比例）；无规则时 {@link #DEFAULT_STAMP_SELL_RATE}。
     */
    public BigDecimal loadSellStampRate(String marketCode, String securityType) {
        BigDecimal r = loadTaxRate("STAMP", marketCode, securityType, "S");
        return r.compareTo(BigDecimal.ZERO) > 0 ? r : DEFAULT_STAMP_SELL_RATE;
    }

    private CommissionRule queryCommissionRow(String acctClsCode, String marketCode, String securityType) {
        if (acctClsCode == null || marketCode == null || securityType == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT commission_rate, min_fee, max_fee
                FROM md_commission_rule
                WHERE status = '1'
                  AND acct_cls_code = ?
                  AND market_code = ?
                  AND security_type = ?
                  AND effective_date <= ?
                  AND (expire_date IS NULL OR expire_date >= ?)
                ORDER BY effective_date DESC
                LIMIT 1
                """,
                acctClsCode, marketCode, securityType, LocalDate.now(), LocalDate.now()
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> m = rows.get(0);
        return new CommissionRule(
                nzDecimal(m.get("commission_rate")),
                nzDecimal(m.get("min_fee")),
                m.get("max_fee") == null ? null : nzDecimal(m.get("max_fee"))
        );
    }

    private BigDecimal loadTaxRate(String feeType, String marketCode, String securityType, String tradeDirection) {
        if (marketCode == null || securityType == null) {
            return BigDecimal.ZERO;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT tax_rate
                FROM md_tax_rule
                WHERE status = '1'
                  AND fee_type = ?
                  AND market_code = ?
                  AND security_type = ?
                  AND trade_direction = ?
                  AND effective_date <= ?
                  AND (expire_date IS NULL OR expire_date >= ?)
                ORDER BY effective_date DESC
                LIMIT 1
                """,
                feeType, marketCode, securityType, tradeDirection, LocalDate.now(), LocalDate.now()
        );
        if (rows.isEmpty() && !"COMMON".equalsIgnoreCase(securityType)) {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT tax_rate
                    FROM md_tax_rule
                    WHERE status = '1'
                      AND fee_type = ?
                      AND market_code = ?
                      AND security_type = 'COMMON'
                      AND trade_direction = ?
                      AND effective_date <= ?
                      AND (expire_date IS NULL OR expire_date >= ?)
                    ORDER BY effective_date DESC
                    LIMIT 1
                    """,
                    feeType, marketCode, tradeDirection, LocalDate.now(), LocalDate.now()
            );
        }
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return nzDecimal(rows.get(0).get("tax_rate"));
    }

    private static BigDecimal nzDecimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal d) {
            return d;
        }
        return new BigDecimal(String.valueOf(v));
    }
}
