<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../api";
import type { Rrset, Zone } from "../types";
const props = defineProps<{ open: boolean; zone: Zone; record?: Rrset }>();
const emit = defineEmits<{ close: []; saved: [] }>();
const form = reactive({ name: "", type: "A", ttl: 300, values: "" });
const busy = ref(false),
  review = ref(false);
const types = ["A", "AAAA", "CNAME", "MX", "TXT", "PTR", "SRV", "CAA"];
const hints: Record<string, string> = {
  A: "例如：192.0.2.50",
  AAAA: "例如：2001:db8::10",
  CNAME: "例如：storage.intra.example.com.",
  MX: "优先级与邮件服务器，例如：10 mail.example.com.",
  TXT: '文本请用双引号包裹，例如："v=spf1 mx -all"',
  PTR: "目标完整域名，例如：storage.intra.example.com.",
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
    form.ttl === undefined ||
    form.ttl < 0
  ) {
    ElMessage.warning("请填写记录名称、TTL 和至少一个记录值");
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
    ElMessage.success("记录已保存，BIND 将自动同步到从节点");
    emit("saved");
    emit("close");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <el-dialog
    :model-value="open"
    :title="record ? '编辑解析记录' : '添加解析记录'"
    width="660px"
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    @close="emit('close')"
  >
    <div class="editor-zone">
      {{ zone.name }} <el-tag size="small">主区域</el-tag>
    </div>
    <el-form
      v-if="!review"
      :model="form"
      label-position="top"
      class="record-form"
      @submit.prevent
    >
      <el-form-item label="记录名称" required
        ><el-input
          v-model="form.name"
          :disabled="!!record"
          placeholder="例如 storage，或完整域名 storage.intra.example.com."
          aria-label="记录名称"
        /><span class="field-help"
          >相对名称会自动补全当前区域；@ 表示区域根。</span
        ></el-form-item
      >
      <div class="form-pair">
        <el-form-item label="记录类型"
          ><el-select
            v-model="form.type"
            :disabled="!!record"
            aria-label="记录类型"
            ><el-option
              v-for="value in types"
              :key="value"
              :label="value"
              :value="value" /></el-select></el-form-item
        ><el-form-item label="TTL（秒）"
          ><el-input-number
            v-model="form.ttl"
            :min="0"
            :max="2147483647"
            controls-position="right"
            style="width: 100%"
            aria-label="TTL"
        /></el-form-item>
      </div>
      <el-form-item label="记录值" required
        ><el-input
          v-model="form.values"
          type="textarea"
          :rows="5"
          :placeholder="hints[form.type]"
          aria-label="记录值"
        /><span class="field-help"
          >{{ hints[form.type] }} ·
          多个值每行一个，将一起保存为同一记录集。</span
        ></el-form-item
      >
    </el-form>
    <template v-else
      ><el-alert
        title="确认这次变更"
        description="更新将提交到主节点。同一记录集中的现有值会替换为下方内容，其他记录保持不变。"
        type="info"
        show-icon
        :closable="false"
      />
      <div class="change-preview">
        <div class="mono preview-name">
          {{ form.name }} <el-tag size="small">{{ form.type }}</el-tag
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
    <template #footer
      ><el-button
        :disabled="busy"
        @click="review ? (review = false) : emit('close')"
        >{{ review ? "返回修改" : "取消" }}</el-button
      ><el-button v-if="!review" type="primary" @click="next"
        >预览变更</el-button
      ><el-button v-else type="primary" :loading="busy" @click="save"
        >确认保存</el-button
      ></template
    >
  </el-dialog>
</template>
