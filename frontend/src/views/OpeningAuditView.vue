<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-badge">FT</div>
        <span class="logo-text">FTS Operator</span>
      </div>
      <nav class="sidebar-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="s-item"
          :class="{ active: item.key === activeNav }"
          @click="onNavClick(item)"
        >
          <svg class="s-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-html="navIcon(item.key)"></svg>
          {{ item.label }}
        </button>
      </nav>
      <div class="sidebar-foot">
        <div class="s-avatar">{{ (operatorName || 'O')[0] }}</div>
        <span class="s-uname">{{ operatorName }}</span>
        <button class="s-logout" @click="logout">退出</button>
      </div>
    </aside>
    <div class="main-area">
      <header class="page-hd">
        <div class="page-hd-title">{{ currentNavLabel }}</div>
        <div class="page-hd-right">
          <span class="hd-op">{{ operatorName }}</span>
          <button class="hd-logout" @click="logout">安全退出</button>
        </div>
      </header>
      <div class="page-body">

    <div v-if="activeNav === 'opening-audit'" class="metrics-grid">
      <div class="metric-card">
        <div class="metric-label">待处理申请</div>
        <div class="metric-value">{{ countByStatus("SUBMITTED") }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">审核中申请</div>
        <div class="metric-value">{{ countByStatus("IN_REVIEW") }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">今日已开户</div>
        <div class="metric-value">{{ countByStatus("OPENED") }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">总申请数</div>
        <div class="metric-value">{{ applications.length }}</div>
      </div>
    </div>

    <div v-if="activeNav === 'opening-audit'" class="card">
      <div class="row">
        <label class="label">状态筛选</label>
        <select v-model="statusFilter">
          <option value="">全部</option>
          <option value="SUBMITTED">SUBMITTED</option>
          <option value="IN_REVIEW">IN_REVIEW</option>
          <option value="APPROVED">APPROVED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="OPENED">OPENED</option>
        </select>
        <button @click="loadApplications">刷新列表</button>
        <button class="secondary" @click="importAllAppRecords">一键导入App申请</button>
      </div>
    </div>

    <div v-if="activeNav === 'opening-audit'" class="card list-switch-card">
      <div class="list-switch">
        <button class="list-switch-item" :class="{ active: activeListTab === 'submitted' }" @click="activeListTab = 'submitted'">
          待处理列表（{{ submittedApplications.length }}）
        </button>
        <button class="list-switch-item" :class="{ active: activeListTab === 'pending' }" @click="activeListTab = 'pending'">
          待通过列表（{{ pendingApplications.length }}）
        </button>
        <button class="list-switch-item" :class="{ active: activeListTab === 'reviewed' }" @click="activeListTab = 'reviewed'">
          已审核列表（{{ reviewedApplications.length }}）
        </button>
      </div>
    </div>

    <div v-if="activeNav === 'opening-audit'" class="card">
      <h3 class="title">开户申请队列</h3>
      <table class="data-table opening-queue-table">
        <colgroup>
          <col style="width: 11%" />
          <col style="width: 8%" />
          <col style="width: 12%" />
          <col style="width: 8%" />
          <col style="width: 6%" />
          <col style="width: 9%" />
          <col style="width: 7%" />
          <col style="width: 19%" />
          <col style="width: 20%" />
        </colgroup>
        <thead>
          <tr>
            <th>申请单号</th>
            <th>客户姓名</th>
            <th>证件号</th>
            <th>账户类别</th>
            <th>开户市场</th>
            <th>状态</th>
            <th>审核人</th>
            <th>审核意见</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="item in displayedApplications" :key="item.id">
            <tr>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.applyNo)">{{ item.applyNo }}</span>
              </td>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.customerName)">{{ item.customerName }}</span>
              </td>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.idNo)">{{ item.idNo }}</span>
              </td>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.acctClsCode)">{{ item.acctClsCode }}</span>
              </td>
              <td>{{ marketLabel(item) }}</td>
              <td>
                <span class="status" :class="statusClass(item.status)" :title="item.status">{{ item.status }}</span>
              </td>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.reviewerId)">{{ item.reviewerId || "-" }}</span>
              </td>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.reviewComment)">{{ item.reviewComment || "-" }}</span>
              </td>
              <td class="td-actions">
                <div class="action-group">
                  <button v-if="item.status === 'SUBMITTED' && isAppApiSource(item)" @click="importOneAppRecord(item.id)">导入</button>
                  <button v-if="item.status === 'SUBMITTED' && !isAppApiSource(item)" @click="claim(item.id)">接单</button>
                  <button v-if="item.status === 'IN_REVIEW' && !isAppApiSource(item)" @click="approve(item.id)">通过</button>
                  <button v-if="item.status === 'IN_REVIEW' && !isAppApiSource(item)" class="danger" @click="reject(item.id)">拒绝</button>
                  <button v-if="item.status === 'OPENED' && !isAppApiSource(item)" @click="openOpeningResult(item.id)">
                    结果
                  </button>
                  <button class="secondary" @click="loadLogs(item.id)">日志</button>
                  <span v-if="isAppApiSource(item)" class="source-tag">APP来源</span>
                </div>
              </td>
            </tr>
          </template>
          <tr v-if="!displayedApplications.length">
            <td colspan="9" class="empty-row">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeNav === 'opening-audit' && logs.length" class="card">
      <h3 class="title">审核轨迹日志</h3>
      <table class="data-table audit-log-table">
        <colgroup>
          <col style="width: 12%" />
          <col style="width: 14%" />
          <col style="width: 10%" />
          <col style="width: 44%" />
          <col style="width: 20%" />
        </colgroup>
        <thead>
          <tr>
            <th>审核编号</th>
            <th>状态变化</th>
            <th>动作</th>
            <th>意见</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>
              <span class="cell-ellipsis" :title="textOrDash(log.auditNo)">{{ log.auditNo }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="`${log.previousStatus} -> ${log.currentStatus}`">
                {{ log.previousStatus }} -> {{ log.currentStatus }}
              </span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(log.action)">{{ log.action }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(log.comment)">{{ log.comment }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(log.createdAt)">{{ log.createdAt }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeNav === 'opening-audit' && message" class="card message">{{ message }}</div>

    <div v-if="activeNav === 'opening-audit' && openResultApplyId && openingResultMap[openResultApplyId]" class="result-modal-mask" @click.self="closeOpeningResult">
      <div class="result-modal">
        <div class="result-modal-head">
          <h3 class="title">开户结果与初始化资产</h3>
          <button class="secondary" @click="closeOpeningResult">关闭</button>
        </div>
        <div class="metrics-grid opening-result-grid">
          <div class="metric-card">
            <div class="metric-label">客户编码</div>
            <div class="metric-value opening-value">{{ openingResultMap[openResultApplyId].customer?.customerCode || "-" }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">资金账户</div>
            <div class="metric-value opening-value">{{ openingResultMap[openResultApplyId].fundAccount?.fundAccountNo || "-" }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">初始化资金</div>
            <div class="metric-value opening-value">{{ formatMoney(openingResultMap[openResultApplyId].initCashBalance) }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">初始化持仓条数</div>
            <div class="metric-value opening-value">{{ openingResultMap[openResultApplyId].initPositionCount ?? 0 }}</div>
          </div>
        </div>
        <table>
          <thead>
            <tr>
              <th>市场</th>
              <th>股东账户</th>
              <th>状态</th>
              <th>开通时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="acct in openingResultMap[openResultApplyId].shareholderAccounts || []" :key="acct.id">
              <td>{{ acct.marketCode === "1" ? "沪A" : "深A" }}</td>
              <td>{{ acct.shareholderAccountNo }}</td>
              <td>{{ acct.status }}</td>
              <td>{{ acct.openedAt || "-" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="activeNav === 'customer-center' && customerViewMode === 'list'" class="card">
      <div class="row">
        <label class="label">客户检索</label>
        <input v-model="customerKeyword" placeholder="客户号/姓名/证件号" class="field-flex" />
        <button @click="loadCustomers">查询客户</button>
      </div>
    </div>

    <div v-if="activeNav === 'customer-center' && customerViewMode === 'list'" class="card">
      <h3 class="title">客户资料</h3>
      <table class="data-table customer-list-table">
        <colgroup>
          <col style="width: 12%" />
          <col style="width: 9%" />
          <col style="width: 14%" />
          <col style="width: 9%" />
          <col style="width: 9%" />
          <col style="width: 8%" />
          <col style="width: 11%" />
          <col style="width: 28%" />
        </colgroup>
        <thead>
          <tr>
            <th>客户号</th>
            <th>姓名</th>
            <th>证件号</th>
            <th>账户类别</th>
            <th>状态</th>
            <th>风险等级</th>
            <th>开户日期</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in customers" :key="item.id">
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.customerCode)">{{ item.customerCode }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.customerName)">{{ item.customerName }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.idNo)">{{ item.idNo }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.acctClsCode)">{{ item.acctClsCode }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.customerStatus)">{{ item.customerStatus }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.riskLevel)">{{ item.riskLevel }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(item.openDate)">{{ item.openDate }}</span>
            </td>
            <td class="td-actions td-actions-split">
              <button type="button" @click="openCustomerDetail(item.id)">查看详情</button>
              <button type="button" class="secondary" @click="openCustomerPositionModal(item.id)">持仓详情</button>
            </td>
          </tr>
          <tr v-if="!customers.length">
            <td colspan="8" class="empty-row">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeNav === 'customer-center' && customerViewMode === 'detail' && selectedCustomerId && customerAssets.customer" class="card">
      <div class="row row-space-between">
        <button class="secondary" type="button" @click="backToCustomerList">返回客户列表</button>
        <button type="button" @click="openCustomerPositionModal(selectedCustomerId)">持仓详情（弹窗）</button>
      </div>
      <h3 class="title">资产持仓</h3>
      <div class="metrics-grid opening-result-grid">
        <div class="metric-card">
          <div class="metric-label">客户编码</div>
          <div class="metric-value opening-value">{{ customerAssets.customer.customerCode }}</div>
        </div>
        <div class="metric-card" v-if="customerAssets.fundAccounts?.length">
          <div class="metric-label">资金账户</div>
          <div class="metric-value opening-value">{{ customerAssets.fundAccounts[0].fundAccountNo }}</div>
        </div>
        <div class="metric-card" v-if="customerAssets.fundAccounts?.length">
          <div class="metric-label">可用资金</div>
          <div class="metric-value opening-value">{{ formatMoney(customerAssets.fundAccounts[0].availableBalance) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">持仓条数</div>
          <div class="metric-value opening-value">{{ customerAssets.positions?.length || 0 }}</div>
        </div>
      </div>
      <table class="data-table customer-position-table">
        <colgroup>
          <col style="width: 6%" />
          <col style="width: 9%" />
          <col style="width: 14%" />
          <col style="width: 7%" />
          <col style="width: 7%" />
          <col style="width: 7%" />
          <col style="width: 10%" />
          <col style="width: 10%" />
          <col style="width: 10%" />
          <col style="width: 10%" />
          <col style="width: 10%" />
        </colgroup>
        <thead>
          <tr>
            <th>市场</th>
            <th>证券代码</th>
            <th>证券名称</th>
            <th>总持仓</th>
            <th>可卖</th>
            <th>冻结</th>
            <th>成本价</th>
            <th>最新价</th>
            <th>行情价</th>
            <th>市值</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in customerAssets.positions || []" :key="p.id">
            <td>{{ p.market_code === "1" ? "沪A" : "深A" }}</td>
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(p.security_code_snapshot)">{{ p.security_code_snapshot }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(p.security_name)">{{ p.security_name || "-" }}</span>
            </td>
            <td>{{ p.total_qty }}</td>
            <td>{{ p.available_qty }}</td>
            <td>{{ p.frozen_qty }}</td>
            <td>{{ p.cost_price != null && p.cost_price !== '' ? formatMoney(p.cost_price) : '-' }}</td>
            <td>{{ p.last_price != null && p.last_price !== '' ? formatMoney(p.last_price) : '-' }}</td>
            <td>{{ p.quote_price != null && p.quote_price !== '' ? formatMoney(p.quote_price) : '-' }}</td>
            <td>{{ p.market_value != null && p.market_value !== '' ? formatMoney(p.market_value) : '-' }}</td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(p.position_status)">{{ p.position_status || "-" }}</span>
            </td>
          </tr>
          <tr v-if="!(customerAssets.positions || []).length">
            <td colspan="11" class="empty-row">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeNav === 'customer-center' && customerViewMode === 'detail' && selectedCustomerId" class="card">
      <h3 class="title">委托记录</h3>
      <table class="data-table customer-order-table">
        <colgroup>
          <col style="width: 13%" />
          <col style="width: 8%" />
          <col style="width: 14%" />
          <col style="width: 8%" />
          <col style="width: 6%" />
          <col style="width: 7%" />
          <col style="width: 8%" />
          <col style="width: 8%" />
          <col style="width: 8%" />
          <col style="width: 9%" />
          <col style="width: 11%" />
        </colgroup>
        <thead>
          <tr>
            <th>委托编号</th>
            <th>来源</th>
            <th>资金账户</th>
            <th>代码</th>
            <th>方向</th>
            <th>价格</th>
            <th>委托量</th>
            <th>已成量</th>
            <th>剩余量</th>
            <th>状态</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in customerOrders" :key="o.order_no">
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(o.order_no)">{{ o.order_no }}</span>
            </td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(o.source_type)">{{ o.source_type }}</span>
            </td>
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(o.fund_account_no)">{{ o.fund_account_no || "-" }}</span>
            </td>
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(o.security_code)">{{ o.security_code }}</span>
            </td>
            <td>{{ o.trade_direction === "B" ? "买入" : "卖出" }}</td>
            <td>{{ o.order_price }}</td>
            <td>{{ o.order_qty }}</td>
            <td>{{ o.filled_qty }}</td>
            <td>{{ o.remain_qty }}</td>
            <td>
              <span class="cell-ellipsis" :title="textOrDash(o.order_status)">{{ o.order_status }}</span>
            </td>
            <td>
              <span class="cell-ellipsis mono" :title="formatQuoteTime(o.created_at)">{{ formatQuoteTime(o.created_at) }}</span>
            </td>
          </tr>
          <tr v-if="!customerOrders.length">
            <td colspan="11" class="empty-row">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeNav === 'customer-center' && customerViewMode === 'detail' && selectedCustomerId" class="card">
      <h3 class="title">成交记录</h3>
      <table class="data-table customer-trade-table">
        <colgroup>
          <col style="width: 11%" />
          <col style="width: 11%" />
          <col style="width: 7%" />
          <col style="width: 6%" />
          <col style="width: 7%" />
          <col style="width: 7%" />
          <col style="width: 9%" />
          <col style="width: 8%" />
          <col style="width: 8%" />
          <col style="width: 9%" />
          <col style="width: 17%" />
        </colgroup>
        <thead>
          <tr>
            <th>成交编号</th>
            <th>关联委托</th>
            <th>代码</th>
            <th>方向</th>
            <th>成交价</th>
            <th>成交量</th>
            <th>成交额</th>
            <th>佣金</th>
            <th>税费</th>
            <th>净结算</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in customerTrades" :key="t.trade_no">
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(t.trade_no)">{{ t.trade_no }}</span>
            </td>
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(t.order_no)">{{ t.order_no }}</span>
            </td>
            <td>
              <span class="cell-ellipsis mono" :title="textOrDash(t.security_code)">{{ t.security_code }}</span>
            </td>
            <td>{{ t.trade_direction === "B" ? "买入" : "卖出" }}</td>
            <td>{{ t.trade_price }}</td>
            <td>{{ t.trade_qty }}</td>
            <td>{{ t.trade_amount }}</td>
            <td>{{ t.commission_amount }}</td>
            <td>{{ t.tax_amount }}</td>
            <td>{{ t.net_settle_amount }}</td>
            <td>
              <span class="cell-ellipsis mono" :title="formatQuoteTime(t.trade_time)">{{ formatQuoteTime(t.trade_time) }}</span>
            </td>
          </tr>
          <tr v-if="!customerTrades.length">
            <td colspan="11" class="empty-row">暂无记录</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="activeNav === 'customer-center' && message" class="card message">{{ message }}</div>

    <div v-if="activeNav === 'order-center'" class="card order-switch-card">
      <div class="list-switch order-switch">
        <button class="list-switch-item" :class="{ active: orderMode === 'manual' }" @click="switchOrderMode('manual')">
          手动输入
        </button>
        <button class="list-switch-item" :class="{ active: orderMode === 'app' }" @click="switchOrderMode('app')">
          App申请
        </button>
      </div>
    </div>

    <div v-if="activeNav === 'order-center' && orderMode === 'manual'" class="card order-form-card">
      <h3 class="title">新增委托</h3>
      <div class="order-form-grid">
        <div class="order-form-item order-form-item-wide">
          <label class="order-label">客户ID</label>
          <input v-model="orderCustomerId" placeholder="输入客户ID" />
        </div>
        <div class="order-form-item">
          <label class="order-label">&nbsp;</label>
          <button class="secondary order-action-btn" @click="loadOrderFundAccounts">加载资金账户</button>
        </div>
      </div>
      <div class="order-form-grid">
        <div class="order-form-item order-form-item-wide">
          <label class="order-label">资金账户</label>
          <select v-model="orderForm.fundAccountNo">
            <option value="">请选择</option>
            <option v-for="acc in orderFundAccounts" :key="acc.fundAccountNo" :value="acc.fundAccountNo">
              {{ acc.fundAccountNo }}（可用{{ formatMoney(acc.availableBalance) }}）
            </option>
          </select>
        </div>
        <div class="order-form-item">
          <label class="order-label">市场</label>
          <select v-model="orderForm.marketCode">
            <option value="SH">沪A</option>
            <option value="SZ">深A</option>
          </select>
        </div>
        <div class="order-form-item">
          <label class="order-label">方向</label>
          <select v-model="orderForm.tradeDirection">
            <option value="BUY">买入</option>
            <option value="SELL">卖出</option>
          </select>
        </div>
      </div>
      <div class="order-form-grid">
        <div class="order-form-item">
          <label class="order-label">证券代码</label>
          <input v-model="orderForm.securityCode" placeholder="如 600000" />
        </div>
        <div class="order-form-item">
          <label class="order-label">价格</label>
          <input v-model="orderForm.price" placeholder="如 10.25" />
        </div>
        <div class="order-form-item">
          <label class="order-label">数量</label>
          <input v-model="orderForm.quantity" placeholder="如 100" />
        </div>
        <div class="order-form-item">
          <label class="order-label">&nbsp;</label>
          <button class="order-action-btn" @click="submitOrder">提交委托</button>
        </div>
      </div>
    </div>

    <div v-if="activeNav === 'order-center'" class="card order-filter-card">
      <div class="order-toolbar">
        <div class="order-toolbar-cluster">
          <span class="order-toolbar-kicker">当前模式</span>
          <span
            class="order-mode-chip"
            :class="orderMode === 'manual' ? 'is-manual' : 'is-app'"
          >
            {{ orderMode === "manual" ? "手动输入委托" : "App申请委托" }}
          </span>
        </div>
        <div v-show="orderQueueTab === 'active'" class="order-toolbar-cluster order-toolbar-cluster-end">
          <label class="order-toolbar-kicker" for="order-status-filter">委托状态</label>
          <select
            id="order-status-filter"
            v-model="orderStatusFilter"
            class="order-toolbar-select"
            @change="loadOrderList"
          >
            <option value="">全部（进行中内）</option>
            <option value="INIT">待报</option>
            <option value="REPORTED">已报</option>
            <option value="PART_FILLED">部成</option>
            <option value="REJECTED">废单</option>
          </select>
          <button type="button" class="order-refresh-btn" @click="loadOrderList">刷新委托</button>
        </div>
        <div v-show="orderQueueTab === 'completed'" class="order-toolbar-cluster order-toolbar-cluster-end">
          <span class="order-toolbar-hint">含全成及部撤中产生成交的记录（与「已撤单」可能重叠）</span>
          <button type="button" class="order-refresh-btn" @click="loadOrderList">刷新委托</button>
        </div>
        <div v-show="orderQueueTab === 'canceled'" class="order-toolbar-cluster order-toolbar-cluster-end">
          <span class="order-toolbar-hint">已撤单含全撤、部撤</span>
          <button type="button" class="order-refresh-btn" @click="loadOrderList">刷新委托</button>
        </div>
      </div>
      <div class="order-queue-nav" role="tablist">
        <button
          type="button"
          class="order-queue-tab"
          role="tab"
          :aria-selected="orderQueueTab === 'active'"
          :class="{ active: orderQueueTab === 'active' }"
          @click="setOrderQueueTab('active')"
        >
          进行中
        </button>
        <button
          type="button"
          class="order-queue-tab"
          role="tab"
          :aria-selected="orderQueueTab === 'completed'"
          :class="{ active: orderQueueTab === 'completed' }"
          @click="setOrderQueueTab('completed')"
        >
          已完成
        </button>
        <button
          type="button"
          class="order-queue-tab"
          role="tab"
          :aria-selected="orderQueueTab === 'canceled'"
          :class="{ active: orderQueueTab === 'canceled' }"
          @click="setOrderQueueTab('canceled')"
        >
          已撤单
        </button>
      </div>
    </div>

    <div v-if="activeNav === 'order-center'" class="card order-list-card">
      <div class="order-list-head">
        <h3 class="title order-list-title">
          {{ orderMode === "manual" ? "手动委托列表" : "App申请委托列表" }}
          <span class="order-list-sub">{{ orderListSubLabel }}</span>
        </h3>
        <span class="order-list-meta">共 {{ orderRows.length }} 笔</span>
      </div>
      <div class="order-table-wrap">
        <table class="order-list-table">
          <colgroup>
            <col class="col-order-no" />
            <col class="col-order-src" />
            <col class="col-order-cust" />
            <col class="col-order-fund" />
            <col class="col-order-mkt" />
            <col class="col-order-code" />
            <col class="col-order-name" />
            <col class="col-order-dir" />
            <col class="col-order-price" />
            <col class="col-order-qty" />
            <col class="col-order-qty" />
            <col class="col-order-qty" />
            <col class="col-order-st" />
            <col class="col-order-cancel" />
            <col class="col-order-time" />
            <col class="col-order-act" />
          </colgroup>
          <thead>
            <tr>
              <th>委托编号</th>
              <th class="center">来源</th>
              <th class="right">客户ID</th>
              <th>资金账户</th>
              <th class="center">市场</th>
              <th class="center">证券代码</th>
              <th>证券名称</th>
              <th class="center">方向</th>
              <th class="right">价格</th>
              <th class="right">委托量</th>
              <th class="right">已成量</th>
              <th class="right">剩余量</th>
              <th class="center">状态</th>
              <th class="center">可撤</th>
              <th class="center">时间</th>
              <th class="center">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in orderRows" :key="item.orderNo">
              <td class="mono order-no-cell">
                <span class="cell-ellipsis" :title="textOrDash(item.orderNo)">{{ item.orderNo }}</span>
              </td>
              <td class="center">
                <span
                  class="order-source-tag"
                  :class="item.sourceType === 'APP' ? 'is-app' : 'is-desk'"
                >
                  {{ item.sourceType === "APP" ? "安卓" : "柜台" }}
                </span>
              </td>
              <td class="right mono muted-num">
                <span class="cell-ellipsis" :title="textOrDash(item.customerId)">{{ item.customerId }}</span>
              </td>
              <td class="mono fund-cell">
                <span class="cell-ellipsis" :title="textOrDash(item.fundAccountNo)">{{ item.fundAccountNo || "—" }}</span>
              </td>
              <td class="center">{{ item.market === "SH" ? "沪A" : "深A" }}</td>
              <td class="center mono">
                <span class="cell-ellipsis" :title="textOrDash(item.securityCode)">{{ item.securityCode }}</span>
              </td>
              <td>
                <span class="cell-ellipsis" :title="textOrDash(item.securityName)">{{ item.securityName }}</span>
              </td>
              <td class="center">
                <span
                  class="order-dir-tag"
                  :class="isBuyDirection(item) ? 'is-buy' : 'is-sell'"
                  :title="textOrDash(item.tradeDirectionText)"
                >
                  {{ item.tradeDirectionText }}
                </span>
              </td>
              <td class="right mono num-strong">{{ item.orderPrice }}</td>
              <td class="right mono">{{ formatInteger(item.orderQty) }}</td>
              <td class="right mono">{{ formatInteger(item.filledQty) }}</td>
              <td class="right mono">{{ formatInteger(item.remainQty) }}</td>
              <td class="center">
                <span
                  class="order-status-pill"
                  :class="orderStatusClass(item.orderStatus)"
                  :title="textOrDash(item.orderStatus)"
                >
                  {{ item.orderStatus }}
                </span>
              </td>
              <td class="center">
                <span class="order-cancel-pill" :class="canCancelOrder(item) ? 'yes' : 'no'">
                  {{ canCancelOrder(item) ? "是" : "否" }}
                </span>
              </td>
              <td class="center mono time-cell">
                <span class="cell-ellipsis" :title="formatQuoteTime(item.createdAt)">{{ formatQuoteTime(item.createdAt) }}</span>
              </td>
              <td class="center order-actions-cell">
                <button
                  v-if="canSimulateMatch(item)"
                  type="button"
                  class="secondary order-match-btn"
                  @click="simulateMatchForOrder(item)"
                >
                  模拟成交
                </button>
                <button
                  type="button"
                  class="danger order-cancel-btn"
                  :disabled="!canCancelOrder(item)"
                  @click="cancelOrder(item)"
                >
                  撤单
                </button>
              </td>
            </tr>
            <tr v-if="!orderRows.length">
              <td colspan="16" class="empty-row order-empty-hint">
                {{ orderEmptyHint }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="activeNav === 'order-center' && message" class="card message">{{ message }}</div>

    <div v-if="activeNav === 'market-center'" class="card">
      <div class="row">
        <label class="label">市场</label>
        <select v-model="marketFilter">
          <option value="">全部</option>
          <option value="SH">沪A</option>
          <option value="SZ">深A</option>
        </select>
        <input v-model="marketKeyword" placeholder="代码/名称检索" class="field-flex" />
        <button type="button" @click="loadMarketList(true)">刷新行情</button>
      </div>
    </div>

    <div v-if="activeNav === 'market-center'" class="card">
      <div class="market-list-head">
        <h3 class="title">行情列表</h3>
        <span class="order-list-sub">每页 {{ marketQuotesPageSize }} 条</span>
        <span v-if="marketQuotesTotal > 0" class="market-list-meta">共 {{ marketQuotesTotal }} 条</span>
      </div>
      <table class="market-table">
        <colgroup>
          <col class="col-market" />
          <col class="col-code" />
          <col class="col-name" />
          <col class="col-price" />
          <col class="col-change" />
          <col class="col-pct" />
          <col class="col-limit" />
          <col class="col-limit" />
          <col class="col-volume" />
          <col class="col-amount" />
          <col class="col-time" />
        </colgroup>
        <thead>
          <tr>
            <th class="center">市场</th>
            <th class="center">代码</th>
            <th class="left">名称</th>
            <th class="right">最新价</th>
            <th class="right">涨跌额</th>
            <th class="right">涨跌幅(%)</th>
            <th class="right">涨停</th>
            <th class="right">跌停</th>
            <th class="right">成交量</th>
            <th class="right">成交额</th>
            <th class="center">更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in marketQuotes" :key="`${item.marketCode}-${item.securityCode}`">
            <td class="mono center">{{ item.marketName }}</td>
            <td class="mono center">{{ item.securityCode }}</td>
            <td class="left name-cell">
              <span class="cell-ellipsis" :title="textOrDash(item.securityName)">{{ item.securityName }}</span>
            </td>
            <td class="num mono">{{ formatNumber(item.currentPrice, 2) }}</td>
            <td class="num mono" :class="quoteClass(item.changeAmount)">{{ formatSigned(item.changeAmount, 2) }}</td>
            <td class="num mono" :class="quoteClass(item.changeAmount)">{{ formatSigned(item.changePct, 4) }}</td>
            <td class="num mono">{{ formatNumber(item.upperLimitPrice, 3) }}</td>
            <td class="num mono">{{ formatNumber(item.lowerLimitPrice, 3) }}</td>
            <td class="num mono">{{ formatInteger(item.volume) }}</td>
            <td class="num mono">{{ formatInteger(item.amount) }}</td>
            <td class="mono center">{{ formatQuoteTime(item.quoteTime) }}</td>
          </tr>
          <tr v-if="!marketQuotes.length">
            <td colspan="11" class="empty-row">暂无记录</td>
          </tr>
        </tbody>
      </table>
      <div class="market-pagination">
        <span class="market-pagination-meta">
          第 <strong>{{ marketQuotesPage }}</strong> / {{ marketQuotesTotalPages }} 页
        </span>
        <div class="market-pagination-actions">
          <button
            type="button"
            class="secondary"
            :disabled="marketQuotesPage <= 1"
            @click="goMarketQuotePage('prev')"
          >
            上一页
          </button>
          <button
            type="button"
            class="secondary"
            :disabled="marketQuotesPage >= marketQuotesTotalPages"
            @click="goMarketQuotePage('next')"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
    <div v-if="activeNav === 'market-center' && message" class="card message">{{ message }}</div>

    <!-- ── 风控中心 ─────────────────────────────────────────────────────── -->
    <div v-if="activeNav === 'risk-center'" class="card">
      <div class="section-head">
        <h3 class="title">风控事件</h3>
        <button @click="loadRiskEvents" class="secondary">刷新</button>
      </div>
      <div class="filter-row">
        <select v-model="riskFilter.riskLevel">
          <option value="">全部等级</option>
          <option value="HIGH">HIGH 高风险</option>
          <option value="MEDIUM">MEDIUM 中风险</option>
          <option value="LOW">LOW 低风险</option>
        </select>
        <select v-model="riskFilter.eventStatus">
          <option value="">全部状态</option>
          <option value="OPEN">OPEN 待处理</option>
          <option value="HANDLED">HANDLED 已确认</option>
          <option value="IGNORED">IGNORED 已忽略</option>
          <option value="CLOSED">CLOSED 已关闭</option>
        </select>
        <input v-model="riskFilter.customerCode" placeholder="客户代码" style="width:130px" />
        <input v-model="riskFilter.from" type="date" style="width:140px" />
        <span style="padding:0 4px;color:#888">至</span>
        <input v-model="riskFilter.to" type="date" style="width:140px" />
        <button @click="searchRiskEvents">搜索</button>
      </div>

      <div class="metrics-grid" style="margin-bottom:10px">
        <div class="metric-card">
          <div class="metric-label">待处理 OPEN</div>
          <div class="metric-value" style="color:#e74c3c">{{ riskEventStats.open }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">阻断事件</div>
          <div class="metric-value" style="color:#e74c3c">{{ riskEventStats.blocking }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">预警事件</div>
          <div class="metric-value" style="color:#f39c12">{{ riskEventStats.warning }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">已处理</div>
          <div class="metric-value" style="color:#27ae60">{{ riskEventStats.handled }}</div>
        </div>
      </div>

      <table class="data-table">
        <thead>
          <tr>
            <th>事件号</th>
            <th>规则</th>
            <th>客户</th>
            <th>风险等级</th>
            <th>类型</th>
            <th>触发原因</th>
            <th>状态</th>
            <th>时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="ev in riskEvents" :key="ev.event_no">
            <td><span class="cell-ellipsis" :title="ev.event_no">{{ ev.event_no }}</span></td>
            <td>
              <span class="badge badge-gray" :title="ev.rule_name">{{ ev.rule_code }}</span>
              <span class="cell-ellipsis" style="margin-left:4px;font-size:12px;color:#555">{{ ev.rule_name }}</span>
            </td>
            <td>
              <span v-if="ev.customer_code">{{ ev.customer_code }}<br /><span style="font-size:11px;color:#888">{{ ev.customer_name }}</span></span>
              <span v-else>-</span>
            </td>
            <td>
              <span class="badge" :class="riskLevelClass(ev.risk_level)">{{ ev.risk_level }}</span>
            </td>
            <td>
              <span v-if="ev.is_blocking" class="badge badge-red">阻断</span>
              <span v-else class="badge badge-yellow">预警</span>
            </td>
            <td><span class="cell-ellipsis" style="max-width:200px" :title="ev.hit_message">{{ ev.hit_message }}</span></td>
            <td>
              <span class="badge" :class="riskStatusClass(ev.event_status)">{{ ev.event_status }}</span>
            </td>
            <td style="font-size:12px;white-space:nowrap">{{ formatDatetime(ev.created_at) }}</td>
            <td>
              <template v-if="ev.event_status === 'OPEN'">
                <button class="secondary" style="font-size:12px;padding:2px 8px;margin-right:4px" @click="doHandleEvent(ev.event_no, 'HANDLED')">确认</button>
                <button class="secondary" style="font-size:12px;padding:2px 8px;margin-right:4px" @click="doHandleEvent(ev.event_no, 'IGNORED')">忽略</button>
                <button class="secondary" style="font-size:12px;padding:2px 8px" @click="doHandleEvent(ev.event_no, 'CLOSED')">关闭</button>
              </template>
              <span v-else style="color:#888;font-size:12px">{{ formatDatetime(ev.handled_at) || '-' }}</span>
            </td>
          </tr>
          <tr v-if="!riskEvents.length">
            <td colspan="9" class="empty-row">暂无风控事件</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination-row" v-if="riskTotal > riskPageSize">
        <button :disabled="riskPage <= 1" @click="riskPage--; loadRiskEvents()" class="secondary">上一页</button>
        <span style="padding:0 12px">第 {{ riskPage }} 页 / 共 {{ Math.ceil(riskTotal / riskPageSize) }} 页（{{ riskTotal }} 条）</span>
        <button :disabled="riskPage >= Math.ceil(riskTotal / riskPageSize)" @click="riskPage++; loadRiskEvents()" class="secondary">下一页</button>
      </div>
    </div>
    <div v-if="activeNav === 'risk-center' && riskMessage" class="card message">{{ riskMessage }}</div>

    <!-- ── 报表中心 ────────────────────────────────────────────────────── -->
    <div v-if="activeNav === 'report-center'" class="card">
      <div class="section-head">
        <h3 class="title">报表看板</h3>
        <button @click="loadReportSummary" class="secondary">刷新汇总</button>
      </div>
      <div class="metrics-grid" style="margin-bottom:14px">
        <div class="metric-card">
          <div class="metric-label">总委托数</div>
          <div class="metric-value">{{ reportSummary.totalOrders ?? '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">今日委托</div>
          <div class="metric-value">{{ reportSummary.todayOrders ?? '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">撤单数</div>
          <div class="metric-value">{{ reportSummary.canceledOrders ?? '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">总成交笔数</div>
          <div class="metric-value">{{ reportSummary.totalTrades ?? '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">今日成交额</div>
          <div class="metric-value" style="font-size:14px">{{ reportSummary.todayTradeAmount != null ? formatMoney(reportSummary.todayTradeAmount) : '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">总成交额</div>
          <div class="metric-value" style="font-size:13px">{{ reportSummary.totalTradeAmount != null ? formatMoney(reportSummary.totalTradeAmount) : '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">待处理风控</div>
          <div class="metric-value" style="color:#e74c3c">{{ reportSummary.openRiskEvents ?? '-' }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">活跃客户数</div>
          <div class="metric-value">{{ reportSummary.activeCustomers ?? '-' }}</div>
        </div>
      </div>

      <div class="list-switch" style="margin-bottom:12px">
        <button class="list-switch-item" :class="{ active: reportTab === 'orders' }" @click="switchReportTab('orders')">委托明细</button>
        <button class="list-switch-item" :class="{ active: reportTab === 'trades' }" @click="switchReportTab('trades')">成交明细</button>
        <button class="list-switch-item" :class="{ active: reportTab === 'asset-journal' }" @click="switchReportTab('asset-journal')">资产流水</button>
        <button class="list-switch-item" :class="{ active: reportTab === 'operation-logs' }" @click="switchReportTab('operation-logs')">操作日志</button>
        <button class="list-switch-item" :class="{ active: reportTab === 'fee-details' }" @click="switchReportTab('fee-details')">费用明细</button>
      </div>

      <!-- 委托明细 -->
      <template v-if="reportTab === 'orders'">
        <div class="filter-row">
          <input v-model="reportOrderFilter.customerCode" placeholder="客户代码" style="width:120px" />
          <input v-model="reportOrderFilter.orderNo" placeholder="委托编号" style="width:160px" />
          <input v-model="reportOrderFilter.orderDateFrom" type="date" style="width:140px" />
          <span style="padding:0 4px;color:#888">至</span>
          <input v-model="reportOrderFilter.orderDateTo" type="date" style="width:140px" />
          <select v-model="reportOrderFilter.orderStatus" style="width:100px">
            <option value="">全部状态</option>
            <option value="INIT">待报</option>
            <option value="REPORTED">已报</option>
            <option value="PART_FILLED">部成</option>
            <option value="FILLED">全成</option>
            <option value="CANCELED">已撤</option>
            <option value="REJECTED">废单</option>
          </select>
          <button @click="searchReportOrders">查询</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>委托编号</th><th>客户代码</th><th>资金账户</th><th>市场</th><th>证券代码</th>
              <th>方向</th><th>委托价</th><th>委托量</th><th>已成量</th><th>状态</th><th>来源</th><th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in reportOrderList" :key="row.orderNo || row.id">
              <td><span class="cell-ellipsis mono" :title="row.orderNo">{{ row.orderNo }}</span></td>
              <td><span class="cell-ellipsis">{{ row.customerCode || '-' }}</span></td>
              <td><span class="cell-ellipsis mono">{{ row.fundAccountNo || '-' }}</span></td>
              <td>{{ row.market === 'SH' ? '沪A' : row.market === 'SZ' ? '深A' : (row.marketCode === '1' ? '沪A' : row.marketCode === '0' ? '深A' : '-') }}</td>
              <td class="mono">{{ row.securityCode }}</td>
              <td>{{ row.tradeDirection === 'B' ? '买入' : row.tradeDirection === 'S' ? '卖出' : '-' }}</td>
              <td class="mono">{{ row.orderPrice }}</td>
              <td>{{ formatInteger(row.orderQty) }}</td>
              <td>{{ formatInteger(row.filledQty) }}</td>
              <td>{{ row.orderStatus }}</td>
              <td>{{ row.sourceType }}</td>
              <td style="white-space:nowrap;font-size:12px">{{ formatQuoteTime(row.createdAt) }}</td>
            </tr>
            <tr v-if="!reportOrderList.length"><td colspan="12" class="empty-row">暂无委托</td></tr>
          </tbody>
        </table>
        <div class="pagination-row" v-if="reportOrderTotal > reportOrderPageSize">
          <button :disabled="reportOrderPage <= 1" @click="reportOrderPage--; loadReportOrders()" class="secondary">上一页</button>
          <span style="padding:0 12px">第 {{ reportOrderPage }} 页 / 共 {{ Math.ceil(reportOrderTotal/reportOrderPageSize) }} 页（{{ reportOrderTotal }} 条）</span>
          <button :disabled="reportOrderPage >= Math.ceil(reportOrderTotal/reportOrderPageSize)" @click="reportOrderPage++; loadReportOrders()" class="secondary">下一页</button>
        </div>
      </template>

      <!-- 成交明细 -->
      <template v-if="reportTab === 'trades'">
        <div class="filter-row">
          <input v-model="reportTradeFilter.customerCode" placeholder="客户代码" style="width:120px" />
          <input v-model="reportTradeFilter.tradeNo" placeholder="成交编号" style="width:160px" />
          <input v-model="reportTradeFilter.tradeDateFrom" type="date" style="width:140px" />
          <span style="padding:0 4px;color:#888">至</span>
          <input v-model="reportTradeFilter.tradeDateTo" type="date" style="width:140px" />
          <button @click="searchReportTrades">查询</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>成交编号</th><th>关联委托</th><th>客户代码</th><th>资金账户</th><th>证券代码</th>
              <th>方向</th><th>成交价</th><th>成交量</th><th>成交额</th><th>佣金</th><th>净结算</th><th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in reportTradeList" :key="row.trade_no">
              <td><span class="cell-ellipsis mono" :title="row.trade_no">{{ row.trade_no }}</span></td>
              <td><span class="cell-ellipsis mono" :title="row.order_no">{{ row.order_no }}</span></td>
              <td><span class="cell-ellipsis">{{ row.customer_code || '-' }}</span></td>
              <td><span class="cell-ellipsis mono">{{ row.fund_account_no || '-' }}</span></td>
              <td class="mono">{{ row.security_code }}</td>
              <td>{{ row.trade_direction === 'B' ? '买入' : '卖出' }}</td>
              <td class="mono">{{ row.trade_price }}</td>
              <td>{{ formatInteger(row.trade_qty) }}</td>
              <td class="mono">{{ row.trade_amount }}</td>
              <td class="mono">{{ row.commission_amount }}</td>
              <td class="mono">{{ row.net_settle_amount }}</td>
              <td style="white-space:nowrap;font-size:12px">{{ formatQuoteTime(row.trade_time) }}</td>
            </tr>
            <tr v-if="!reportTradeList.length"><td colspan="12" class="empty-row">暂无记录</td></tr>
          </tbody>
        </table>
        <div class="pagination-row" v-if="reportTradeTotal > reportTradePageSize">
          <button :disabled="reportTradePage <= 1" @click="reportTradePage--; loadReportTrades()" class="secondary">上一页</button>
          <span style="padding:0 12px">第 {{ reportTradePage }} 页 / 共 {{ Math.ceil(reportTradeTotal/reportTradePageSize) }} 页（{{ reportTradeTotal }} 条）</span>
          <button :disabled="reportTradePage >= Math.ceil(reportTradeTotal/reportTradePageSize)" @click="reportTradePage++; loadReportTrades()" class="secondary">下一页</button>
        </div>
      </template>

      <!-- 资产流水 -->
      <template v-if="reportTab === 'asset-journal'">
        <div class="filter-row">
          <input v-model="reportJournalFilter.customerCode" placeholder="客户代码" style="width:120px" />
          <input v-model="reportJournalFilter.idNo" placeholder="证件号" style="width:160px" />
          <input v-model="reportJournalFilter.fundAccountNo" placeholder="资金账户" style="width:150px" />
          <input v-model="reportJournalFilter.orderNo" placeholder="委托编号" style="width:150px" />
          <input v-model="reportJournalFilter.tradeNo" placeholder="成交编号" style="width:150px" />
          <input v-model="reportJournalFilter.journalDateFrom" type="date" style="width:140px" />
          <span style="padding:0 4px;color:#888">至</span>
          <input v-model="reportJournalFilter.journalDateTo" type="date" style="width:140px" />
          <button @click="searchReportJournal">查询</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>流水号</th><th>资金账户</th><th>流水类型</th><th>金额</th>
              <th>变动前余额</th><th>变动后余额</th><th>关联委托</th><th>关联成交</th><th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in reportJournalList" :key="row.journal_no">
              <td><span class="cell-ellipsis mono" :title="row.journal_no">{{ row.journal_no }}</span></td>
              <td><span class="cell-ellipsis mono">{{ row.fund_account_no || '-' }}</span></td>
              <td><span class="cell-ellipsis" :title="journalTypeTitle(row)">{{ journalTypeLabel(row) }}</span></td>
              <td class="mono">{{ row.change_amount }}</td>
              <td class="mono">{{ row.before_amount }}</td>
              <td class="mono">{{ row.after_amount }}</td>
              <td><span class="cell-ellipsis mono">{{ journalRefOrderCell(row) }}</span></td>
              <td><span class="cell-ellipsis mono">{{ journalRefTradeCell(row) }}</span></td>
              <td style="white-space:nowrap;font-size:12px">{{ formatQuoteTime(row.occur_time) }}</td>
            </tr>
            <tr v-if="!reportJournalList.length"><td colspan="9" class="empty-row">暂无资产流水</td></tr>
          </tbody>
        </table>
        <div class="pagination-row" v-if="reportJournalTotal > reportJournalPageSize">
          <button :disabled="reportJournalPage <= 1" @click="reportJournalPage--; loadReportJournal()" class="secondary">上一页</button>
          <span style="padding:0 12px">第 {{ reportJournalPage }} 页 / 共 {{ Math.ceil(reportJournalTotal/reportJournalPageSize) }} 页（{{ reportJournalTotal }} 条）</span>
          <button :disabled="reportJournalPage >= Math.ceil(reportJournalTotal/reportJournalPageSize)" @click="reportJournalPage++; loadReportJournal()" class="secondary">下一页</button>
        </div>
      </template>

      <!-- 操作日志 -->
      <template v-if="reportTab === 'operation-logs'">
        <div class="filter-row">
          <input v-model="reportLogFilter.moduleName" placeholder="模块名称" style="width:120px" />
          <input v-model="reportLogFilter.bizNo" placeholder="业务编号" style="width:150px" />
          <input v-model="reportLogFilter.occurredFrom" type="date" style="width:140px" />
          <span style="padding:0 4px;color:#888">至</span>
          <input v-model="reportLogFilter.occurredTo" type="date" style="width:140px" />
          <button @click="searchReportLogs">查询</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>操作人</th><th>模块</th><th>业务类型</th><th>业务编号</th><th>操作内容</th><th>IP地址</th><th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in reportLogList" :key="idx">
              <td>{{ row.operator_name || row.operator_id || '-' }}</td>
              <td>{{ row.module_name }}</td>
              <td>{{ row.biz_type }}</td>
              <td><span class="cell-ellipsis mono" :title="row.biz_no">{{ row.biz_no || '-' }}</span></td>
              <td><span class="cell-ellipsis" style="max-width:200px" :title="row.operation_content">{{ row.operation_content || '-' }}</span></td>
              <td>{{ row.operator_ip || '-' }}</td>
              <td style="white-space:nowrap;font-size:12px">{{ formatQuoteTime(row.occurred_at) }}</td>
            </tr>
            <tr v-if="!reportLogList.length"><td colspan="7" class="empty-row">暂无记录</td></tr>
          </tbody>
        </table>
        <div class="pagination-row" v-if="reportLogTotal > reportLogPageSize">
          <button :disabled="reportLogPage <= 1" @click="reportLogPage--; loadReportLogs()" class="secondary">上一页</button>
          <span style="padding:0 12px">第 {{ reportLogPage }} 页 / 共 {{ Math.ceil(reportLogTotal/reportLogPageSize) }} 页（{{ reportLogTotal }} 条）</span>
          <button :disabled="reportLogPage >= Math.ceil(reportLogTotal/reportLogPageSize)" @click="reportLogPage++; loadReportLogs()" class="secondary">下一页</button>
        </div>
      </template>

      <!-- 费用明细 -->
      <template v-if="reportTab === 'fee-details'">
        <div class="filter-row">
          <input v-model="reportFeeFilter.customerCode" placeholder="客户代码" style="width:120px" />
          <input v-model="reportFeeFilter.idNo" placeholder="证件号" style="width:160px" />
          <input v-model="reportFeeFilter.fundAccountNo" placeholder="资金账户" style="width:150px" />
          <input v-model="reportFeeFilter.orderNo" placeholder="委托编号" style="width:160px" />
          <input v-model="reportFeeFilter.tradeNo" placeholder="成交编号" style="width:160px" />
          <button @click="searchReportFees">查询</button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>成交编号</th><th>委托编号</th><th>费用类型</th><th>费用金额</th><th>计费基数</th><th>费率</th><th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in reportFeeList" :key="idx">
              <td><span class="cell-ellipsis mono" :title="row.trade_no">{{ row.trade_no || '-' }}</span></td>
              <td><span class="cell-ellipsis mono" :title="row.order_no">{{ row.order_no || '-' }}</span></td>
              <td>{{ row.fee_type }}</td>
              <td class="mono">{{ row.fee_amount }}</td>
              <td class="mono">{{ row.calc_base }}</td>
              <td class="mono">{{ row.fee_rate }}</td>
              <td style="white-space:nowrap;font-size:12px">{{ formatQuoteTime(row.created_at) }}</td>
            </tr>
            <tr v-if="!reportFeeList.length"><td colspan="7" class="empty-row">暂无费用明细</td></tr>
          </tbody>
        </table>
      </template>
    </div>
    <div v-if="activeNav === 'report-center' && reportMessage" class="card message">{{ reportMessage }}</div>

    <div
      v-if="customerPositionModalOpen"
      class="result-modal-mask"
      @click.self="closeCustomerPositionModal"
    >
      <div class="result-modal customer-position-modal">
        <div class="result-modal-head">
          <h3 class="title">客户持仓详情</h3>
          <button type="button" class="secondary" @click="closeCustomerPositionModal">关闭</button>
        </div>
        <p class="customer-position-modal-sub">{{ customerPositionModalTitle }}</p>
        <div class="customer-position-modal-scroll">
          <table class="data-table customer-position-modal-table">
            <thead>
              <tr>
                <th>市场</th>
                <th>证券代码</th>
                <th>证券名称</th>
                <th>总持仓</th>
                <th>可卖</th>
                <th>冻结</th>
                <th>成本价</th>
                <th>最新价</th>
                <th>行情价</th>
                <th>市值</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in customerPositionModalRows" :key="p.id">
                <td>{{ p.market_code === '1' ? '沪A' : '深A' }}</td>
                <td>
                  <span class="cell-ellipsis mono" :title="textOrDash(p.security_code_snapshot)">{{
                    p.security_code_snapshot
                  }}</span>
                </td>
                <td>
                  <span class="cell-ellipsis" :title="textOrDash(p.security_name)">{{ p.security_name || '-' }}</span>
                </td>
                <td>{{ p.total_qty }}</td>
                <td>{{ p.available_qty }}</td>
                <td>{{ p.frozen_qty }}</td>
                <td>{{ p.cost_price != null && p.cost_price !== '' ? formatMoney(p.cost_price) : '-' }}</td>
                <td>{{ p.last_price != null && p.last_price !== '' ? formatMoney(p.last_price) : '-' }}</td>
                <td>
                  {{
                    p.quote_price != null && p.quote_price !== ''
                      ? formatMoney(p.quote_price)
                      : '-'
                  }}
                </td>
                <td>{{ p.market_value != null && p.market_value !== '' ? formatMoney(p.market_value) : '-' }}</td>
                <td>
                  <span class="cell-ellipsis" :title="textOrDash(p.position_status)">{{ p.position_status || '-' }}</span>
                </td>
              </tr>
              <tr v-if="!customerPositionModalRows.length">
                <td colspan="11" class="empty-row">暂无持仓</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  approveOpeningApplication,
  cancelOperatorTradeOrder,
  claimOpeningApplication,
  getCustomerAssets,
  getOpeningResult,
  importAllAppOpeningApplications,
  importOneAppOpeningApplication,
  listCustomerOrders,
  listCustomerTrades,
  listOpenedCustomers,
  listOperatorTradeOrders,
  simulateOperatorTradeMatch,
  listMarketQuotes,
  listAuditLogs,
  listOpeningApplications,
  rejectOpeningApplication,
  submitOperatorTradeOrder,
  listRiskEvents,
  handleRiskEvent,
  getReportSummary,
  listReportOrders,
  listReportTrades,
  listReportAssetJournal,
  listReportOperationLogs,
  listReportFeeDetails
} from "../api";

const router = useRouter();
const operatorName = ref(localStorage.getItem("op_name") || "-");
const statusFilter = ref("");
const applications = ref([]);
const logs = ref([]);
const message = ref("");
const openingResultMap = ref({});
const openResultApplyId = ref(null);
const activeListTab = ref("submitted");
const activeNav = ref("opening-audit");

// ── 风控中心状态 ──────────────────────────────────────────────────────────
const riskEvents = ref([]);
const riskTotal = ref(0);
const riskPage = ref(1);
const riskPageSize = ref(20);
const riskMessage = ref("");
const riskFilter = ref({ riskLevel: "", eventStatus: "OPEN", customerCode: "", from: "", to: "" });
const riskEventStats = ref({ open: 0, blocking: 0, warning: 0, handled: 0 });

// ── 报表中心状态 ──────────────────────────────────────────────────────────
const reportSummary = ref({});
const reportMessage = ref("");
const reportTab = ref("orders");
const reportOrderFilter = ref({ customerCode: "", orderNo: "", orderDateFrom: "", orderDateTo: "", orderStatus: "" });
const reportOrderList = ref([]);
const reportOrderTotal = ref(0);
const reportOrderPage = ref(1);
const reportOrderPageSize = ref(20);
const reportTradeFilter = ref({ customerCode: "", tradeNo: "", tradeDateFrom: "", tradeDateTo: "" });
const reportTradeList = ref([]);
const reportTradeTotal = ref(0);
const reportTradePage = ref(1);
const reportTradePageSize = ref(20);
const reportJournalFilter = ref({
  customerCode: "",
  idNo: "",
  fundAccountNo: "",
  orderNo: "",
  tradeNo: "",
  journalDateFrom: "",
  journalDateTo: ""
});
const reportJournalList = ref([]);
const reportJournalTotal = ref(0);
const reportJournalPage = ref(1);
const reportJournalPageSize = ref(20);
const reportLogFilter = ref({ moduleName: "", bizNo: "", occurredFrom: "", occurredTo: "" });
const reportLogList = ref([]);
const reportLogTotal = ref(0);
const reportLogPage = ref(1);
const reportLogPageSize = ref(20);
const reportFeeFilter = ref({ customerCode: "", idNo: "", fundAccountNo: "", orderNo: "", tradeNo: "" });
const reportFeeList = ref([]);

const marketFilter = ref("");
const marketKeyword = ref("");
const marketQuotes = ref([]);
/** 行情列表分页（与 GET /api/app/market/quotes 一致） */
const marketQuotesPage = ref(1);
const marketQuotesPageSize = ref(20);
const marketQuotesTotal = ref(0);
const orderSourceFilter = ref("APP");
const orderStatusFilter = ref("");
/** 进行中 | 已完成 | 已撤单 */
const orderQueueTab = ref("active");
const orderRows = ref([]);
const orderMode = ref("app");
const orderCustomerId = ref("");
const orderFundAccounts = ref([]);
const orderForm = ref({
  fundAccountNo: "",
  marketCode: "SH",
  securityCode: "",
  tradeDirection: "BUY",
  price: "",
  quantity: ""
});
const customerKeyword = ref("");
const customers = ref([]);
const selectedCustomerId = ref(null);
const customerViewMode = ref("list");
const customerAssets = ref({ customer: null, fundAccounts: [], shareholderAccounts: [], positions: [] });
const customerOrders = ref([]);
const customerTrades = ref([]);
const customerPositionModalOpen = ref(false);
const customerPositionModalTitle = ref("");
const customerPositionModalRows = ref([]);
const navItems = [
  { key: "opening-audit", label: "开户审核" },
  { key: "customer-center", label: "客户管理" },
  { key: "order-center", label: "委托管理" },
  { key: "risk-center", label: "风控中心" },
  { key: "report-center", label: "报表中心" },
  { key: "market-center", label: "行情中心" }
];

const submittedStatuses = ["SUBMITTED"];
const pendingStatuses = ["IN_REVIEW"];
const reviewedStatuses = ["APPROVED", "REJECTED", "OPENED"];
const submittedApplications = computed(() => applications.value.filter((item) => submittedStatuses.includes(item.status)));
const pendingApplications = computed(() => applications.value.filter((item) => pendingStatuses.includes(item.status)));
const reviewedApplications = computed(() => applications.value.filter((item) => reviewedStatuses.includes(item.status)));
const displayedApplications = computed(() => {
  if (activeListTab.value === "submitted") return submittedApplications.value;
  if (activeListTab.value === "pending") return pendingApplications.value;
  return reviewedApplications.value;
});

const orderListSubLabel = computed(() => {
  if (orderQueueTab.value === "canceled") return "· 已撤单";
  if (orderQueueTab.value === "completed") return "· 已完成";
  return "· 进行中";
});

const orderEmptyHint = computed(() => {
  if (orderQueueTab.value === "canceled") {
    return "暂无已撤单记录，可在「进行中」查看当前有效委托";
  }
  if (orderQueueTab.value === "completed") {
    return "暂无已完成委托，成交后会在本页展示；未成交的仍在「进行中」";
  }
  return "暂无进行中委托，可切换「已完成」「已撤单」或调整状态筛选后刷新";
});

const marketQuotesTotalPages = computed(() => {
  const t = marketQuotesTotal.value;
  const s = marketQuotesPageSize.value;
  if (!s || t <= 0) return 1;
  return Math.max(1, Math.ceil(t / s));
});

const currentNavLabel = computed(() =>
  navItems.find((n) => n.key === activeNav.value)?.label ?? "FTS Operator"
);

function navIcon(key) {
  const m = {
    "opening-audit":   '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/><polyline points="16 11 18 13 22 9"/>',
    "customer-center": '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
    "order-center":    '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>',
    "risk-center":     '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
    "report-center":   '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>',
    "market-center":   '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>',
  };
  return m[key] ?? '<circle cx="12" cy="12" r="10"/>';
}

onMounted(() => {
  if (!localStorage.getItem("op_token")) {
    router.push("/login");
    return;
  }
  loadApplications();
  loadMarketList(true);
  loadOrderList();
  loadCustomers();
});

async function loadApplications() {
  try {
    applications.value = await listOpeningApplications(statusFilter.value || undefined);
    message.value = "";
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询失败";
  }
}

async function claim(id) {
  try {
    await claimOpeningApplication(id);
    await loadApplications();
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "接单失败";
  }
}

async function approve(id) {
  const comment = window.prompt("请输入通过意见", "资料齐全，审核通过");
  if (!comment) return;
  try {
    const result = await approveOpeningApplication(id, comment);
    openingResultMap.value[id] = result;
    openResultApplyId.value = id;
    await loadApplications();
    message.value = `审核通过并已自动开户，初始化资金${result.initCashBalance}，初始化持仓${result.initPositionCount}条`;
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "审核失败";
  }
}

async function reject(id) {
  const comment = window.prompt("请输入拒绝原因", "证件信息不完整");
  if (!comment) return;
  try {
    await rejectOpeningApplication(id, comment);
    await loadApplications();
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "拒绝失败";
  }
}

async function loadLogs(id) {
  try {
    logs.value = await listAuditLogs(id);
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询日志失败";
  }
}

async function importAllAppRecords() {
  try {
    const result = await importAllAppOpeningApplications();
    await loadApplications();
    message.value = `导入完成：共${result.total}条，成功${result.imported}条，跳过${result.skipped}条`;
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "一键导入失败";
  }
}

async function importOneAppRecord(id) {
  try {
    await importOneAppOpeningApplication(id);
    await loadApplications();
    message.value = "申请已导入审核队列，可继续接单审核";
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "单条导入失败";
  }
}

async function loadMarketList(resetPage = false) {
  if (resetPage) {
    marketQuotesPage.value = 1;
  }
  try {
    const data = await listMarketQuotes({
      market: marketFilter.value || undefined,
      keyword: marketKeyword.value || undefined,
      page: marketQuotesPage.value,
      pageSize: marketQuotesPageSize.value
    });
    marketQuotes.value = data?.list || [];
    marketQuotesTotal.value = Number(data?.total ?? 0);
    const totalPages = Math.max(1, Math.ceil(marketQuotesTotal.value / marketQuotesPageSize.value) || 1);
    if (marketQuotesPage.value > totalPages) {
      marketQuotesPage.value = totalPages;
      await loadMarketList(false);
      return;
    }
    if (activeNav.value === "market-center") {
      message.value = "";
    }
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询行情失败";
  }
}

function goMarketQuotePage(dir) {
  if (dir === "prev") {
    if (marketQuotesPage.value <= 1) return;
    marketQuotesPage.value -= 1;
  } else {
    if (marketQuotesPage.value >= marketQuotesTotalPages.value) return;
    marketQuotesPage.value += 1;
  }
  loadMarketList(false);
}

async function loadOrderList() {
  try {
    const params = {
      sourceType: orderSourceFilter.value || undefined,
      page: 1,
      pageSize: 100
    };
    if (orderQueueTab.value === "canceled") {
      params.orderStatusGroup = "CANCELED";
    } else if (orderQueueTab.value === "completed") {
      params.orderStatusGroup = "COMPLETED";
    } else {
      params.orderStatusGroup = "ACTIVE";
      if (orderStatusFilter.value) {
        params.orderStatus = orderStatusFilter.value;
      }
    }
    const data = await listOperatorTradeOrders(params);
    orderRows.value = data?.list || [];
    if (activeNav.value === "order-center") {
      message.value = "";
    }
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询委托列表失败";
  }
}

function setOrderQueueTab(tab) {
  if (orderQueueTab.value === tab) return;
  orderQueueTab.value = tab;
  if (tab === "canceled" || tab === "completed") {
    orderStatusFilter.value = "";
  }
  loadOrderList();
}

async function switchOrderMode(mode) {
  orderMode.value = mode;
  orderSourceFilter.value = mode === "manual" ? "OPERATOR" : "APP";
  orderQueueTab.value = "active";
  orderStatusFilter.value = "";
  await loadOrderList();
}

async function loadOrderFundAccounts() {
  if (!orderCustomerId.value) {
    orderFundAccounts.value = [];
    orderForm.value.fundAccountNo = "";
    return;
  }
  try {
    const detail = await getCustomerAssets(Number(orderCustomerId.value));
    orderFundAccounts.value = detail?.fundAccounts || [];
    if (orderFundAccounts.value.length) {
      const exist = orderFundAccounts.value.some((it) => it.fundAccountNo === orderForm.value.fundAccountNo);
      if (!exist) {
        orderForm.value.fundAccountNo = orderFundAccounts.value[0].fundAccountNo;
      }
    } else {
      orderForm.value.fundAccountNo = "";
    }
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "加载资金账户失败";
  }
}

async function submitOrder() {
  if (!orderCustomerId.value) {
    message.value = "请先填写客户ID";
    return;
  }
  if (!orderForm.value.fundAccountNo) {
    message.value = "请先选择资金账户";
    return;
  }
  const price = Number(orderForm.value.price);
  const quantity = Number(orderForm.value.quantity);
  if (!(price > 0) || !(quantity > 0)) {
    message.value = "价格和数量必须大于0";
    return;
  }
  const requestSeqNo = `OPORDER_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
  try {
    await submitOperatorTradeOrder({
      customerId: Number(orderCustomerId.value),
      fundAccountNo: orderForm.value.fundAccountNo,
      marketCode: orderForm.value.marketCode,
      securityCode: orderForm.value.securityCode.trim(),
      tradeDirection: orderForm.value.tradeDirection,
      price,
      quantity: Math.trunc(quantity),
      requestSeqNo
    });
    await loadOrderList();
    message.value = "委托下单成功";
    orderForm.value.securityCode = "";
    orderForm.value.price = "";
    orderForm.value.quantity = "";
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "委托下单失败";
  }
}

async function loadCustomers() {
  try {
    customers.value = await listOpenedCustomers(customerKeyword.value || undefined);
    if (activeNav.value === "customer-center") {
      message.value = "";
    }
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询客户失败";
  }
}

async function selectCustomer(customerId) {
  selectedCustomerId.value = customerId;
  try {
    customerAssets.value = await getCustomerAssets(customerId);
    customerOrders.value = await listCustomerOrders(customerId);
    customerTrades.value = await listCustomerTrades(customerId);
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询客户详情失败";
  }
}

async function openCustomerDetail(customerId) {
  customerViewMode.value = "detail";
  await selectCustomer(customerId);
}

async function openCustomerPositionModal(customerId) {
  customerPositionModalOpen.value = true;
  customerPositionModalRows.value = [];
  customerPositionModalTitle.value = "加载中…";
  try {
    const data = await getCustomerAssets(customerId);
    const c = data.customer;
    const label = c?.customerName || c?.customerCode || String(customerId);
    const code = c?.customerCode ? `（${c.customerCode}）` : "";
    customerPositionModalTitle.value = `${label}${code}`;
    customerPositionModalRows.value = data.positions || [];
  } catch (e) {
    customerPositionModalOpen.value = false;
    message.value = e?.response?.data?.message || e.message || "加载持仓失败";
  }
}

function closeCustomerPositionModal() {
  customerPositionModalOpen.value = false;
}

function backToCustomerList() {
  customerViewMode.value = "list";
}

async function openOpeningResult(id) {
  try {
    if (!openingResultMap.value[id]) {
      openingResultMap.value[id] = await getOpeningResult(id);
    }
    openResultApplyId.value = id;
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "查询开户结果失败";
  }
}

function closeOpeningResult() {
  openResultApplyId.value = null;
}

function logout() {
  localStorage.removeItem("op_token");
  localStorage.removeItem("op_name");
  router.push("/login");
}

function countByStatus(status) {
  return applications.value.filter((item) => item.status === status).length;
}

function statusClass(status) {
  const map = {
    SUBMITTED: "status-submitted",
    IN_REVIEW: "status-review",
    APPROVED: "status-approved",
    REJECTED: "status-rejected",
    OPENED: "status-opened"
  };
  return map[status] || "";
}

// ── 风控中心函数 ──────────────────────────────────────────────────────────
async function loadRiskEvents() {
  try {
    riskMessage.value = "";
    const f = riskFilter.value;
    const params = { page: riskPage.value, pageSize: riskPageSize.value };
    if (f.riskLevel) params.riskLevel = f.riskLevel;
    if (f.eventStatus) params.riskEventStatus = f.eventStatus;
    if (f.customerCode) params.customerCode = f.customerCode;
    if (f.from) params.riskDateFrom = f.from;
    if (f.to) params.riskDateTo = f.to;
    const data = await listRiskEvents(params);
    riskEvents.value = data.list || [];
    riskTotal.value = Number(data.total ?? 0);
    computeRiskStats();
  } catch (e) {
    riskMessage.value = e?.response?.data?.message || e.message || "查询失败";
  }
}

function computeRiskStats() {
  const list = riskEvents.value;
  riskEventStats.value = {
    open:     list.filter(e => e.event_status === "OPEN").length,
    blocking: list.filter(e => e.is_blocking).length,
    warning:  list.filter(e => !e.is_blocking).length,
    handled:  list.filter(e => e.event_status !== "OPEN").length
  };
}

function searchRiskEvents() {
  riskPage.value = 1;
  loadRiskEvents();
}

async function doHandleEvent(eventNo, action) {
  const labelMap = { HANDLED: "确认", IGNORED: "忽略", CLOSED: "关闭" };
  if (!window.confirm(`确定要「${labelMap[action]}」事件 ${eventNo} 吗？`)) return;
  try {
    await handleRiskEvent(eventNo, action);
    await loadRiskEvents();
  } catch (e) {
    riskMessage.value = e?.response?.data?.message || e.message || "操作失败";
  }
}

function riskLevelClass(level) {
  if (level === "HIGH") return "badge-red";
  if (level === "MEDIUM") return "badge-yellow";
  return "badge-gray";
}

function riskStatusClass(status) {
  if (status === "OPEN") return "badge-red";
  if (status === "HANDLED") return "badge-green";
  return "badge-gray";
}

function formatDatetime(val) {
  if (!val) return "";
  const d = new Date(val);
  if (isNaN(d)) return String(val);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function trimStr(v) {
  return v == null ? "" : String(v).trim();
}

/** 资产流水：后端 acct_asset_journal 字段 */
function journalTypeLabel(row) {
  const a = trimStr(row.asset_type);
  const c = trimStr(row.change_type);
  if (a && c) return `${a} / ${c}`;
  return a || c || "-";
}

function journalTypeTitle(row) {
  const r = trimStr(row.remark);
  return r || journalTypeLabel(row);
}

function journalRefOrderCell(row) {
  const t = (row.ref_type || "").toString().toUpperCase();
  if (t === "ORDER") return row.ref_no || "-";
  return "-";
}

function journalRefTradeCell(row) {
  const t = (row.ref_type || "").toString().toUpperCase();
  if (t === "TRADE") return row.ref_no || "-";
  return "-";
}

// ── 报表中心函数 ──────────────────────────────────────────────────────────
async function loadReportSummary() {
  try {
    reportSummary.value = await getReportSummary();
  } catch (e) {
    reportMessage.value = e?.response?.data?.message || e.message || "加载汇总失败";
  }
}

function switchReportTab(tab) {
  reportTab.value = tab;
  reportMessage.value = "";
  if (tab === "orders") loadReportOrders();
  else if (tab === "trades") loadReportTrades();
  else if (tab === "asset-journal") loadReportJournal();
  else if (tab === "operation-logs") loadReportLogs();
  else if (tab === "fee-details") loadReportFees();
}

async function loadReportOrders() {
  try {
    const f = reportOrderFilter.value;
    const params = { page: reportOrderPage.value, pageSize: reportOrderPageSize.value };
    if (f.customerCode) params.customerCode = f.customerCode;
    if (f.orderNo) params.orderNo = f.orderNo;
    if (f.orderDateFrom) params.orderDateFrom = f.orderDateFrom;
    if (f.orderDateTo) params.orderDateTo = f.orderDateTo;
    if (f.orderStatus) params.orderStatus = f.orderStatus;
    const data = await listReportOrders(params);
    reportOrderList.value = data.list || [];
    reportOrderTotal.value = Number(data.total ?? 0);
  } catch (e) {
    reportOrderList.value = [];
    reportOrderTotal.value = 0;
    reportMessage.value = e?.response?.data?.message || e.message || "查询委托失败";
  }
}

function searchReportOrders() { reportOrderPage.value = 1; loadReportOrders(); }

async function loadReportTrades() {
  try {
    const f = reportTradeFilter.value;
    const params = { page: reportTradePage.value, pageSize: reportTradePageSize.value };
    if (f.customerCode) params.customerCode = f.customerCode;
    if (f.tradeNo) params.tradeNo = f.tradeNo;
    if (f.tradeDateFrom) params.tradeDateFrom = f.tradeDateFrom;
    if (f.tradeDateTo) params.tradeDateTo = f.tradeDateTo;
    const data = await listReportTrades(params);
    reportTradeList.value = data.list || [];
    reportTradeTotal.value = Number(data.total ?? 0);
  } catch (e) {
    reportMessage.value = e?.response?.data?.message || e.message || "查询成交失败";
  }
}

function searchReportTrades() { reportTradePage.value = 1; loadReportTrades(); }

async function loadReportJournal() {
  const f = reportJournalFilter.value;
  const hasScope =
    trimStr(f.customerCode) ||
    trimStr(f.idNo) ||
    trimStr(f.fundAccountNo) ||
    trimStr(f.orderNo) ||
    trimStr(f.tradeNo);
  if (!hasScope) {
    reportJournalList.value = [];
    reportJournalTotal.value = 0;
    reportMessage.value =
      "请至少填写「客户代码」「证件号」「资金账户」「委托编号」或「成交编号」之一，再点击查询。";
    return;
  }
  try {
    const params = { page: reportJournalPage.value, pageSize: reportJournalPageSize.value };
    if (trimStr(f.customerCode)) params.customerCode = trimStr(f.customerCode);
    if (trimStr(f.idNo)) params.idNo = trimStr(f.idNo);
    if (trimStr(f.fundAccountNo)) params.fundAccountNo = trimStr(f.fundAccountNo);
    if (trimStr(f.orderNo)) params.orderNo = trimStr(f.orderNo);
    if (trimStr(f.tradeNo)) params.tradeNo = trimStr(f.tradeNo);
    if (f.journalDateFrom) params.journalDateFrom = f.journalDateFrom;
    if (f.journalDateTo) params.journalDateTo = f.journalDateTo;
    const data = await listReportAssetJournal(params);
    reportJournalList.value = data.list || [];
    reportJournalTotal.value = Number(data.total ?? 0);
    reportMessage.value = "";
  } catch (e) {
    reportJournalList.value = [];
    reportJournalTotal.value = 0;
    reportMessage.value = e?.response?.data?.message || e.message || "查询资产流水失败";
  }
}

function searchReportJournal() { reportJournalPage.value = 1; loadReportJournal(); }

async function loadReportLogs() {
  try {
    const f = reportLogFilter.value;
    const params = { page: reportLogPage.value, pageSize: reportLogPageSize.value };
    if (f.moduleName) params.moduleName = f.moduleName;
    if (f.bizNo) params.bizNo = f.bizNo;
    if (f.occurredFrom) params.occurredFrom = f.occurredFrom;
    if (f.occurredTo) params.occurredTo = f.occurredTo;
    const data = await listReportOperationLogs(params);
    reportLogList.value = data.list || [];
    reportLogTotal.value = Number(data.total ?? 0);
  } catch (e) {
    reportMessage.value = e?.response?.data?.message || e.message || "查询操作日志失败";
  }
}

function searchReportLogs() { reportLogPage.value = 1; loadReportLogs(); }

async function loadReportFees() {
  const f = reportFeeFilter.value;
  const hasScope =
    trimStr(f.customerCode) ||
    trimStr(f.idNo) ||
    trimStr(f.fundAccountNo) ||
    trimStr(f.orderNo) ||
    trimStr(f.tradeNo);
  if (!hasScope) {
    reportFeeList.value = [];
    reportMessage.value =
      "请至少填写「客户代码」「证件号」「资金账户」「委托编号」或「成交编号」之一，再点击查询。";
    return;
  }
  try {
    const params = {};
    if (trimStr(f.customerCode)) params.customerCode = trimStr(f.customerCode);
    if (trimStr(f.idNo)) params.idNo = trimStr(f.idNo);
    if (trimStr(f.fundAccountNo)) params.fundAccountNo = trimStr(f.fundAccountNo);
    if (trimStr(f.orderNo)) params.orderNo = trimStr(f.orderNo);
    if (trimStr(f.tradeNo)) params.tradeNo = trimStr(f.tradeNo);
    reportFeeList.value = await listReportFeeDetails(params);
    reportMessage.value = "";
  } catch (e) {
    reportFeeList.value = [];
    reportMessage.value = e?.response?.data?.message || e.message || "查询费用明细失败";
  }
}

function searchReportFees() { loadReportFees(); }

function onNavClick(item) {
  if (["opening-audit","market-center","order-center","customer-center","risk-center","report-center"].includes(item.key)) {
    activeNav.value = item.key;
    if (item.key === "market-center") {
      loadMarketList(true);
    } else if (item.key === "order-center") {
      loadOrderList();
    } else if (item.key === "customer-center") {
      customerViewMode.value = "list";
      loadCustomers();
    } else if (item.key === "risk-center") {
      riskPage.value = 1;
      loadRiskEvents();
    } else if (item.key === "report-center") {
      reportMessage.value = "";
      reportTab.value = "orders";
      loadReportSummary();
      loadReportOrders();
    }
    return;
  }
  message.value = `模块「${item.label}」待开发，后续可直接接入路由。`;
}

function isAppApiSource(item) {
  return item?.sourceType === "APP_API";
}

function marketLabel(item) {
  if (item?.shareholderMarket === "SH") return "沪A";
  if (item?.shareholderMarket === "SZ") return "深A";
  return "-";
}

function formatMoney(value) {
  const amount = Number(value || 0);
  return `¥${amount.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function quoteClass(changeAmount) {
  const val = Number(changeAmount || 0);
  if (val > 0) return "quote-up";
  if (val < 0) return "quote-down";
  return "quote-flat";
}

function formatNumber(value, digits = 2) {
  if (value === null || value === undefined || value === "") return "-";
  const num = Number(value);
  if (Number.isNaN(num)) return "-";
  return num.toFixed(digits);
}

function formatSigned(value, digits = 2) {
  if (value === null || value === undefined || value === "") return "-";
  const num = Number(value);
  if (Number.isNaN(num)) return "-";
  const abs = Math.abs(num).toFixed(digits);
  if (num > 0) return `+${abs}`;
  if (num < 0) return `-${abs}`;
  return `0.${"0".repeat(digits)}`;
}

function formatInteger(value) {
  if (value === null || value === undefined || value === "") return "-";
  const num = Number(value);
  if (Number.isNaN(num)) return "-";
  return Math.round(num).toLocaleString("zh-CN");
}

function formatQuoteTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ");
}

/** 用于 title 与省略展示：空值仍返回可读字符串 */
function textOrDash(value) {
  if (value === null || value === undefined) return "";
  return String(value);
}

function isBuyDirection(item) {
  const label = item?.tradeDirectionText || "";
  if (label.includes("买")) return true;
  if (label.includes("卖")) return false;
  const d = item?.tradeDirection;
  return d === "BUY" || d === "B";
}

function orderStatusClass(status) {
  const map = {
    REPORTED: "ost-reported",
    PART_FILLED: "ost-partial",
    FILLED: "ost-filled",
    CANCELED: "ost-canceled",
    PART_CANCELED: "ost-partcancel",
    REJECTED: "ost-rejected",
    INIT: "ost-init"
  };
  return map[status] || "ost-default";
}

function canCancelOrder(item) {
  return Boolean(item?.canCancel);
}

function canSimulateMatch(item) {
  const st = item?.orderStatus;
  const remain = Number(item?.remainQty ?? 0);
  return (st === "REPORTED" || st === "PART_FILLED") && remain > 0;
}

async function simulateMatchForOrder(item) {
  const remain = Number(item.remainQty ?? 0);
  const qtyStr = window.prompt("成交数量（不超过剩余委托量）", String(remain));
  if (qtyStr === null) return;
  const qty = Math.trunc(Number(qtyStr));
  if (!(qty > 0) || qty > remain) {
    message.value = "数量无效或超过剩余委托量";
    return;
  }
  const priceStr = window.prompt("成交价格", String(item.orderPrice ?? ""));
  if (priceStr === null) return;
  const fillPrice = Number(priceStr);
  if (!(fillPrice > 0)) {
    message.value = "价格无效";
    return;
  }
  const orderPrice = Number(item.orderPrice);
  if (!Number.isFinite(orderPrice) || orderPrice <= 0) {
    message.value = "委托价格异常，无法校验限价";
    return;
  }
  const dir = String(item.tradeDirection ?? "").toUpperCase();
  const buy =
    dir === "B" ||
    dir === "BUY" ||
    (item.tradeDirectionText && String(item.tradeDirectionText).includes("买"));
  const sell =
    dir === "S" ||
    dir === "SELL" ||
    (item.tradeDirectionText && String(item.tradeDirectionText).includes("卖"));
  if (buy && fillPrice > orderPrice) {
    message.value = "买入限价委托：成交价不能高于委托价";
    return;
  }
  if (sell && fillPrice < orderPrice) {
    message.value = "卖出限价委托：成交价不能低于委托价";
    return;
  }
  const requestSeqNo = `OPEXEC_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  try {
    await simulateOperatorTradeMatch(item.orderNo, {
      fillQty: qty,
      fillPrice,
      requestSeqNo
    });
    await loadOrderList();
    message.value = "模拟成交已入账（冻结已按成交释放/结算）";
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "模拟成交失败";
  }
}

async function cancelOrder(item) {
  if (!canCancelOrder(item)) {
    message.value = "该委托当前状态不可撤销";
    return;
  }
  const reason = window.prompt("请输入撤单原因", "操作员撤单");
  if (reason === null) return;
  const requestSeqNo = `OPCANCEL_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
  try {
    await cancelOperatorTradeOrder(item.orderNo, {
      requestSeqNo,
      cancelReason: reason || "操作员撤单"
    });
    await loadOrderList();
    message.value = `委托${item.orderNo}撤单申请成功`;
  } catch (e) {
    message.value = e?.response?.data?.message || e.message || "撤单失败";
  }
}
</script>
