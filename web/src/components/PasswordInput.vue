<script setup>
import { ref } from 'vue'

defineOptions({ inheritAttrs: false })

defineProps({
  code: { type: String, default: '02' },
  label: { type: String, default: '密码' },
  placeholder: { type: String, default: '' },
  modelValue: { type: String, default: '' },
})

defineEmits(['update:modelValue'])

// 密码是否明文显示（组件内部状态，外部无需关心）
const showPassword = ref(false)
</script>

<template>
  <div class="field">
    <div class="field-head">
      <span class="field-code">{{ code }} /</span>
      <span class="field-label">{{ label }}</span>
      <span class="field-line"></span>
    </div>

    <div class="password-wrap">
      <!-- inheritAttrs: false + v-bind="$attrs"，让外部传入的原生事件（如 @keyup.enter）直接落在 input 上 -->
      <input
        v-bind="$attrs"
        :value="modelValue"
        :type="showPassword ? 'text' : 'password'"
        :placeholder="placeholder"
        @input="$emit('update:modelValue', $event.target.value)"
      />
      <button
        type="button"
        class="toggle-pwd"
        @click="showPassword = !showPassword"
      >
        {{ showPassword ? 'HIDE' : 'SHOW' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
/* 密码框的外层容器，用来承载"SHOW / HIDE"按钮 */
.password-wrap {
  position: relative;
}

.password-wrap input {
  padding-right: 62px; /* 给右侧按钮留出空间 */
}

.toggle-pwd {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  padding: 2px 4px;
  background: none;
  border: none;
  font-family: inherit;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--muted);
  cursor: pointer;
  transition: color 0.15s;
}

.toggle-pwd:hover {
  color: var(--ink);
}
</style>
