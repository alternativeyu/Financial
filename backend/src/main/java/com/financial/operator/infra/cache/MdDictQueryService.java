package com.financial.operator.infra.cache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典查询（Redis 缓存），供柜台/App 下拉与校验使用。
 */
@Service
public class MdDictQueryService {

    private final JdbcTemplate jdbcTemplate;

    public MdDictQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Cacheable(
            cacheNames = "dictItemsByCode",
            key = "#dictCode.trim().toUpperCase()",
            condition = "#dictCode != null && !#dictCode.isBlank()",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<Map<String, Object>> listItemsByDictCode(String dictCode) {
        if (dictCode == null || dictCode.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                SELECT i.item_code AS itemCode, i.item_name AS itemName, i.item_value AS itemValue, i.sort_no AS sortNo
                FROM md_dict_type t
                JOIN md_dict_item i ON i.dict_id = t.id
                WHERE t.dict_code = ? AND t.status = '1' AND i.status = '1'
                ORDER BY i.sort_no ASC, i.id ASC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("itemCode", rs.getString("itemCode"));
                    m.put("itemName", rs.getString("itemName"));
                    m.put("itemValue", rs.getString("itemValue"));
                    m.put("sortNo", rs.getInt("sortNo"));
                    return m;
                },
                dictCode.trim().toUpperCase()
        );
    }
}
