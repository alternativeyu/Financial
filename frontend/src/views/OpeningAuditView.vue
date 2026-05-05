<template>
  <div class="dashboard-neo-page">
    <div
      class="container dashboard-neo-container"
      :class="{ 'dashboard-order-wide': activeNav === 'order-center' }"
    >
    <div class="topbar card">
      <div>
        <div class="sys-name">FTS Operator Workbench</div>
        <div class="sys-subtitle">开户审核中心 | 金融交易系统</div>
      </div>
      <div style="text-align: right;">
        <div class="operator-tag">当前操作员：{{ operatorName }}</div>
        <button class="secondary" @click="logout">安全退出</button>
      </div>
    </div>

    <div class="card nav-card">
      <div class="top-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: item.key === activeNav }"
          @click="onNavClick(item)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

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
  submitOperatorTradeOrder
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
  { key: "market-center", label: "行情中心" },
  { key: "system-config", label: "系统设置" }
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

function onNavClick(item) {
  if (item.key === "opening-audit" || item.key === "market-center" || item.key === "order-center" || item.key === "customer-center") {
    activeNav.value = item.key;
    if (item.key === "market-center") {
      loadMarketList(true);
    } else if (item.key === "order-center") {
      loadOrderList();
    } else if (item.key === "customer-center") {
      customerViewMode.value = "list";
      loadCustomers();
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
