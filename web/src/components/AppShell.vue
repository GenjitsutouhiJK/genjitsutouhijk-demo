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
  /* 54 秒挪 16px —— 快到能感觉到"水印在呼吸"，慢到永远抓不住它。
     这种几乎察觉不到的位移有个实际作用：整页不是一张死图。
     alternate 让它到了就自己走回来，不用写 100% 那一帧。 */
  animation: mark-drift 54s ease-in-out infinite alternate;
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

/* 两条笔画分别"长"出来：竖的从中心上下展开，横的从中心左右展开。
   from 里必须把 translate 一起写上 —— keyframes 不会叠加元素原有的 transform，
   只写 scaleY 会把那句 translateX(-50%) 挤掉，准星会当场歪掉半个身位。
   --d 由下面四个角各自给，形成顺时针依次"校准"的节奏。 */
.crosshair::before {
  left: 50%;
  top: 0;
  width: 1px;
  height: 100%;
  transform: translateX(-50%);
  animation: cross-v 0.42s var(--ease-out) var(--d, 0s) both;
}

.crosshair::after {
  top: 50%;
  left: 0;
  height: 1px;
  width: 100%;
  transform: translateY(-50%);
  animation: cross-h 0.42s var(--ease-out) calc(var(--d, 0s) + 0.1s) both;
}

/* --d 的数值：左上 → 右上 → 左下 → 右下，一圈走下来约 0.3s。
   放在内容进场之后，像是设备先亮起来、再自动校准。 */
.crosshair--tl {
  top: 78px;
  left: 40px;
  --d: 0.4s;
}

.crosshair--tr {
  top: 78px;
  right: 40px;
  --d: 0.5s;
}

.crosshair--bl {
  bottom: 78px;
  left: 40px;
  --d: 0.6s;
}

.crosshair--br {
  bottom: 78px;
  right: 40px;
  --d: 0.7s;
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

/* 下沿那道 1px 发丝线上，7 秒一次缓缓划过一小段浅光。
   它不代表任何状态，纯粹让整个顶栏看起来"通着电"。
   bottom: -1px 是压在边框上的 —— 让光走在线上，而不是线下面。
   超出右边界的那部分会被 .page 的 overflow: hidden 收掉，不用管。 */
.topbar::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -1px;
  width: 140px;
  height: 1px;
  background: linear-gradient(
    90deg,
    rgba(0, 0, 0, 0) 0%,
    var(--line-strong) 50%,
    rgba(0, 0, 0, 0) 100%
  );
  pointer-events: none;
  animation: signal-run 7s linear infinite;
}

/* 从左侧被"裁掉"的位置起步，一直走到视口右边外面。
   用 100vw 而不是百分比：百分比在这里是相对元素自己的宽度（140px）算的，
   那样只能挪 140px，走不出整个顶栏。 */
@keyframes signal-run {
  from {
    transform: translateX(-140px);
  }
  to {
    transform: translateX(100vw);
  }
}

.topbar-title {
  grid-column: 2;
  font-size: 13px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--text-title);
  /* 像打印机一样从左往右把字"吐"出来。
     clip-path 是裁自己，不改变布局宽度 —— 用 width 做同样的事
     会让居中的标题在动画期间一直往左偏。 */
  animation: title-type 0.5s var(--ease-out) 0.08s both;
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

/* 状态文字后面跟一个一闪一闪的小方块 —— 终端的命令行光标。
   它让"Terminal / Online"这句话从一句静态文案，变成"这台终端正在运行"。
   用 background: currentColor 而不是写死颜色：底栏文字颜色一变，
   光标自己跟着变，不用两处维护。
   55% 那个硬切点配 linear 就是干脆的"亮—灭"，不需要 steps()。 */
.footer-text::after {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-left: 8px;
  vertical-align: middle;
  background: currentColor;
  animation: caret-blink 1.4s linear infinite;
}

@keyframes caret-blink {
  0%,
  55% {
    opacity: 0.65;
  }
  56%,
  100% {
    opacity: 0;
  }
}

/* ---------------- 关键帧 ---------------- */

/* 水印的极慢漂移。keyframes 只写和基准不同的那部分，
   但 translate(-50%, -50%) 这个居中位移必须原样带上 ——
   它不在 to 里就会在动画结束的瞬间被丢掉。 */
@keyframes mark-drift {
  from {
    transform: translate(-50%, -50%);
  }
  to {
    transform: translate(calc(-50% + 16px), calc(-50% - 12px));
  }
}

/* 准星的竖笔画：从中心往上下一同展开 */
@keyframes cross-v {
  from {
    transform: translateX(-50%) scaleY(0);
  }
  to {
    transform: translateX(-50%) scaleY(1);
  }
}

/* 准星的横笔画：从中心往左右一同展开 */
@keyframes cross-h {
  from {
    transform: translateY(-50%) scaleX(0);
  }
  to {
    transform: translateY(-50%) scaleX(1);
  }
}

/* 顶栏标题从左往右被"打印"出来 */
@keyframes title-type {
  from {
    clip-path: inset(0 100% 0 0);
  }
  to {
    clip-path: inset(0 0 0 0);
  }
}
</style>
