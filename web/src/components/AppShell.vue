<script setup lang="ts">
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
 *
 * ================== 关于主题 ==================
 *
 * 外壳同时也是"两套皮肤"的落点：
 *   ① <ThemeDecor /> 铺一层 Blueprint 专属的装饰（Terminal 下整体透明）
 *   ② 顶栏左侧放主题切换器
 * 这两样都放在外壳里、而不是页面里 —— 于是登录页 / 注册页 / 主页
 * 三页自动都有，而且不会和 HomeView 通过 #aside 插槽塞进来的
 * "用户名 + 退出登录"打架。
 */
import ThemeDecor from './ThemeDecor.vue'
import { THEMES, theme, setTheme } from '../utils/theme'

defineProps({
  /** 顶栏中央的标题 */
  title: { type: String, default: 'GENJITSUTOUHIJK / TERMINAL' },
  /** 底栏状态文字 */
  status: { type: String, default: 'Terminal / Online' },
  /**
   * 当前页面的身份标识 —— 登录/注册页传输入框里正在敲的用户名，
   * 主页传服务端核验过的用户名。
   *
   * 外壳自己不用它，只负责把它转发给 <ThemeDecor />：
   * 装饰层靠这个值把左外栏那块无含义的衬线字母换成真实的 ID。
   * 由外壳中转而不是各页面自己塞，是为了让三个页面共用同一份接线，
   * 且页面不必知道"装饰层是怎么实现的"。
   */
  identifier: { type: String, default: '' },
  /**
   * 当前页内容列的宽度（px）—— 同样只负责转发给 <ThemeDecor />。
   *
   * 装饰层要拿它算"左外栏还剩多少横向空间"，才能把用户 ID 的字号
   * 调成刚好填满那一格。⚠️ 必须和那一页的容器宽度一致：
   *   登录页 / 注册页 → .panel 的 400   主页 → .home 的 920
   * 传小了，算出的余量会偏大，字就会压到面板上。
   *
   * 默认 920 是"三个页面里最宽"的那个：万一哪个页面忘了传，
   * 结果也只是字偏小，而不是溢出去。
   */
  contentWidth: { type: Number, default: 920 },
})
</script>

<template>
  <div class="page">
    <!-- Blueprint 皮肤的装饰层。Terminal 下它整体透明（靠 CSS 变量控制，
         模板里没有 v-if），见 ThemeDecor.vue。
         identifier 一并转发：装饰层用它把左外栏那块无含义的衬线字母
         换成当前页面的用户 ID。
         contentWidth 也转发：装饰层要靠它算那块 ID 的字号（详见 ThemeDecor）。 -->
    <ThemeDecor :identifier="identifier" :content-width="contentWidth" />

    <!-- 背景装饰：超大水印字（Blueprint 下会换成材质水印，见下方 scoped 样式） -->
    <div class="watermark" aria-hidden="true">GENJITSU</div>

    <!-- 四角十字准星（Blueprint 下让位给 ThemeDecor 里的十字素材） -->
    <span class="crosshair crosshair--tl" aria-hidden="true"></span>
    <span class="crosshair crosshair--tr" aria-hidden="true"></span>
    <span class="crosshair crosshair--bl" aria-hidden="true"></span>
    <span class="crosshair crosshair--br" aria-hidden="true"></span>

    <header class="topbar">
      <!-- 左列：主题切换器。放在外壳里而不是页面里，
           是为了让三个页面都能切，且不占用右侧的 #aside 插槽。 -->
      <div class="topbar-left">
        <div class="theme-switch" role="group" aria-label="界面主题">
          <button
            v-for="option in THEMES"
            :key="option.id"
            type="button"
            class="theme-opt"
            :class="{ 'is-on': theme === option.id }"
            :aria-pressed="theme === option.id"
            @click="setTheme(option.id)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

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

/* 中心柔光：制造那种由中心向外压暗的层次。
   起始色走 --glow：Blueprint 下要减弱（浅底上强白光会把装饰线洗掉）。
   末端保持 rgba(255,255,255,0) 而不是 transparent ——
   白色到"透明的白"渐变才是干净的，写 transparent 会经过灰。 */
.page::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: radial-gradient(
    circle at 50% 44%,
    var(--glow) 0%,
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
    transparent 0%,
    var(--line-strong) 50%,
    transparent 100%
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

/* ---------------- 顶栏左列：主题切换器 ---------------- */
.topbar-left {
  grid-column: 1;
  justify-self: start;
  display: flex;
  align-items: center;
}

/* 两段式的分段控件：零圆角、1px 描边，选中态反白。
   刻意做得比正文还小一号 —— 它是个"设置项"，不该和页面标题抢注意力。 */
.theme-switch {
  display: flex;
  border: 1px solid var(--line);
}

.theme-opt {
  padding: 5px 11px;
  font-family: inherit;
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--faint);
  background: transparent;
  border: 0;
  cursor: pointer;
  transition: color 0.15s, background 0.15s;
}

/* 两段之间的竖线。用相邻兄弟选择器而不是给每项加 border-right ——
   那样最后一项也会拖一条多余的线出来。 */
.theme-opt + .theme-opt {
  border-left: 1px solid var(--line);
}

.theme-opt:hover {
  color: var(--text-title);
}

.theme-opt.is-on {
  color: var(--on-ink);
  background: var(--ink);
}

/* Blueprint 下取消实心块：选中态改成"蓝字 + 底部一条蓝线"，
   和 .panel-bar / .btn--primary 的处理保持一致。
   （这条写在 scoped 里也用 html[data-theme] 前缀，原因见 main.css
     末尾「主题专属覆盖」那一节的说明：要比组件自身的选择器更具体。） */
html[data-theme='blueprint'] .theme-opt.is-on {
  color: var(--ink);
  background: transparent;
  box-shadow: inset 0 -2px 0 var(--ink);
}

/* Blueprint 下改用 ThemeDecor 里的材质水印（"众生行记"标题图层），
   文字水印退场 —— 两种水印叠在一起只会互相干扰。 */
html[data-theme='blueprint'] .watermark {
  display: none;
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
   ★ 闪烁本身不在本地定义 —— 用的是 main.css 里那条 caret-blink，
   和打字机末尾的光标**共用同一条关键帧与同一个周期令牌**
   （--blink-period，见 tokens.css），两处永远同频。
   这里只声明自己"亮起来的强度"：这一颗是淡淡的一点，不是满亮。 */
.footer-text::after {
  --blink-on: 0.65;
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-left: 8px;
  vertical-align: middle;
  background: currentColor;
  animation: caret-blink var(--blink-period) linear infinite;
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
