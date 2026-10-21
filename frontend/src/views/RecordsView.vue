<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from "vue";
import { message, Modal } from "ant-design-vue";
import {
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  LockOutlined,
  DeleteOutlined,
  ExperimentOutlined,
  CopyOutlined,
  FilterOutlined,
  SettingOutlined,
  DownloadOutlined,
  DownOutlined,
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
    editor.value = false;
    editing.value = undefined;
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
const page = ref(1),
  pageSize = ref(12),
  editableOnly = ref(false);
const visibleColumns = ref(["values", "management", "ttl"]);
const referenceOpen = ref(true),
  referenceTab = ref("zone");
const media = window.matchMedia("(max-width: 760px)");
const mobile = ref(media.matches);
function resize(e: MediaQueryListEvent) {
  mobile.value = e.matches;
}
onMounted(() => media.addEventListener("change", resize));
onUnmounted(() => media.removeEventListener("change", resize));
watch(
  [search, type, system, selected, pageSize, editableOnly],
  () => (page.value = 1),
);
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
        (!editableOnly.value || (r.editable && props.admin)) &&
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
const columns = computed(() =>
  [
    { title: "名称", key: "name", width: 240 },
    { title: "类型", key: "type", width: 84 },
    { title: "内容", key: "values" },
    { title: "维护方式", key: "management", width: 140 },
    { title: "TTL", key: "ttl", width: 95 },
    { title: "操作", key: "actions", width: 150, fixed: "right" as const },
  ].filter(
    (c) =>
      ["name", "type", "actions"].includes(c.key) ||
      visibleColumns.value.includes(c.key),
  ),
);
const paginated = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
watch(filtered, (rows) => {
  if ((page.value - 1) * pageSize.value >= rows.length) page.value = 1;
});
function key(record: Rrset) {
  return record.name + "|" + record.type;
}
function management(record: Rrset) {
  return data.value?.zone.readOnly
    ? "Kea 自动维护"
    : record.editable
      ? "手动维护"
      : "系统保护";
}
function edit(record?: Rrset) {
  editing.value = record;
  editor.value = true;
  nextTick(() => {
    document
      .querySelector(".inline-editor")
      ?.scrollIntoView({ block: "nearest" });
    (
      document.querySelector(
        record ? ".inline-editor textarea" : ".inline-editor input",
      ) as HTMLElement
    )?.focus();
  });
}
function exportRecords() {
  if (!data.value) return;
  const text = [
    "; DNS Probe - filtered record export",
    "; Zone: " + data.value.zone.name,
    ...filtered.value.flatMap((r) =>
      r.values.map((value) => `${r.name} ${r.ttl} IN ${r.type} ${value}`),
    ),
    "",
  ].join("\n");
  const url = URL.createObjectURL(
    new Blob([text], { type: "text/plain;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = data.value.zone.name.replace(/\.$/, "") + "-records.zone";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
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
  <div class="dns-zone-bar">
    <div>
      <span class="zone-bar-label">DNS 区域</span
      ><a-select
        v-model:value="selected"
        :options="
          zones.map((z) => ({ value: z.id, label: z.name.replace(/\.$/, '') }))
        "
        aria-label="选择 DNS 区域"
        show-search
        option-filter-prop="label"
        class="zone-switcher"
      />
    </div>
    <span class="zone-count-label">{{ zones.length }} 个区域</span>
  </div>
  <div class="page-heading dns-heading">
    <div>
      <h1>DNS 记录</h1>
      <p>管理域名解析记录，控制服务发现与访问。</p>
    </div>
    <a-button :loading="loading" @click="load"
      ><ReloadOutlined />刷新记录</a-button
    >
  </div>
  <a-alert v-if="error" :message="error" type="error" show-icon />
  <div v-if="error" class="empty-retry">
    <a-button @click="load">重新读取区域</a-button>
  </div>
  <a-skeleton v-if="loading" active :paragraph="{ rows: 8 }" />
  <template v-if="data">
    <section class="dns-reference">
      <div class="reference-tabs">
        <button
          v-for="tab in [
            { id: 'zone', label: '区域信息' },
            { id: 'dns', label: '关于 DNS' },
            { id: 'ttl', label: 'TTL 与同步' },
          ]"
          :key="tab.id"
          :class="{ active: referenceTab === tab.id }"
          :aria-pressed="referenceTab === tab.id"
          @click="
            referenceTab = tab.id;
            referenceOpen = true;
          "
        >
          {{ tab.label }}</button
        ><a-button
          type="text"
          :aria-label="referenceOpen ? '收起区域说明' : '展开区域说明'"
          :aria-expanded="referenceOpen"
          @click="referenceOpen = !referenceOpen"
          ><DownOutlined :rotate="referenceOpen ? 180 : 0"
        /></a-button>
      </div>
      <div v-if="referenceOpen" class="reference-content">
        <template v-if="referenceTab === 'zone'"
          ><span class="status-dot"></span>
          <div>
            <h2>{{ data.zone.name.replace(/\.$/, "") }}</h2>
            <p>
              {{
                data.zone.readOnly
                  ? "此区域由 Kea 自动维护。"
                  : "在主节点维护记录，变更自动同步到从节点。"
              }}
            </p>
            <div class="zone-meta">
              <span
                >主节点 <b>{{ data.zone.primary }}</b></span
              ><span
                >从节点
                <b>{{ data.zone.secondaries.join(", ") || "未配置" }}</b></span
              ><span
                >SOA <b>{{ data.zone.serial }}</b></span
              >
            </div>
          </div>
          <a-tag>{{
            data.zone.readOnly ? "Kea 管理" : "手动维护"
          }}</a-tag></template
        ><template v-else-if="referenceTab === 'dns'"
          ><div>
            <strong>名称、类型与内容共同决定一条 DNS 记录。</strong>
            <p>
              A / AAAA 指向 IP，CNAME 指向其他域名，PTR 用于反向解析。系统记录和
              DHCID 所有权记录受保护。
            </p>
          </div></template
        ><template v-else
          ><div>
            <strong>TTL 决定解析结果可被缓存的时间，单位为秒。</strong>
            <p>
              保存后从节点自动同步。已有客户端缓存可能保留到 TTL
              结束，可通过“主从对比”核对服务器上的实际记录。
            </p>
          </div></template
        >
      </div>
    </section>
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
        placeholder="搜索 DNS 记录"
        allow-clear
        class="record-search"
        aria-label="搜索记录"
        ><template #prefix><SearchOutlined /></template
      ></a-input>
      <a-popover trigger="click" placement="bottomLeft"
        ><template #content
          ><div class="filter-popover">
            <label>记录类型</label
            ><a-select
              v-model:value="type"
              :options="recordTypes"
              aria-label="筛选类型"
              style="width: 100%"
            /><a-checkbox v-model:checked="system">显示系统记录</a-checkbox
            ><a-checkbox v-model:checked="editableOnly"
              >仅显示可编辑记录</a-checkbox
            >
          </div></template
        ><a-button
          :class="{ 'filter-active': type !== 'ALL' || system || editableOnly }"
          ><FilterOutlined />筛选</a-button
        ></a-popover
      >
      <a-popover trigger="click" placement="bottom"
        ><template #content
          ><div class="filter-popover">
            <strong>显示列</strong
            ><a-checkbox-group
              v-model:value="visibleColumns"
              :options="[
                { value: 'values', label: '内容' },
                { value: 'management', label: '维护方式' },
                { value: 'ttl', label: 'TTL' },
              ]"
            /></div></template
        ><a-button class="display-options" aria-label="显示选项"
          ><SettingOutlined />显示选项</a-button
        ></a-popover
      >
      <a-button aria-label="导出" @click="exportRecords"
        ><DownloadOutlined />导出</a-button
      ><a-button
        type="primary"
        :disabled="data.zone.readOnly || !admin"
        @click="edit()"
        ><PlusOutlined />添加记录</a-button
      >
    </div>
    <div
      v-if="type !== 'ALL' || system || editableOnly || search"
      class="filter-summary"
    >
      <span>{{ filtered.length }} 条匹配记录</span
      ><a-button
        type="link"
        size="small"
        @click="
          type = 'ALL';
          system = false;
          editableOnly = false;
          search = '';
        "
        >清除筛选</a-button
      >
    </div>
    <RecordEditor
      v-if="editor && !editing"
      :open="editor"
      :zone="data.zone"
      @close="editor = false"
      @saved="
        load();
        emit('changed');
      "
    />
    <section class="records-table-panel">
      <div class="table-caption">
        <span>{{ filtered.length }} 组 DNS 记录</span
        ><a-button type="link" @click="openProbe()"
          ><ExperimentOutlined />主从对比</a-button
        >
      </div>
      <a-table
        v-if="!mobile"
        :data-source="paginated"
        :columns="columns"
        :row-key="key"
        :pagination="false"
        :scroll="{ x: 1000 }"
        size="middle"
        :expanded-row-keys="editor && editing ? [key(editing)] : []"
        :show-expand-column="false"
      >
        <template #bodyCell="{ column, record }"
          ><template v-if="column.key === 'name'"
            ><div class="record-owner">
              <strong>{{ shortName(record.name) }}</strong
              ><a-tooltip v-if="!record.editable" :title="record.protection"
                ><LockOutlined class="muted"
              /></a-tooltip>
            </div>
            <div class="record-fqdn">{{ record.name }}</div></template
          ><template v-else-if="column.key === 'type'"
            ><span class="record-type">{{ record.type }}</span></template
          ><template v-else-if="column.key === 'values'"
            ><div
              v-for="value in record.values"
              :key="value"
              class="record-value"
            >
              {{ value }}
            </div></template
          ><template v-else-if="column.key === 'management'"
            ><span
              class="management-label"
              :class="{ managed: !record.editable }"
              ><LockOutlined v-if="!record.editable" />{{
                management(record)
              }}</span
            ></template
          ><template v-else-if="column.key === 'ttl'"
            ><span class="ttl"
              >{{ record.ttl }} <small>秒</small></span
            ></template
          ><template v-else-if="column.key === 'actions'"
            ><div class="row-actions">
              <a-button
                type="link"
                size="small"
                :disabled="!record.editable || !admin"
                aria-label="编辑记录"
                @click="edit(record)"
                >编辑</a-button
              ><a-button
                type="text"
                size="small"
                aria-label="复制记录"
                @click="copy(record)"
                ><CopyOutlined /></a-button
              ><a-button
                type="text"
                size="small"
                danger
                :disabled="!record.editable || !admin"
                aria-label="删除记录"
                @click="remove(record)"
                ><DeleteOutlined
              /></a-button></div></template
        ></template>
        <template #expandedRowRender="{ record }"
          ><RecordEditor
            :open="editor"
            :zone="data.zone"
            :record="record"
            @close="editor = false"
            @saved="
              load();
              emit('changed');
            "
        /></template>
        <template #emptyText><a-empty description="没有匹配的记录" /></template>
      </a-table>
      <div v-else class="mobile-records">
        <article
          v-for="record in paginated"
          :key="key(record)"
          class="mobile-record"
        >
          <div class="mobile-record-heading">
            <strong>{{ shortName(record.name) }}</strong
            ><a-tag>{{ record.type }}</a-tag>
          </div>
          <div class="record-fqdn">{{ record.name }}</div>
          <div
            v-if="visibleColumns.includes('values')"
            class="mobile-record-content"
          >
            <span>内容</span>
            <div
              v-for="value in record.values"
              :key="value"
              class="record-value"
            >
              {{ value }}
            </div>
          </div>
          <div class="mobile-record-meta">
            <span v-if="visibleColumns.includes('ttl')"
              >TTL {{ record.ttl }} 秒</span
            ><span v-if="visibleColumns.includes('management')">{{
              management(record)
            }}</span>
          </div>
          <div class="row-actions">
            <a-button
              type="link"
              size="small"
              :disabled="!record.editable || !admin"
              aria-label="编辑记录"
              @click="edit(record)"
              >编辑</a-button
            ><a-button
              type="text"
              size="small"
              aria-label="复制记录"
              @click="copy(record)"
              ><CopyOutlined /></a-button
            ><a-button
              type="text"
              size="small"
              danger
              :disabled="!record.editable || !admin"
              aria-label="删除记录"
              @click="remove(record)"
              ><DeleteOutlined
            /></a-button>
          </div>
          <RecordEditor
            v-if="editor && editing && key(editing) === key(record)"
            :open="editor"
            :zone="data.zone"
            :record="record"
            @close="editor = false"
            @saved="
              load();
              emit('changed');
            "
          />
        </article>
        <a-empty v-if="!paginated.length" description="没有匹配的记录" />
      </div>
      <div class="records-pagination">
        <span class="pagination-summary"
          >第 {{ filtered.length ? (page - 1) * pageSize + 1 : 0 }}–{{
            Math.min(page * pageSize, filtered.length)
          }}
          条，共 {{ filtered.length }} 条</span
        ><a-pagination
          v-model:current="page"
          v-model:page-size="pageSize"
          :total="filtered.length"
          :page-size-options="['12', '24', '48']"
          :show-size-changer="!mobile"
          :show-less-items="true"
          :simple="mobile"
        />
      </div>
    </section>
    <p class="panel-footnote">
      <LockOutlined />带锁记录由系统或 Kea
      维护，手动编辑已禁用。导出内容为当前筛选的记录。
    </p>
  </template>
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
