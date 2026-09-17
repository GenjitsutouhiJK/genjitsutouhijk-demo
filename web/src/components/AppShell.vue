<script setup>
/**
 * AppShell —— 页面外壳（共用框架）
 *
 * 登录页和主页长得几乎一样：浅灰渐变底 + 水印大字 + 四角准星 + 顶栏 + 底栏。
 * 与其把这一大段抄两遍，不如抽成一个"外壳"组件：
 *   - 外壳负责所有装饰和边框
 *   - 各个页面只负责往中间填自己的内容（写在外壳标签之间，会落到 <slot /> 的位置）
 *
 * 这样两个页面的风格不可能走偏 —— 因为它们用的是同一份代码。
 * <slot /> 就是 Vue 的"插槽"：外壳开一个口子，谁用谁往里塞东西。
 */
defineProps({
  /** 顶栏中央的标题 */
  title: { type: String, default: 'GENJITSUTOUHIJK / TERMINAL' },
  /** 底栏状态文字 */
  status: { type: String, default: 'Terminal / Online' },
})
</script>

<template>
  <div class="page">
    <!-- 背景装饰：超大水印字 -->
    <div class="watermark" aria-hidden="true">GENJITSU</div>

    <!-- 四角十字准星 -->
    <span class="crosshair crosshair--tl" aria-hidden="true"></span>
    <span class="crosshair crosshair--tr" aria-hidden="true"></span>
    <span class="crosshair crosshair--bl" aria-hidden="true"></span>
    <span class="crosshair crosshair--br" aria-hidden="true"></span>

    <header class="topbar">
      <span class="topbar-title">{{ title }}</span>
      <!-- 顶栏右侧：具名插槽，由页面自己决定放不放东西（主页放了当前用户名） -->
      <div class="topbar-aside">
        <slot name="aside" />
      </div>
    </header>

    <main class="stage">
      <slot />
    </main>

    <footer class="footer">
      <span class="footer-text">{{ status }}</span>
    </footer>
  </div>
</template>

<style scoped>
.page {
  position: relative;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-grad);
}

/* 中心柔光：制造那种由中心向外压暗的层次 */
.page::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: radial-gradient(
    circle at 50% 44%,
    rgba(255, 255, 255, 0.95) 0%,
    rgba(255, 255, 255, 0) 58%
  );
}

.watermark {
  position: absolute;
  top: 46%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 180px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--watermark);
  white-space: nowrap;
  user-select: none;
  pointer-events: none;
}

/* ---------------- 四角十字准星 ---------------- */
.crosshair {
  position: absolute;
  width: 13px;
  height: 13px;
  pointer-events: none;
}

.crosshair::before,
.crosshair::after {
  content: '';
  position: absolute;
  background: var(--line-strong);
}

.crosshair::before {
  left: 50%;
  top: 0;
  width: 1px;
  height: 100%;
  transform: translateX(-50%);
}

.crosshair::after {
  top: 50%;
  left: 0;
  height: 1px;
  width: 100%;
  transform: translateY(-50%);
}

.crosshair--tl {
  top: 78px;
  left: 40px;
}

.crosshair--tr {
  top: 78px;
  right: 40px;
}

.crosshair--bl {
  bottom: 78px;
  left: 40px;
}

.crosshair--br {
  bottom: 78px;
  right: 40px;
}

/* ---------------- 顶部标题条 ---------------- */
/* 用三列网格，标题放中间那一列，右侧插槽放第三列。
   这样即使右边有内容，标题依然保持居中。 */
.topbar {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 22px 24px 16px;
  border-bottom: 1px solid var(--line);
}

.topbar-title {
  grid-column: 2;
  font-size: 13px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--text-title);
}

.topbar-aside {
  grid-column: 3;
  justify-self: end;
  display: flex;
  align-items: center;
}

/* ---------------- 主区域 ---------------- */
.stage {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}

/* ---------------- 底部状态条 ---------------- */
.footer {
  position: relative;
  z-index: 1;
  padding: 14px 24px 18px;
  border-top: 1px solid var(--line);
  text-align: center;
}

.footer-text {
  font-size: 12px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--muted);
}
</style>
