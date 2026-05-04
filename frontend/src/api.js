import axios from "axios";

/**
 * 开发环境默认走 Vite 代理（同源 /api → VITE_PROXY_TARGET，默认 28480），避免写死端口与跨域问题。
 * 需要直连后端/网关时：.env.local 中设置 VITE_DEV_PROXY=0 与 VITE_API_HOST=...
 */
const devUseProxy = import.meta.env.DEV && import.meta.env.VITE_DEV_PROXY !== "0";
const API_HOST = devUseProxy ? "" : import.meta.env.VITE_API_HOST ?? "http://127.0.0.1:28480";

const http = axios.create({
  baseURL: `${API_HOST}/api/operator`,
  timeout: 10000
});

const appHttp = axios.create({
  baseURL: `${API_HOST}/api/app`,
  timeout: 10000
});

function attachNetworkHint(client) {
  client.interceptors.response.use(
    (r) => r,
    (err) => {
      if (!err.response) {
        const base = client.defaults.baseURL || "";
        err.hint =
          "无法连接后端。请确认：① operator-backend 已启动；② 端口为 28480（或与本机 SERVER_PORT 一致）；③ 开发环境可删除 .env.local 中的 VITE_DEV_PROXY=0 以使用 Vite 代理。" +
          (base ? ` 当前请求基址：${base}` : "");
      }
      return Promise.reject(err);
    }
  );
}

attachNetworkHint(http);
attachNetworkHint(appHttp);

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
