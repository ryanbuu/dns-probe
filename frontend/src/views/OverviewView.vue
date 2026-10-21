<script setup lang="ts">
import { computed } from "vue";
import {
  ArrowRightOutlined,
  GlobalOutlined,
  DatabaseOutlined,
  LockOutlined,
  CheckCircleOutlined,
  WarningOutlined,
} from "@ant-design/icons-vue";
import type { Zone } from "../types";
const props = defineProps<{ zones: Zone[]; loading: boolean; demo: boolean }>();
const emit = defineEmits<{ select: [id: string] }>();
const total = computed(() =>
  props.zones.reduce((n, z) => n + (z.recordCount || 0), 0),
);
const healthy = computed(() => props.zones.filter((z) => !z.error).length);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>DNS 概览</h1>
      <p>查看 DNS 区域、记录数量与主从服务器状态。</p>
    </div>
    <span class="overview-date">{{
      new Intl.DateTimeFormat("zh-CN", { dateStyle: "long" }).format(new Date())
    }}</span>
  </div>
  <div class="stats-grid">
    <div class="stat-card">
      <span class="stat-icon blue"><GlobalOutlined /></span>
      <div>
        <span class="stat-label">管理区域</span
        ><strong>{{ zones.length }}<small>zones</small></strong>
      </div>
    </div>
    <div class="stat-card">
      <span class="stat-icon violet"><DatabaseOutlined /></span>
      <div>
        <span class="stat-label">解析记录集</span
        ><strong>{{ total }}<small>RRsets</small></strong>
      </div>
    </div>
    <div class="stat-card">
      <span class="stat-icon green"><CheckCircleOutlined /></span>
      <div>
        <span class="stat-label">区域读取正常</span
        ><strong
          >{{ healthy }}<small>/ {{ zones.length }}</small></strong
        >
      </div>
    </div>
    <div class="stat-card">
      <span class="stat-icon amber"><LockOutlined /></span>
      <div>
        <span class="stat-label">自动维护区域</span
        ><strong
          >{{ zones.filter((z) => z.readOnly).length
          }}<small>protected</small></strong
        >
      </div>
    </div>
  </div>
  <div class="overview-columns">
    <section class="surface zone-overview">
      <div class="section-heading">
        <div>
          <h2>DNS 区域</h2>
          <p>固定服务手动维护，客户端记录交由 Kea。</p>
        </div>
        <span class="subtle-pill">{{ zones.length }} 个区域</span>
      </div>
      <a-skeleton v-if="loading" active /><button
        v-for="zone in zones"
        :key="zone.id"
        class="overview-zone"
        @click="emit('select', zone.id)"
      >
        <span class="zone-symbol" :class="{ protected: zone.readOnly }"
          ><LockOutlined v-if="zone.readOnly" /><GlobalOutlined v-else /></span
        ><span class="overview-zone-info"
          ><strong>{{ zone.name.replace(/\.$/, "") }}</strong
          ><span>{{ zone.description }}</span></span
        ><a-tag v-if="zone.error" color="red">读取失败</a-tag
        ><span v-else class="zone-count"
          >{{ zone.recordCount }} <small>记录集</small></span
        ><ArrowRightOutlined />
      </button>
    </section>
    <aside class="overview-aside">
      <div class="topology-card">
        <span class="inline-label">DNS TOPOLOGY</span>
        <h2>主从服务器</h2>
        <div class="topology-node app-node">
          <img src="/favicon.svg" alt="" />
          <div><strong>DNS Probe</strong><span>记录管理与变更审计</span></div>
        </div>
        <div class="topology-line"><span>RFC2136 · TSIG</span></div>
        <div class="topology-node">
          <span class="node-letter">P</span>
          <div>
            <strong>主节点</strong
            ><span class="mono">{{ zones[0]?.primary || "未配置" }}</span>
          </div>
          <span class="subtle-pill">写入</span>
        </div>
        <div class="topology-line"><span>BIND 区域同步</span></div>
        <div class="topology-node">
          <span class="node-letter secondary">S</span>
          <div>
            <strong>从节点</strong
            ><span class="mono">{{
              zones[0]?.secondaries.join(", ") || "未配置"
            }}</span>
          </div>
          <span class="subtle-pill">读取</span>
        </div>
      </div>
      <div class="safety-note">
        <LockOutlined />
        <div>
          <h3>Kea DDNS 保护</h3>
          <p>客户端区域、DNS 系统记录和带 DHCID 所有权的名称均有修改保护。</p>
        </div>
      </div>
      <div v-if="demo" class="demo-small">
        <WarningOutlined />当前为独立演示数据，操作不会写入内网 DNS。
      </div>
    </aside>
  </div>
</template>
