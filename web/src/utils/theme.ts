/**
 * 界面主题 —— 两套皮肤共存与切换的唯一入口
 *
 * ================== 它是怎么工作的 ==================
 *
 * 主题**不改任何组件代码**，只改 <html> 上的一个属性：
 *
 *     <html>                          → Terminal（tokens.css 里的 :root）
 *     <html data-theme="blueprint">   → Blueprint（同文件的 [data-theme] 块）
 *
 * 剩下的全是 CSS 的事：两组令牌值取其一，而组件样式里写的都是 var(--xxx)，
 * 于是同一份 DOM 自动长成两种样子。
 *
 * 好处很实际：切换主题**不触发任何组件重渲染**。Vue 完全不知道主题变了 ——
 * 没有响应式更新、没有 DOM diff，只有一次样式重算。所以开关是瞬时的，
 * 页面里的定时器、正在倒数的凭证寿命、输入框里没提交的内容，全都原封不动。
 *
 * ================== 为什么用 ref 而不是普通变量 ==================
 *
 * 切换按钮需要"知道自己是不是选中态"，那是界面状态，必须响应式。
 * 这里用模块级的 ref —— 它不在任何组件里，因此**天然是全局单例**：
 * AppShell（切换器）和别处读的是同一个值，不需要 pinia，也不需要
 * provide/inject。本项目只有这一处全局状态，为它引一个状态库不划算。
 *
 * ⚠️ 分清两件事：
 *   - theme 这个 ref 是**给界面看**的（按钮高亮）
 *   - <html> 上的属性是**给浏览器看**的（真正的样式来源）
 *   apply() 负责让后者跟上前者。少了这一步，按钮会亮但页面不变。
 */
import { ref } from 'vue'

/** localStorage 的键。带项目前缀，免得和同源下的其它东西撞名。 */
const STORAGE_KEY = 'gj-theme'

/**
 * 可选主题清单。
 * ★ 顺序 = 切换器里从左到右的顺序，改这里就等于改界面。
 * 以后要加第三套皮肤，只需两步：① tokens.css 里加一个 [data-theme=...] 块；
 * ② 这里加一行。样式和组件都不用动。
 */
export interface ThemeOption {
  id: string
  label: string
}

export const THEMES: ThemeOption[] = [
  { id: 'terminal', label: 'Terminal' },
  { id: 'blueprint', label: 'Blueprint' },
]

/**
 * 读出上次的选择。
 *
 * 三种情况都必须给出确定的答案，不能返回 undefined：
 *   ① 从没选过            → 默认 terminal
 *   ② 存了个不认识的值    → 默认 terminal（比如以后删掉某套主题，
 *                            而用户浏览器里还留着那个 id）
 *   ③ localStorage 抛异常 → 默认 terminal
 *      为什么会抛？隐私模式下 Safari 会禁用 localStorage，部分环境读它直接 throw。
 *      **外面必须包 try**，否则一行存储报错会让整个应用白屏 ——
 *      代价和收益完全不成比例。
 */
function read(): string {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved && THEMES.some((t) => t.id === saved)) return saved
    return 'terminal'
  } catch {
    return 'terminal'
  }
}

/** 当前主题。模块级 ref = 全局单例，见文件头说明。 */
export const theme = ref<string>(read())

/**
 * 把 theme 的值落到 <html> 上。
 *
 * Terminal 是**移除属性**，而不是设成 data-theme="terminal"。
 * 原因：:root 里的默认值不需要任何选择器就能生效，
 * 不设属性意味着"默认态不依赖任何机制"—— 就算这段 JS 全程没跑成功，
 * 页面也还是完整的 Terminal 皮肤。
 */
function apply(): void {
  const el = document.documentElement

  if (theme.value === 'terminal') {
    el.removeAttribute('data-theme')
  } else {
    el.setAttribute('data-theme', theme.value)
  }

  // 写存储失败不算错误：本次切换照样生效，只是下次进来记不住。
  try {
    localStorage.setItem(STORAGE_KEY, theme.value)
  } catch {
    /* 忽略 */
  }
}

/** 切到指定主题。传了不认识的值就退回 Terminal，不让页面进到无主题的裸样式。 */
export function setTheme(id: string): void {
  theme.value = THEMES.some((t) => t.id === id) ? id : 'terminal'
  apply()
}

/** 在几套主题之间轮换。目前只有两套，等价于来回切。 */
export function toggleTheme(): void {
  const index = THEMES.findIndex((t) => t.id === theme.value)
  setTheme(THEMES[(index + 1) % THEMES.length].id)
}

/**
 * 启动时同步一次。
 *
 * 为什么还需要这一步？——因为 index.html 里已经有一段内联脚本，
 * 在 CSS 加载之前就把属性写好了（那是为了消除"先灰后蓝"的闪跳）。
 * 但那段脚本改的是 DOM，改不了这个 ref，所以这里必须再跑一次，
 * 让切换按钮的选中态和页面实际皮肤对上。
 * 不做这一步的表现是：页面已经是蓝的，切换器却还高亮着 Terminal。
 */
export function initTheme(): void {
  theme.value = read()
  apply()
}
