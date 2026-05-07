package com.financial.operator.controller;

import com.financial.operator.dto.ApiResponse;
import com.financial.operator.service.UnifiedBusinessQueryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 风险事件处理接口：柜台操作员对 OPEN 状态的风控事件执行确认/忽略/关闭操作。
 * 事件列表查询复用 {@link OperatorReportController}（/api/operator/report/risk-events）。
 */
@Validated
@RestController
@RequestMapping("/api/operator/risk-events")
public class RiskEventController {

    private final JdbcTemplate jdbcTemplate;
    private final UnifiedBusinessQueryService queryService;

    public RiskEventController(JdbcTemplate jdbcTemplate, UnifiedBusinessQueryService queryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.queryService = queryService;
    }

    /**
     * 处理风险事件：将 OPEN 状态的事件标记为 HANDLED / IGNORED / CLOSED。
     * POST /api/operator/risk-events/{eventNo}/handle
     */
    @PostMapping("/{eventNo}/handle")
    public ApiResponse<Map<String, Object>> handleRiskEvent(
            @RequestHeader("X-Operator-Token") String token,
            @PathVariable String eventNo,
            @RequestBody HandleRequest request
    ) {
        try {
            Long operatorId = queryService.requireOperatorId(token);

            if (!List.of("HANDLED", "IGNORED", "CLOSED").contains(request.action())) {
                return ApiResponse.fail(1001, "action 仅支持 HANDLED / IGNORED / CLOSED");
            }

            int updated = jdbcTemplate.update(
                    """
                    UPDATE risk_event
                    SET event_status = ?, handled_by = ?, handled_at = NOW()
                    WHERE event_no = ? AND event_status = 'OPEN'
                    """,
                    request.action(), operatorId, eventNo
            );

            if (updated == 0) {
                return ApiResponse.fail(2001, "事件不存在或已处理");
            }
            return ApiResponse.ok(Map.of("eventNo", eventNo, "newStatus", request.action()));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    record HandleRequest(@NotBlank String action) {}
}
