package com.financial.operator.controller;

import com.financial.operator.infra.cache.MdDictQueryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 元数据与主数据辅助接口（字典等，结果经 Redis 缓存）。
 */
@RestController
@RequestMapping("/api/operator/meta")
public class MetaController {

    private final MdDictQueryService dictQueryService;
    private final JdbcTemplate jdbcTemplate;

    public MetaController(MdDictQueryService dictQueryService, JdbcTemplate jdbcTemplate) {
        this.dictQueryService = dictQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/dict-items")
    public Map<String, Object> dictItems(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam String dictCode
    ) {
        requireOperatorSession(token);
        if (dictCode == null || dictCode.isBlank()) {
            throw new IllegalArgumentException("dictCode 不能为空");
        }
        List<Map<String, Object>> items = dictQueryService.listItemsByDictCode(dictCode);
        return Map.of("dictCode", dictCode.trim().toUpperCase(), "items", items);
    }

    private void requireOperatorSession(String token) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM op_login_session WHERE token = ? AND expire_at > NOW()",
                Integer.class,
                token
        );
        if (n == null || n == 0) {
            throw new IllegalArgumentException("登录已过期，请重新登录");
        }
    }
}
