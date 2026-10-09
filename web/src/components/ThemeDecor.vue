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
 * ★ 空值时**不回落成原来的字母，也不彻底留白** —— 只留末尾那个闪烁的下划线。
 *   那两个字母本来就是"没有含义"才被换掉的，留着当兜底等于白换一场；
 *   但整块留白又太哑：这一格在登录/注册页就是"名字输入位"的镜像，
 *   空着应该像终端一样**闪着等着你敲**，而不是什么都没有。
 *   所以空输入 = 一条在起点闪动的光标（字符数为 0，字号算式自动只算它这一笔）。
 *   字体仍用衬线体 —— 换掉内容，但不换调子。
 *
 * ★ **两种节奏，别混**（脚本里"打字机的这一批"那一节是全部依据）：
 *   ① 整串一次性揭示（主页核验回来的用户名 / 粘贴）→ 逐字错峰，光标等末字落定才登场；
 *   ② 用户逐键敲 → 该字符**立刻落笔**，光标一直亮着、贴在已出现文字的末尾。
 *   混掉的后果实测过：每多敲一个字就多等一格（第 5 个字符 928ms 才露面），
 *   而光标早已蹲在整串终点上（探针实测超前 115px、55.6% 的帧都超前）——
 *   既不像打字机，更不像输入框。
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
import { computed, onMounted, ref, watch } from 'vue'
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

/**
 * 末尾那个「待输入」光标占用的横向宽度（em）。
 *
 * ★ 它只用于量宽，不参与渲染 —— 但**必须**补进 idEmWidth，理由是：
 *   `.d-id` 上有 `max-width: max(0px, var(--avail))` + `overflow: hidden`
 *   这道兜底闸门，而字号又正是按 --em 反算出来的。
 *   漏算这一笔，光标就会正好悬在右边界外被裁掉。
 *   ⚠️ 而且它**只裁长 ID**（5% 余量被摊得越薄越不够用），短 ID 反而看不出问题 ——
 *   这类"只在边界条件上发作"的错最难查，所以宁可多留一点。
 *
 * 对应 CSS 里 .d-id-caret 的 `0.36em` 宽 + `0.04em` 左边距。
 */
const CARET_EM = 0.4

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

/**
 * ================== 打字机的"这一批" ==================
 *
 * 错峰揭示只该发生在**一次性出现一整串**的时候（主页核验回来的用户名、粘贴）。
 * 用户逐键敲进来时，每个字符都该**立刻落笔**。
 *
 * ★ 原来的实现没区分这两件事：--i 用的是**绝对下标**，而 span 是敲键那一刻才新建的，
 *   于是每多敲一个字就多等一格 —— 探针实测第 5 个字符要 928ms 才露面，
 *   而光标早就在整串末尾等着了（超前最多 115px，55.6% 的帧都超前）。
 *   既不像打字机，更不像输入框，只是"延迟随长度线性增长"。
 *
 * 所以这里算出"这一批新增的是哪一段"，--i 改成**相对本批**的序号：
 * 单键敲下的字符 --i = 0（立刻动），整串设进来才 0,1,2… 错峰。
 * 旧字符拿到负数 --i，延迟只会更小 —— 它们的动画早就跑完、停在终态，不会被重放。
 */
const revealFrom = ref(0)
const revealCount = ref(idChars.value.length) // 首次渲染就当"一批"（主页可能一上来就有名字）
/** 只有"整段揭示"才换这个 key —— 见模板里光标的 :key 说明。 */
const caretGen = ref(0)

watch(idChars, (now, before) => {
  const b = before ?? []
  let common = 0
  while (common < now.length && common < b.length && now[common] === b[common]) common++
  revealFrom.value = common
  revealCount.value = Math.max(0, now.length - common)
  // 单个字符的即时敲击**不**让光标重新登场：输入框里那根线不该一闪一闪地消失。
  if (revealCount.value > 1) caretGen.value++
})

/** 用户正在逐键敲（既不是空白态，也不是整串一次性揭示）。 */
const isLive = computed(() => revealCount.value === 1 && idChars.value.length > 0)

/**
 * 量一段文字在当前字体下占多少「em」（1em = 一个字号）。
 *
 * ★ 为什么不用"每字符平均宽度 × 字符数"估？
 *   大写衬线体的字宽从 I 的 0.33em 到 W 的 0.94em，差了近三倍。
 *   按平均值算：W / M 多的名字会挤爆版面，I / J 多的那种又白白浪费三分之一空间。
 *   所以直接拿 canvas 的 measureText 量真实字形宽度 ——
 *   同一个字体栈、同一个字号，量出来就是浏览器真的会画多宽。
 *
 * canvas 取不到（老环境）时退回"每字符 0.72em"的经验值：宁可小一点，也不能压到面板上。
 *
 * ★ 结果里**已经含字距**（letter-spacing 在**每个**字符后面都会加一份，末字也不例外）。
 * ★ 字号（idEmWidth）和"未落定字符让位"（pendingCharEm）共用这一套度量 ——
 *   两处各量各的话，哪天改了字体就会一处对一处错，而且错得很隐蔽。
 */
function measureEm(text: string): number {
  if (!text) return 0

  // ★ 依赖 fontReady：字体到位后会重新算一次。
  //   字体没就绪时量到的是回落字体（Georgia）的宽度，先渲染出来，
  //   等字体好了再默默纠正 —— 用户看到的只是"字号轻轻对了一下"。
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

  return glyphs + text.length * ID_TRACKING
}

/** 整串 ID 的宽度 + 末尾光标那一笔。空串也有值（只剩光标），见下面的 ⚠️。 */
const idEmWidth = computed(() => {
  // ⚠️ 空串**不能提前 return 0**。--em 是字号算式的除数（calc(--avail / --em)），
  //   除以 0 在 CSS 里属"计算值无效"，font-size 会整个失效掉回继承值 ——
  //   表现是光标突然缩成十几 px 的一小点，而且不报任何错。
  //   所以空串也照常往下走：measureEm('') 是 0，最后只剩 CARET_EM 那一笔 ——
  //   正好就是"空输入只剩光标"要的结果。
  //   （顺带一个好处：空串的 em 极小，字号会顶到 52px 上限，和输入一两个字符同一档 ——
  //     敲下第一个字符时字号不会跳。）
  //
  // ★ 再留 5% 余量。canvas 量的是字形宽度，真实排版还会有 kerning 之类的细微差别。
  //   宁可小 5%，也不能因为算大了一点点被切掉半个字母 ——
  //   蓝图的 .panel 是半透明白底（--surface: rgba(255,255,255,.62)），
  //   溢出去的部分会**透出来**，比字小一点难看得多。
  return (measureEm(idChars.value.join('')) + CARET_EM) * 1.05
})

/**
 * 本批新增的那个字符占多宽（em）—— 交给 CSS 让它**先不占地方**（见 .d-id-ch 的 id-occupy）。
 *
 * ★ 这是"输入框手感"的关键：新字符刚落笔时不让位，它右边的所有东西（包括末尾那根光标）
 *   就还停在原来的位置 —— 光标于是正好贴在**已经出现的那段文字**末尾，
 *   再随这个字一起往右让开。
 *   整串揭示时给 0：那时字符是错峰落位的，逐个让位会互相压住
 *   （前一个还没让开，后一个已经挤上来了）。
 */
const pendingCharEm = computed(() =>
  isLive.value ? measureEm(idChars.value[idChars.value.length - 1] ?? '') : 0,
)

/**
 * 相邻两个字符之间的间隔。
 *
 * ★ 这个值不能固定。原本写死 70ms，结果 15 字的用户名要 0.98 秒才排完、
 *   最后一个字 1.66 秒才露面 —— 装饰抢在了内容前面，等得人心焦。
 *   所以改成"总时长封顶 620ms"：短 ID（≤9 字）保持 70ms 的从容节奏，
 *   长 ID 自动压缩间隔。字符越多、每个越快，整体像是"一扫而过"。
 *
 * ★ 按**本批新增的个数**算，不是总长度：单键敲击时这一批只有 1 个字符，间隔无关紧要。
 */
const idStep = computed(() => {
  const n = revealCount.value
  return n > 1 ? Math.min(70, Math.round(620 / n)) : 70
})

/** 一股脑绑到元素上的几个自定义属性（避免模板里堆一长串内联样式） */
const idStyle = computed(() => ({
  '--step': idStep.value + 'ms',
  '--em': String(idEmWidth.value),
  // **本批新增的字符个数**（不是总数）。末尾光标的出场时机按它算：
  // (n-1) * --step + 末字的起点与时长 —— 见 CSS 里的 --caret-delay。
  // 把个数交给 CSS、公式整条留在样式里，改一处就够。
  // ⚠️ 空白态这里是 0，CSS 那边必须 max(n - 1, 0)，否则会算出一个负的延迟。
  '--n': String(revealCount.value),
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
         ★ 一个字都没输入时**也照样渲染这一块** —— 此时它只剩末尾那个闪动的
         下划线，落在起点（第一个字符将要出现的地方），就是"这里可以输入"的信号。
         所以这里没有 v-if：给空值留白等于把信号也一起留掉了。
         d-id--live = 用户正在逐键敲（见 isLive）：字符立刻落笔，光标不重新登场。 -->
    <div class="d-id" :class="{ 'd-id--live': isLive }" :style="idStyle">
      <span
        v-for="(ch, i) in idChars"
        :key="i"
        class="d-id-ch"
        :style="{
          '--i': String(i - revealFrom),
          '--w': i === idChars.length - 1 ? pendingCharEm + 'em' : '0em',
        }"
        >{{ ch }}</span
      >
      <!-- 末尾那个「待输入」光标：一条一直在闪的下划线。
           正常情况它跟在最后一个字符后面；一个字都没输入时它落在起点。
           ★ :key 只在**整段揭示**时变化（caretGen）——
           那时光标要"等末字落定"才登场，所以得让动画重新开始计时；
           而单个字符的敲击**不**换 key：输入框里那根线应该一直在，不该一闪一闪地消失。
           它不在 idChars 里，所以不参与打字的错峰，也不参与量宽
           （宽度单独补在 idEmWidth 里，否则会被 .d-id 的 overflow: hidden 裁掉）。 -->
      <span :key="caretGen" class="d-id-caret"></span>
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

  /* ★ 打字的时间常量。抽出来是因为末尾的光标要**算**出"末字什么时候落定"。
     写成字面量也行，但那个数是从这两条推出来的；
     一旦有人把 --type-dur 调了，光标就会悄悄地早到或迟到，很难发现。 */
  --type-lead: 0.34s; /* 起点偏移：等装饰层自己淡入完再开始打字 */
  --type-dur: 0.34s; /* 单个字符从自己左下角升起来的时长 */
  /* ★ 光标什么时候登场。"末字落定那一刻" = 末字的起点 (n-1) * --step + --type-lead，
     再加它自己的时长。n 是**本批新增**的字符数（见脚本里的 revealCount）。
     max(n - 1, 0) 是给空白态准备的：那时 --n = 0，压根没有"末字"，
     光标自己就是这一行的第一个东西，让它等价于 n = 1。
     少了这个 max 会算出负延迟（-70ms + 0.68s），虽然也能跑，但式子读起来就是错的。 */
  --caret-delay: calc(
    max(var(--n, 1) - 1, 0) * var(--step, 70ms) + var(--type-lead) + var(--type-dur)
  );

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
     那种情况靠下面的 max-width 把它整块收掉，而不是让它溢出去。
     ★ 空输入时 --em 只剩光标那一笔（约 0.42），会顶到 52px 上限 ——
     和输入一两个字符同一档，所以敲下第一个字符时字号不会跳
     （空串绝不能返回 em = 0，那是除零，见 idEmWidth）。 */
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

/* ==================================================================
 * 逐键敲击（不是整串揭示）
 * ==================================================================
 * 用户正在一个字一个字地敲进输入框 —— 此时**不能**按打字机的节奏走：
 *
 * ★ 字符必须立刻落笔。原来的实现在这里每敲一个字要多等一格
 *   （延迟 = 下标 × --step + 0.34s），第 5 个字要 928ms 才露面。
 *   探针实测过：这既不像打字机，也不像输入框。
 *   现在 --i 是相对本批的序号（单键 = 0），于是延迟只剩这一条 0.06s 的小起手。
 * ★ 时长压到 0.18s：0.34s 是"揭示一整串"的从容节奏，逐键敲击要的是跟手。
 * ★ 光标 --caret-delay: 0s —— 它不该等末字落定，那 0.18s 里它正在
 *   **贴着已出现的文字** 往右让位（靠 .d-id-ch 的 id-occupy），
 *   等一等再出现反而成了"闪一下就不见"。
 * ================================================================== */
.d-id--live {
  --type-lead: 0.06s;
  --type-dur: 0.18s;
  --caret-delay: 0s;
}

/* 打字机就是这么来的：每个字符一个 span，靠 --i 把延迟逐个错开。
   --i 是**相对本批**的序号（见脚本里的 revealFrom）：
   整串揭示时是 0,1,2…（错峰），用户敲单键时它就是 0（立刻动）。
   --step（相邻间隔）由脚本按本批字符数算好传进来 —— 见 idStep 的说明。
   起点延迟是 --type-lead：**等装饰层自己淡入完**再开始 ——
   否则字会先于底图出现，看起来像"贴上去的"，不是"画出来的"。
   曲线用 --ease-out（tokens 里的"① 机械"性格），不用弹跳。
   transform-origin 定在左下角：每个字从自己的左下角长出来，像被"写"上去。

   ★ 第二条动画 id-occupy 是"输入框手感"的关键：还没落定的字符**先不占地方**，
     margin-right 从 -自己宽度 涨到 0，于是它右边的所有东西（含末尾光标）
     随着它一起往右让位。光标因此永远贴在**已经出现的那段文字**末尾，
     而不是提前蹲在整串的终点上。
     --w 由脚本按 canvas 量出的字形宽度给，只有"刚敲下的那一个字"才非零；
     整串揭示时是 0（那种情况字符是错峰落位的，逐个让位会互相压住）。
     ⚠️ 这条动画动的是 margin（会触发布局），但它是**一次性、单元素、0.18s**，
     不是循环动画 —— 和"循环里不许动 width/margin"那条禁令不冲突。 */
.d-id-ch {
  display: inline-block;
  transform-origin: 0 100%;
  margin-right: calc(-1 * var(--w, 0em));
  animation:
    id-type var(--type-dur) var(--ease-out) both,
    id-occupy var(--type-dur) var(--ease-out) both;
  animation-delay: calc(var(--i) * var(--step, 70ms) + var(--type-lead));
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
 * 末尾的「待输入」光标
 * ==================================================================
 * 一条一直在闪的下划线，跟在打出来的 ID 后面，像终端等着你继续敲。
 * 一个字都没输入时它落在**起点**（第一个字符将要出现的位置）——
 * 那一格于是变成"闪着等你敲"，而不是一片空白。
 *
 * ★ 为什么拆成两层（span + ::before）而不是一个元素一个动画？
 *   "什么时候登场"和"一直闪"是两件事，都想动 opacity，
 *   压在一个元素上会互相覆盖。现在外层管登场、::before 管闪，
 *   两层 opacity 相乘 —— 淡入期间光标跟着一起亮起来，之后就是纯粹的闪。
 *
 * ★ 外层是 animation-fill-mode: both，所以**延迟期间它停在 0% 的 opacity: 0 上**。
 *   这个延迟（--caret-delay）在两种语境下含义不同：
 *
 *   ① 整串揭示（主页核验回来的用户名 / 粘贴）：延迟 = 末字落定那一刻。
 *      字符是靠 opacity 进的场，而 `.d-id` 是 nowrap 的行内块，**整串宽度一上来就铺满**，
 *      光标若提前亮相就会蹲在整行最右端，离正在打字的位置隔着十几个字，像个 bug。
 *      ★ 让它在这种语境下"重新登场"靠的是模板里的 :key（caretGen）——
 *        同一个元素上改 animation-delay 不会重新计时。
 *
 *   ② 用户逐键敲（.d-id--live）：延迟是 0，光标**一直亮着、从不消失** ——
 *      那才是输入框里的竖线。它的位置也**不靠延迟去追**，而是靠 .d-id-ch 的
 *      id-occupy（未落定的字符先不占地方）自然落在已出现文字的末尾。
 *      ⚠️ 这两件事必须一起做：只加"未落定不占位"而延迟还留着，
 *         光标就会每隔一下消失一次；只改延迟而占位照旧，它又会提前跑到终点。
 *
 * ★ 空白态（一个字都没输入）靠**同一套公式**自动成立，不需要第三套逻辑：
 *   --n = 0，max(n - 1, 0) 把它压成 0，延迟就是 --type-lead + --type-dur ——
 *   等装饰层淡入完，它就亮起来。此时行宽只剩光标自己，于是它正好落在起点。
 *
 * ★ 下划线用 ::before 画，不用真的键盘字符 `_`：
 *   这里字号是 2~52px 之间算出来的动态值，`_` 的位置和粗细由字体度量决定，
 *   字号一小它就跟着糊掉；画一根 0.06em 的条子反而处处一致。
 *   顺带和 AppShell 底栏那个方块光标保持了一致：本项目里的光标都是画出来的。
 *   ⚠️ 宽高不能只写 em：字号下限是 2px，那时 0.06em 只有 0.12px，会直接消失，
 *   所以用 max() 兜住一个 1px 的地板。
 *
 * ★ 节奏 1.1s：比底栏那个方块光标（1.4s）稍快。两者离得远，
 *   不会撞成"两个频率打架"（那条教训记在 HomeView 的 .life-fill--low 上）。
 * ================================================================== */
.d-id-caret {
  display: inline-block;
  margin-left: 0.04em;
  /* 负值 = 相对基线往下挪。用长度而不是 baseline 关键字：
     inline-block 的 baseline 各家算法有细微差别，这里要的是确定的落点。
     0.08em ≈ 让这条线的顶边刚好贴着基线 —— 就是下划线该在的位置。 */
  vertical-align: -0.08em;
  /* 出场时机见上面那一大段说明 + .d-id 里的 --caret-delay。
     兜底值 0.68s = --type-lead + --type-dur（空白态那条路径）。 */
  animation: id-caret-in 0.18s var(--ease-out) both;
  animation-delay: var(--caret-delay, 0.68s);
}

.d-id-caret::before {
  content: '';
  display: block;
  width: max(2px, 0.36em);
  height: max(1px, 0.06em);
  /* currentColor 而不是写死颜色：ID 用的是 --ink，Terminal 深灰、Blueprint 蓝，
     光标自动跟着走，不用按主题写两遍。 */
  background: currentColor;
  animation: id-caret-blink 1.1s linear infinite;
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
