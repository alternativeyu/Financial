package com.financial.operator.controller;

import com.financial.operator.dto.ApiResponse;
import com.financial.operator.service.OperatorReportFilter;
import com.financial.operator.service.UnifiedBusinessQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 柜台端报表查询：多维度条件由服务端解析为 {@link UnifiedBusinessQueryService} 的安全查询，禁止客户端拼接 SQL。
 */
@RestController
@RequestMapping("/api/operator/report")
public class OperatorReportController {

    private final UnifiedBusinessQueryService queryService;

    public OperatorReportController(UnifiedBusinessQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/customers")
    public ApiResponse<List<Map<String, Object>>> searchCustomers(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String idNo
    ) {
        try {
            return ApiResponse.ok(queryService.searchCustomersForOperator(token, keyword, idNo));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String idNo,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String shareholderAccountNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String orderStatusGroup,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            OperatorReportFilter filter = OperatorReportFilter.orders(
                    customerCode, idNo, fundAccountNo, shareholderAccountNo, orderNo,
                    sourceType, orderStatus, orderStatusGroup, orderDateFrom, orderDateTo,
                    page, pageSize
            );
            return ApiResponse.ok(queryService.pageOrdersForOperatorReport(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/trades")
    public ApiResponse<Map<String, Object>> trades(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String idNo,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String tradeNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            OperatorReportFilter filter = OperatorReportFilter.trades(
                    customerCode, idNo, fundAccountNo, tradeNo, tradeDateFrom, tradeDateTo, page, pageSize
            );
            return ApiResponse.ok(queryService.pageTradesForOperatorReport(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/asset-journal")
    public ApiResponse<Map<String, Object>> assetJournal(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String idNo,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String tradeNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journalDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journalDateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            OperatorReportFilter filter = new OperatorReportFilter(
                    customerCode, idNo, fundAccountNo, null, orderNo, tradeNo,
                    null, null, null,
                    null, null,
                    null, null,
                    journalDateFrom, journalDateTo,
                    null, null,
                    null,
                    null, null, null, null, null, null,
                    page, pageSize
            );
            return ApiResponse.ok(queryService.pageAssetJournalForOperatorReport(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/risk-events")
    public ApiResponse<Map<String, Object>> riskEvents(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String idNo,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String riskEventStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate riskDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate riskDateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            OperatorReportFilter filter = OperatorReportFilter.risk(
                    customerCode, idNo, fundAccountNo, orderNo, riskEventStatus, riskDateFrom, riskDateTo, page, pageSize
            );
            return ApiResponse.ok(queryService.pageRiskEventsForOperatorReport(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/operation-logs")
    public ApiResponse<Map<String, Object>> operationLogs(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String moduleName,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        try {
            OperatorReportFilter filter = OperatorReportFilter.operationLog(
                    moduleName, bizType, bizNo, operatorId, occurredFrom, occurredTo, page, pageSize
            );
            return ApiResponse.ok(queryService.pageOperationLogsForOperator(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/dispatches")
    public ApiResponse<List<Map<String, Object>>> dispatches(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam String orderNo
    ) {
        try {
            return ApiResponse.ok(queryService.listOrderDispatchesForOperator(token, orderNo));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/cancel-requests")
    public ApiResponse<List<Map<String, Object>>> cancelRequests(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String idNo,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDateTo
    ) {
        try {
            OperatorReportFilter filter = OperatorReportFilter.orders(
                    customerCode, idNo, fundAccountNo, null, orderNo,
                    null, null, null, orderDateFrom, orderDateTo,
                    1, 1
            );
            return ApiResponse.ok(queryService.listCancelRequestsForOperator(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    /** 报表看板：全局汇总数据（委托数、成交额、撤单数、风险事件数等）。 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestHeader("X-Operator-Token") String token
    ) {
        try {
            return ApiResponse.ok(queryService.getReportSummary(token));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }

    @GetMapping("/fee-details")
    public ApiResponse<List<Map<String, Object>>> feeDetails(
            @RequestHeader("X-Operator-Token") String token,
            @RequestParam(required = false) String customerCode,
            @RequestParam(required = false) String idNo,
            @RequestParam(required = false) String fundAccountNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String tradeNo
    ) {
        try {
            OperatorReportFilter filter = new OperatorReportFilter(
                    customerCode, idNo, fundAccountNo, null, orderNo, tradeNo,
                    null, null, null,
                    null, null,
                    null, null,
                    null, null,
                    null, null,
                    null,
                    null, null, null, null, null, null,
                    1, 1
            );
            return ApiResponse.ok(queryService.listFeeDetailsForOperator(token, filter));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(1001, ex.getMessage());
        }
    }
}
