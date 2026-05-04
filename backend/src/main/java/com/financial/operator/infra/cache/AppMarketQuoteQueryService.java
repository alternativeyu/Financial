package com.financial.operator.infra.cache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行情列表与明细查询（Redis 缓存，减轻 md_security + md_market_quote 高频读）。
 */
@Service
public class AppMarketQuoteQueryService {

    private final JdbcTemplate jdbcTemplate;

    public AppMarketQuoteQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(cacheNames = "marketQuotesPage", key = "'p:' + (#marketCode == null ? 'all' : #marketCode) + ':' + (#keywordLike == null ? '' : #keywordLike) + ':' + #pageNo + ':' + #size")
    public Map<String, Object> loadQuotesPage(String marketCode, String keywordLike, int pageNo, int size, int offset) {
        String where = """
                WHERE s.listed_status = '1'
                  AND (? IS NULL OR s.market_code = ?)
                  AND (? IS NULL OR s.security_code LIKE ? OR s.security_name LIKE ?)
                """;

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM md_security s " + where,
                Integer.class,
                marketCode, marketCode, keywordLike, keywordLike, keywordLike
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT
                  s.market_code,
                  s.security_code,
                  s.security_name,
                  q.current_price,
                  q.prev_close_price,
                  q.upper_limit_price,
                  q.lower_limit_price,
                  q.volume,
                  q.amount,
                  q.quote_time
                FROM md_security s
                LEFT JOIN md_market_quote q
                  ON q.security_id = s.id
                 AND q.quote_time = (
                     SELECT MAX(q2.quote_time) FROM md_market_quote q2 WHERE q2.security_id = s.id
                 )
                """ + where + """
                ORDER BY s.market_code ASC, s.security_code ASC
                LIMIT ? OFFSET ?
                """,
                marketCode, marketCode, keywordLike, keywordLike, keywordLike, size, offset
        );

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(toQuoteItem(row));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("page", pageNo);
        data.put("pageSize", size);
        data.put("total", total == null ? 0 : total);
        data.put("list", list);
        return data;
    }

    @Cacheable(cacheNames = "marketQuoteDetail", key = "'d:' + #marketCode + ':' + #securityCodeTrim")
    public Map<String, Object> loadQuoteDetail(String marketCode, String securityCodeTrim) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT
                  s.market_code,
                  s.security_code,
                  s.security_name,
                  q.current_price,
                  q.prev_close_price,
                  q.upper_limit_price,
                  q.lower_limit_price,
                  q.volume,
                  q.amount,
                  q.quote_time
                FROM md_security s
                LEFT JOIN md_market_quote q
                  ON q.security_id = s.id
                 AND q.quote_time = (
                     SELECT MAX(q2.quote_time) FROM md_market_quote q2 WHERE q2.security_id = s.id
                 )
                WHERE s.market_code = ? AND s.security_code = ?
                LIMIT 1
                """,
                marketCode, securityCodeTrim
        );
        if (rows.isEmpty()) {
            return null;
        }
        return toQuoteItem(rows.get(0));
    }

    private Map<String, Object> toQuoteItem(Map<String, Object> row) {
        BigDecimal current = asDecimal(row.get("current_price"));
        BigDecimal prev = asDecimal(row.get("prev_close_price"));
        BigDecimal change = (current != null && prev != null) ? current.subtract(prev) : null;
        BigDecimal changePct = null;
        if (change != null && prev != null && prev.compareTo(BigDecimal.ZERO) != 0) {
            changePct = change.multiply(BigDecimal.valueOf(100))
                    .divide(prev, 4, RoundingMode.HALF_UP);
        }

        String marketCode = asString(row.get("market_code"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("marketCode", marketCode);
        item.put("market", "1".equals(marketCode) ? "SH" : "SZ");
        item.put("marketName", "1".equals(marketCode) ? "沪A" : "深A");
        item.put("securityCode", asString(row.get("security_code")));
        item.put("securityName", asString(row.get("security_name")));
        item.put("currentPrice", current);
        item.put("prevClosePrice", prev);
        item.put("changeAmount", change);
        item.put("changePct", changePct);
        item.put("upperLimitPrice", asDecimal(row.get("upper_limit_price")));
        item.put("lowerLimitPrice", asDecimal(row.get("lower_limit_price")));
        item.put("volume", row.get("volume"));
        item.put("amount", row.get("amount"));
        item.put("quoteTime", row.get("quote_time"));
        return item;
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
}
