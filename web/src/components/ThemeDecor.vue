<script setup lang="ts">
/**
 * ThemeDecor —— Blueprint 皮肤专属的装饰层
 *
 * 静态部分在本文件；**会动的那部分**（作图线生长、椭圆弧扫描、关节环扩张）
 * 单独拆在 ThemeMotion.vue —— 那层是矢量重绘的几何动效，
 * 和这里的位图素材摆放是两码事，混在一个文件里只会互相干扰。
 *
 * ================== 它解决什么问题 ==================
 *
 * 换掉配色只能让页面"看起来是蓝的"；真正让人一眼认出这是"图纸"的，
 * 是压在最底下的那层测绘符号：十字、带 y 刻度的纵轴、弧线、
 * 星形交汇点、技术标签碎片。
 *
 * 这一层在 Terminal 皮肤下**整体透明**——见 style 里的
 * `opacity: var(--decor)`，那个值由 tokens.css 按主题给：Terminal 0 / Blueprint 1。
 * 所以模板里不需要任何 v-if：两套皮肤共用同一份 DOM，
 * 区别只在 CSS 里那几个数。这就是"两套皮肤共存"最省事的地方。
 *
 * ================== 素材从哪来 ==================
 *
 * 全部来自参考实现 `众生行记`，原文件已按用途改名后放在 src/assets/theme/。
 * 尺寸与颜色是实测的，不是估的：
 *
 *   cross-02.png      44×44    蓝 #004fd4   十字（带一条长横线）
 *   axis-v.png        44×762   蓝 #003b9e   纵轴 + 刻度
 *   tick-1/2/3.png    约 30×64 黑           刻度 + 原作里的 y1/y2/y3 标注
 *   arc-line.png      609×65   黑           细弧
 *   star-01.png       43×43    浅蓝         X 形交汇
 *   star-l.png        141×141  浅蓝         大星形
 *   corner-square.png 28×28    蓝           2×2 方块阵列
 *   dec-02/03/04.png  小尺寸   蓝 / 黑      原作里的技术标签碎片
 *   bird-left/right   蓝灰     飞鸟剪影
 *   paper.png         512×512  近白         纸纹底
 *   event-title.png   406×193  深灰         "众生行记"标题图层（作水印）
 *
 * ★ 目录里现在**只留被引用到的**这 16 张（2026-10-08 清掉了 11 张用不上的：
 *   cross-01/03、dec-01、fx-line-01/02/03、letter-e/s、node-square、star-dot、star-s）。
 *   其中 letter-e/s 是原先那两个衬线字母 —— 位置已经让给用户 ID，留着不会有人用。
 *   真要找回来：原包在 `arknights-motion-library-main/众生行记` 里，按名解包即可。
 *
 * ⚠️ 版权：这些素材的权利归原作权利方，参考实现的 LICENSE 只覆盖它自己写的
 *    代码、明确**不覆盖**素材。本项目作为个人学习用途内嵌，公开分发前需自行
 *    处理授权。
 *
 * ================== 关于 mask（已退役，知识留档） ==================
 *
 * 原来 E / S 两个字母是**白色**素材，浅底上会隐形，所以用 `mask` 把图片当
 * "形状模板"、再用 background-color 填 --ink。现在字母换成了真实文字，
 * 这套写法**全项目已无使用者**。若以后还要给位图素材上主题色，照这个来：
 *   background-color: var(--ink); -webkit-mask-image: url(...); mask-image: url(...);
 * ⚠️ `-webkit-` 前缀不能省：Chrome / Edge / Safari 支持无前缀版本很晚。
 *
 * ================== 摆放原则 ==================
 *
 * 内容区在中间（登录页 400px、主页 920px 居中），所以装饰只往
 * **上/下横带**和**左右外栏**放，整体压在半透明以下。
 * 窄屏时用媒体查询砍掉左右外栏那一组，免得压到内容上。
 *
 * ★ 一条实测教训：**装饰的不透明度要比直觉低一档。**
 *   素材是在"白色画布 + 程序叠加"的环境里设计的，直接搬到带底色的
 *   网页上时对比度会明显变高 —— 0.45 的飞鸟糊成一团、0.75 的字母压得人喘不过气。
 *   现在这套数值（多在 0.26~0.58）是截图比对后定下来的，不是拍脑袋给的。
 *
 * ================== 身份标识（取代无含义的衬线字母） ==================
 *
 * 原作的左外栏压着一个白色衬线大字母 E（素材 letter-e.png）——
 * 好看，但它是原作活动标题的碎片，在本项目里**不指向任何东西**。
 * 现在把它换成当前页面的用户 ID：登录/注册页是输入框里正在敲的那个名字，
 * 主页是服务端核验过的用户名。
 *
 * 于是这块装饰从"好看但无意义"变成"一块会说话的信息"，
 * 而且顺带解决了一件事：**用户能立刻确认自己没敲错名字**。
 *
 * ★ 空值时这块**彻底留白**，不回落成原来的字母。
 *   那两个字母本来就是"没有含义"才被换掉的，留着当兜底等于白换一场。
 *   字体仍用衬线体 —— 换掉内容，但不换调子。
 *
 * ★ 右下角原本那个 S 的位置**直接空出来**，不拿首字母去填。
 *   理由是同一个：要的是"没有字母"，不是"换个字母"。
 *   于是全页只剩一处字母（会打字的那个），注意力不会被打散。
 *
 * ★ 横排，位置与大小都跟着 E 原来那一格走：
 *   横向从 E 的 left（92px）起，纵向对齐 E 的垂直中心（26% + 半个 E 的高度）。
 *   **字号自适应**：按该页内容列宽度算出左侧还剩多少，再按 ID 的真实字形宽度
 *   反推字号 —— 名字越长字越小，但永远刚好填满那一格，不会压到面板上。
 *
 *   为什么非得自适应？因为 E 那一格的横向空间是"页面内容列"挤出来的，
 *   而三个页面的内容列差了一倍多（登录/注册 400px、主页 920px）：
 *   1414px 的窗口下，登录页左侧有 391px 可铺，主页只剩 131px。
 *   同一个位置、相差三倍的空间，写死任何一个字号都必然有一边出错。
 */
import { computed, onMounted, ref } from 'vue'
import ThemeMotion from './ThemeMotion.vue'

const props = withDefaults(
  defineProps<{
    /** 当前页面的用户 ID（登录/注册页是正在敲的，主页是核验过的） */
    identifier?: string
    /**
     * 该页内容列的宽度（px）—— 用来算"左侧还剩多少横向空间"。
     *
     * ⚠️ 必须和那一页的容器宽度**一致**：
     *   登录页 / 注册页 = `.panel` 的 400，主页 = `.home` 的 920。
     *   传小了 → 算出来的余量偏大 → 字号偏大 → 文字会压到面板上。
     *   默认给 920（最宽的那个），是"算错时宁可字小一点"的方向。
     */
    contentWidth?: number
  }>(),
  { identifier: '', contentWidth: 920 },
)

/**
 * ID 用的衬线字体栈。
 *
 * ★ 只在这里写一次：脚本要用它去量宽度，样式通过 `--id-font` 读同一个常量。
 *   两处各写一份的话，哪天改了字体，字号计算就会按另一套字体度量算 ——
 *   算出来的宽度和实际渲染对不上，表现是"字要么白白小一圈、要么被切掉半个"。
 *
 * 为什么是 Playfair Display：
 *   原素材 letter-e.png（已清理，见文件头说明）是高对比度 Didone 骨架（竖极粗、横极细、细方衬线），
 *   而系统字体里没有这一族（查过 C:\Windows\Fonts 全部 565 个文件）。
 *   Playfair Display 就是照 18 世纪过渡体 / Didone 做的，字面窄而挺拔、
 *   笔画粗细反差大，是免费字体里最接近原素材的一支。
 *
 *   ★ 它是**自托管**的（src/assets/fonts/，OFL 授权，声明在 styles/fonts.css），
 *     不是系统字体 —— 所以 Mac / 手机 / Windows 用的是同一份文件，
 *     量宽和排版不会再随机器变。回落链里的 Georgia 只是"字体文件没加载成功"
 *     时的兜底，正常情况用不到。
 */
const ID_FONT = "'Playfair Display', Georgia, 'Times New Roman', 'Songti SC', 'SimSun', serif"

/**
 * ID 的字重。
 *
 * Playfair Display 下载的是 **wght 可变字体**（一个文件覆盖 400~700），
 * 所以 700 是**真字重**，不像之前的 Century Schoolbook 那样靠浏览器合成。
 * 但这里仍然把它写进 canvas 量宽（见 idEmWidth）——
 * 字重一变宽度就变，量宽时带上它才是"浏览器真的会画多宽"。
 */
const ID_WEIGHT = 700

/**
 * 字体文件是否已经就绪。
 *
 * ★ 为什么需要这个开关：
 *   系统字体的量宽是**同步立刻可用**的，而自托管字体要下载。
 *   如果在字体到位之前就用 canvas 量宽度，量到的是**回落字体**（Georgia）的
 *   字形宽度 —— 两者差得不小，算出来的字号会偏大或偏小，字要么压到面板上、
 *   要么白白空一截。
 *   所以 onMounted 里显式请求一次这个字体，加载完成后把开关置位，
 *   idEmWidth 会自动重算一遍，把宽度纠正回来。
 *
 * 这里**显式** load 而不等 document.fonts.ready：
 *   本页如果没输入用户名，`.d-id` 这个元素根本不渲染，
 *   浏览器就不会去请求这个字体，ready 也就等于"没有它"。
 */
const fontReady = ref(false)

/** 字距。必须和 CSS 里的 `letter-spacing` 一致 —— 量宽度时要把它算进去。 */
const ID_TRACKING = 0.05

/** 量宽度用的基准字号。挑 100 是因为量完除以 100 就直接是"占多少个 em"。 */
const PROBE_SIZE = 100

onMounted(async () => {
  try {
    // 只请求这一个字重、一个字号 —— 只要能把字形度量拿到手就够了
    await document.fonts.load(`${ID_WEIGHT} ${PROBE_SIZE}px "Playfair Display"`)
  } catch {
    // 加载失败（离线 / 文件缺失）就按回落字体继续，不该让装饰层把页面拖垮
  }
  fontReady.value = true
})

/**
 * 最多 20 个字符 —— 和注册页的 USERNAME_MAX 是同一个数。
 * 对齐后端校验上限，就不用担心"合法的用户名在装饰里被截一半"。
 *
 * （横排 + 字号自适应之后，长度不再有"放不下"的问题：
 *   20 字只是把字号压得更小，位置永远守在 E 那一格。）
 */
const MAX_CHARS = 20

const idChars = computed(() =>
  [...(props.identifier ?? '').trim().toUpperCase()].slice(0, MAX_CHARS),
)
const hasId = computed(() => idChars.value.length > 0)

/**
 * 这一串 ID 在当前字体下总共占多少「em」（1em = 一个字号）。
 *
 * ★ 为什么不用"每字符平均宽度 × 字符数"估？
 *   大写衬线体的字宽从 I 的 0.33em 到 W 的 0.94em，差了近三倍。
 *   按平均值算：W / M 多的名字会挤爆版面，I / J 多的那种又白白浪费三分之一空间。
 *   所以直接拿 canvas 的 measureText 量真实字形宽度 ——
 *   同一个字体栈、同一个字号，量出来就是浏览器真的会画多宽。
 *
 * canvas 取不到（老环境）时退回"每字符 0.72em"的经验值：
 * 宁可小一点，也不能压到面板上。
 */
const idEmWidth = computed(() => {
  const text = idChars.value.join('')
  if (!text) return 0

  // ★ 依赖 fontReady：字体到位后会重新算一次。
  //   字体没就绪时下面量到的是回落字体的宽度，先渲染出来，
  //   等字体好了再默默纠正 —— 用户看到的只是字号"轻轻对了一下"。
  void fontReady.value

  let glyphs = 0
  try {
    const ctx = document.createElement('canvas').getContext('2d')
    if (ctx) {
      // ★ 带上字重：Playfair Display 是真字重，不同字重宽度不同，漏了就量错
      ctx.font = `${ID_WEIGHT} ${PROBE_SIZE}px ${ID_FONT}`
      glyphs = ctx.measureText(text).width / PROBE_SIZE
    }
  } catch {
    glyphs = 0
  }
  if (!glyphs) glyphs = text.length * 0.72

  // 字距：letter-spacing 在**每个**字符后面都会加一份，末字也不例外
  const tracked = glyphs + text.length * ID_TRACKING

  // ★ 再留 5% 余量。canvas 量的是字形宽度，真实排版还会有 kerning 之类的细微差别。
  //   宁可小 5%，也不能因为算大了一点点被切掉半个字母 ——
  //   蓝图的 .panel 是半透明白底（--surface: rgba(255,255,255,.62)），
  //   溢出去的部分会**透出来**，比字小一点难看得多。
  return tracked * 1.05
})

/**
 * 相邻两个字符之间的间隔。
 *
 * ★ 这个值不能固定。原本写死 70ms，结果 15 字的用户名要 0.98 秒才排完、
 *   最后一个字 1.66 秒才露面 —— 装饰抢在了内容前面，等得人心焦。
 *   所以改成"总时长封顶 620ms"：短 ID（≤9 字）保持 70ms 的从容节奏，
 *   长 ID 自动压缩间隔。字符越多、每个越快，整体像是"一扫而过"。
 */
const idStep = computed(() => {
  const n = idChars.value.length
  return n > 1 ? Math.min(70, Math.round(620 / n)) : 70
})

/** 一股脑绑到元素上的几个自定义属性（避免模板里堆一长串内联样式） */
const idStyle = computed(() => ({
  '--step': idStep.value + 'ms',
  '--em': String(idEmWidth.value),
  '--id-font': ID_FONT,
  '--id-weight': String(ID_WEIGHT),
}))
</script>

<template>
  <div class="decor" aria-hidden="true" :style="{ '--content-w': contentWidth + 'px' }">
    <div class="d-paper"></div>

    <!-- 几何动效层：作图线生长 / 椭圆弧扫描 / 关节环扩张。
         压在所有素材之下 —— 它是"底稿"，先有作图线，再有图。 -->
    <ThemeMotion />

    <div class="d-axis"></div>
    <div class="d-tick d-tick--1"></div>
    <div class="d-tick d-tick--2"></div>
    <div class="d-tick d-tick--3"></div>

    <div class="d-arc"></div>

    <div class="d-cross d-cross--tl"></div>
    <div class="d-cross d-cross--tr"></div>
    <div class="d-cross d-cross--bl"></div>
    <div class="d-cross d-cross--br"></div>

    <div class="d-star d-star--l"></div>
    <div class="d-star d-star--a"></div>
    <div class="d-star d-star--b"></div>

    <div class="d-corner"></div>
    <div class="d-decal"></div>
    <div class="d-serial"></div>
    <div class="d-vert"></div>

    <div class="d-bird d-bird--r"></div>
    <div class="d-bird d-bird--l"></div>

    <!-- 身份标识：取代原先那两个无含义的衬线字母 E / S。
         横排、字号自适应，位置守在原来 E 的那一格。
         只在有 ID 时出现；空输入时这里**整块留白**，不回落成字母 ——
         要的是"没有字母"，不是"换个字母"。 -->
    <div v-if="hasId" class="d-id" :style="idStyle">
      <span
        v-for="(ch, i) in idChars"
        :key="i"
        class="d-id-ch"
        :style="{ '--i': String(i) }"
        >{{ ch }}</span
      >
    </div>

    <div class="d-title"></div>
  </div>
</template>

<style scoped>
/* ==================================================================
 * 整层
 * ================================================================== */

/* 铺满整个页面外壳，不吃鼠标事件。
   ★ 显隐的唯一开关是 --decor（tokens.css 按主题给值）。
   用 opacity 而不是 display:none：切换主题时能淡入淡出，
   硬切会像"啪"地贴上去一层纸。
   注意 opacity 到 0 也还在渲染，但这一层元素不多、又全是静态背景图，
   合成开销可以忽略；换来的是切换动画，这笔账划算。 */
.decor {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
  opacity: var(--decor);
  transition: opacity var(--dur-slow) var(--ease-out);
}

/* ==================================================================
 * 纸纹底
 * ================================================================== */

/* 平铺近白色的纸纹。它不是"背景色"（背景色由 --bg-grad 给），
   而是一层贴在颜色之上的肌理，所以用 repeat 而不是 cover。
   ★ 尺寸必须写成素材的原始尺寸 512px：素材中位亮度只有 246（几乎全白），
     但含低频竖纹；一旦缩到 340px 平铺，竖纹被相对放大并开始自我叠加，
     整页会泛起"水渍状"的脏感 —— 这是实测踩过的坑，不是猜的。 */
.d-paper {
  position: absolute;
  inset: 0;
  background-image: url('../assets/theme/paper.png');
  background-repeat: repeat;
  background-size: 512px 512px;
  opacity: 0.15;
}

/* ==================================================================
 * 左外栏：纵轴 + 刻度
 * ================================================================== */

/* 纵轴。630×520 是按素材原始比例算的（原图 44×762，等比缩到宽 30）。 */
.d-axis {
  position: absolute;
  left: 22px;
  top: 50%;
  width: 30px;
  height: 520px;
  transform: translateY(-50%);
  background: url('../assets/theme/axis-v.png') center / 100% 100% no-repeat;
  opacity: 0.42;
}

/* 刻度：素材里已经带好了原作那种 "y1 / -y1" 的小标注，直接用，不要重画。
   background-position 用 left center，让刻度线贴着轴线那一侧对齐。
   left 给 44 是为了紧挨着纵轴（纵轴占 22~52）但又不压在它身上。 */
.d-tick {
  position: absolute;
  left: 44px;
  background-repeat: no-repeat;
  background-position: left center;
  background-size: contain;
  opacity: 0.4;
}

.d-tick--1 {
  top: 30%;
  width: 34px;
  height: 68px;
  background-image: url('../assets/theme/tick-1.png');
}

.d-tick--2 {
  top: 48%;
  width: 29px;
  height: 62px;
  background-image: url('../assets/theme/tick-2.png');
}

.d-tick--3 {
  top: 66%;
  width: 31px;
  height: 65px;
  background-image: url('../assets/theme/tick-3.png');
}

/* ==================================================================
 * 上横带：细弧
 * ================================================================== */

/* 上横带：细弧。top 给 74 是为了让开顶栏（顶栏下沿约在 62px），
   压在边框上会看起来像"两条线打架"。 */
.d-arc {
  position: absolute;
  top: 74px;
  right: 24px;
  width: 360px;
  height: 41px;
  background: url('../assets/theme/arc-line.png') right center / contain no-repeat;
  opacity: 0.5;
}

/* ==================================================================
 * 四角十字
 * ================================================================== */

/* 接管 Terminal 皮肤里那组 CSS 准星的位置（main.css 里
   `html[data-theme='blueprint'] .crosshair { display: none }` 给它让的位）。
   这不是"删掉一个装饰"，是"换一种测绘符号"。
   top/bottom 给 86 而不是紧贴边缘：顶栏和底栏各有一条 1px 分隔线，
   十字压上去会看起来像排版错位。 */
.d-cross {
  position: absolute;
  width: 44px;
  height: 44px;
  background: url('../assets/theme/cross-02.png') center / contain no-repeat;
  opacity: 0.38;
}

.d-cross--tl {
  top: 86px;
  left: 40px;
}

.d-cross--tr {
  top: 86px;
  right: 40px;
}

.d-cross--bl {
  bottom: 86px;
  left: 40px;
}

.d-cross--br {
  bottom: 86px;
  right: 40px;
}

/* ==================================================================
 * 星形交汇点
 * ================================================================== */

.d-star {
  position: absolute;
  background-repeat: no-repeat;
  background-size: contain;
  background-position: center;
}

.d-star--l {
  top: 14%;
  right: 7%;
  width: 120px;
  height: 120px;
  background-image: url('../assets/theme/star-l.png');
  opacity: 0.16;
}

.d-star--a {
  bottom: 22%;
  left: 11%;
  width: 38px;
  height: 38px;
  background-image: url('../assets/theme/star-01.png');
  opacity: 0.3;
}

.d-star--b {
  bottom: 34%;
  right: 15%;
  width: 30px;
  height: 30px;
  background-image: url('../assets/theme/star-01.png');
  opacity: 0.26;
}

/* ==================================================================
 * 方块节点与标签碎片
 * ================================================================== */

/* 原作用于标注元件编号。这里只保留形状，不夹带任何原文案。 */
.d-corner {
  position: absolute;
  right: 9%;
  bottom: 13%;
  width: 26px;
  height: 26px;
  background: url('../assets/theme/corner-square.png') center / contain no-repeat;
  opacity: 0.34;
}

.d-decal {
  position: absolute;
  left: 74px;
  bottom: 17%;
  width: 50px;
  height: 27px;
  background: url('../assets/theme/dec-02.png') left center / contain no-repeat;
  opacity: 0.42;
}

.d-serial {
  position: absolute;
  right: 13%;
  top: 29%;
  width: 76px;
  height: 15px;
  background: url('../assets/theme/dec-04.png') center / contain no-repeat;
  opacity: 0.34;
}

.d-vert {
  position: absolute;
  right: 30px;
  top: 34%;
  width: 33px;
  height: 41px;
  background: url('../assets/theme/dec-03.png') center / contain no-repeat;
  opacity: 0.34;
}

/* ==================================================================
 * 飞鸟剪影
 * ================================================================== */

/* 原作首页里它们是真的在飞的。这里做成静态剪影 ——
   理由：这一层的定位是"图纸"，而图纸不会动。
   想让它动的话，给 .d-bird 加一条几十秒的横向漂移即可。
   ★ 不透明度压到 0.3 以下：素材本身是 #5b7b8b 这种偏深的蓝灰，
     放到浅底上会比预期的重得多，0.45 就已经糊成一团。 */
.d-bird {
  position: absolute;
  background-repeat: no-repeat;
  background-size: contain;
  background-position: center;
}

.d-bird--r {
  top: 20%;
  right: 5%;
  width: 142px;
  height: 153px;
  background-image: url('../assets/theme/bird-right.png');
  opacity: 0.28;
}

.d-bird--l {
  top: 57%;
  left: 9%;
  width: 96px;
  height: 151px;
  background-image: url('../assets/theme/bird-left.png');
  opacity: 0.26;
}

/* ==================================================================
 * 身份标识（取代原先那组衬线字母 E / S）
 * ==================================================================
 * 横排、单行、字号自适应 —— 位置和尺寸都跟着 E 原来那一格走。
 *
 * ★ 横向起点 92px 是 E 的 left：左外栏里纵轴占 22~52、刻度到 78，
 *   92 正好落在它们右侧，既不压轴线也不贴着刻度。
 * ★ 纵向对齐 E 的**垂直中心**：E 从 26% 开始、高 126px，
 *   所以中心在 `26% + 63px`（不是 26%，那是 E 的顶边）。
 *
 * 颜色沿用 --ink（和原来的字母、主题切换器选中态同一个值），
 * 所以"保持颜色"这条自动成立：Terminal 深灰、Blueprint 蓝，不用分主题写。
 */
.d-id {
  /* 内容列左右两侧各剩下多少（等价于 (100vw - 内容列宽) / 2）。
     ★ 用 100vw 而不用 100%：百分比在 font-size 的算式里会被解释成
     "相对父级字号"，而这里要的是相对**视口宽度** —— 只有 vw 能做到。
     代价是 100vw 在出现纵向滚动条时会比实际可用宽度大一点点（约 15px），
     所以下面 --avail 里刻意留了 20px 的呼吸位来吸收它。 */
  --gutter: calc((100vw - var(--content-w)) / 2);
  /* 减掉纵轴与刻度占掉的 92px，再留 20px，让字不贴着面板边框 */
  --avail: calc(var(--gutter) - 92px - 20px);

  position: absolute;
  left: 92px;
  top: calc(26% + 63px);
  transform: translateY(-50%);
  white-space: nowrap; /* 用户名绝不换行 —— 换行就不是"横排打出来"了 */
  font-family: var(--id-font);
  font-weight: var(--id-weight);
  /* 字号 = 可用宽度 ÷ 这串 ID 实际占的 em 数（--em 由脚本量出来传进来）。
     上限 52px：短 ID 不至于撑得比原来的 E 还大，把版面压垮。
     下限 2px 只是防负数（窗口窄到面板已经越过 92px 时 --avail 会为负），
     那种情况靠下面的 max-width 把它整块收掉，而不是让它溢出去。 */
  font-size: max(2px, min(52px, calc(var(--avail) / var(--em))));
  letter-spacing: 0.05em;
  line-height: 1.2;
  /* 上下各留 0.3em：给打字机动效里"从下方升起来"的字符让出空间 ——
     不留的话，overflow: hidden 会把正在升起的字从下边缘切掉一截。 */
  padding: 0.3em 0;
  color: var(--ink);
  opacity: 0.5;
  /* ★ 兜底闸门。字号是按 canvas 量出的宽度算的，万一字体文件没加载成功、
     落到了 Georgia 这种度量不同的回落字体上，这里保证它最多铺到余量边界为止，
     绝不会伸到面板底下 —— Blueprint 的 .panel 是半透明白底，溢出的字会透出来。
     --avail 为负时收到 0，整块自然隐没。 */
  max-width: max(0px, var(--avail));
  overflow: hidden;
}

/* 打字机就是这么来的：每个字符一个 span，靠 --i 把延迟逐个错开。
   --step（相邻间隔）由脚本按字符数算好传进来 —— 见 idStep 的说明，
   写死 70ms 会让长用户名排到 1.6 秒才结束。
   起点延迟 0.34s 是**等装饰层自己淡入完**再开始 ——
   否则字会先于底图出现，看起来像"贴上去的"，不是"画出来的"。
   曲线用 --ease-out（tokens 里的"① 机械"性格），不用弹跳。
   transform-origin 定在左下角：每个字从自己的左下角长出来，像被"写"上去。 */
.d-id-ch {
  display: inline-block;
  transform-origin: 0 100%;
  animation: id-type 0.34s var(--ease-out) both;
  animation-delay: calc(var(--i) * var(--step, 70ms) + 0.34s);
}

/* 位移和缩放都用 em 而不是 px：字号是算出来的，em 能跟着一起缩放。
   写死 px 的话，大字号的"升起"会显得太轻，小字号又会顶出上边。 */
@keyframes id-type {
  from {
    opacity: 0;
    transform: translateY(0.28em) scale(0.84);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ==================================================================
 * 标题水印
 * ================================================================== */

/* Blueprint 下替掉 AppShell 里那个 "GENJITSU" 文字水印（那边会 display:none）。
   位置和原水印一致，压在内容下面；不透明度压到 0.12，
   比 Terminal 的 rgba(62,66,71,.035) 略强一点 —— 因为它是张带细节的图，
   太淡就完全看不见了。 */
.d-title {
  position: absolute;
  top: 44%;
  left: 50%;
  width: 300px;
  height: 143px;
  transform: translate(-50%, -50%);
  background: url('../assets/theme/event-title.png') center / contain no-repeat;
  opacity: 0.15;
}

/* ==================================================================
 * 窄屏兜底
 * ==================================================================
 * 主页的版面是 920px。视口一旦小于 1024px，左右只剩不到 30px 的余量，
 * 左外栏那一组（纵轴 / 刻度 / 标签 / 用户 ID / 左下飞鸟）就会压到内容上。
 * 这时候只保留上横带、四角十字和右侧的零星元素 —— 装饰可以少，
 * 但不能和文字抢地方。
 * ================================================================== */
@media (max-width: 1023px) {
  .d-axis,
  .d-tick,
  .d-decal,
  .d-id,
  .d-bird--l,
  .d-vert {
    display: none;
  }
}
</style>
