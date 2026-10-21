<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import { message } from "ant-design-vue";
import {
  AppstoreOutlined,
  GlobalOutlined,
  AuditOutlined,
  LogoutOutlined,
  LockOutlined,
  MenuOutlined,
  CloseOutlined,
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
      <a class="brand login-brand" href="/"
        ><img src="/favicon.svg" alt="" /><strong>DNS Probe</strong></a
      >
      <section class="login-panel">
        <div class="login-form-wrap">
          <h1>欢迎回来</h1>
          <p class="muted">登录 DNS 管理控制台。</p>
          <a-alert
            v-if="loginError"
            :message="loginError"
            type="error"
            show-icon
          />
          <a-form
            layout="vertical"
            :model="{ username, password }"
            @finish="signIn"
          >
            <a-form-item label="用户名"
              ><a-input
                v-model:value="username"
                size="large"
                autocomplete="username"
                placeholder="输入用户名"
                aria-label="用户名"
            /></a-form-item>
            <a-form-item label="密码"
              ><a-input-password
                v-model:value="password"
                size="large"
                autocomplete="current-password"
                placeholder="输入密码"
                aria-label="密码"
            /></a-form-item>
            <a-button
              type="primary"
              size="large"
              html-type="submit"
              block
              :loading="loggingIn"
              >登录工作空间</a-button
            >
          </a-form>
          <div v-if="meta?.demo" class="login-demo">
            <strong>演示模式</strong>
            <p>使用独立示例数据，不会连接现网 DNS。</p>
            <a-button
              type="link"
              @click="
                username = 'admin';
                password = 'dns-probe-demo';
              "
              >填入演示账户</a-button
            >
          </div>
        </div>
      </section>
      <p class="login-bottom">DNS Probe · v{{ meta?.version || "0.1.0" }}</p>
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
            ><img src="/favicon.svg" alt="" /><strong>DNS Probe</strong></a
          ><a-button
            class="mobile-close"
            type="text"
            aria-label="关闭菜单"
            @click="mobileMenu = false"
            ><CloseOutlined
          /></a-button>
        </div>
        <div class="workspace-chip">
          <a-avatar size="small">{{
            user.username.slice(0, 1).toUpperCase()
          }}</a-avatar>
          <div><strong>我的工作空间</strong><small>内部 DNS 管理</small></div>
        </div>
        <div class="nav-label">工作空间</div>
        <a-menu mode="inline" :selected-keys="[page]"
          ><a-menu-item
            v-for="item in menu"
            :key="item.id"
            @click="
              page = item.id;
              mobileMenu = false;
            "
            ><template #icon><component :is="item.icon" /></template
            >{{ item.title }}</a-menu-item
          ></a-menu
        >
        <div class="sidebar-spacer"></div>
        <div class="sidebar-protocol">
          <LockOutlined /><span>专用 TSIG · Kea 区域保护</span>
        </div>
        <div class="sidebar-user">
          <a-avatar size="small">{{
            user.username.slice(0, 1).toUpperCase()
          }}</a-avatar>
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
          <div class="topbar-left">
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
            <span class="connection-status"
              ><span class="status-dot" :class="{ demo: meta?.demo }"></span
              >{{ meta?.demo ? "演示环境" : "生产连接" }}</span
            ><span class="version-label">v{{ meta?.version }}</span>
          </div>
        </header>
        <div class="page-content">
          <a-alert
            v-if="meta?.demo"
            class="demo-banner"
            message="演示环境"
            description="这里的变更仅影响示例数据，不会写入现网 DNS。"
            type="warning"
            show-icon
          />
          <a-alert
            v-if="zoneError"
            :message="zoneError"
            type="error"
            show-icon
          />
          <OverviewView
            v-if="page === 'overview'"
            :zones="zones"
            :loading="loadingZones"
            :demo="!!meta?.demo"
            @select="selectZone"
          />
          <RecordsView
            v-else-if="page === 'records'"
            :zones="zones"
            :initial-zone="selectedZone"
            :admin="user.admin"
            @changed="loadZones"
          />
          <AuditView v-else-if="page === 'audit' && user.admin" />
          <footer class="page-footer">
            <span>DNS Probe</span><span>BIND DNS 管理控制台</span>
          </footer>
        </div>
      </main>
    </div>
  </a-config-provider>
</template>
