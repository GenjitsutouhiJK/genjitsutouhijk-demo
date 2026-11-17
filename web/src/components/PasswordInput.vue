<script setup lang="ts">
import { ref } from 'vue'

defineOptions({ inheritAttrs: false })

/**
 * props 用**类型化**声明（defineProps<{...}> + withDefaults），
 * 与下面的类型化 defineEmits 配套 —— 让 vue-tsc 静态检查这些 prop，
 * 而不是像对象式那样只在运行时校验，写错名字类型都不报错。
 *
 * 结果不赋给变量：这些 prop 只在 <template> 里用，脚本中不引用，
 * 赋值会撞上 noUnusedLocals。
 */
withDefaults(
  defineProps<{
    id: string
    code?: string
    label?: string
    placeholder?: string
    modelValue?: string
  }>(),
  {
    code: '02',
    label: '密码',
    placeholder: '',
    modelValue: '',
  },
)

/**
 * 类型化的 emits。
 *
 * 写成对象形式而不是数组 `['update:modelValue']`，是为了把"负载必须是 string"
 * 交给编译器管：以后 emit 时漏传或传了个别的类型，保存就会标红，
 * 不用等到运行时父组件拿到一个奇怪的值才发现。
 */
const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// 密码是否明文显示（组件内部状态，外部无需关心）
const showPassword = ref(false)

/**
 * 输入框内容变化 → 把新值交给父组件（v-model 的另一半）。
 *
 * 为什么不直接在模板里写 `$event.target.value`：
 *   模板里 $event 的类型是 Event，而 Event.target 的类型是 `EventTarget | null` ——
 *   既可能为空，EventTarget 上也没有 value 这个属性，所以两处都会报错。
 *   要把"这个事件一定来自 <input>"这个前提写出来，编译器才放行。
 *
 * ⚠️ 这不是"为了让报错闭嘴"：断言集中在这一行，等于把假设摆明。
 *   将来这个 @input 要是挂到别的元素上，需要改的地方也只有这一处。
 */
function handleInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <div class="field">
    <div class="field-head">
      <span class="field-code">{{ code }} /</span>
      <label class="field-label" :for="id">{{ label }}</label>
      <span class="field-line"></span>
    </div>

    <div class="password-wrap">
      <input
        v-bind="$attrs"
        :id="id"
        :value="modelValue"
        :type="showPassword ? 'text' : 'password'"
        :placeholder="placeholder"
        @input="handleInput"
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
  transition: color 0.15s, transform var(--dur-fast) var(--ease-out);
}

.toggle-pwd:hover {
  color: var(--ink);
}

/* 按下时轻微缩一下。⚠️ 这里的 transform 必须把 translateY(-50%) 一起写上 ——
   它同时承担着"垂直居中"这个职责，只写 scale 会让按钮瞬间往下跳半格。 */
.toggle-pwd:active {
  transform: translateY(-50%) scale(0.9);
}
</style>
