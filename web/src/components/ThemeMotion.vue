<script setup lang="ts">
/**
 * ThemeMotion —— Blueprint 皮肤的「作图」动效层
 *
 * ================== 它在动什么 ==================
 *
 * 把参考实现 `众生行记` 开场从头到尾读了一遍，真正"带几何结构"的动态
 * 只有两处，而且它们都是**绘制型**动效（从起点把自己画出来），
 * 不是位移、不是缩放：
 *
 *   ① diagram()       —— 4 条作图线按错开的时间依次"生长"，
 *                        外加一条倾斜椭圆弧做角度扫描（sweep）
 *   ② drawSkeleton()  —— 骨骼线段生长、关节圆环半径扩张、径向刻度生长
 *
 * 原作里这两处是 canvas 逐帧 lineTo / arc 现画的。本组件用 SVG 的
 * `stroke-dasharray` + `stroke-dashoffset` 复刻同一件事。
 * ★ 关键技巧：给路径加 `pathLength="1"`，把路径长度归一化成 1 ——
 *   于是"画出 40%"只是把 dashoffset 从 1 补间到 0.6，
 *   完全不必去算椭圆弧的真实弧长（那正是最容易出错、也最没必要的地方）。
 *
 * ================== 为什么不照搬 canvas ==================
 *
 * 原作那 8 个模块（animation / skeleton / motion-tracks / calibration …）
 * 是强耦合的：`animation.mjs` 依赖骨架、运动曲线、标定矩阵三者才能出画，
 * 拆不出半个；而且它们绑定 852×480 的固定舞台和一批游戏素材。
 * 所以这里只取**几何关系**——线的起止点、弧的圆心/半径/起止角、
 * 圆环的半径与刻度角——这些坐标就是从原作源码里抄来的那几组数，不是估的。
 *
 * ================== 触发时机 ==================
 *
 * 用 `v-if` 绑主题：切到 Blueprint 才挂载，于是**每次切过来都会重放一遍**，
 * 蓝图的作图过程本身就是进场动画；切回 Terminal 直接卸载，不留残余。
 * 它套在 ThemeDecor 的 .decor 里，共享那层的淡入和 overflow: hidden，
 * 不用自己处理裁切，也自动跟着两套皮肤一起显隐。
 *
 * ================== 视觉分寸 ==================
 *
 * 描边一律 `vector-effect: non-scaling-stroke`：不管视口多大、SVG 被放大到
 * 几倍，线宽恒为 1px，和页面里其它发丝线一个规格，不会糊成一片。
 * 整体不透明度压在 0.5 上下 —— 它的身份是"图纸底下的作图辅助线"，
 * 是背景，不是主角。
 *
 * ================== 坐标从哪来 ==================
 *
 * viewBox 沿用作曲舞台的 852×480，配 `preserveAspectRatio="xMidYMid slice"`，
 * 行为等同于 CSS 的 `background-size: cover`：铺满视口、多出来的部分裁掉。
 * 于是下面这些坐标可以原样照抄原作，不用做任何换算：
 *
 *   线   d "M -27 79 L 605 192" 等 4 条 —— animation.mjs: diagram() 里的 lines[]
 *   弧   M 175.05 168.77 A 157 114 -18.9 0 1 480.99 123.89
 *        —— 圆心 (328,150)、rx 157、ry 114、旋转 -0.33rad、起止角 1.09π→2.07π
 *           由椭圆参数方程算出的两个端点
 *   环   半径 13.5 / 12.25 取自 skeleton.mjs: rings[]；
 *        但**落点没抄**，原因见模板里 ③ 那段注释（抄过来会被面板盖住）
 */
import { theme } from '../utils/theme'
</script>

<template>
  <svg
    v-if="theme === 'blueprint'"
    class="motion"
    viewBox="0 0 852 480"
    preserveAspectRatio="xMidYMid slice"
    aria-hidden="true"
  >
    <!-- ① 作图线：4 条，按错开的时间依次生长 -->
    <g class="m-lines">
      <path class="m-line m-line--1" d="M -27 79 L 605 192" pathLength="1" />
      <path class="m-line m-line--2" d="M -4 -15 L 613 281" pathLength="1" />
      <path class="m-line m-line--3" d="M 234 -28 L 590 298" pathLength="1" />
      <path class="m-line m-line--4" d="M 301 -15 L 455 201" pathLength="1" />
    </g>

    <!-- ② 椭圆弧：角度扫描。起止点是按原作椭圆参数算出来的 -->
    <path
      class="m-arc"
      d="M 175.05 168.77 A 157 114 -18.9 0 1 480.99 123.89"
      pathLength="1"
    />

    <!-- ③ 关节标注环：外环画出 + 径向刻度 + 内点，三个部件错开进场。
         半径取自 skeleton.mjs 的 rings[]（13.5 / 12.25），刻度在局部坐标里
         按 ref-r → ref+r+0.5 画。但**坐标不能再照抄原作了** —— 原作那些环
         长在人物关节上，落在舞台中央；而这里中央是被登录面板（400px）
         和主页面板（920px）占满的，抄过来会整组被盖住。
         所以按本项目"装饰只走上下横带与左右外栏"的既有原则重新落点，
         刻度方向也刻意打散（上 / 左 / 左上 / 下），免得像机器摆的。 -->
    <g class="m-rings">
      <g class="m-ring m-ring--1" transform="translate(135 62)">
        <circle class="m-ring-o" r="13.5" pathLength="1" />
        <line class="m-tick" x1="0" y1="-9.5" x2="0" y2="-18" />
        <circle class="m-ring-i" r="1.75" />
      </g>
      <g class="m-ring m-ring--2" transform="translate(690 58)">
        <circle class="m-ring-o" r="13.5" pathLength="1" />
        <line class="m-tick" x1="-9.5" y1="0" x2="-18" y2="0" />
        <circle class="m-ring-i" r="1.75" />
      </g>
      <g class="m-ring m-ring--3" transform="translate(752 252)">
        <circle class="m-ring-o" r="12.25" pathLength="1" />
        <line class="m-tick" x1="5.83" y1="-5.83" x2="11.84" y2="-11.84" />
        <circle class="m-ring-i" r="1.75" />
      </g>
      <g class="m-ring m-ring--4" transform="translate(752 386)">
        <circle class="m-ring-o" r="12.25" pathLength="1" />
        <line class="m-tick" x1="0" y1="8.25" x2="0" y2="16.75" />
        <circle class="m-ring-i" r="1.75" />
      </g>
    </g>
  </svg>
</template>

<style scoped>
/* ==================================================================
 * 整层：铺满装饰区
 * ================================================================== */

.motion {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

/* 描边恒为 1px —— non-scaling-stroke 让线宽不随 SVG 缩放而变，
   这样 1440 视口和 2560 视口下的线一样细，和页面发丝线同规格。
   fill 不在这里统一写成 none：内点环要靠 fill 显形，
   统一写会被更低特异性的规则覆盖掉，不如各自声明。 */
.motion path,
.motion circle,
.motion line {
  vector-effect: non-scaling-stroke;
  stroke-width: 1;
}

/* ==================================================================
 * ① 作图线
 * ================================================================== */

.m-lines {
  opacity: 0.8;
}

.m-line {
  fill: none;
  stroke: var(--line-strong);
  /* 用归一化长度画：dasharray 1 = 整条线，offset 1 = 整条藏在起点外 */
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: m-draw 0.9s var(--ease-out) both;
}

/* 依次生长，间隔 0.08~0.1s —— 原作 diagram() 里四条线的
   start 时间也是错开的（.38 / .40 / .43 / .44），这里只是压缩了节奏 */
.m-line--1 {
  animation-delay: 0.16s;
}

.m-line--2 {
  animation-delay: 0.24s;
}

.m-line--3 {
  animation-delay: 0.34s;
}

.m-line--4 {
  animation-delay: 0.44s;
}

/* ==================================================================
 * ② 椭圆弧：角度扫描
 * ================================================================== */

/* 弧用强调蓝而不是灰线 —— 它是这层里唯一的"焦点几何"，
   跟 4 条辅助线拉开一档层次。
   画完之后接一条 12s 的极慢呼吸，让它不至于变成一张死图。 */
.m-arc {
  fill: none;
  stroke: var(--ink);
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  opacity: 0.3;
  animation:
    m-draw 1.1s var(--ease-out) 0.4s both,
    m-glow 12s ease-in-out 2s infinite;
}

/* ==================================================================
 * ③ 关节标注环
 * ================================================================== */

/* 整组做一次很浅的呼吸（0.5 ↔ 0.78），周期 9s。
   这是整套动效里唯一的"持续运动"，性格属于 tokens.css 里写的
   "② 信号 —— 匀速、缓慢、不引人注意，像设备通着电"。 */
.m-rings {
  opacity: 0.5;
  animation: m-breathe 9s ease-in-out 2.2s infinite;
}

/* 每个环的三个部件按 0.12~0.18s 的间隔依次到位：
   先外环画出 → 再径向刻度弹出 → 最后内点落下。
   用 --d 在每个环上给一个基准延迟，子元素在此基础上加偏移，
   改节奏时只动这四个数。 */
.m-ring--1 {
  --d: 0.52s;
}

.m-ring--2 {
  --d: 0.6s;
}

.m-ring--3 {
  --d: 0.68s;
}

.m-ring--4 {
  --d: 0.76s;
}

.m-ring-o {
  fill: none;
  stroke: var(--line-strong);
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: m-draw 0.75s var(--ease-out) var(--d) both;
}

/* 刻度与内点用 scale 弹出而不是画出来 —— 它们太短，
   逐段画出来在 1px 线宽下几乎看不出，反而显得顿。
   transform-box: fill-box 让 scale 绕元素自己的中心，
   不写的话原点在 SVG 坐标系的原点，刻度会从屏幕左上角飞过来。 */
.m-tick {
  stroke: var(--line-strong);
  transform-box: fill-box;
  transform-origin: center;
  animation: m-pop 0.45s var(--ease-out) calc(var(--d) + 0.12s) both;
}

.m-ring-i {
  fill: var(--line-strong);
  stroke: none;
  transform-box: fill-box;
  transform-origin: center;
  animation: m-pop 0.4s var(--ease-out) calc(var(--d) + 0.18s) both;
}

/* ==================================================================
 * 关键帧
 * ================================================================== */

/* 只写 to：from 自动取元素当前的静态值（dashoffset: 1），
   这样一条 keyframes 能服务所有"从起点画到终点"的元素，不用逐个复述起点。
   配合 animation-fill-mode: both，延迟期间停在起点（不可见）。 */
@keyframes m-draw {
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes m-pop {
  from {
    opacity: 0;
    transform: scale(0.2);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes m-glow {
  0%,
  100% {
    opacity: 0.3;
  }
  50% {
    opacity: 0.5;
  }
}

@keyframes m-breathe {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 0.78;
  }
}

/* ==================================================================
 * 窄屏兜底
 * ==================================================================
 * 视口变窄时 slice 会横向裁切，靠边的作图线可能只剩一小截、
 * 反而显得像画歪了。干脆把线收掉，只留弧和环这两组集中在中部的几何 ——
 * 少一点，比"残缺"好。
 * ================================================================== */
@media (max-width: 1023px) {
  .m-lines {
    display: none;
  }
}
</style>
