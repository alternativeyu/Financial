package com.financial.operator.controller;

import com.financial.operator.dto.ApiResponse;
import com.financial.operator.service.UnifiedBusinessQueryService;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * App 端报表查询：仅允许按当前登录用户（userId）解析出的客户维度查本人数据，不接收任意客户代码等越权参数。
 */
@Validated
@RestController
@RequestMapping("/api/app/report")
public class AppReportController {

    private final UnifiedBusinessQueryService queryService;

    public AppReportController(UnifiedBusinessQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@RequestParam @NotNull Long userId) {
        try {
            return ApiResponse.ok(queryService.appCustomerProfile(userId));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/asset-journal")
    public ApiResponse<Map<String, Object>> assetJournal(
            @RequestParam @NotNull Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            return ApiResponse.ok(queryService.pageAssetJournalForApp(userId, from, to, page, pageSize));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/trades")
    public ApiResponse<Map<String, Object>> trades(
            @RequestParam @NotNull Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            return ApiResponse.ok(queryService.pageTradesForApp(userId, from, to, page, pageSize));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/risk-events")
    public ApiResponse<Map<String, Object>> riskEvents(
            @RequestParam @NotNull Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            return ApiResponse.ok(queryService.pageRiskEventsForApp(userId, from, to, page, pageSize));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications(
            @RequestParam @NotNull Long userId,
            @RequestParam(defaultValue = "30") int limit
    ) {
        try {
            return ApiResponse.ok(queryService.listNotificationsForApp(userId, limit));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }
}
