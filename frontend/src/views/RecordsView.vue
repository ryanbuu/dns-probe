<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { message, Modal } from "ant-design-vue";
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  LockOutlined,
  EditOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  CopyOutlined,
} from "@ant-design/icons-vue";
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
  editing = ref<Rrset>();
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
const recordTypes = computed(() =>
  ["ALL", ...new Set(data.value?.records.map((r) => r.type) || [])].map(
    (v) => ({ value: v, label: v === "ALL" ? "全部类型" : v }),
  ),
);
const columns = [
  { title: "记录名称", key: "name", width: "30%" },
  { title: "类型", key: "type", width: 90 },
  { title: "TTL", key: "ttl", width: 85 },
  { title: "记录值", key: "values" },
  { title: "操作", key: "actions", width: 168, fixed: "right" as const },
];
function edit(record?: Rrset) {
  editing.value = record;
  editor.value = true;
}
function remove(record: Rrset) {
  const zoneId = selected.value;
  Modal.confirm({
    title: "删除这组解析记录？",
    content: `${record.name} · ${record.type}，共 ${record.values.length} 个值。此操作会删除整个记录集，并同步到从节点。`,
    okText: "确认删除",
    okType: "danger",
    cancelText: "取消",
    async onOk() {
      try {
        await api(`/zones/${zoneId}/records/delete`, {
          method: "POST",
          body: JSON.stringify({
            name: record.name,
            type: record.type,
            expectedRevision: record.revision,
          }),
        });
        message.success("记录已删除");
        emit("changed");
        await load();
      } catch (e) {
        message.error((e as Error).message);
        throw e;
      }
    },
  });
}
async function copy(record: Rrset) {
  try {
    await navigator.clipboard.writeText(
      `${record.name} ${record.ttl} IN ${record.type} ${record.values.join("\n")}`,
    );
    message.success("记录已复制");
  } catch {
    message.info("当前浏览器不支持复制，请手动选择记录文本");
  }
}
async function probe() {
  probeBusy.value = true;
  try {
    probeResult.value = await api(
      `/zones/${selected.value}/probe?${new URLSearchParams({ name: probeName.value || data.value?.zone.name || "@", type: probeType.value })}`,
    );
  } catch (e) {
    message.error((e as Error).message);
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
    <a-button @click="load" :loading="loading"
      ><ReloadOutlined />刷新记录</a-button
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
        ><LockOutlined v-if="zone.readOnly" />
      </button>
      <div class="rail-note">
        <LockOutlined /><span
          >Kea 区域受保护，避免人工修改影响租约及 DNS 所有权。</span
        >
      </div>
    </aside>
    <section class="record-panel">
      <a-alert v-if="error" :message="error" type="error" show-icon />
      <div v-if="error" class="empty-retry">
        <a-button @click="load">重新读取区域</a-button>
      </div>
      <a-skeleton v-if="loading" active :paragraph="{ rows: 8 }" />
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
          <a-button
            type="primary"
            :disabled="data.zone.readOnly || !admin"
            @click="edit()"
            ><PlusOutlined />添加记录</a-button
          >
        </div>
        <a-alert
          v-if="data.zone.readOnly"
          class="readonly-alert"
          message="此区域由 Kea 自动维护"
          description="可以查询和对比记录；增删改已禁用，以保护 DHCP 客户端的 A、PTR 和 DHCID 所有权。"
          type="info"
          show-icon
        />
        <div class="records-toolbar">
          <a-input
            v-model:value="search"
            placeholder="搜索名称或记录值"
            allow-clear
            class="record-search"
            aria-label="搜索记录"
            ><template #prefix><SearchOutlined /></template></a-input
          ><a-select
            v-model:value="type"
            :options="recordTypes"
            style="width: 128px"
            aria-label="筛选类型"
          /><a-checkbox v-model:checked="system">系统记录</a-checkbox
          ><a-button class="probe-button" @click="openProbe()"
            ><ExperimentOutlined />主从对比</a-button
          >
        </div>
        <a-table
          :data-source="filtered"
          :columns="columns"
          :row-key="(r: Rrset) => r.name + '|' + r.type"
          :pagination="{
            pageSize: 12,
            showSizeChanger: true,
            showTotal: (total: number) => `共 ${total} 组记录`,
          }"
          :scroll="{ x: 780 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'"
              ><div class="record-owner">
                <strong class="mono">{{ shortName(record.name) }}</strong
                ><a-tooltip v-if="!record.editable" :title="record.protection"
                  ><LockOutlined class="muted"
                /></a-tooltip>
              </div>
              <div class="record-fqdn mono">{{ record.name }}</div></template
            >
            <template v-else-if="column.key === 'type'"
              ><a-tag>{{ record.type }}</a-tag></template
            >
            <template v-else-if="column.key === 'ttl'"
              ><span class="mono ttl"
                >{{ record.ttl }}<small>s</small></span
              ></template
            >
            <template v-else-if="column.key === 'values'"
              ><div
                v-for="value in record.values"
                :key="value"
                class="record-value mono"
              >
                {{ value }}
              </div></template
            >
            <template v-else-if="column.key === 'actions'"
              ><div class="row-actions">
                <a-tooltip title="复制记录"
                  ><a-button
                    type="text"
                    size="small"
                    aria-label="复制记录"
                    @click="copy(record)"
                    ><CopyOutlined /></a-button></a-tooltip
                ><a-tooltip
                  :title="record.editable ? '编辑记录' : record.protection"
                  ><a-button
                    type="text"
                    size="small"
                    :disabled="!record.editable || !admin"
                    aria-label="编辑记录"
                    @click="edit(record)"
                    ><EditOutlined /></a-button></a-tooltip
                ><a-button
                  type="text"
                  size="small"
                  danger
                  :disabled="!record.editable || !admin"
                  aria-label="删除记录"
                  @click="remove(record)"
                  ><DeleteOutlined
                /></a-button></div
            ></template>
          </template>
          <template #emptyText
            ><a-empty description="没有匹配的记录"
              ><a-button v-if="search" @click="search = ''"
                >清除搜索</a-button
              ></a-empty
            ></template
          >
        </a-table>
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
  <a-drawer
    :open="probeOpen"
    title="主从查询对比"
    :width="580"
    @close="probeOpen = false"
    ><p class="muted">查询主节点与从节点的实际响应，核对记录和 SOA 序列号。</p>
    <div class="probe-form">
      <a-input
        v-model:value="probeName"
        placeholder="记录名称"
        aria-label="查询名称"
      /><a-select
        v-model:value="probeType"
        :options="
          [
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
          ].map((value) => ({ value }))
        "
        style="width: 105px"
      /><a-button type="primary" :loading="probeBusy" @click="probe"
        >查询</a-button
      >
    </div>
    <a-spin :spinning="probeBusy"
      ><div v-for="r in probeResult" :key="r.server" class="probe-result">
        <div class="probe-result-head">
          <strong>{{ r.server }}</strong
          ><a-tag :color="r.reachable ? 'green' : 'red'">{{
            r.reachable ? r.rcode : "不可达"
          }}</a-tag
          ><span>{{ r.elapsedMs }} ms</span>
        </div>
        <div class="muted">SOA {{ r.serial ?? "—" }}</div>
        <pre>{{ r.error || r.answers.join("\n") || "未返回此类型的记录" }}</pre>
      </div></a-spin
    ><a-alert
      v-if="
        probeResult.length > 1 &&
        probeResult.every((r) => r.serial != null) &&
        new Set(probeResult.map((r) => r.serial)).size > 1
      "
      type="warning"
      message="SOA 序列号暂不一致，请稍后重试并检查区域同步。"
      show-icon
  /></a-drawer>
</template>
