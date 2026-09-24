<script setup>
/**
 * 主页：登录成功之后看到的第一页。
 *
 * ============ ★ 这一页最重要的一条规则 ============
 *
 * **服务端核验通过之前，不渲染任何会话内容。**
 *
 * 本地 sessionStorage 里那份数据，只是"登录那一刻"后端给的快照 ——
 * 它可能是过期的、被改过的，或者这个账号早就不存在了。
 * 如果拿它直接把面板画出来，等于"先假装你已登录，再慢慢去问后端"：
 * 万一张票根本不能用，用户会先看到一个像模像样的面板，然后被弹走。
 *
 * 所以顺序是：核验中 → 通过才渲染面板。
 * 三种结果各有各的去处：
 *   通过 → 显示面板
 *   被后端拒绝（1005/1006/1007）→ request.js 清会话，送回登录页
 *   压根没问到后端（网络不通）→ 显示明确的错误态，**保留会话**、可以重试
 *
 * 最后那条尤其重要：**连不上后端 ≠ 没登录**。
 * 网络抖一下就替用户登出，是最典型也最招人烦的自伤 bug。
 *
 * ============ 版面本身 ============
 *
 * 分三层，从"概览"到"细节"：
 *   ① 身份卡   —— 我是谁 + 这张票还剩多久（一眼看全）
 *   ② 指标格   —— 四个关键事实，来自下面两栏的不同源头
 *   ③ 双栏对照 —— 左边是浏览器里存的（登录那一刻给的），
 *                 右边是服务端**此刻**确认的（GET /api/user/me 现问现答）
 *
 * ③ 里这两栏的区别，正好是里程碑 ③ 的核心：
 *   左边只是把前端 sessionStorage 里的东西念一遍；
 *   右边是真的带着 token 去问了一次后端，验了签名、看了过期时间，
 *   然后从数据库里查出来的资料 —— 这才叫"这张票现在确实管用"。
 *
 * 所以身份卡右侧那条"票的寿命"进度条，是看着左栏的数据在倒数，
 * 而"服务端已确认"的徽标是跟着右栏的结果走 —— 两者不是一回事，别混淆。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { getMe } from '../api/user'
import { session, clearSession } from '../utils/session'

const router = useRouter()

// ---------------- 通用小工具 ----------------
// 这几个纯粹是"把一个数字变成好看的字符串"，和业务无关，所以放在最上面。

const pad = (n) => String(n).padStart(2, '0')

/** 秒数 -> "1 小时" / "30 分" / "45 秒"（说给人听的） */
function toHumanDuration(seconds) {
  if (!seconds) return '—'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours) return `${hours} 小时`
  if (minutes) return `${minutes} 分`
  return `${seconds} 秒`
}

/**
 * 秒数 -> "00:45" / "1:05:32"（倒计时用）
 *
 * 必须补零到固定宽度：秒数每跳一次宽度就变一个字，
 * 数字会在原地左右抖动，看着很廉价。等宽字体 + 固定位数才稳。
 */
function toClock(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(s / 3600)
  const minutes = Math.floor((s % 3600) / 60)
  if (hours) return `${hours}:${pad(minutes)}:${pad(s % 60)}`
  return `${pad(minutes)}:${pad(s % 60)}`
}

/** 时间戳(毫秒) -> "2026-09-16 11:45" */
function toDateTime(timestamp) {
  if (!timestamp) return '—'
  const d = new Date(timestamp)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 时间戳(毫秒) -> "11:45"，只到分钟 */
function toTime(timestamp) {
  if (!timestamp) return '—'
  const d = new Date(timestamp)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ---------------- 从会话里取数据 ----------------
// 这些数据是登录成功时 LoginView 存进 utils/session.js 的，
// 也就是后端 /api/auth/login 返回体里的 data。
// computed 的意思是"跟着源数据自动算出来"，session 一变，这里就会重新算。

/** 浏览器里存的那个名字（下图左栏用） */
const localUsername = computed(() => session.value?.username ?? '—')

const tokenType = computed(() => session.value?.tokenType ?? '—')
const expiresIn = computed(() => session.value?.expiresIn ?? 0)
const expiresText = computed(() => toHumanDuration(expiresIn.value))

// 令牌只显示"头 + 尾"。JWT 有三个点分段，掐掉中间既能看出它是个真 JWT，
// 又不会把版面撑坏。
// （真实项目里 token 不应该渲染到页面上，这里只是为了让你看清流程。）
const tokenPreview = computed(() => {
  const token = session.value?.accessToken
  if (!token) return '—'
  if (token.length <= 24) return token
  return `${token.slice(0, 16)}…${token.slice(-6)}`
})

const loggedInAtText = computed(() => toDateTime(session.value?.loggedInAt))
const startedClock = computed(() => toTime(session.value?.loggedInAt))

// ---------------- 会自己跳动的"现在" ----------------
// 一个 ref 存当前时间，定时器每秒钟把它更新一次，
// 所有依赖它的 computed 就会自动重算 —— 这就是 Vue 响应式的用处：
// 我们只负责改数据，不用手写"去更新那个 DOM 节点"。

const now = ref(Date.now())
let ticker = null

/** 这张票还剩多少秒（用签发时刻 + 有效期，减掉已经过去的时间） */
const remainingSeconds = computed(() => {
  const startedAt = session.value?.loggedInAt
  if (!startedAt || !expiresIn.value) return 0
  return Math.max(0, expiresIn.value - (now.value - startedAt) / 1000)
})

const remainingText = computed(() => toClock(remainingSeconds.value))

/** 剩余比例，用来画进度条（夹在 0~100 之间，防止时间漂移算成负数） */
const lifePercent = computed(() => {
  if (!expiresIn.value) return 0
  return Math.max(0, Math.min(100, (remainingSeconds.value / expiresIn.value) * 100))
})

/** 顶栏右下角那行小字用的时钟 */
const clockText = computed(() => {
  const d = new Date(now.value)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

// ==================================================================
// 闸门：向服务端核验身份（里程碑 ③ 的核心）
// ==================================================================
//
// 这一页有三种状态，任何时刻只可能是其中一种。
// 用**一个** status 变量表达，而不是 isLoading + hasError 两个布尔量：
// 两个布尔量能拼出四种组合，其中"既在加载又出错了"是不可能的，
// 那种"不可能的状态"一旦被拼出来，就是 bug 的温床。
// 一个状态变量则从结构上杜绝了这件事。

const status = ref('checking') // 'checking' | 'verified' | 'error'
const errorText = ref('') // status==='error' 时给用户看的原因
const profile = ref(null) // 核验通过后，服务端返回的用户资料

/** 核验通过了才有的那个名字（身份卡和顶栏用它，左栏仍显示本地那份） */
const verifiedUsername = computed(() => profile.value?.username ?? localUsername.value)

// 头像方块里的那个字母：取用户名首字母大写
const initial = computed(() => verifiedUsername.value.slice(0, 1).toUpperCase())

/** 底栏那行状态文字也得跟着闸门走 —— 没核验过就说 "Active" 是在撒谎 */
const shellStatus = computed(
  () =>
    ({
      checking: 'Session / Verifying',
      verified: 'Session / Active',
      error: 'Session / Unreachable',
    })[status.value],
)

/**
 * 核验一次。挂载时自动跑一次，用户点"重试"时再跑来一次。
 *
 * 把它抽成函数（而不是把逻辑全塞在 onMounted 里）就是为了这个重试按钮 ——
 * 加载逻辑只写一份，谁想重新加载就调它。
 */
async function verify() {
  status.value = 'checking'
  errorText.value = ''

  try {
    const json = await getMe()

    if (json.code === 0) {
      profile.value = json.data
      status.value = 'verified'
      return
    }

    // 走到这里说明后端**明确拒绝**了这张票（1005 没带 / 1006 过期 / 1007 无效）。
    // 此时 utils/request.js 已经清掉本地会话、并让 main.js 跳登录页了，
    // 所以下面这两行通常来不及显示。仍然分开写，是为了不把
    // "后端说不行" 和 "压根没问到后端" 混成同一件事 —— 它们的处理方式完全不同。
    errorText.value = json.message || '身份校验未通过'
    status.value = 'error'
  } catch (error) {
    // fetch 抛出的错误分两种，提示必须分开，否则会把排查方向带偏：
    //   - TypeError：请求根本没拿到响应 —— 后端没启动、端口写错、断网、
    //     或者被浏览器以跨域为由拦下（这种情况在 Network 面板里看得到请求发了，
    //     但 Console 会报 CORS，而 fetch 抛的是同一个 TypeError）；
    //   - 其它：后端答了，但答的内容没法用（这类错误由 request.js 自己抛，带说明文字）。
    errorText.value =
      error instanceof TypeError
        ? '无法连接后端服务。后端可能没启动，或地址不是 http://localhost:8080。'
        : error?.message || '请求失败'
    status.value = 'error'
  }
}

/**
 * onMounted = "这个组件被显示到页面上之后，执行一次"。
 *
 * 为什么核验放在 mounted 里而不是直接写在 setup 顶层？
 *   因为 setup 执行时组件还没挂载，而且顶层不能 await（会变成异步组件，
 *   需要 <Suspense> 包裹）。放在 onMounted 里最省心。
 */
onMounted(() => {
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 1000)

  verify()
})

/**
 * onBeforeUnmount = "组件即将被销毁时执行"。
 *
 * ★ 定时器必须在这里清掉，否则它会一直跑下去 ——
 *   组件已经不在页面上了，每秒还在改一个没人看的数据，
 *   这叫内存泄漏。开发时感觉不到，页面来回切几十次就明显卡了。
 *   凡是 onMounted 里 start 的东西，都要在这里 stop，成对出现。
 */
onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
})

// 服务端返回的 createdAt 形如 "2026-09-21T11:40:00"，
// 把中间的 T 换成空格、截到分钟，读起来顺眼一些。
const createdAtText = computed(() => {
  const raw = profile.value?.createdAt
  if (!raw) return '—'
  return String(raw).replace('T', ' ').slice(0, 16)
})

/** 指标格里的注册时间：日期短一截，才塞得进一格宽 */
const registeredShort = computed(() => {
  const raw = profile.value?.createdAt
  if (!raw) return '—'
  return String(raw).replace('T', ' ').slice(5, 16)
})

// ---------------- 指标格 ----------------
// 用数组 + v-for 而不是把四个格子抄四遍：
// 以后想加一格、调顺序、或者从后端多拿一个字段，只改这里。
// tag 标出这条数据的"出处"——local 是浏览器里存的，server 是刚问来的。

const tiles = computed(() => [
  { label: 'User ID', value: profile.value?.id ?? '—', tag: 'server' },
  { label: 'Token Type', value: tokenType.value, tag: 'local' },
  { label: 'Token TTL', value: expiresText.value, tag: 'local' },
  { label: 'Registered', value: registeredShort.value, tag: 'server' },
])

// ---------------- 退出登录 ----------------
function handleLogout() {
  // ★ 注意这里只有前端动作，**没有**调任何后端接口。
  //   原因：JWT 是无状态的，服务端压根没记录"这张票还在用中"，
  //   所以没有任何东西可以"注销"。把本地的 token 丢掉，
  //   浏览器下次就不会再带它了，效果上等同于登出。
  //
  //   ⚠️ 但这留下一个必须知道的事实：**被丢掉的 token 本身仍然有效**，
  //      只要还在有效期内，谁拿到它都能继续访问 ——
  //      比如它刚好被记在了某个日志里。
  //      所以有效期不能设太长（当前 1 小时）。
  //      真正要"立刻作废"，业界做法是维护一张黑名单（通常放 Redis），
  //      代价是牺牲了无状态带来的好处。
  clearSession() // 1. 清掉会话
  router.push({ name: 'login' }) // 2. 回登录页
}
</script>

<template>
  <AppShell :status="shellStatus">
    <!-- 顶栏右侧：当前用户 + 退出。
         只在核验通过后出现 —— 没核验就显示用户名，等于拿本地那份没验证过的数据
         当"已确认的身份"用，正是这一页要避免的事。 -->
    <template #aside>
      <!-- 注意条件写在插槽**里面**，不是 <template v-if ... #aside>。
           插槽名和 v-if 挂在同一个 <template> 上时行为并不明确，
           写在里面就没有任何歧义。 -->
      <div v-if="status === 'verified'" class="who-block">
        <span class="who">
          <span class="who-dot"></span>
          {{ verifiedUsername }}
        </span>
        <button class="btn btn--inline" @click="handleLogout">退出登录</button>
      </div>
    </template>

    <!-- .home 是这一页真正的"画布"：给整个页面一个最大宽度（920px），
         比登录页的 400px 宽一倍多，才放得下仪表盘式的排布。
         外面 AppShell 的 .stage 仍然负责水平居中。 -->
    <div class="home">
      <div class="stage-head">
        <span class="field-code">01 /</span>
        <span class="stage-title">Home</span>
        <span class="field-line"></span>
        <!-- 自己走的时钟，给静态版面加一点"活着"的感觉 -->
        <span class="stage-meta">{{ clockText }}</span>
      </div>

      <!-- ============================================================
          闸门 ①：核验中
          注意这里**不显示任何会话内容**，连用户名都不显示 ——
          本地那份数据此刻还没有被证明是有效的。
          ============================================================ -->
      <section v-if="status === 'checking'" class="panel panel--gate">
        <div class="panel-bar">Verifying</div>

        <div class="panel-body">
          <p class="hint">
            正在向服务端确认身份 —— 带上本地保存的凭证请求
            <code class="mono">/api/user/me</code>，确认通过后才会显示面板。
          </p>

          <div class="skeleton">
            <span></span><span></span><span></span>
          </div>
        </div>
      </section>

      <!-- ============================================================
          闸门 ②：问不到后端
          这是"核验失败"里唯一不该被当成"未登录"的一种。
          ============================================================ -->
      <section v-else-if="status === 'error'" class="panel panel--gate">
        <div class="panel-bar">Unverified</div>

        <div class="panel-body">
          <p class="status">{{ errorText }}</p>

          <p class="hint">
            这一页在<strong>服务端确认身份之前不会显示任何会话内容</strong>。
            连不上后端时，本地那张票到底还有没有效是无从判断的 ——
            所以这里既不显示面板，也不会替你登出。
            网络恢复后点重试即可；确实想换个账号，再退出登录。
          </p>

          <div class="actions">
            <button class="btn btn--inline btn--primary" @click="verify">重 试</button>
            <button class="btn btn--inline btn--ghost" @click="handleLogout">退出登录</button>
          </div>
        </div>
      </section>

      <!-- ============================================================
          核验通过 —— 到这里才允许渲染真正的面板
          ============================================================ -->
      <template v-else>
        <!-- ============ ① 身份卡 ============ -->
        <section class="panel panel--hero">
          <div class="panel-bar">Identity</div>

          <div class="panel-body panel-body--hero">
            <!-- 用户名首字母做成实心方块，替代头像位 -->
            <div class="monogram" aria-hidden="true">{{ initial }}</div>

            <div class="hero-text">
              <p class="hero-name">欢迎回来，{{ verifiedUsername }}</p>
              <div class="hero-sub">
                <!-- 面板能显示出来，就说明这一条已经成立了，所以是常量不是条件 -->
                <span class="chip">服务端已确认</span>

                <span class="hero-note">
                  带着 <code class="mono">Authorization</code> 头请求
                  <code class="mono">/api/user/me</code>
                </span>
              </div>
            </div>

            <!-- 凭证寿命：数字每秒跳一次，进度条跟着缩短 -->
            <div class="life">
              <div class="life-head">
                <span class="life-label">Token Life</span>
                <span class="life-value mono">{{ remainingText }}</span>
              </div>
              <div class="life-track">
                <div
                  class="life-fill"
                  :class="{ 'life-fill--low': lifePercent < 20 }"
                  :style="{ width: lifePercent + '%' }"
                ></div>
              </div>
              <div class="life-foot">
                <span>签发 {{ startedClock }}</span>
                <span>有效 {{ expiresText }}</span>
              </div>
            </div>
          </div>
        </section>

        <!-- ============ ② 指标格 ============ -->
        <div class="tiles">
          <div v-for="tile in tiles" :key="tile.label" class="tile">
            <div class="tile-head">
              <span class="tile-label">{{ tile.label }}</span>
              <span class="tile-tag">{{ tile.tag }}</span>
            </div>
            <span class="tile-value">{{ tile.value }}</span>
          </div>
        </div>

        <!-- ============ ③ 双栏：浏览器存的 vs 服务端现问的 ============ -->
        <div class="panels">
          <section class="panel">
            <div class="panel-bar">Session / Local</div>

            <div class="panel-body">
              <p class="hint">
                登录接口的返回值，存在浏览器里。它是<strong>登录那一刻</strong>拿到的快照 ——
                现在这张票还灵不灵，这一栏自己并不知道。
              </p>

              <dl class="info">
                <div class="info-row">
                  <dt>登录账号</dt>
                  <dd>{{ localUsername }}</dd>
                </div>
                <div class="info-row">
                  <dt>登录时间</dt>
                  <dd>{{ loggedInAtText }}</dd>
                </div>
                <div class="info-row">
                  <dt>令牌类型</dt>
                  <dd>{{ tokenType }}</dd>
                </div>
                <div class="info-row">
                  <dt>访问令牌</dt>
                  <dd class="mono">{{ tokenPreview }}</dd>
                </div>
              </dl>
            </div>
          </section>

          <section class="panel">
            <div class="panel-bar">Server / Verified</div>

            <div class="panel-body">
              <p class="hint">
                本页每次打开都重新问一次后端，验过签名、查过数据库才返回 ——
                这一栏才是<strong>此刻真实有效</strong>的身份。
              </p>

              <dl class="info">
                <div class="info-row">
                  <dt>用户 ID</dt>
                  <dd class="mono">{{ profile.id }}</dd>
                </div>
                <div class="info-row">
                  <dt>用户名</dt>
                  <dd>{{ profile.username }}</dd>
                </div>
                <div class="info-row">
                  <dt>注册时间</dt>
                  <dd>{{ createdAtText }}</dd>
                </div>
              </dl>
            </div>
          </section>
        </div>
      </template>
    </div>
  </AppShell>
</template>

<style scoped>
/* ==================================================================
 * 版面骨架
 * ================================================================== */

/* 这一页的画布：比登录页宽一倍多。
   AppShell 的 .stage 已经负责水平居中，这里只管"多宽、几行、行间距"。 */
.home {
  width: 100%;
  max-width: 920px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 全局的 .stage-head / .panel 都是按登录页的 400px 定的宽度，
   这一页要通栏，就地覆盖。
   （留在 scoped 里而不是改全局：登录页 / 注册页的窄列是刻意的，
     那边一改就散了。） */
.home .stage-head,
.home .panel {
  width: 100%;
}

/* 闸门（核验中 / 连不上）那两块不需要 920px —— 里面只有一两行字，
   铺满整屏会显得很空。
   ★ 注意用的是 max-width + margin:auto，**没有改 .home 的宽度**：
   .home 始终是 920px，闸门切成面板时外层盒子不会忽宽忽窄，
   否则核验通过的那一瞬间会有一次明显的"版面抽搐"。 */
.panel--gate {
  max-width: 520px;
  margin: 0 auto;
}

/* 标题行右侧的时钟 */
.stage-meta {
  flex: none;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: var(--ls-mid);
  color: var(--faint);
  font-variant-numeric: tabular-nums;
}

/* ==================================================================
 * ① 身份卡
 * ================================================================== */

/* .panel-body 默认是纵向排列，身份卡要横向一行排开 */
.panel-body--hero {
  flex-direction: row;
  align-items: center;
  gap: 20px;
  padding: 22px 20px;
}

/* 用户名首字母：实心深灰方块，比圆形头像更贴这套"零圆角"的语汇 */
.monogram {
  flex: none;
  width: 62px;
  height: 62px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ink);
  color: #ffffff;
  font-size: 26px;
  font-weight: 600;
  line-height: 1;
  user-select: none;
}

.hero-text {
  flex: 1;
  min-width: 0; /* 不加这行，里面的长文本会把 flex 项撑破 */
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.hero-name {
  font-size: 19px;
  letter-spacing: var(--ls-mid);
  color: var(--text-title);
}

.hero-sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

/* 徽标：一个小方块 + 一行小字，比纯文字更有"状态"的分量 */
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border: 1px solid var(--line-strong);
  font-size: 11px;
  letter-spacing: var(--ls-mid);
  color: var(--muted);
  white-space: nowrap;
}

/* 方块颜色：绿 = 已确认 */
.chip::before {
  content: '';
  flex: none;
  width: 5px;
  height: 5px;
  background: var(--ok);
}

.hero-note {
  font-size: 12px;
  letter-spacing: var(--ls-mid);
  color: var(--muted);
  word-break: break-word;
}

/* ---------------- 凭证寿命 ---------------- */

/* margin-left:auto 把它推到身份卡最右边，和左边的头像/文字拉开 */
.life {
  flex: none;
  width: 250px;
  margin-left: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.life-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.life-label {
  font-size: 11px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--muted);
}

.life-value {
  font-size: 16px;
  color: var(--text-title);
  font-variant-numeric: tabular-nums;
}

/* 进度条：3px 的发丝条，宽度用内联 style 绑定剩余比例 */
.life-track {
  height: 3px;
  background: var(--line-soft);
}

.life-fill {
  height: 100%;
  background: var(--ink);
  /* linear 而不是 ease：这是"匀速流逝的时间"，缓动会让它看起来像在喘气 */
  transition: width 1s linear, background 0.3s;
}

/* 剩余不足 20% 转红，提示"快到期了" */
.life-fill--low {
  background: var(--err);
}

.life-foot {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  letter-spacing: var(--ls-mid);
  color: var(--faint);
}

/* ==================================================================
 * ② 指标格
 * ================================================================== */

.tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 15px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
}

.tile-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tile-label {
  font-size: 11px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--muted);
}

/* 数据出处的小标记：server = 刚从后端问来的，local = 浏览器里存的 */
.tile-tag {
  flex: none;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  color: var(--faint);
}

.tile-value {
  font-family: var(--font-mono);
  font-size: 19px;
  line-height: 1.2;
  color: var(--text-title);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ==================================================================
 * ③ 双栏
 * ================================================================== */

.panels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: start; /* 两栏各自多高就多高，别互相拉齐 */
}

/* ---------------- 说明文字 ---------------- */

.hint {
  font-size: 12px;
  line-height: 1.75;
  letter-spacing: var(--ls-mid);
  color: var(--muted);
}

/* 强调"登录那一刻 / 此刻"这几个关键词，但不改变字号 */
.hint strong {
  font-weight: 600;
  color: var(--text-title);
}

/* 行内代码（正文里的接口名）。它嵌在正文中间，背景要轻，不能抢视线。 */
.hint code {
  padding: 1px 4px;
  background: var(--bg);
  color: var(--text-title);
}

/* ---------------- 信息清单 ---------------- */
/* dl = 描述列表，dt 是"名称"、dd 是"内容"，正好适合这种键值对照 */
.info {
  display: flex;
  flex-direction: column;
}

.info-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line-soft);
}

.info-row:first-child {
  padding-top: 0;
}

/* 最后一行不需要下边线，否则会和面板底部的留白叠在一起显得脏 */
.info-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.info-row dt {
  flex: none;
  font-size: 12px;
  letter-spacing: var(--ls-mid);
  color: var(--muted);
}

.info-row dd {
  font-size: 13px;
  letter-spacing: var(--ls-mid);
  color: var(--text-title);
  text-align: right;
  word-break: break-all;
}

.mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

/* ---------------- 加载骨架 ---------------- */
/* 三根灰条 + 一道来回扫的浅光，比一行"加载中…"更能说明
   "这里马上会出现三行内容"，版面也不会因为文字长度变化而跳动。 */
.skeleton {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skeleton span {
  height: 11px;
  background: linear-gradient(90deg, #eceef0 0%, #f8f9fa 50%, #eceef0 100%);
  background-size: 200% 100%;
  animation: sweep 1.5s ease-in-out infinite;
}

.skeleton span:nth-child(2) {
  width: 74%;
}

.skeleton span:nth-child(3) {
  width: 86%;
}

@keyframes sweep {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}

/* ---------------- 错误态的动作按钮 ---------------- */
/* .btn 默认 width:100%，所以必须用 flex 容器 + .btn--inline
   （后者把宽度收回 auto），否则两个按钮会各占一整行。 */
.actions {
  display: flex;
  gap: 10px;
}

/* ==================================================================
 * 顶栏右侧
 * ================================================================== */

.who-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.who {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  letter-spacing: var(--ls-wide);
  text-transform: uppercase;
  color: var(--muted);
}

/* 小绿点 = 会话在线，呼应登录页底部的状态条语汇 */
.who-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--ok);
}

/* ==================================================================
 * 窄屏兜底
 * 920px 的版面在 900px 以下必然挤，改成两列 / 单列往下堆。
 * 不做这一步的话，手机上会出现横向滚动条 —— 这是最常见的"设计崩了"。
 * ================================================================== */
@media (max-width: 940px) {
  .tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panels {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  /* 身份卡改成纵向：头像 + 文字一行，寿命条另起一行占满 */
  .panel-body--hero {
    flex-direction: column;
    align-items: stretch;
  }

  .monogram {
    align-self: flex-start;
  }

  .life {
    width: auto;
    margin-left: 0;
  }
}

/* 用户在系统里开了"减少动态效果"就别扫了，也不让进度条慢慢爬 */
@media (prefers-reduced-motion: reduce) {
  .skeleton span {
    animation: none;
  }

  .life-fill {
    transition: none;
  }
}
</style>
