<script setup lang="ts">
import { App as AntdApp, ConfigProvider } from "ant-design-vue";
import zhCN from "ant-design-vue/es/locale/zh_CN";
import { themeConfig } from "./theme";
import ThemeTokens from "./components/ThemeTokens.vue";
import Workspace from "./Workspace.vue";
</script>
<template>
  <ConfigProvider :locale="zhCN" :theme="themeConfig">
    <AntdApp>
      <ThemeTokens />
      <Workspace />
    </AntdApp>
  </ConfigProvider>
</template>
