<template>
  <div v-if="rows.length" class="data-table-wrap">
    <table class="data-table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key">
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in rows" :key="rowIndex">
          <td v-for="column in columns" :key="column.key">
            <span v-if="column.type === 'status'" :class="statusClass(row[column.key])">{{
              row[column.key] || 'UNKNOWN'
            }}</span>
            <span v-else>{{ displayValue(row[column.key]) }}</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
  <div v-else class="panel-empty">暂无明细数据</div>
</template>

<script>
/** 通用数据表组件，统一处理状态样式和空数据展示。 */
export default {
  props: {
    columns: { type: Array, default: () => [] },
    rows: { type: Array, default: () => [] }
  },
  methods: {
    statusClass(value) {
      const isNormal = value === 'UP' || value === 'ONLINE' || value === 'running'
      return ['status-tag', isNormal ? 'ok' : 'bad']
    },
    displayValue(value) {
      return value === undefined || value === null || value === '' ? '-' : value
    }
  }
}
</script>
