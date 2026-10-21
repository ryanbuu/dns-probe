<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { message } from "ant-design-vue";
import { api } from "../api";
import type { Rrset, Zone } from "../types";
const props = defineProps<{ open: boolean; zone: Zone; record?: Rrset }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const form = reactive({ name: "", type: "A", ttl: 300, values: "" });
const busy = ref(false),
  review = ref(false);
const types = ["A", "AAAA", "CNAME", "MX", "TXT", "PTR", "SRV", "CAA"];
const hints: Record<string, string> = {
  A: "例如：192.0.2.100",
  AAAA: "例如：2001:db8::10",
  CNAME: "例如：nas.intra.example.com.",
  MX: "优先级与邮件服务器，例如：10 mail.example.com.",
  TXT: '文本请用双引号包裹，例如："v=spf1 mx -all"',
  PTR: "目标完整域名，例如：nas.intra.example.com.",
  SRV: "优先级 权重 端口 目标，例如：10 5 443 service.example.com.",
  CAA: '例如：0 issue "letsencrypt.org"',
};
watch(
  () => props.open,
  (open) => {
    if (open) {
      Object.assign(form, {
        name: props.record?.name || "",
        type: props.record?.type || "A",
        ttl: props.record?.ttl ?? 300,
        values: props.record?.values.join("\n") || "",
      });
      review.value = false;
    }
  },
  { immediate: true },
);
const values = computed(() =>
  form.values
    .split("\n")
    .map((v) => v.trim())
    .filter(Boolean),
);
function next() {
  if (
    !form.name.trim() ||
    !values.value.length ||
    form.ttl === null ||
    form.ttl < 0
  ) {
    message.warning("请填写记录名称、TTL 和至少一个记录值");
    return;
  }
  review.value = true;
}
async function save() {
  busy.value = true;
  try {
    await api(`/zones/${props.zone.id}/records`, {
      method: props.record ? "PUT" : "POST",
      body: JSON.stringify({
        ...form,
        name: form.name.trim(),
        values: values.value,
        expectedRevision: props.record?.revision,
      }),
    });
    message.success("记录已保存，BIND 将自动同步到从节点");
    emit("saved");
    emit("close");
  } catch (e) {
    message.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section
    v-if="open"
    class="inline-editor"
    :aria-label="record ? '编辑解析记录' : '添加解析记录'"
  >
    <div class="inline-editor-heading">
      <h3>{{ record ? "编辑解析记录" : "添加解析记录" }}</h3>
      <a-button
        type="text"
        :disabled="busy"
        aria-label="关闭记录编辑"
        @click="emit('close')"
        >取消</a-button
      >
    </div>
    <a-form v-if="!review" layout="vertical" class="record-form">
      <div class="editor-fields">
        <a-form-item label="类型"
          ><a-select
            v-model:value="form.type"
            :disabled="!!record"
            :options="types.map((value) => ({ value }))"
            aria-label="记录类型"
        /></a-form-item>
        <a-form-item label="记录名称" required
          ><a-input
            v-model:value="form.name"
            :disabled="!!record"
            placeholder="例如 www，或使用 @ 表示根域名"
            aria-label="记录名称"
        /></a-form-item>
        <a-form-item label="TTL（秒）"
          ><a-input-number
            v-model:value="form.ttl"
            :min="0"
            :max="2147483647"
            style="width: 100%"
            aria-label="TTL"
        /></a-form-item>
      </div>
      <a-form-item label="记录值" required
        ><a-textarea
          v-model:value="form.values"
          :rows="3"
          :placeholder="hints[form.type]"
          aria-label="记录值"
        /><span class="field-help"
          >{{ hints[form.type] }}。多个值每行一个。</span
        ></a-form-item
      >
      <p class="field-help">
        相对名称自动补全为 {{ zone.name }}；@ 表示区域根。
      </p>
    </a-form>
    <template v-else
      ><a-alert
        message="确认这次变更"
        description="确认记录值后提交，变更将自动同步到从节点。"
        type="info"
        show-icon
      />
      <div class="change-preview">
        <div class="preview-name mono">
          {{ form.name }} <a-tag>{{ form.type }}</a-tag
          ><span>TTL {{ form.ttl }}s</span>
        </div>
        <div v-if="record" class="diff-before">
          <span>当前值</span>
          <pre>{{ record.values.join("\n") }}</pre>
        </div>
        <div class="diff-after">
          <span>{{ record ? "修改后" : "新增值" }}</span>
          <pre>{{ values.join("\n") }}</pre>
        </div>
      </div></template
    >
    <div class="editor-footer">
      <a-button
        :disabled="busy"
        @click="review ? (review = false) : emit('close')"
        >{{ review ? "返回修改" : "取消" }}</a-button
      ><a-button v-if="!review" type="primary" @click="next">预览变更</a-button
      ><a-button v-else type="primary" :loading="busy" @click="save"
        >确认保存</a-button
      >
    </div>
  </section>
</template>
