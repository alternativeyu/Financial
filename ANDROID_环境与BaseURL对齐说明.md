# Android 端：环境与 Base URL 对齐说明

本文档与仓库内 **`backend/src/main/resources/application.yml`**、**`gateway/src/main/resources/application.yml`**、**`frontend`** 的约定保持一致，便于 Android 只改一处即可对齐联调/部署。

> 专项接口细节仍以各文档为准：  
> `ANDROID_行情查看接口对接文档.md`、`ANDROID_委托撤单多资金账户对接文档.md`、`ANDROID_冻结资金与交易接口说明.md`。

---

## 1. 仓库当前默认值（务必对齐）

| 角色 | 默认地址 | 说明 |
|------|------------|------|
| **App 直连后端** | `http://<主机>:28480/api/app` | 后端 `SERVER_PORT` 默认 **28480**，可用环境变量 `SERVER_PORT` 覆盖 |
| **经 API 网关访问** | `http://<主机>:28481/api/app` | 网关 `GATEWAY_SERVER_PORT` 默认 **28481**；网关会把 `/api/**` 转发到后端（默认 `GATEWAY_BACKEND_URI=http://127.0.0.1:28480`） |

- **路径前缀**：所有 App 接口均在 **`/api/app`** 下（例如登录注册、开户、行情、交易等）。
- **柜台操作员接口**（一般不给 App 用）：`/api/operator`，Android 通常忽略。

修改后端或网关端口后，**Android 的 Base URL 必须同步修改**，否则会出现连接失败、超时或 `Network Error` 类现象。

---

## 2. Android 工程里建议怎么配（任选一种，团队统一即可）

### 方案 A：`build.gradle` / `build.gradle.kts` + `BuildConfig`（推荐）

在 **`app`** 模块中定义 `buildConfigField`（或 `buildConfigField` + `productFlavors` 区分 `dev` / `prod`）：

**Kotlin DSL 示例（`app/build.gradle.kts`）**

```kotlin
android {
    defaultConfig {
        // 开发机本机直连后端（模拟器访问电脑上的 127.0.0.1 见下文「模拟器」）
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:28480/api/app\"")
    }
    buildTypes {
        release {
            // 生产环境改为实际域名 + HTTPS，例如：
            // buildConfigField("String", "API_BASE_URL", "\"https://api.example.com/api/app\"")
        }
    }
    buildFeatures { buildConfig = true }
}
```

**Groovy 示例（`app/build.gradle`）**

```groovy
android {
    defaultConfig {
        buildConfigField "String", "API_BASE_URL", "\"http://10.0.2.2:28480/api/app\""
    }
    buildFeatures { buildConfig true }
}
```

Retrofit / OkHttp 初始化时使用：

```kotlin
Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL) // 注意：Retrofit 要求 baseUrl 以 / 结尾时可按库要求处理
```

若 `API_BASE_URL` 已含末尾 `/`，与 Retrofit 2 的 `@GET("market/quotes")` 等路径拼接规则需符合 [Retrofit baseUrl 规范](https://square.github.io/retrofit/)（通常 `baseUrl` 以 `/` 结尾，接口路径不以 `/` 开头）。

### 方案 B：根目录 `local.properties`（不入库）

```properties
# 仅本机有效，勿提交到 Git
fts.api.base=http://192.168.1.100:28480/api/app
```

在 `app/build.gradle.kts` 中 `readProperties` 注入 `BuildConfig` 或 `resValue`。

### 方案 C：常量类（小项目）

集中维护例如 `app/.../network/ApiConstants.kt`：

```kotlin
object ApiConstants {
    /** 与后端 application.yml 中 SERVER_PORT 一致；走网关时改为 28481 且路径仍为 /api/app */
    const val BASE_URL = "http://10.0.2.2:28480/api/app/"
}
```

改端口时**只改这一处**并全工程引用。

---

## 3. 模拟器 vs 真机（常见踩坑）

| 场景 | 访问开发机上的后端 | 说明 |
|------|---------------------|------|
| **Android 模拟器** | `http://10.0.2.2:28480` | `10.0.2.2` 为模拟器到宿主机的特殊别名，**不要用 `127.0.0.1`**（那会指向模拟器自己） |
| **真机 USB / 同一 WiFi** | `http://<电脑局域网IP>:28480` | 在电脑执行 `ipconfig`，取 IPv4；手机与电脑需同网段且防火墙放行端口 |
| **本机浏览器 / 前端** | `http://127.0.0.1:28480` | 与 Android 真机/模拟器地址不同属正常 |

走网关时，把上表端口改为 **28481**，路径仍为 **`/api/app`**。

---

## 4. 何时用网关 Base URL

- **直连后端**：`http://<host>:28480/api/app` —— 联调最简单，与当前各 `ANDROID_*.md` 示例一致。
- **经网关**：`http://<host>:28481/api/app` —— 与前端生产或统一入口对齐；需保证网关已启动且 `GATEWAY_BACKEND_URI` 指向可达的后端。

Android 端**无需**单独配置 Redis、RabbitMQ；仅 HTTP(S) Base URL 与业务 Header（如登录态）即可。

---

## 5. 修改清单（发布前自检）

- [ ] `API_BASE_URL`（或等价配置）主机 + 端口与 **后端 `SERVER_PORT` / 网关 `GATEWAY_SERVER_PORT`** 一致  
- [ ] 路径包含 **`/api/app`**（注意不要写成 `/api` 漏掉 `app`）  
- [ ] 模拟器用 **`10.0.2.2`**，真机用 **局域网 IP**  
- [ ] Release 包改为 **HTTPS + 正式域名**（若上生产）  
- [ ] 若后端启用了 **自签名 HTTPS**，OkHttp 需配置证书或调试信任策略（仅 debug）

---

## 6. 快速验证（可选）

在电脑终端（后端已启动、端口 28480）：

```bash
curl -s "http://127.0.0.1:28480/api/app/market/quotes?page=1&pageSize=5"
```

模拟器内浏览器若可访问 `http://10.0.2.2:28480/api/app/...` 说明网络路径正确。

---

## 7. 与 Web 前端（Vite）的对应关系（供对照）

| 项目 | 开发时默认行为 |
|------|----------------|
| Web | `npm run dev` 默认通过 **Vite 代理** 访问 `VITE_PROXY_TARGET`（默认 `http://127.0.0.1:28480`），见 `frontend/vite.config.js` |
| Android | 无 Vite 代理，需使用 **10.0.2.2 / 局域网 IP + 端口** 直连或使用网关地址 |

两端**业务路径**均为 `/api/app/...` 与 `/api/operator/...`（App 只用前者），对齐即可。

---

**文档版本**：与仓库端口 **28480 / 28481** 及 `application.yml` 中 `${SERVER_PORT}`、`${GATEWAY_SERVER_PORT}` 约定同步；若仓库默认端口再变更，请同步更新本节表格与各 `ANDROID_*.md` 中的示例 URL。
