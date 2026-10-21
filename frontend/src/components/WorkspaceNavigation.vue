<script setup lang="ts">
import { computed, ref } from "vue";
import {
  AppstoreOutlined,
  AuditOutlined,
  CloseOutlined,
  ClusterOutlined,
  GlobalOutlined,
  LogoutOutlined,
} from "@ant-design/icons-vue";
import { routes } from "../navigation";
import type { User } from "../types";
import BrandIdentity from "./BrandIdentity.vue";

const props = defineProps<{ user: User; page: string; mobile?: boolean }>();
const emit = defineEmits<{
  navigate: [id: string];
  close: [];
  signOut: [];
}>();
const search = ref("");
const openKeys = ref(["dns", "dhcp"]);
const menu = computed(() =>
  routes.filter(
    (r) =>
      (r.id !== "audit" || props.user.admin) && r.title.includes(search.value),
  ),
);
</script>

<template>
  <div class="sidebar-layout">
    <div class="sidebar-top">
      <a
        class="brand"
        href="#/overview"
        @click.prevent="emit('navigate', 'overview')"
      >
        <BrandIdentity />
      </a>
      <a-button
        v-if="mobile"
        type="text"
        aria-label="关闭菜单"
        @click="emit('close')"
      >
        <CloseOutlined />
      </a-button>
    </div>
    <div class="nav-search">
      <a-input
        v-model:value="search"
        placeholder="搜索功能"
        aria-label="搜索导航"
        allow-clear
      />
    </div>
    <a-menu mode="inline" :selected-keys="[page]" v-model:openKeys="openKeys">
      <a-menu-item
        v-if="menu.some((m) => m.id === 'overview')"
        key="overview"
        @click="emit('navigate', 'overview')"
      >
        <template #icon><AppstoreOutlined /></template>概览
      </a-menu-item>
      <a-sub-menu v-if="menu.some((m) => m.group === 'DNS')" key="dns">
        <template #icon><GlobalOutlined /></template
        ><template #title>DNS</template>
        <a-menu-item
          v-for="item in menu.filter((m) => m.group === 'DNS')"
          :key="item.id"
          @click="emit('navigate', item.id)"
        >
          {{ item.title }}
        </a-menu-item>
      </a-sub-menu>
      <a-sub-menu v-if="menu.some((m) => m.group === 'DHCP')" key="dhcp">
        <template #icon><ClusterOutlined /></template
        ><template #title>DHCP</template>
        <a-menu-item
          v-for="item in menu.filter((m) => m.group === 'DHCP')"
          :key="item.id"
          @click="emit('navigate', item.id)"
        >
          {{ item.title }}
        </a-menu-item>
      </a-sub-menu>
      <a-menu-item
        v-if="menu.some((m) => m.id === 'audit')"
        key="audit"
        @click="emit('navigate', 'audit')"
      >
        <template #icon><AuditOutlined /></template>操作审计
      </a-menu-item>
    </a-menu>
    <div class="sidebar-user">
      <a-avatar size="small">{{
        user.username.slice(0, 1).toUpperCase()
      }}</a-avatar>
      <div>
        <strong>{{ user.username }}</strong
        ><span>{{ user.admin ? "管理员" : "只读用户" }}</span>
      </div>
      <a-button type="text" aria-label="退出登录" @click="emit('signOut')"
        ><LogoutOutlined
      /></a-button>
    </div>
  </div>
</template>
