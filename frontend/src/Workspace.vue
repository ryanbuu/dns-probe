<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { App as AntdApp } from "ant-design-vue";
import {
  AppstoreOutlined,
  GlobalOutlined,
  AuditOutlined,
  ClusterOutlined,
  TeamOutlined,
  DatabaseOutlined,
  SafetyCertificateOutlined,
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
import DhcpView from "./views/DhcpView.vue";
import ThemeSwitcher from "./components/ThemeSwitcher.vue";
import HelpHint from "./components/HelpHint.vue";
const { message } = AntdApp.useApp();
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
const routes = [
  { id: "overview", title: "概览", icon: AppstoreOutlined, group: "工作空间" },
  { id: "records", title: "解析记录", icon: GlobalOutlined, group: "DNS" },
  {
    id: "dhcp-subnets",
    title: "子网与地址池",
    icon: ClusterOutlined,
    group: "DHCP",
  },
  {
    id: "dhcp-reservations",
    title: "保留地址",
    icon: TeamOutlined,
    group: "DHCP",
  },
  { id: "dhcp-leases", title: "租约", icon: DatabaseOutlined, group: "DHCP" },
  {
    id: "dhcp-ha",
    title: "高可用状态",
    icon: SafetyCertificateOutlined,
    group: "DHCP",
  },
  { id: "audit", title: "操作审计", icon: AuditOutlined, group: "管理" },
];
const navSearch = ref(""),
  openKeys = ref(["dns", "dhcp"]);
const menu = computed(() =>
  routes.filter(
    (r) =>
      (r.id !== "audit" || user.value?.admin) &&
      r.title.includes(navSearch.value),
  ),
);
const activeTitle = computed(
  () => routes.find((r) => r.id === page.value)?.title || "概览",
);
const activeGroup = computed(
  () => routes.find((r) => r.id === page.value)?.group || "工作空间",
);
function navigate(id: string, zone?: string) {
  page.value = id;
  mobileMenu.value = false;
  if (zone) selectedZone.value = zone;
  const h =
    "#/" +
    id +
    (id === "records" && selectedZone.value
      ? "?zone=" + encodeURIComponent(selectedZone.value)
      : "");
  if (location.hash !== h) location.hash = h;
}
function readRoute() {
  const [route, query] = (
    location.hash.replace(/^#\//, "") || "overview"
  ).split("?");
  if (routes.some((r) => r.id === route)) {
    page.value = route!;
    const z = new URLSearchParams(query).get("zone");
    if (z) selectedZone.value = z;
  } else page.value = "overview";
  mobileMenu.value = false;
}
function section() {
  return page.value.slice(5) as "subnets" | "leases" | "reservations" | "ha";
}
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
    navigate("overview");
  } catch (e) {
    message.error((e as Error).message);
  }
}
function selectZone(id: string) {
  selectedZone.value = id;
  navigate("records", id);
}
function expired() {
  if (user.value) {
    user.value = undefined;
    message.warning("会话已过期，请重新登录");
  }
}
onMounted(() => {
  readRoute();
  window.addEventListener("hashchange", readRoute);
  window.addEventListener("session-expired", expired);
  void start();
});
onUnmounted(() => {
  window.removeEventListener("session-expired", expired);
  window.removeEventListener("hashchange", readRoute);
});
</script>
<template>
  <div v-if="initializing" class="boot-screen">
    <a-spin size="large" />
    <p>正在连接 InfraPilot…</p>
  </div>
  <div v-else-if="!user" class="login-layout">
    <ThemeSwitcher class="login-theme" />
    <a class="brand login-brand" href="/"
      ><img src="/favicon.svg" alt="" /><strong>InfraPilot</strong></a
    >
    <section class="login-panel">
      <div class="login-form-wrap">
        <div class="heading-with-help">
          <h1>欢迎回来</h1>
          <HelpHint label="登录说明" text="登录以管理 DNS、DHCP 和网络配置。" />
        </div>
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
          <strong>演示模式</strong
          ><HelpHint
            label="演示模式说明"
            text="使用独立示例数据，不会连接现网 DNS。"
          />
          <a-button
            type="link"
            @click="
              username = 'admin';
              password = 'infrapilot-demo';
            "
            >填入演示账户</a-button
          >
        </div>
      </div>
    </section>
    <p class="login-bottom">InfraPilot · v{{ meta?.version || "0.1.0" }}</p>
  </div>
  <div v-else class="app-shell">
    <div
      v-if="mobileMenu"
      class="sidebar-backdrop"
      @click="mobileMenu = false"
    ></div>
    <aside class="sidebar" :class="{ visible: mobileMenu }">
      <div class="sidebar-top">
        <a class="brand" href="#/overview" @click.prevent="navigate('overview')"
          ><img src="/favicon.svg" alt="" /><strong>InfraPilot</strong></a
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
      <div class="nav-search">
        <a-input
          v-model:value="navSearch"
          placeholder="搜索功能"
          aria-label="搜索导航"
          allow-clear
        />
      </div>
      <a-menu mode="inline" :selected-keys="[page]" v-model:openKeys="openKeys">
        <a-menu-item
          v-if="menu.some((m) => m.id === 'overview')"
          key="overview"
          @click="navigate('overview')"
          ><template #icon><AppstoreOutlined /></template>概览</a-menu-item
        >
        <a-sub-menu v-if="menu.some((m) => m.group === 'DNS')" key="dns"
          ><template #icon><GlobalOutlined /></template
          ><template #title>DNS</template
          ><a-menu-item
            v-for="item in menu.filter((m) => m.group === 'DNS')"
            :key="item.id"
            @click="navigate(item.id)"
            >{{ item.title }}</a-menu-item
          ></a-sub-menu
        >
        <a-sub-menu v-if="menu.some((m) => m.group === 'DHCP')" key="dhcp"
          ><template #icon><ClusterOutlined /></template
          ><template #title>DHCP</template
          ><a-menu-item
            v-for="item in menu.filter((m) => m.group === 'DHCP')"
            :key="item.id"
            @click="navigate(item.id)"
            >{{ item.title }}</a-menu-item
          ></a-sub-menu
        >
        <a-menu-item
          v-if="menu.some((m) => m.id === 'audit')"
          key="audit"
          @click="navigate('audit')"
          ><template #icon><AuditOutlined /></template>操作审计</a-menu-item
        >
      </a-menu>
      <div class="sidebar-spacer"></div>
      <div class="sidebar-protocol">
        <LockOutlined /><span>DNS · DHCP · 高可用</span>
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
            >{{ activeGroup }} <span>/</span> <b>{{ activeTitle }}</b></span
          >
        </div>
        <div class="topbar-right">
          <ThemeSwitcher />
          <span class="connection-status"
            ><span class="status-dot" :class="{ demo: meta?.demo }"></span
            >{{ meta?.demo ? "演示环境" : "生产连接" }}</span
          ><HelpHint
            v-if="meta?.demo"
            label="演示环境说明"
            text="这里的变更仅影响示例数据，不会写入现网 DNS。"
          /><span class="version-label">v{{ meta?.version }}</span>
        </div>
      </header>
      <div class="page-content">
        <a-alert v-if="zoneError" :message="zoneError" type="error" show-icon />
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
        <DhcpView
          v-else-if="page.startsWith('dhcp-')"
          :key="page"
          :section="section()"
          :admin="user.admin"
        /><AuditView v-else-if="page === 'audit' && user.admin" />
        <footer class="page-footer">
          <span>InfraPilot</span><span>v{{ meta?.version }}</span>
        </footer>
      </div>
    </main>
  </div>
</template>
