<script setup lang="ts">
import { watchEffect } from "vue";
import { theme } from "ant-design-vue";
const { token } = theme.useToken();
watchEffect(() => {
  const t = token.value;
  const colors = {
    "--page-bg": t.colorBgLayout,
    "--surface": t.colorBgContainer,
    "--surface-subtle": t.colorFillQuaternary,
    "--surface-inset": t.colorFillTertiary,
    "--border": t.colorBorderSecondary,
    "--border-strong": t.colorBorder,
    "--text": t.colorText,
    "--text-secondary": t.colorTextSecondary,
    "--text-muted": t.colorTextTertiary,
    "--text-faint": t.colorTextQuaternary,
    "--primary": t.colorPrimary,
    "--link": t.colorLink,
    "--success": t.colorSuccess,
    "--warning": t.colorWarning,
    "--diff-before": t.colorErrorBg,
    "--diff-after": t.colorSuccessBg,
    "--overlay": t.colorBgMask,
  };
  for (const [key, value] of Object.entries(colors))
    document.documentElement.style.setProperty(key, value);
});
</script>
<template></template>
