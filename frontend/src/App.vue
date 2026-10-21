<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import { message } from "ant-design-vue";
import {
  AppstoreOutlined,
  GlobalOutlined,
  AuditOutlined,
  LogoutOutlined,
  ArrowRightOutlined,
  LockOutlined,
  MenuOutlined,
  CloseOutlined,
  ApiOutlined,
} from "@ant-design/icons-vue";
import { api, csrf, login, logout } from "./api";
import type { Meta, User, Zone } from "./types";
import OverviewView from "./views/OverviewView.vue";
import RecordsView from "./views/RecordsView.vue";
import AuditView from "./views/AuditView.vue";
const meta = ref<Meta>(),
  user = ref<User>(),
  initializing = ref(true),
  username = ref(""),
  password = ref(""),
  loggingIn = ref(false),
  loginError = ref(""),
  zones = ref<Zone[]>([]),
  loadingZones = ref(false),
  zoneError = ref("");
const page = ref("overview"),
  selectedZone = ref(""),
  mobileMenu = ref(false);
const menu = computed(() => [
  { id: "overview", title: "概览", en: "Overview", icon: AppstoreOutlined },
  { id: "records", title: "解析记录", en: "DNS records", icon: GlobalOutlined },
  ...(user.value?.admin
    ? [{ id: "audit", title: "操作审计", en: "Audit log", icon: AuditOutlined }]
    : []),
]);
const activeTitle = computed(
  () => menu.value.find((m) => m.id === page.value)?.title || "概览",
);
async function loadZones() {
  loadingZones.value = true;
  zoneError.value = "";
  try {
    zones.value = await api("/zones");
  } catch (e) {
    zoneError.value = (e as Error).message;
  } finally {
    loadingZones.value = false;
  }
}
async function start() {
  try {
    [meta.value] = await Promise.all([api<Meta>("/meta"), csrf()]);
    try {
      user.value = await api("/session");
      await loadZones();
    } catch {
      user.value = undefined;
    }
  } catch {
    loginError.value = "无法连接后端，请确认服务已启动";
  } finally {
    initializing.value = false;
  }
}
async function signIn() {
  if (!username.value || !password.value) {
    loginError.value = "请输入用户名和密码";
    return;
  }
  loggingIn.value = true;
  loginError.value = "";
  try {
    await login(username.value, password.value);
    user.value = await api("/session");
    password.value = "";
    await loadZones();
  } catch (e) {
    loginError.value = (e as Error).message;
  } finally {
    loggingIn.value = false;
  }
}
async function signOut() {
  try {
    await logout();
    user.value = undefined;
    zones.value = [];
    page.value = "overview";
  } catch (e) {
    message.error((e as Error).message);
  }
}
function selectZone(id: string) {
  selectedZone.value = id;
  page.value = "records";
}
function expired() {
  if (user.value) {
    user.value = undefined;
    message.warning("会话已过期，请重新登录");
  }
}
onMounted(() => {
  window.addEventListener("session-expired", expired);
  void start();
});
onUnmounted(() => window.removeEventListener("session-expired", expired));
</script>
<template>
  <a-config-provider :locale="zhCN">
    <div v-if="initializing" class="boot-screen">
      <a-spin size="large" />
      <p>正在连接 DNS Probe…</p>
    </div>
    <div v-else-if="!user" class="login-layout">
      <section class="login-story">
        <a class="brand" href="/"
          ><img src="/favicon.svg" alt="" /><span
            >DNS Probe<small>NETWORK WORKSPACE</small></span
          ></a
        >
        <div class="login-copy">
          <div class="eyebrow">A LITTLE MORE CLARITY.</div>
          <h1>让内部网络的<br />每个名字，<br /><em>都有着落。</em></h1>
          <p>区域、记录与变更历史，<br />在一个清晰的工作空间里。</p>
          <div class="login-flow">
            <span><GlobalOutlined />BIND DNS</span><i></i
            ><span><LockOutlined />Kea DDNS</span>
          </div>
        </div>
        <div class="login-story-footer">
          Built for your network. Made to stay simple.
        </div>
      </section>
      <section class="login-panel">
        <div class="login-form-wrap">
          <span class="subtle-pill">DNS MANAGEMENT</span>
          <h2>欢迎回来</h2>
          <p class="muted">登录以管理 DNS 区域与解析记录。</p>
          <a-alert
            v-if="loginError"
            :message="loginError"
            type="error"
            show-icon
            style="margin-bottom: 20px"
          /><a-form
            layout="vertical"
            :model="{ username, password }"
            @finish="signIn"
            ><a-form-item label="用户名"
              ><a-input
                v-model:value="username"
                size="large"
                autocomplete="username"
                placeholder="输入用户名"
                aria-label="用户名" /></a-form-item
            ><a-form-item label="密码"
              ><a-input-password
                v-model:value="password"
                size="large"
                autocomplete="current-password"
                placeholder="输入密码"
                aria-label="密码" /></a-form-item
            ><a-button
              type="primary"
              size="large"
              html-type="submit"
              block
              :loading="loggingIn"
              >登录工作空间 <ArrowRightOutlined /></a-button
          ></a-form>
          <div v-if="meta?.demo" class="login-demo">
            <strong>演示模式</strong
            ><span>使用独立示例数据，不会连接现网 DNS。</span
            ><a-button
              type="link"
              size="small"
              @click="
                username = 'admin';
                password = 'dns-probe-demo';
              "
              >填入演示账户</a-button
            >
          </div>
          <p class="login-bottom">
            DNS Probe <span>v{{ meta?.version || "0.1.0" }}</span>
          </p>
        </div>
      </section>
    </div>
    <div v-else class="app-shell">
      <div
        v-if="mobileMenu"
        class="sidebar-backdrop"
        @click="mobileMenu = false"
      ></div>
      <aside class="sidebar" :class="{ visible: mobileMenu }">
        <div class="sidebar-top">
          <a class="brand" href="#" @click.prevent="page = 'overview'"
            ><img src="/favicon.svg" alt="" /><span
              >DNS Probe<small>NETWORK WORKSPACE</small></span
            ></a
          ><button
            class="mobile-close"
            aria-label="关闭菜单"
            @click="mobileMenu = false"
          >
            <CloseOutlined />
          </button>
        </div>
        <div class="workspace-chip">
          <span class="workspace-avatar">R</span
          ><span
            ><strong>内部网络</strong
            ><small>{{
              meta?.demo ? "演示工作空间" : "DNS 工作空间"
            }}</small></span
          ><span class="status-dot"></span>
        </div>
        <div class="nav-label">工作空间</div>
        <a-menu mode="inline" :selected-keys="[page]">
          <a-menu-item
            v-for="item in menu"
            :key="item.id"
            @click="
              page = item.id;
              mobileMenu = false;
            "
          >
            <template #icon><component :is="item.icon" /></template
            >{{ item.title }}
          </a-menu-item>
        </a-menu>
        <div class="sidebar-spacer"></div>
        <div class="sidebar-protocol">
          <ApiOutlined />
          <div>
            <strong>BIND 原生协议</strong><span>AXFR · RFC2136 · TSIG</span>
          </div>
        </div>
        <div class="sidebar-user">
          <a-avatar>{{ user.username.slice(0, 1).toUpperCase() }}</a-avatar>
          <div>
            <strong>{{ user.username }}</strong
            ><span>{{ user.admin ? "管理员" : "只读用户" }}</span>
          </div>
          <a-button type="text" aria-label="退出登录" @click="signOut"
            ><LogoutOutlined
          /></a-button>
        </div>
      </aside>
      <main class="main-content">
        <header class="topbar">
          <div>
            <a-button
              class="mobile-toggle"
              type="text"
              aria-label="打开菜单"
              @click="mobileMenu = true"
              ><MenuOutlined /></a-button
            ><span class="breadcrumb"
              >工作空间 <span>/</span> <b>{{ activeTitle }}</b></span
            >
          </div>
          <div class="topbar-right">
            <span class="mode-badge" :class="{ demo: meta?.demo }"
              ><span class="status-dot"></span
              >{{ meta?.demo ? "演示环境" : "生产连接" }}</span
            ><span class="version-label">v{{ meta?.version }}</span>
          </div>
        </header>
        <div class="page-content">
          <div v-if="meta?.demo" class="demo-banner">
            <span><span class="demo-dot"></span>演示工作空间</span>
            <p>这里的变更仅影响示例数据，不会写入现网 DNS。</p>
            <span class="demo-banner-end">SANDBOX</span>
          </div>
          <a-alert
            v-if="zoneError"
            :message="zoneError"
            type="error"
            show-icon
          /><OverviewView
            v-if="page === 'overview'"
            :zones="zones"
            :loading="loadingZones"
            :demo="!!meta?.demo"
            @select="selectZone"
          /><RecordsView
            v-else-if="page === 'records'"
            :zones="zones"
            :initial-zone="selectedZone"
            :admin="user.admin"
            @changed="loadZones"
          /><AuditView v-else-if="page === 'audit' && user.admin" />
          <footer class="page-footer">
            <span>DNS Probe · 内网 DNS 管理</span
            ><span>记录清晰，变更可追溯。</span>
          </footer>
        </div>
      </main>
    </div>
  </a-config-provider>
</template>
