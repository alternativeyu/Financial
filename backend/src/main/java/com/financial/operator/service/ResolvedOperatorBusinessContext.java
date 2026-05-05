package com.financial.operator.service;

/**
 * 柜台查询条件解析后的主键边界，用于统一拼装参数化 WHERE 子句。
 */
public record ResolvedOperatorBusinessContext(
        Long customerId,
        Long fundAccountId,
        Long orderId,
        Long tradeDbId
) {
    public static ResolvedOperatorBusinessContext empty() {
        return new ResolvedOperatorBusinessContext(null, null, null, null);
    }
}
