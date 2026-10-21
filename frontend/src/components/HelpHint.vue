<script setup lang="ts">
import { ref } from "vue";
import { QuestionCircleOutlined } from "@ant-design/icons-vue";
defineProps<{ text: string; label: string }>();
const open = ref(false);
</script>
<template>
  <a-tooltip
    v-model:open="open"
    :title="text"
    :trigger="['hover', 'focus', 'click']"
    :mouse-enter-delay="0.2"
    :overlay-style="{ maxWidth: 'min(320px, calc(100vw - 32px))' }"
    destroy-tooltip-on-hide
  >
    <button
      type="button"
      class="help-hint"
      :aria-label="label"
      :aria-expanded="open"
      @click.stop
      @keydown.esc.stop.prevent="open = false"
    >
      <QuestionCircleOutlined aria-hidden="true" />
    </button>
  </a-tooltip>
</template>
