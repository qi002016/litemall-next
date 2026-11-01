# t15 独立验收报告 · 四项界面改动（频道原位筛选 / 搜索框整块可点 / 分类上半屏抽屉 / 列表页顶部 bar）

- 验收人：proto-verify（只读走查；未修改 `assets/**`、`index.html`、`tools/**`、`README.md`，未 git 提交）
- 验收对象：t14 交付（工作区 HEAD `caf48bd`，`git status --porcelain -- prototype` 仅 `assets/README.md`、`assets/app.js`、`assets/theme.css` 三处改动）
- 环境：node v24.14.0；Chrome headless（`tools/cdp.js`，`CDP_VIEWPORT` 控制视口）；静态服务 `http://127.0.0.1:8765`（根 = `prototype/`）；主视口 **390×844**，宽屏补充 **1200×900**
- 取证脚本与结果（均一次性，位于 `.shots/`）：`t15-audit.js` → `t15-audit.json`（30 项）、`t15-wide.js` → `t15-wide.json`（7 项）、`t15-shots.js` → 12 张截图、`t15-timer.js`（轮播定时器叠加探针）、`t15-entry.js`（分类入口跨屏计数）

---

## §1 结论

**可接受。**

- 四项改动全部按 t14 声称的行为工作，30 项 390×844 断言 **30/30 通过**，宽屏 7 项 **7/7 通过**；运行期 **console.error=0、未捕获异常=0**。
- 5 项回归全部通过：`node --check` 5/5、`tools/scan.js`（420 与 390 各 31 路由，空屏/可疑文案/横向溢出均 0）、`tools/r1.js` 15/15、`tools/fix-check.js` 7/7、`tools/store-fix-check.js` PASS 22 / FAIL 0、`tools/seed-check.js` 未付款 3 笔（可付 2 / 超时 1）。
- 未发现 blocker / high / medium 缺陷；仅 1 条 low 观察项（V-01，宽屏下抽屉高度按视口 56vh 计算，占手机画框 63.0%，窄屏为 56.0%）与 1 条待确认项（V-02，分类屏按 t14 口径去掉了返回键，与 v2 的「所有 C 屏有返回键」基线不同——t14 README 已声明，属有意变更）。
- `store.js` / `ui.js` / `thumbs.js` / `index.html` / `tools/**` 均无改动，t14「只改 app.js + theme.css + README」的声明成立。

---

## §2 四项逐条判定表（390×844；实测值来自 `t15-audit.json`）

### 2.1 首页频道原位筛选（A 组 8 项，8/8 通过）

| ID | 判定点 | 实测 | 结果 |
| --- | --- | --- | --- |
| A-01 | 基线：推荐频道 = 全部在售 | 卡片 17 = `Store.goodsList({}).length`；标题「为你推荐 / 在售 17 件」 | 通过 |
| A-02 | 点「服饰」后 hash 不变、`history.length` 不增、首页 DOM 未跳走 | `hash=#/c/home`、`history.length=3`（点击前 3）、`.x-hhead`+`.goods-grid` 均在 | 通过 |
| A-03 | 条数 = `Store.goodsList({catId:'c1'})`，每件属于 c1 子树，标题/计数同步 | 17 → 5；标题「服饰 · 精选 / 共 5 件」；所有卡片 `catId` ∈ {c1} ∪ 子类 | 通过 |
| A-04 | 再点「推荐」恢复 | 卡片回到 17，标题回「为你推荐 / 在售 17 件」 | 通过 |
| A-05 | 其余 5 频道逐一与 Store 一致 | c2=3、c4=2、c5=2、c6=2、c3=3，逐项相等；标题均带分类名 | 通过 |
| A-06 | 空分类用 `emp` 空态 | 临时下架食品类 2 件后点「食品」→「该分类暂无在售商品 / 换一个频道看看」，随后 `Store.resetAll()` 复原 | 通过 |
| A-07 | 切换后 banner 轮播仍工作、指示点不错位 | 3 个点、初始 `on=0`；4.7s 后 `on=1` 且标题「咖啡茶饮 满 99 减 30」→「秋冬上新 低至 5 折」；点第 3 点 `on=2` 且标题再变 | 通过 |
| A-08 | 切换保持滚动位置 | 先滚到 368（页面最大滚动值）→ 切「服饰」后仍 368 | 通过 |

补充探针（`t15-timer.js`）：连续切 3 次频道后 9.2s 内只发生 **2 次**自动轮播（3.7s、7.6s），间隔 ≈3.9s → `render()` 前的 `clearTimers()`（`assets/app.js:375` 一带）确实清掉了旧 interval，**没有定时器叠加**。

### 2.2 搜索框整块可点 + 无搜索按钮（B 组 5 项，5/5 通过）

| ID | 判定点 | 实测 | 结果 |
| --- | --- | --- | --- |
| B-01 | 点 `.x-sbox` 空白区（非图标）跳搜索 | 命中 `.x-sbox` 自身的点 (177,100) → `#/c/search` | 通过 |
| B-02 | 无 `.x-sbtn`、`.x-sbox` 内无 input、无 `#home-kw` | `.x-sbtn=0`、`.x-sbox input=0`、`#home-kw=0`、`.x-sbox=1` | 通过 |
| B-03 | 角标 = `Store.cartCount()` | cartCount=0，`.x-cartbtn` 无 `.badge-dot`、无数字 | 通过 |
| B-04 | 无异常空隙/溢出 | document 溢出 0、`.x-hsearch` 溢出 0、`.x-sbox` 右沿到购物车左沿 **8px**、无重叠 | 通过 |
| B-05 | 点扫码/相机图标也只跳搜索 | 两个图标各点一次均落在 `#/c/search`，无独立动作 | 通过 |

### 2.3 分类上半屏抽屉（C 组 7 项，7/7 通过 + 宽屏 7 项）

| ID | 判定点 | 实测 | 结果 |
| --- | --- | --- | --- |
| C-01 | 面板高/视口 ≤0.60、贴顶栏下沿 | 面板 **473px / 844 = 0.560**；`panel.top=52` = demo-bar 下沿（±0）；分类屏无 `.c-head` | 通过 |
| C-02 | 面板外有遮罩且铺满剩余 | `.x-catmask` top=525（=473+52）、高 319，52+473+319=844 刚好铺满视口 | 通过 |
| C-03 | 点遮罩回首页 | 点 (195,585) → `#/c/home` | 通过 |
| C-04 | 左栏切一级分类、右栏随之更新 | 点「家居」→ `#/c/category?catId=c2`；左栏 `.on`=家居；右栏子类 `[全部家居, 杯壶, 香氛, 餐厨, 收纳]` 与 Store 顺序一致 | 通过 |
| C-05 | 点子分类跳列表 | 点「杯壶」→ `#/c/list?catId=c21`，列表 1 件，头部「杯壶 / 共 1 件」 | 通过 |
| C-06 | 面板内部不被裁切 | 右栏 `scrollHeight=425 = clientHeight`（内容未溢出，无需滚动）、`overflow-y:auto`；最后一项「鞋靴」距容器底 192px（未被裁切） | 通过 |
| C-07 | × 关闭回首页 | 点 `.x-catclose` → `#/c/home` | 通过 |

宽屏 1200×900（`t15-wide.json`，7/7 通过）：面板 88..592 **落在** `.phone` 78..878 内、x 区间 [404,796] 在画框 [394,806] 内；遮罩 592..868 铺到画框内容底部（phone 内边距 10px）；`document` 横向溢出 0；画框内滚 600 后 `head.top - phone.top` 恒为 10（头部相对画框，不贴视口 0）。详见 V-01 的 63.0% 比例观察。

### 2.4 列表页顶部 bar（D 组 6 项，6/6 通过）

| ID | 判定点 | 实测 | 结果 |
| --- | --- | --- | --- |
| D-01 | `.c-head` 高 48、含返回/搜索/AI、关键词回显 | 高 **48**；`.back`、`.x-lsearch`、`.x-aibtn` 齐；kw 显示「卫衣」；列表 1 件 | 通过 |
| D-02 | 点搜索框跳搜索 | → `#/c/search` | 通过 |
| D-03 | 点 AI 出 toast 且不跳路由 | toast「AI 助手为独立立项（需求文档 §5），R1–R3 期间暂不开放，此处为入口占位」；hash 未变 | 通过 |
| D-04 | `.pad` 与 `.x-listbar` 完整 | `.pad`「「卫衣」的结果 / 共 1 件」；`.x-chip` 5 个；筛选按钮在 | 通过 |
| D-05 | 筛选打开/改价/应用/重置 | 打开 sheet → 输入 50/100 → 按钮预检「查看 0 件商品」→ 应用后 `#/c/list?kw=…&minP=50&maxP=100`、0 件；重置后 `#/c/list?kw=…`、1 件（与 `Store.goodsList({kw:'卫衣'})` 一致） | 通过 |
| D-06 | 窄屏 `.x-listbar` sticky top=100 | `position=sticky`，滚 900 后 `rect.top=100`、computed `top:100px`（`theme.css:970`），未变成 48/148 | 通过 |

### 2.5 截图清单（`.shots/t15-*.png`，390×844；宽屏为 1200×900）

| 文件 | 字节 | 说明 |
| --- | --- | --- |
| `t15-home.png` | 106,631 | 首页「推荐」全量 17 件 |
| `t15-home-c1.png` | 100,522 | 点「服饰」原位筛选（5 件，URL 仍 `#/c/home`） |
| `t15-category.png` | 17,755 | 分类抽屉（面板 473 + 遮罩 319）；**<20KB，Pillow 验非白：colors=631、非白 47.2%、暗色 5.4%、尺寸 390×844** |
| `t15-list.png` | 29,787 | 列表页顶部 bar（返回 + 搜索框 + AI） |
| `t15-filter.png` | 43,601 | 筛选 sheet 打开态 |
| `t15-home-scroll.png` | 53,361 | 首页滚动 520px 后的固定头部 |
| `t15-wide-home.png` | 126,904 | 宽屏首页 |
| `t15-wide-home-c1.png` | 121,943 | 宽屏频道筛选 |
| `t15-wide-category.png` | 31,871 | 宽屏分类抽屉（画框内） |
| `t15-wide-list.png` | 44,722 | 宽屏列表页 |
| `t15-wide-filter.png` | 58,300 | 宽屏筛选 sheet |
| `t15-wide-home-scroll.png` | 81,899 | 宽屏画框内滚 |

---

## §3 缺陷表

| ID | 严重级 | 文件:行 | 现象与复现 | 期望 vs 实际 | 建议修法 |
| --- | --- | --- | --- | --- | --- |
| V-01 | low（观察项，不阻塞） | `assets/theme.css:997`（`.x-catpanel{height:56vh}`）、`assets/theme.css:1041` | `CDP_VIEWPORT=1200x900` 打开 `#/c/category`：面板 88..592 高 504 = 手机画框（800px）的 **63.0%**，遮罩只剩 276px；390×844 下为 473/844=**56.0%** | 期望：宽屏「上半屏抽屉」与窄屏比例一致（约 56% 画框高）；实际：`vh` 按**视口**而非画框计算，宽屏抽屉偏大 | 在 `@media (min-width:641px)` 内把 `.x-catpanel` 改为 `height:56%`（相对 `.c-page`），或对 `.x-catframe` 用容器高度变量；窄屏保持 56vh |
| V-02 | low（待确认的基线差异） | `assets/app.js:939-994`（分类屏无 `UI.cHeader`）、`assets/README.md` §14 | `#/c/category` 无 `.c-head`、无返回键，只能点 ×/遮罩回首页（C-01/C-03/C-07 实测） | v2 基线要求「除 4 个 Tab 屏外所有 C 屏有返回键」；t14 按 captain 口径改为抽屉式、无返回键并写入 README。若该口径仍然有效则**不算缺陷**，仅需 captain 确认基线替换 | 无需改代码；若需保留返回键，可在 `.x-catpanel-hd` 左侧加 `.back[data-go="back"]`，与 × 并存 |

> 未发现 blocker / high / medium。A-06 为制造空态临时调用了 `Store.goodsToggle()` 下架 2 件商品，结束时 `Store.resetAll()` 已复原（内存态，未写文件）。

---

## §4 回归结果表（全部实跑）

| 命令 | 结果 | 关键输出 |
| --- | --- | --- |
| `node --check assets/{store,ui,app,admin-screens,thumbs}.js` | 5/5 通过 | 逐个 `OK` |
| `node tools/scan.js`（420×900） | 通过 | 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0 |
| `CDP_VIEWPORT=390x844 node tools/scan.js` | 通过 | 同上，31 路由三项均 0 |
| `node tools/r1.js` | **15/15** | 6.1 十三步 + 规格价格随动；`console.error=0、未捕获异常=0` |
| `node tools/fix-check.js` | **7/7** | 含 P-01 退款金额同源（o104 行实付 24800，qty=1→¥124、qty=2→¥248） |
| `node tools/store-fix-check.js` | **PASS 22 / FAIL 0** | 超时关单、锁量释放、券退回、日志均 PASS |
| `node tools/seed-check.js` | 通过 | 未付款 3 笔：o100 3min / o101 12min 可付，o102 45min 超时 |
| `git status --porcelain -- prototype` | 符合约束 | 仅 ` M assets/README.md`、` M assets/app.js`、` M assets/theme.css`；`store.js`/`ui.js`/`thumbs.js`/`index.html`/`tools/**` 干净 |
| `git diff --stat -- prototype` | 只 3 文件 | README +117、app.js +98/−?、theme.css +69/−?（合计 253 insertions / 31 deletions） |

### 4.1 会话/渲染健康度

- 30 项断言执行期间 `page.errors`（未捕获异常）= 0、`consoleErrors` = 0。
- 连续 3 次频道重绘后轮播间隔仍 ≈3.9s（`t15-timer.js`），无定时器泄漏。

---

## §5 复验命令

```bash
cd D:/litemall-next/prototype

# 语法
node --check assets/store.js && node --check assets/ui.js && node --check assets/app.js \
  && node --check assets/admin-screens.js && node --check assets/thumbs.js

# 全路由扫描（两种视口）
node tools/scan.js
CDP_VIEWPORT=390x844 node tools/scan.js

# 主回归
node tools/r1.js            # 期望 15/15
node tools/fix-check.js     # 期望 7/7
node tools/store-fix-check.js  # 期望 PASS 22 / FAIL 0
node tools/seed-check.js    # 期望 未付款 3（可付 2 / 超时 1）

# 本报告的一次性验收脚本
CDP_VIEWPORT=390x844 node .shots/t15-audit.js   # 期望 30/30，写 .shots/t15-audit.json
CDP_VIEWPORT=1200x900 node .shots/t15-wide.js   # 期望 7/7，写 .shots/t15-wide.json
node .shots/t15-timer.js                        # 期望 9.2s 内 2 次轮播
node .shots/t15-entry.js                        # 期望 home=1、cart/me/messages/list/search=0
CDP_VIEWPORT=390x844 node .shots/t15-shots.js   # 生成 6 张 390×844 截图
CDP_VIEWPORT=1200x900 node .shots/t15-shots.js --wide  # 生成 6 张宽屏截图
```

---

## §6 禁止项核查

| 禁止项 | 结果 | 证据 |
| --- | --- | --- |
| 首页 innerText 含 emoji | **0 命中** | `E-01`：遍历 `#root` 全部码点，无 >0x1F000 |
| 分类入口除频道栏胶囊外还有第二个 | **仅 1 个** | `E-02` + `t15-entry.js`：`#/c/home` 1 个 `.x-chan-cat`；`#/c/cart`、`#/c/me`、`#/c/messages`、`#/c/list`、`#/c/search` 均 0 |
| 商品卡被换回渐变色块（THUMBS 失效） | **正常** | `E-03`：`.x-gcard` 背景 `rgb(255,255,255)`、圆角 12px、卡内 `svg=1`；`.x-gpic` 仅为淡色底 `linear-gradient(150deg, rgba(255,122,69,.1)…)` + 内联 SVG 插图 |
| 390×844 横向溢出 | **0** | `E-04`：document / `.c-page` / `.x-hsearch` 溢出均为 0；`scan.js` 31 路由溢出 0 |
| 首页仍有「搜索」按钮 / 输入框 | **无** | `B-02`：`.x-sbtn=0`、`.x-sbox input=0`、`#home-kw=0` |

---

## §7 未验证边界与局限

1. **触摸/惯性滚动**：CDP 用鼠标事件（`Input.dispatchMouseEvent`）模拟点击与滚动，未模拟真实触摸手势、iOS 橡皮筋与惯性滚动；分类抽屉两栏的 `-webkit-overflow-scrolling:touch` 效果未在真机验证。
2. **滚动位置回填的极端场景**：A-08 只验证「滚到页面最大滚动值 368 后切频道」；未覆盖「切换前处于接近底部、切换后内容更短」的贴底回填（代码在 `assets/app.js:763-785`，逻辑为 `min(y, max)`，本机未构造该场景）。
3. **空分类**：A-06 用 `Store.goodsToggle()` 临时下架制造，属内存态；未验证「后台新建空分类后前台出现」的真实数据路径（t14 未改 store.js，`goodsList` 行为由种子数据决定）。
4. **宽屏比例**：V-01 只是记录项；本报告未判定「宽屏抽屉应占画框 56%」为硬性需求，需 captain 结合设计口径决定是否修。
5. **分类屏返回键**：V-02 与 v2 基线（所有 C 屏有返回键）冲突，t14 按 captain 口径改为 ×/遮罩；本报告按 t14 口径验收，未将其计为失败。
6. **a11y / 键盘**：未验证 `.x-sbox`（`div[data-go]`）的键盘可达性、`aria-*` 语义与屏幕阅读器朗读；未验证长关键词在 `.x-lsearch` 的省略号截断（仅代码层 `text-overflow:ellipsis`）。
7. **浏览器矩阵**：仅 Chrome headless；未验证 Firefox/Safari 下 `position:sticky`、`aspect-ratio`、`overflow-anchor:none` 的差异。
8. **性能**：未测量频道切换重绘耗时；仅确认 17 件商品规模下无异常、无定时器泄漏。