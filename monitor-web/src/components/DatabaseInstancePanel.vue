<template>
  <el-card class="db-instance" shadow="never">
    <header class="db-instance__header">
      <div class="db-instance__identity">
        <span class="db-instance__icon"><i class="el-icon-coin" /></span>
        <div>
          <div class="db-instance__name">
            <h2>{{ client.hostname || client.clientId }}</h2>
            <el-tag size="small" effect="plain">{{ productName }}</el-tag>
          </div>
          <div class="db-instance__meta">
            <span><i class="el-icon-monitor" /> {{ client.ipAddress || '未配置节点地址' }}</span>
            <span>客户端 {{ client.clientId }}</span>
          </div>
        </div>
      </div>
      <div class="db-instance__live">
        <span class="db-instance__status" :class="{ 'is-available': available }">
          <i />{{ available ? '实时指标已接入' : hasMetrics ? '实例采集异常' : '等待实例数据' }}
        </span>
        <span>最近采集 {{ collectedAt }}</span>
      </div>
    </header>

    <div class="db-instance__body">
      <div class="db-primary">
        <div v-for="item in primaryMetrics" :key="item.label" class="db-primary__item">
          <div class="db-primary__label">
            <span>{{ item.label }}</span
            ><i :class="item.icon" />
          </div>
          <div class="db-primary__value" :class="item.tone">
            {{ item.value }}<small v-if="item.value !== UNAVAILABLE">{{ item.unit }}</small>
          </div>
          <span class="db-primary__caption">{{ item.caption }}</span>
        </div>
      </div>

      <div class="db-groups">
        <section class="db-group">
          <div class="db-group__heading">
            <i class="el-icon-connection" />
            <h3>连接与会话</h3>
          </div>
          <div class="db-group__load">
            <span>连接负载</span
            ><b>{{ metric(metrics.load, 1) }}<small v-if="valid(metrics.load)">%</small></b>
          </div>
          <el-progress :percentage="progress(metrics.load)" :show-text="false" color="#48a9ff" />
          <div class="db-group__row">
            <span>空闲连接</span><b>{{ metric(metrics.idleConnectionCount) }}<small>个</small></b>
          </div>
        </section>
        <section class="db-group">
          <div class="db-group__heading">
            <i class="el-icon-odometer" />
            <h3>事务与等待</h3>
          </div>
          <div class="db-group__row">
            <span>锁等待会话</span
            ><b :class="eventTone(metrics.waitingSessionCount)"
              >{{ metric(metrics.waitingSessionCount) }}<small>个</small></b
            >
          </div>
          <div class="db-group__row">
            <span>长事务</span
            ><b :class="eventTone(metrics.longTransactionCount)"
              >{{ metric(metrics.longTransactionCount) }}<small>个</small></b
            >
          </div>
          <p class="db-group__note">观察事务堆积与锁竞争情况</p>
        </section>
        <section class="db-group">
          <div class="db-group__heading">
            <i class="el-icon-lock" />
            <h3>安全与查询事件</h3>
          </div>
          <div class="db-group__row">
            <span>登录失败</span
            ><b :class="eventTone(metrics.loginFailureCount)"
              >{{ metric(metrics.loginFailureCount) }}<small>次</small></b
            >
          </div>
          <div class="db-group__row">
            <span>大表查询</span
            ><b :class="eventTone(metrics.repeatedLargeTableQueryCount)"
              >{{ metric(metrics.repeatedLargeTableQueryCount) }}<small>次</small></b
            >
          </div>
          <p class="db-group__note">
            {{
              eventsAvailable
                ? '事件确认与历史记录请前往告警记录'
                : '日志事件暂不可用，请检查日志采集配置'
            }}
          </p>
        </section>
      </div>

      <section class="db-queries">
        <div class="db-queries__heading">
          <div>
            <h3>
              大表查询记录 <span>{{ queries.length }}</span>
            </h3>
            <p>当前采集窗口内的查询，展开查看完整 SQL</p>
          </div>
          <el-button type="text" icon="el-icon-right" @click="openAlerts">查看告警记录</el-button>
        </div>
        <el-table
          :data="pagedQueries"
          class="monitor-detail-table db-query-table"
          size="small"
          :empty-text="eventsAvailable ? '当前采集窗口内暂无大表查询记录' : '日志事件暂不可用'"
        >
          <el-table-column type="expand">
            <template slot-scope="scope">
              <div class="db-query-detail">
                <span>原始 SQL</span>
                <pre>{{ scope.row.query || '日志未提供 SQL' }}</pre>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="queryTime"
            label="执行时间"
            min-width="155"
            show-overflow-tooltip
          />
          <el-table-column label="数据库 / 执行账号" min-width="150">
            <template slot-scope="scope"
              ><div class="db-query-cell">
                <b>{{ scope.row.databaseName || UNAVAILABLE }}</b
                ><small>{{ scope.row.userName || '账号未提供' }}</small>
              </div></template
            >
          </el-table-column>
          <el-table-column label="表 / 容量" min-width="145">
            <template slot-scope="scope"
              ><div class="db-query-cell">
                <b>{{ scope.row.tableName || UNAVAILABLE }}</b
                ><small>{{ capacity(scope.row.tableSizeMB).text }}</small>
              </div></template
            >
          </el-table-column>
          <el-table-column prop="query" label="查询语句" min-width="220" show-overflow-tooltip>
            <template slot-scope="scope"
              ><code class="db-query-preview">{{ scope.row.query }}</code></template
            >
          </el-table-column>
          <el-table-column
            prop="clientAddress"
            label="来源地址"
            min-width="135"
            show-overflow-tooltip
          />
          <el-table-column label="次数" min-width="65" align="right">
            <template slot-scope="scope">{{ metric(scope.row.occurrences) }}</template>
          </el-table-column>
        </el-table>
        <div class="db-queries__footer">
          <span>仅展示采集窗口内事件，历史告警将保留</span>
          <el-pagination
            v-if="queries.length > PAGE_SIZE"
            small
            layout="total, prev, pager, next"
            :current-page.sync="currentPage"
            :page-size="PAGE_SIZE"
            :total="queries.length"
          />
        </div>
      </section>
    </div>
  </el-card>
</template>

<script>
const UNAVAILABLE = '--'
const PAGE_SIZE = 6
const PERCENT_MAX = 100
const CAPACITY_SCALE = 1024
const CAPACITY_UNITS = ['MB', 'GB', 'TB', 'PB']

/** 数据库实例详情面板，复用 PostgreSQL 与瀚高指标，集中展示运行状态和原始查询记录。 */
export default {
  props: { client: { type: Object, required: true } },
  data() {
    return { UNAVAILABLE, PAGE_SIZE, currentPage: 1 }
  },
  computed: {
    metrics() {
      return this.client.highgoMetrics || {}
    },
    hasMetrics() {
      return Boolean(this.client.highgoMetrics)
    },
    available() {
      return this.valid(this.metrics.connectionCount)
    },
    productName() {
      return this.metrics.databaseProduct || (this.hasMetrics ? '数据库监控' : '数据库实例')
    },
    collectedAt() {
      // 采集时间为客户端本地时间，不能按服务端告警的 UTC 时间再次加八小时。
      return this.metrics.collectTime
        ? String(this.metrics.collectTime).replace('T', ' ').slice(0, 19)
        : UNAVAILABLE
    },
    eventsAvailable() {
      return (
        this.valid(this.metrics.loginFailureCount) &&
        this.valid(this.metrics.repeatedLargeTableQueryCount)
      )
    },
    primaryMetrics() {
      const size = this.capacity(this.metrics.databaseSizeMB)
      return [
        {
          label: '当前连接',
          value: this.metric(this.metrics.connectionCount),
          unit: '个',
          icon: 'el-icon-connection',
          caption: '实例客户端连接',
          tone: ''
        },
        {
          label: '活跃连接',
          value: this.metric(this.metrics.activeConnectionCount),
          unit: '个',
          icon: 'el-icon-link',
          caption: '正在执行请求的会话',
          tone: 'is-active'
        },
        {
          label: '实例总容量',
          value: size.value,
          unit: size.unit,
          icon: 'el-icon-coin',
          caption: '纳入监控的数据库容量合计',
          tone: ''
        },
        {
          label: '缓存命中率',
          value: this.metric(this.metrics.cacheHitRatio, 2),
          unit: '%',
          icon: 'el-icon-data-analysis',
          caption: '数据库缓冲区命中比例',
          tone: ''
        }
      ]
    },
    queries() {
      return this.metrics.repeatedLargeTableQueries || []
    },
    pageCount() {
      return Math.max(1, Math.ceil(this.queries.length / PAGE_SIZE))
    },
    pagedQueries() {
      return this.queries.slice((this.currentPage - 1) * PAGE_SIZE, this.currentPage * PAGE_SIZE)
    }
  },
  watch: {
    pageCount(value) {
      // 实时窗口过期会减少记录，确保当前页始终落在有效范围内。
      this.currentPage = Math.min(this.currentPage, value)
    }
  },
  methods: {
    /** 判断指标是否为有效非负数，缺失和采集失败不能展示为零。 */
    valid(value) {
      return (
        value !== null &&
        value !== undefined &&
        value !== '' &&
        Number.isFinite(Number(value)) &&
        Number(value) >= 0
      )
    },
    /** 格式化指标，保留实际零值。 */
    metric(value, digits = 0) {
      return this.valid(value)
        ? Number(value).toLocaleString('zh-CN', { maximumFractionDigits: digits })
        : UNAVAILABLE
    },
    /** 将采集的 MB 容量自动转换为适合阅读的单位。 */
    capacity(value) {
      if (!this.valid(value)) return { value: UNAVAILABLE, unit: '', text: UNAVAILABLE }
      let size = Number(value)
      let unitIndex = 0
      while (size >= CAPACITY_SCALE && unitIndex < CAPACITY_UNITS.length - 1) {
        size /= CAPACITY_SCALE
        unitIndex++
      }
      const formatted = this.metric(size, 2)
      return {
        value: formatted,
        unit: CAPACITY_UNITS[unitIndex],
        text: `${formatted} ${CAPACITY_UNITS[unitIndex]}`
      }
    },
    /** 将有效负载限制在进度条范围内。 */
    progress(value) {
      return this.valid(value) ? Math.min(PERCENT_MAX, Number(value)) : 0
    },
    /** 仅对实际观测到的非零事件做提示，不推定未配置的告警阈值。 */
    eventTone(value) {
      return this.valid(value) && Number(value) > 0 ? 'has-events' : ''
    },
    /** 进入现有告警历史与确认页面。 */
    openAlerts() {
      window.location.hash = '/alerts'
    }
  }
}
</script>

<style scoped>
.db-instance {
  margin-bottom: 1rem;
  color: var(--text);
  border: 1px solid var(--line);
  border-radius: 0.85rem;
  background: #0f1c2d;
}
.db-instance ::v-deep .el-card__body {
  padding: 0;
}
.db-instance__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1.3rem 1.5rem;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(110deg, #152a42, #101e30 65%);
}
.db-instance__identity,
.db-instance__name,
.db-instance__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.8rem;
  min-width: 0;
}
.db-instance__identity {
  gap: 1rem;
}
.db-instance__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 2.8rem;
  aspect-ratio: 1;
  background: #48a9ff14;
  border: 1px solid #48a9ff30;
  border-radius: 0.7rem;
  color: #6dbaff;
  font-size: 1.4rem;
}
.db-instance h2 {
  margin: 0;
  font-size: 1.15rem;
  overflow-wrap: anywhere;
}
.db-instance__name .el-tag {
  border-color: #48a9ff38;
  background: #48a9ff0c;
  color: #8dcbff;
}
.db-instance__meta {
  margin-top: 0.45rem;
  color: #91a7c1;
  font-size: 0.75rem;
  overflow-wrap: anywhere;
}
.db-instance__meta span + span {
  padding-left: 0.8rem;
  border-left: 1px solid #344760;
}
.db-instance__live {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  align-items: flex-end;
  font-size: 0.7rem;
  color: #8b9fb9;
}
.db-instance__status {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--yellow);
}
.db-instance__status i {
  width: 0.4rem;
  aspect-ratio: 1;
  border-radius: 50%;
  background: currentColor;
}
.db-instance__status.is-available {
  color: var(--green);
}
.db-instance__body {
  padding: 1.5rem;
}
.db-primary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding-bottom: 1.5rem;
}
.db-primary__item {
  min-width: 0;
  padding: 0.2rem 1.5rem;
  border-left: 1px solid var(--line);
}
.db-primary__item:first-child {
  padding-left: 0;
  border-left: none;
}
.db-primary__item:last-child {
  padding-right: 0;
}
.db-primary__label {
  display: flex;
  align-items: center;
  gap: 1rem;
  justify-content: space-between;
  color: #a1b5ce;
  font-size: 0.8rem;
}
.db-primary__label i {
  color: #5c83ab;
  font-size: 1rem;
}
.db-primary__value {
  margin: 0.65rem 0 0.45rem;
  color: #e6f2ff;
  font-size: clamp(1.65rem, 2.2vw, 2.5rem);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  overflow-wrap: anywhere;
}
.db-primary__value.is-active {
  color: #53d9b1;
}
.db-primary__value small {
  margin-left: 0.4rem;
  font-size: 0.75rem;
  color: #8a9fb9;
  font-weight: 400;
}
.db-primary__caption {
  color: #7f96b1;
  font-size: 0.7rem;
}
.db-groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}
.db-group {
  min-width: 0;
  padding: 1rem 1.15rem;
  border: 1px solid #243952;
  border-radius: 0.55rem;
  background: #122237;
}
.db-group__heading {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: #86b6e1;
  margin-bottom: 1rem;
}
.db-group h3,
.db-queries h3 {
  margin: 0;
  color: #dce9f8;
  font-size: 0.85rem;
  font-weight: 600;
}
.db-group__row,
.db-group__load {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  color: #94abc6;
  font-size: 0.75rem;
}
.db-group__row + .db-group__row {
  margin-top: 0.65rem;
}
.db-group__row b,
.db-group__load b {
  color: #e2edfb;
  font-size: 1rem;
  font-variant-numeric: tabular-nums;
}
.db-group small {
  margin-left: 0.3rem;
  color: #7e98b4;
  font-size: 0.65rem;
  font-weight: 400;
}
.db-group .has-events {
  color: #f4b740;
}
.db-group ::v-deep .el-progress {
  margin: 0.7rem 0 1rem;
}
.db-group ::v-deep .el-progress-bar__outer {
  background: #243b54 !important;
}
.db-group__note {
  margin: 0.85rem 0 0;
  color: #7c95b1;
  font-size: 0.68rem;
  line-height: 1.5;
}
.db-queries {
  margin-top: 1.6rem;
  padding-top: 1.35rem;
  border-top: 1px solid var(--line);
}
.db-queries__heading {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 1rem;
}
.db-queries h3 span {
  margin-left: 0.5rem;
  padding: 0.12rem 0.45rem;
  border-radius: 0.3rem;
  color: #84bdf1;
  background: #48a9ff12;
  font-size: 0.7rem;
}
.db-queries__heading p {
  margin: 0.45rem 0 0;
  color: #8098b3;
  font-size: 0.7rem;
}
.db-queries__heading .el-button {
  padding: 0;
  color: #70b7f7;
  font-size: 0.75rem;
}
.db-query-table.monitor-detail-table {
  width: 100%;
  margin: 0;
  border: 1px solid #263c55;
  border-radius: 0.4rem;
}
.db-query-table ::v-deep .el-table__cell {
  padding: 0.3rem 0;
}
.db-query-table ::v-deep .cell {
  line-height: 1.4;
  font-size: 0.75rem;
}
.db-query-table ::v-deep th.el-table__cell {
  background: #162a40 !important;
  color: #95acc7;
  font-size: 0.72rem;
  border-bottom-color: #2a405b !important;
}
.db-query-table ::v-deep td.el-table__cell,
.db-query-table ::v-deep .el-table__body tr:hover > td.el-table__cell {
  background: #101e31 !important;
  border-bottom-color: #21364e !important;
}
.db-query-table ::v-deep .el-table__expanded-cell,
.db-query-table ::v-deep .el-table__expanded-cell:hover {
  background: #0c1828 !important;
  padding: 1rem 1.25rem;
}
.db-query-table ::v-deep .el-table__expand-icon {
  color: #7f9cba;
}
.db-query-table ::v-deep .el-table__empty-block {
  min-height: 7rem;
}
.db-query-cell {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}
.db-query-cell b {
  font-size: 0.75rem;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.db-query-cell small {
  color: #8099b5;
  font-size: 0.68rem;
}
.db-query-preview {
  color: #a9c6e5;
  font-size: 0.72rem;
  white-space: nowrap;
}
.db-query-detail > span {
  color: #809bb8;
  font-size: 0.7rem;
}
.db-query-detail pre {
  margin: 0.6rem 0 0;
  color: #b7d7f6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 0.78rem;
  line-height: 1.7;
  font-family: Consolas, monospace;
}
.db-queries__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 0.85rem;
  color: #748da9;
  font-size: 0.68rem;
}
.db-queries__footer ::v-deep .el-pagination {
  padding: 0;
  color: #b2c8df;
}
.db-queries__footer ::v-deep .el-pagination button,
.db-queries__footer ::v-deep .el-pager li {
  background: #152a40;
  color: #a4bdd8;
  border-radius: 0.25rem;
}
.db-queries__footer ::v-deep .el-pager li.active {
  color: #65b5ff;
  background: #48a9ff1a;
}
.db-queries__footer ::v-deep .el-pagination__total {
  color: #8099b5;
}
@media (max-width: 64rem) {
  .db-groups {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .db-group:last-child {
    grid-column: 1 / -1;
  }
  .db-primary__item {
    padding-inline: 1rem;
  }
}
@media (max-width: 40rem) {
  .db-instance__header,
  .db-instance__body {
    padding: 1rem;
  }
  .db-instance__live {
    align-items: flex-start;
  }
  .db-primary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 1.25rem 0;
  }
  .db-primary__item:nth-child(odd) {
    padding-left: 0;
    border-left: 0;
  }
  .db-primary__item:nth-child(even) {
    padding-right: 0;
  }
  .db-groups {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
