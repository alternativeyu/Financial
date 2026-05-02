import axios from "axios";

const http = axios.create({
  baseURL: "http://127.0.0.1:8080/api/operator",
  timeout: 10000
});

const appHttp = axios.create({
  baseURL: "http://127.0.0.1:8080/api/app",
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("op_token");
  if (token) {
    config.headers["X-Operator-Token"] = token;
  }
  return config;
});

export function loginOperator(payload) {
  return http.post("/auth/login", payload).then((r) => r.data);
}

export function listOpeningApplications(status) {
  return http.get("/opening/applications", { params: { status } }).then((r) => r.data);
}

export function claimOpeningApplication(id) {
  return http.post(`/opening/applications/${id}/claim`).then((r) => r.data);
}

export function importAllAppOpeningApplications() {
  return http.post("/opening/applications/import-app").then((r) => r.data);
}

export function importOneAppOpeningApplication(id) {
  return http.post(`/opening/applications/${id}/import-app`).then((r) => r.data);
}

export function approveOpeningApplication(id, comment) {
  return http.post(`/opening/applications/${id}/approve`, { comment }).then((r) => r.data);
}

export function rejectOpeningApplication(id, comment) {
  return http.post(`/opening/applications/${id}/reject`, { comment }).then((r) => r.data);
}

export function listAuditLogs(id) {
  return http.get(`/opening/applications/${id}/audit-logs`).then((r) => r.data);
}

export function getOpeningResult(id) {
  return http.get(`/opening/applications/${id}/result`).then((r) => r.data);
}

export function listMarketQuotes(params) {
  return appHttp.get("/market/quotes", { params }).then((r) => r.data?.data ?? {});
}

/** App 端委托列表（含 canCancel），GET /api/app/trade/orders */
export function listAppTradeOrders(params) {
  return appHttp.get("/trade/orders", { params }).then((r) => r.data?.data ?? {});
}

export function getMarketQuoteDetail(market, securityCode) {
  return appHttp.get(`/market/quotes/${market}/${securityCode}`).then((r) => r.data?.data ?? null);
}

export function listOperatorTradeOrders(params) {
  return http.get("/trade/orders", { params }).then((r) => r.data?.data ?? {});
}

export function cancelOperatorTradeOrder(orderNo, payload) {
  return http.post(`/trade/orders/${orderNo}/cancel`, payload).then((r) => r.data?.data ?? {});
}

/** 模拟成交：解冻/结算与 trd_trade 落库，幂等 requestSeqNo */
export function simulateOperatorTradeMatch(orderNo, payload) {
  return http.post(`/trade/orders/${orderNo}/simulate-match`, payload).then((r) => r.data?.data ?? {});
}

export function submitOperatorTradeOrder(payload) {
  return http.post("/trade/orders", payload).then((r) => r.data?.data ?? {});
}

export function listOpenedCustomers(keyword) {
  return http.get("/opening/customers", { params: { keyword } }).then((r) => r.data);
}

export function getCustomerAssets(customerId) {
  return http.get(`/opening/customers/${customerId}/assets`).then((r) => r.data);
}

export function listCustomerOrders(customerId) {
  return http.get(`/opening/customers/${customerId}/orders`).then((r) => r.data);
}

export function listCustomerTrades(customerId) {
  return http.get(`/opening/customers/${customerId}/trades`).then((r) => r.data);
}
