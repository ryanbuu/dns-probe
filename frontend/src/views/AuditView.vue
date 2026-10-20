<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RefreshIcon, ClockIcon } from "../icons";
import { api } from "../api";
import type { AuditEvent } from "../types";
const rows = ref<AuditEvent[]>([]),
  loading = ref(false),
  error = ref(""),
  offset = ref(0),
  selected = ref<AuditEvent>(),
  drawer = ref(false);
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
    <el-button :loading="loading" @click="load"
      ><RefreshIcon />刷新日志</el-button
    >
  </div>
  <el-alert
    v-if="error"
    :title="error"
    type="error"
    show-icon
    :closable="false"
  />
  <div class="surface audit-surface">
    <div class="section-heading">
      <div>
        <h2>操作审计</h2>
        <p>待核实表示操作结果尚未完整确认，需要检查实际 DNS 记录。</p>
      </div>
      <el-tag size="small">持久化存储</el-tag>
    </div>
    <el-table v-loading="loading" :data="rows" row-key="id"
      ><el-table-column label="操作时间" width="180"
        ><template #default="{ row }"
          ><span class="audit-time">{{
            new Date(row.occurredAt).toLocaleString("zh-CN", { hour12: false })
          }}</span></template
        ></el-table-column
      ><el-table-column label="操作" width="85"
        ><template #default="{ row }"
          ><el-tag
            size="small"
            :type="
              row.action === 'DELETE'
                ? 'danger'
                : row.action === 'CREATE'
                  ? 'primary'
                  : 'warning'
            "
            >{{ actions[row.action] }}</el-tag
          ></template
        ></el-table-column
      ><el-table-column label="记录" min-width="230"
        ><template #default="{ row }"
          ><div class="mono">{{ row.recordName }}</div>
          <div class="record-fqdn">
            {{ row.recordType }} · {{ row.zoneId }}
          </div></template
        ></el-table-column
      ><el-table-column
        label="操作人"
        prop="actor"
        width="120"
      /><el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag
            size="small"
            :type="
              row.status === 'SUCCESS'
                ? 'success'
                : row.status === 'FAILED'
                  ? 'danger'
                  : 'warning'
            "
            >{{ statuses[row.status] }}</el-tag
          ></template
        ></el-table-column
      ><el-table-column width="80" fixed="right"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            size="small"
            @click="
              selected = row;
              drawer = true;
            "
            >详情</el-button
          ></template
        ></el-table-column
      ><template #empty
        ><div class="audit-empty">
          <ClockIcon />
          <h3>变更历史从这里开始</h3>
          <p>添加、修改或删除一条记录后，操作会出现在这里。</p>
        </div></template
      ></el-table
    >
  </div>
  <div class="audit-pagination">
    <el-button
      :disabled="offset === 0 || loading"
      @click="
        offset = Math.max(0, offset - 50);
        load();
      "
      >上一页</el-button
    ><span>第 {{ offset / 50 + 1 }} 页</span
    ><el-button
      :disabled="rows.length < 50 || loading"
      @click="
        offset += 50;
        load();
      "
      >下一页</el-button
    >
  </div>
  <el-drawer v-model="drawer" title="变更详情" size="600px"
    ><template v-if="selected"
      ><el-descriptions :column="1" border size="small"
        ><el-descriptions-item label="操作编号"
          >#{{ selected.id }}</el-descriptions-item
        ><el-descriptions-item label="操作人">{{
          selected.actor
        }}</el-descriptions-item
        ><el-descriptions-item label="结果"
          >{{ statuses[selected.status] }} ·
          {{ selected.detail }}</el-descriptions-item
        ></el-descriptions
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
    ></el-drawer
  >
</template>
