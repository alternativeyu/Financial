package com.financial.operator.service;

import java.time.LocalDate;

/**
 * 柜台端报表查询条件：由 {@link UnifiedBusinessQueryService} 统一解析为参数化 SQL，禁止客户端拼接查询语句。
 */
public record OperatorReportFilter(
        String customerCode,
        String idNo,
        String fundAccountNo,
        String shareholderAccountNo,
        String orderNo,
        String tradeNo,
        String sourceType,
        String orderStatus,
        String orderStatusGroup,
        LocalDate orderDateFrom,
        LocalDate orderDateTo,
        LocalDate tradeDateFrom,
        LocalDate tradeDateTo,
        LocalDate journalDateFrom,
        LocalDate journalDateTo,
        LocalDate riskDateFrom,
        LocalDate riskDateTo,
        String riskEventStatus,
        String opLogModule,
        String opLogBizType,
        String opLogBizNo,
        Long opLogOperatorId,
        LocalDate opLogDateFrom,
        LocalDate opLogDateTo,
        int page,
        int pageSize
) {
    public OperatorReportFilter {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        pageSize = Math.min(pageSize, 200);
    }

    public static OperatorReportFilter orders(
            String customerCode,
            String idNo,
            String fundAccountNo,
            String shareholderAccountNo,
            String orderNo,
            String sourceType,
            String orderStatus,
            String orderStatusGroup,
            LocalDate orderDateFrom,
            LocalDate orderDateTo,
            int page,
            int pageSize
    ) {
        return new OperatorReportFilter(
                customerCode, idNo, fundAccountNo, shareholderAccountNo, orderNo, null,
                sourceType, orderStatus, orderStatusGroup,
                orderDateFrom, orderDateTo,
                null, null,
                null, null,
                null, null,
                null,
                null, null, null, null, null, null,
                page, pageSize
        );
    }

    public static OperatorReportFilter trades(
            String customerCode,
            String idNo,
            String fundAccountNo,
            String tradeNo,
            LocalDate tradeDateFrom,
            LocalDate tradeDateTo,
            int page,
            int pageSize
    ) {
        return new OperatorReportFilter(
                customerCode, idNo, fundAccountNo, null, null, tradeNo,
                null, null, null,
                null, null,
                tradeDateFrom, tradeDateTo,
                null, null,
                null, null,
                null,
                null, null, null, null, null, null,
                page, pageSize
        );
    }

    public static OperatorReportFilter journal(
            String customerCode,
            String idNo,
            String fundAccountNo,
            LocalDate journalDateFrom,
            LocalDate journalDateTo,
            int page,
            int pageSize
    ) {
        return new OperatorReportFilter(
                customerCode, idNo, fundAccountNo, null, null, null,
                null, null, null,
                null, null,
                null, null,
                journalDateFrom, journalDateTo,
                null, null,
                null,
                null, null, null, null, null, null,
                page, pageSize
        );
    }

    public static OperatorReportFilter risk(
            String customerCode,
            String idNo,
            String fundAccountNo,
            String orderNo,
            String riskEventStatus,
            LocalDate riskDateFrom,
            LocalDate riskDateTo,
            int page,
            int pageSize
    ) {
        return new OperatorReportFilter(
                customerCode, idNo, fundAccountNo, null, orderNo, null,
                null, null, null,
                null, null,
                null, null,
                null, null,
                riskDateFrom, riskDateTo,
                riskEventStatus,
                null, null, null, null, null, null,
                page, pageSize
        );
    }

    public static OperatorReportFilter operationLog(
            String opLogModule,
            String opLogBizType,
            String opLogBizNo,
            Long opLogOperatorId,
            LocalDate opLogDateFrom,
            LocalDate opLogDateTo,
            int page,
            int pageSize
    ) {
        return new OperatorReportFilter(
                null, null, null, null, null, null,
                null, null, null,
                null, null,
                null, null,
                null, null,
                null, null,
                null,
                opLogModule, opLogBizType, opLogBizNo, opLogOperatorId, opLogDateFrom, opLogDateTo,
                page, pageSize
        );
    }
}
