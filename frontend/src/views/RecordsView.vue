<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  PlusIcon,
  RefreshIcon,
  SearchIcon,
  LockIcon,
  EditIcon,
  DeleteIcon,
  ProbeIcon,
  CopyIcon,
} from "../icons";
import { api } from "../api";
import type { Rrset, Zone, ZoneRecords, ProbeResult } from "../types";
import RecordEditor from "../components/RecordEditor.vue";
const props = defineProps<{
  zones: Zone[];
  initialZone?: string;
  admin: boolean;
}>();
const emit = defineEmits<{ changed: [] }>();
const selected = ref(props.initialZone || props.zones[0]?.id || ""),
  data = ref<ZoneRecords>(),
  loading = ref(false),
  error = ref(""),
  search = ref(""),
  type = ref("ALL"),
  system = ref(false),
  editor = ref(false),
  editing = ref<Rrset>(),
  page = ref(1),
  pageSize = ref(12);
const probeOpen = ref(false),
  probeBusy = ref(false),
  probeName = ref(""),
  probeType = ref("A"),
  probeResult = ref<ProbeResult[]>([]);
let sequence = 0;
watch(
  () => props.initialZone,
  (id) => {
    if (id) selected.value = id;
  },
);
watch(
  selected,
  () => {
    search.value = "";
    type.value = "ALL";
    void load();
  },
  { immediate: true },
);
watch([search, type, system, pageSize], () => (page.value = 1));
async function load() {
  if (!selected.value) return;
  const request = ++sequence;
  loading.value = true;
  error.value = "";
  data.value = undefined;
  try {
    const r = await api<ZoneRecords>(`/zones/${selected.value}/records`);
    if (request === sequence) data.value = r;
  } catch (e) {
    if (request === sequence) error.value = (e as Error).message;
  } finally {
    if (request === sequence) loading.value = false;
  }
}
const systemTypes = new Set([
  "SOA",
  "NS",
  "DNSKEY",
  "RRSIG",
  "NSEC",
  "NSEC3",
  "NSEC3PARAM",
  "DHCID",
  "TYPE65534",
]);
const filtered = computed(
  () =>
    data.value?.records.filter(
      (r) =>
        (system.value || !systemTypes.has(r.type)) &&
        (type.value === "ALL" || r.type === type.value) &&
        `${r.name} ${r.values.join(" ")}`
          .toLowerCase()
          .includes(search.value.toLowerCase()),
    ) || [],
);
const paginated = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
const recordTypes = computed(() => [
  "ALL",
  ...new Set(data.value?.records.map((r) => r.type) || []),
]);
watch(filtered, (v) => {
  if ((page.value - 1) * pageSize.value >= v.length) page.value = 1;
});
function edit(record?: Rrset) {
  editing.value = record;
  editor.value = true;
}
async function remove(record: Rrset) {
  const zoneId = selected.value;
  try {
    await ElMessageBox.confirm(
      `${record.name} · ${record.type}，共 ${record.values.length} 个值。此操作会删除整个记录集，并同步到从节点。`,
      "删除这组解析记录？",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
        confirmButtonClass: "el-button--danger",
      },
    );
    await api(`/zones/${zoneId}/records/delete`, {
      method: "POST",
      body: JSON.stringify({
        name: record.name,
        type: record.type,
        expectedRevision: record.revision,
      }),
    });
    ElMessage.success("记录已删除");
    emit("changed");
    await load();
  } catch (e) {
    if (e instanceof Error) ElMessage.error(e.message);
  }
}
async function copy(record: Rrset) {
  try {
    await navigator.clipboard.writeText(
      `${record.name} ${record.ttl} IN ${record.type} ${record.values.join("\n")}`,
    );
    ElMessage.success("记录已复制");
  } catch {
    ElMessage.info("当前浏览器不支持复制，请手动选择记录文本");
  }
}
async function probe() {
  probeBusy.value = true;
  try {
    probeResult.value = await api(
      `/zones/${selected.value}/probe?${new URLSearchParams({ name: probeName.value || data.value?.zone.name || "@", type: probeType.value })}`,
    );
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    probeBusy.value = false;
  }
}
function openProbe(record?: Rrset) {
  probeName.value = record?.name || data.value?.zone.name || "@";
  probeType.value =
    record?.type && !systemTypes.has(record.type) ? record.type : "A";
  probeResult.value = [];
  probeOpen.value = true;
  void probe();
}
function shortName(name: string) {
  const zone = data.value?.zone.name || "";
  return name === zone
    ? "@"
    : name.endsWith(zone)
      ? name.slice(0, -zone.length).replace(/\.$/, "")
      : name;
}
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">DNS WORKSPACE</div>
      <h1>解析记录</h1>
      <p>管理区域记录，变更通过主节点自动同步。</p>
    </div>
    <el-button @click="load" :loading="loading"
      ><RefreshIcon />刷新记录</el-button
    >
  </div>
  <div class="records-layout">
    <aside class="zone-rail">
      <div class="rail-heading">
        DNS 区域 <span>{{ zones.length }}</span>
      </div>
      <button
        v-for="zone in zones"
        :key="zone.id"
        class="zone-choice"
        :class="{ active: selected === zone.id }"
        @click="selected = zone.id"
      >
        <span class="zone-dot" :class="{ protected: zone.readOnly }"></span
        ><span
          ><strong>{{ zone.name.replace(/\.$/, "") }}</strong
          ><small>{{
            zone.readOnly ? "自动维护 · 只读" : "手动维护"
          }}</small></span
        ><LockIcon v-if="zone.readOnly" />
      </button>
      <div class="rail-note">
        <LockIcon /><span
          >Kea 区域受保护，避免人工修改影响租约及 DNS 所有权。</span
        >
      </div>
    </aside>
    <section class="record-panel">
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      />
      <div v-if="error" class="empty-retry">
        <el-button @click="load">重新读取区域</el-button>
      </div>
      <el-skeleton v-if="loading" animated :rows="8" />
      <template v-if="data">
        <div class="zone-panel-heading">
          <div>
            <div class="inline-label">
              {{ data.zone.readOnly ? "KEA MANAGED" : "PRIMARY ZONE" }}
            </div>
            <h2>{{ data.zone.name.replace(/\.$/, "") }}</h2>
            <div class="zone-meta">
              <span
                >主节点 <b class="mono">{{ data.zone.primary }}</b></span
              ><span
                >SOA <b class="mono">{{ data.zone.serial }}</b></span
              >
            </div>
          </div>
          <el-button
            type="primary"
            :disabled="data.zone.readOnly || !admin"
            @click="edit()"
            ><PlusIcon />添加记录</el-button
          >
        </div>
        <el-alert
          v-if="data.zone.readOnly"
          class="readonly-alert"
          title="此区域由 Kea 自动维护"
          description="可以查询和对比记录；增删改已禁用，以保护 DHCP 客户端的 A、PTR 和 DHCID 所有权。"
          type="info"
          show-icon
          :closable="false"
        />
        <div class="records-toolbar">
          <el-input
            v-model="search"
            placeholder="搜索名称或记录值"
            clearable
            class="record-search"
            aria-label="搜索记录"
            ><template #prefix><SearchIcon /></template></el-input
          ><el-select v-model="type" style="width: 128px" aria-label="筛选类型"
            ><el-option
              v-for="value in recordTypes"
              :key="value"
              :value="value"
              :label="value === 'ALL' ? '全部类型' : value" /></el-select
          ><el-checkbox v-model="system">系统记录</el-checkbox
          ><el-button class="probe-button" @click="openProbe()"
            ><ProbeIcon />主从对比</el-button
          >
        </div>
        <el-table
          :data="paginated"
          :row-key="(r: Rrset) => r.name + '|' + r.type"
          style="width: 100%"
        >
          <el-table-column label="记录名称" min-width="220"
            ><template #default="{ row }"
              ><div class="record-owner">
                <strong class="mono">{{ shortName(row.name) }}</strong
                ><el-tooltip v-if="!row.editable" :content="row.protection"
                  ><LockIcon class="muted"
                /></el-tooltip>
              </div>
              <div class="record-fqdn mono">{{ row.name }}</div></template
            ></el-table-column
          >
          <el-table-column label="类型" width="88"
            ><template #default="{ row }"
              ><el-tag>{{ row.type }}</el-tag></template
            ></el-table-column
          >
          <el-table-column label="TTL" width="80"
            ><template #default="{ row }"
              ><span class="mono ttl"
                >{{ row.ttl }}<small>s</small></span
              ></template
            ></el-table-column
          >
          <el-table-column label="记录值" min-width="220"
            ><template #default="{ row }"
              ><div
                v-for="value in row.values"
                :key="value"
                class="record-value mono"
              >
                {{ value }}
              </div></template
            ></el-table-column
          >
          <el-table-column label="操作" width="168" fixed="right"
            ><template #default="{ row }"
              ><div class="row-actions">
                <el-tooltip content="复制记录"
                  ><el-button
                    text
                    size="small"
                    aria-label="复制记录"
                    @click="copy(row)"
                    ><CopyIcon /></el-button></el-tooltip
                ><el-tooltip
                  :content="row.editable ? '编辑记录' : row.protection"
                  ><el-button
                    text
                    size="small"
                    :disabled="!row.editable || !admin"
                    aria-label="编辑记录"
                    @click="edit(row)"
                    ><EditIcon /></el-button></el-tooltip
                ><el-button
                  text
                  size="small"
                  type="danger"
                  :disabled="!row.editable || !admin"
                  aria-label="删除记录"
                  @click="remove(row)"
                  ><DeleteIcon
                /></el-button></div></template
          ></el-table-column>
          <template #empty
            ><el-empty description="没有匹配的记录"
              ><el-button v-if="search" @click="search = ''"
                >清除搜索</el-button
              ></el-empty
            ></template
          >
        </el-table>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[12, 24, 48]"
          layout="total, prev, pager, next, sizes"
          :total="filtered.length"
          class="record-pagination"
        />
        <div class="panel-footnote">
          <span class="status-dot"></span>变更写入主节点 · 从节点通过 BIND
          区域传送同步
        </div>
        <RecordEditor
          :open="editor"
          :zone="data.zone"
          :record="editing"
          @close="editor = false"
          @saved="
            load();
            emit('changed');
          "
        />
      </template>
    </section>
  </div>
  <el-drawer v-model="probeOpen" title="主从查询对比" size="580px"
    ><p class="muted">查询主节点与从节点的实际响应，核对记录和 SOA 序列号。</p>
    <div class="probe-form">
      <el-input
        v-model="probeName"
        placeholder="记录名称"
        aria-label="查询名称"
      /><el-select v-model="probeType" style="width: 105px"
        ><el-option
          v-for="value in [
            'A',
            'AAAA',
            'CNAME',
            'MX',
            'TXT',
            'PTR',
            'SRV',
            'CAA',
            'NS',
            'SOA',
          ]"
          :key="value"
          :value="value"
          :label="value" /></el-select
      ><el-button type="primary" :loading="probeBusy" @click="probe"
        >查询</el-button
      >
    </div>
    <div v-loading="probeBusy">
      <div v-for="r in probeResult" :key="r.server" class="probe-result">
        <div class="probe-result-head">
          <strong>{{ r.server }}</strong
          ><el-tag size="small" :type="r.reachable ? 'success' : 'danger'">{{
            r.reachable ? r.rcode : "不可达"
          }}</el-tag
          ><span>{{ r.elapsedMs }} ms</span>
        </div>
        <div class="muted">SOA {{ r.serial ?? "—" }}</div>
        <pre>{{ r.error || r.answers.join("\n") || "未返回此类型的记录" }}</pre>
      </div>
    </div>
    <el-alert
      v-if="
        probeResult.length > 1 &&
        probeResult.every((r) => r.serial != null) &&
        new Set(probeResult.map((r) => r.serial)).size > 1
      "
      type="warning"
      title="SOA 序列号暂不一致，请稍后重试并检查区域同步。"
      show-icon
      :closable="false"
  /></el-drawer>
</template>
