<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ReloadOutlined, ClockCircleOutlined } from "@ant-design/icons-vue";
import { api } from "../api";
import type { AuditEvent } from "../types";
const rows = ref<AuditEvent[]>([]),
  loading = ref(false),
  error = ref(""),
  offset = ref(0),
  selected = ref<AuditEvent>(),
  drawer = ref(false);
const columns = [
  { title: "操作时间", key: "time", width: 180 },
  { title: "操作", key: "action", width: 85 },
  { title: "记录", key: "record" },
  { title: "操作人", dataIndex: "actor", width: 120 },
  { title: "状态", key: "status", width: 100 },
  { title: "", key: "details", width: 80 },
];
const actions: Record<string, string> = {
    CREATE: "新增",
    UPDATE: "修改",
    DELETE: "删除",
  },
  statuses: Record<string, string> = {
    SUCCESS: "已完成",
    FAILED: "失败",
    PENDING: "待核实",
  };
async function load() {
  loading.value = true;
  error.value = "";
  try {
    rows.value = await api(`/audit?offset=${offset.value}&limit=50`);
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function json(v: string) {
  try {
    const value = JSON.parse(v);
    return value
      ? `${value.name} · ${value.type} · TTL ${value.ttl}s\n${value.values.join("\n")}`
      : "无";
  } catch {
    return v;
  }
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">CHANGE HISTORY</div>
      <h1>每一次变更，都有记录。</h1>
      <p>追溯记录的修改前后内容与提交结果。</p>
    </div>
    <a-button :loading="loading" @click="load"
      ><ReloadOutlined />刷新日志</a-button
    >
  </div>
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <div class="surface audit-surface">
    <div class="section-heading">
      <div>
        <h2>操作审计</h2>
        <p>待核实表示操作结果尚未完整确认，需要检查实际 DNS 记录。</p>
      </div>
      <a-tag>持久化存储</a-tag>
    </div>
    <a-table
      :columns="columns"
      :data-source="rows"
      row-key="id"
      :loading="loading"
      :pagination="false"
      :scroll="{ x: 760 }"
      ><template #bodyCell="{ column, record }"
        ><template v-if="column.key === 'time'"
          ><span class="audit-time">{{
            new Date(record.occurredAt).toLocaleString("zh-CN", {
              hour12: false,
            })
          }}</span></template
        ><template v-else-if="column.key === 'action'"
          ><a-tag
            :color="
              record.action === 'DELETE'
                ? 'red'
                : record.action === 'CREATE'
                  ? 'blue'
                  : 'orange'
            "
            >{{ actions[record.action] }}</a-tag
          ></template
        ><template v-else-if="column.key === 'record'"
          ><div class="mono">{{ record.recordName }}</div>
          <div class="record-fqdn">
            {{ record.recordType }} · {{ record.zoneId }}
          </div></template
        ><template v-else-if="column.key === 'status'"
          ><a-badge
            :status="
              record.status === 'SUCCESS'
                ? 'success'
                : record.status === 'FAILED'
                  ? 'error'
                  : 'warning'
            "
            :text="statuses[record.status]" /></template
        ><template v-else-if="column.key === 'details'"
          ><a-button
            type="link"
            size="small"
            @click="
              selected = record;
              drawer = true;
            "
            >详情</a-button
          ></template
        ></template
      ><template #emptyText
        ><div class="audit-empty">
          <ClockCircleOutlined />
          <h3>变更历史从这里开始</h3>
          <p>添加、修改或删除一条记录后，操作会出现在这里。</p>
        </div></template
      ></a-table
    >
    <div class="audit-pagination">
      <a-button
        :disabled="offset === 0 || loading"
        @click="
          offset = Math.max(0, offset - 50);
          load();
        "
        >上一页</a-button
      ><span>第 {{ offset / 50 + 1 }} 页</span
      ><a-button
        :disabled="rows.length < 50 || loading"
        @click="
          offset += 50;
          load();
        "
        >下一页</a-button
      >
    </div>
  </div>
  <a-drawer :open="drawer" title="变更详情" :width="600" @close="drawer = false"
    ><template v-if="selected"
      ><a-descriptions :column="1" bordered size="small"
        ><a-descriptions-item label="操作编号"
          >#{{ selected.id }}</a-descriptions-item
        ><a-descriptions-item label="操作人">{{
          selected.actor
        }}</a-descriptions-item
        ><a-descriptions-item label="结果"
          >{{ statuses[selected.status] }} ·
          {{ selected.detail }}</a-descriptions-item
        ></a-descriptions
      >
      <div class="change-preview">
        <div class="diff-before">
          <span>变更前</span>
          <pre>{{ json(selected.beforeJson) }}</pre>
        </div>
        <div class="diff-after">
          <span>变更后</span>
          <pre>{{ json(selected.afterJson) }}</pre>
        </div>
      </div></template
    ></a-drawer
  >
</template>
