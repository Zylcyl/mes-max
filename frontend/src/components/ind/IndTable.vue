<script setup>
/**
 * 高密度数据表（车间终端风格）。
 *
 * 列定义 col：
 *   { key, title, width, align, kind, digits, format(row), tone(row), sub(row) }
 * kind 支持：text | num | pct | mono | date | time | tag | led | bar | progress
 * 需要更复杂的单元格时，用 #cell="{ row, col }" 作用域插槽覆盖。
 */
import { computed } from 'vue';
import IndTag from './IndTag.vue';
import IndLed from './IndLed.vue';
import IndBar from './IndBar.vue';
import IndEmpty from './IndEmpty.vue';
import { dateTime, num, pct, timeOnly } from '@/utils/format';

const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  loading: { type: Boolean, default: false },
  dense: { type: Boolean, default: false },
  clickable: { type: Boolean, default: false },
  activeKey: { type: [String, Number, null], default: null },
  emptyText: { type: String, default: '暂无数据' },
  emptyHint: { type: String, default: '' },
  rowClass: { type: Function, default: null },
});

const emit = defineEmits(['row-click', 'row-dblclick']);

const showEmpty = computed(() => !props.loading && (!props.rows || props.rows.length === 0));

function cellText(row, col) {
  if (col.format) return col.format(row);
  const v = row[col.key];
  switch (col.kind) {
    case 'num':
      return num(v, col.digits ?? 0);
    case 'pct':
      return pct(v, col.digits ?? 1);
    case 'date':
      return dateTime(v).slice(0, 10);
    case 'time':
      return timeOnly(v);
    case 'datetime':
      return dateTime(v);
    default:
      return v === null || v === undefined || v === '' ? '—' : v;
  }
}

function barTone(row, col) {
  if (col.tone) return col.tone(row);
  const v = Number(col.value ? col.value(row) : 0);
  return v >= 95 ? 'stop' : v >= 75 ? 'idle' : 'run';
}

function rowCls(row) {
  const list = [];
  if (props.rowClass) list.push(props.rowClass(row));
  if (props.activeKey !== null && row[props.rowKey] === props.activeKey) list.push('is-active');
  if (props.clickable) list.push('is-clickable');
  return list;
}
</script>

<template>
  <div class="ind-table-wrap">
    <div v-if="loading" class="ind-loading">
      <div class="ind-loading__bar" />
      <span>数据加载中</span>
    </div>
    <IndEmpty v-else-if="showEmpty" :text="emptyText" :hint="emptyHint" />
    <table v-else class="ind-table" :class="{ 'ind-table--compact': dense }">
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            :style="{ width: col.width, minWidth: col.minWidth || col.width, textAlign: col.align || 'left' }"
          >
            {{ col.title }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row[rowKey]"
          :class="rowCls(row)"
          @click="emit('row-click', row)"
          @dblclick="emit('row-dblclick', row)"
        >
          <td
            v-for="col in columns"
            :key="col.key"
            :style="{ textAlign: col.align || 'left' }"
            :class="{ num: col.kind === 'num' || col.kind === 'pct' || col.align === 'right' }"
          >
            <slot name="cell" :row="row" :col="col">
              <template v-if="col.kind === 'tag'">
                <IndTag :tone="col.tone ? col.tone(row) : 'plain'">{{ cellText(row, col) }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'led'">
                <span class="center gap-1">
                  <IndLed :tone="col.tone ? col.tone(row) : 'offline'" />
                  {{ cellText(row, col) }}
                </span>
              </template>
              <template v-else-if="col.kind === 'bar' || col.kind === 'progress'">
                <IndBar
                  :value="col.value ? col.value(row) : 0"
                  :tone="barTone(row, col)"
                  :label="col.kind === 'progress' ? `${Math.round(col.value ? col.value(row) : 0)}%` : ''"
                  :striped="col.kind === 'progress'"
                />
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' || col.kind === 'num' }">{{ cellText(row, col) }}</span>
                <div v-if="col.sub" class="mute" style="font-size: 11px; line-height: 1.2">{{ col.sub(row) }}</div>
              </template>
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
