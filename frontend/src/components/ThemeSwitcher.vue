<script setup lang="ts">
import {
  BgColorsOutlined,
  DesktopOutlined,
  BulbOutlined,
} from "@ant-design/icons-vue";
import { themePreference, setThemePreference } from "../theme";
import type { ThemePreference } from "../theme";
function select({ key }: { key: string | number }) {
  setThemePreference(key as ThemePreference);
}
</script>
<template>
  <a-dropdown :trigger="['click']" placement="bottomRight">
    <a-button type="text" aria-label="切换主题" class="theme-switcher"
      ><BgColorsOutlined
    /></a-button>
    <template #overlay>
      <a-menu :selected-keys="[themePreference]" @click="select">
        <a-menu-item key="system"><DesktopOutlined />跟随系统</a-menu-item>
        <a-menu-item key="light"><BulbOutlined />浅色模式</a-menu-item>
        <a-menu-item key="dark"><BgColorsOutlined />深色模式</a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>
