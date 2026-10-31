# 首页改版（t9）独立验收报告 · VERIFY-HOME-v3

- 验收对象：t9「首页改版：390×844 淘宝风 + 四 Tab」当前工作区（`prototype/`，基线 commit `0618203` + 未提交改版）
- 验收方式：**只读代码 + 真实浏览器点击/取色**（`tools/cdp.js` 驱动 headless Chrome，390×844 视口）
- 验收时间：2026-10-31
- 验收脚本（一次性，不入库）：`prototype/.shots/t10-audit.js`（39 条断言）、`prototype/.shots/t10-shots.js`、`prototype/.shots/t10-probe-*.js`
- 原始结果：`prototype/.shots/t10-audit.json`；截图：`prototype/.shots/t10-*.png`

---

## 1. 结论

**需修订后接受（needs revision, acceptance with fixes）**

> **t12 修订复核**：t11 / t13 修复已落盘，本节为首轮历史结论。修订后复跑 **40/40 通过**，A/B 两路吸顶复验通过，最终结论改为 **可接受**——见 **§11**。

- 逐条比对 **39 项：34 通过 / 5 不通过**；不通过项集中在「顶部固定」、「Banner 指示器」、「搜索胶囊配色」、「我的·客服入口」四个点上。
- 其中 **2 项为高风险**：①滚动后所有 C 屏的 sticky 头部被演示顶栏完全覆盖（频道栏 44px、详情页 48px 头部连带返回键全部不可见、不可点）；②Banner 指示点状态永不更新，且每次切换（含每 4s 自动轮播）都抛一次未捕获 `TypeError`。
- **回归全部通过**（R1 15/15、fix-check 7/7、store-fix-check 22/0、31 路由扫描 ×2 全 0），改版没有破坏既有业务链路。
- **一项硬条件不满足**：`git status --porcelain -- prototype/tools` 非空（`M prototype/tools/cdp.js`，t9 为本次验收新增的 `CDP_VIEWPORT` 支持）。改动未削弱任何断言，但按任务硬性要求仍判不通过。
- 禁止项（emoji / 新增模块 / 重复分类入口）**全部通过**，代码层面没有引回旧版 emoji 图或多余模块。

---

## 2. 逐条判定表（39 项）

> 全部为 computed style / DOM 实测值，不是肉眼判断。证据列取自 `t10-audit.json`。

### 2.1 配色（4）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| C-01 | 页面背景 `#F5F5F7` | 通过 | `body` / `.c-page` = `rgb(245,245,247)`，`--bg:#f5f5f7` |
| C-02 | 品牌橙 `#FF6A00` | 通过 | 搜索按钮 / 频道下划线 / 选中 Tab 均 `rgb(255,106,0)`，`--brand:#ff6a00` |
| C-03 | 促销红 `#FF2741` | 通过 | `.x-gmeta .price` = `rgb(255,39,65)`，`--price:#ff2741` |
| C-04 | 卡片白底 + 阴影 | 通过 | `bg rgb(255,255,255)`、`radius 12px`、`shadow rgba(17,24,39,.07) 0 1px 6px` |

### 2.2 顶部（8）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| T-01 | 无「小满」logo、无彩色图标导航栏 | 通过 | head 文本 `推荐\|服饰\|…\|分类\|搜索`，`hasXiaoman:false`，`.logo/.brand/.nav` 命中 0，`head img=0` |
| T-02 | 频道栏 7 项且顺序正确 | 通过 | `["推荐","服饰","家居","食品","运动","美妆","数码"]` |
| T-03 | 选中黑粗+橙色短下划线，未选中灰常规 | 通过 | on: `#111` / 700 / 13.5px，`::after` 16×3px r2 `rgb(255,106,0)`；off: `rgb(107,114,128)` / 400 |
| T-04 | 6 个分类项跳 `catId=c1/c2/c4/c5/c6/c3` 且有数据 | 通过 | hash 全对，卡片数 5/3/2/2/2（美妆 c6）/3 |
| T-05 | 搜索栏结构：白底容器 + 胶囊输入 + 左扫码右相机 + 橙色搜索按钮 | 通过 | `.x-hsearch` `rgb(255,255,255)`，`.x-ico`×2 均内联 SVG，扫码在左/相机在右，按钮「搜索」`rgb(255,106,0)` r999 |
| **T-05b** | **搜索输入胶囊为白色 `#FFFFFF`** | **不通过** | `.x-sbox` = `rgb(245,245,247)`（浅灰），radius 999px → 见 H-04 |
| T-06 | 购物车角标 = cartCount，0 隐藏 | 通过 | 0→`badge:false`；加 1 件→`badge:true, txt:"1"`；移除→隐藏 |
| **T-07** | **滚动 600/1200 后频道栏 top=0 且不被遮挡** | **不通过** | 滚动后 `head.top=0` 但 `elementFromPoint(195,8)` / `(195,30)` 均命中 demo-bar 的 `BUTTON`（`covered:true`）→ 见 H-01 |
| **T-07b** | **详情页滚动 420 后返回键可见可点** | **不通过** | `.c-head` `position:sticky` `top:0` `h:48`，返回键中心命中 demo-bar `.brand`（`covered:true`）→ 见 H-01 |

> 首屏不遮挡：`head.bottom=141`、`banner.top=151`、`gap=10`，首屏无重叠 —— 该项通过（问题只出现在滚动后）。

### 2.3 Banner（3）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| B-01 | 橙红渐变 + 半透明白 tag + 大号白标题 + 浅白副标 | 通过 | `linear-gradient(120deg,rgb(255,122,24),rgb(255,77,77))`，tag `rgba(255,255,255,.24)`，标题 19px/800 白，副标 12px `rgba(255,255,255,.92)`，radius 12px |
| B-02 | 右侧为 SVG 矢量图（`THUMBS.of('coffee')`，非 emoji） | 通过 | DOM 解析后 `innerHTML` 与 `THUMBS.of('coffee')` 逐字符一致；`svg=1 / img=0 / emoji=false` |
| **B-03** | **3 个指示器：第 1 个白色实心其余半透明；点第 2/3 个状态跟随** | **不通过** | 初始/点第 2 个/点第 3 个，`on:[true,false,false]` 始终不变；样式为橙 `rgb(255,106,0)` 14px 胶囊 + `rgb(217,221,229)` 5px 圆点 → 见 H-02 / H-03 |

### 2.4 提示条（1）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| N-01 | `#FFF8E6` 底 + 深棕字 + 灯泡图标 + 右侧时间 | 通过 | `bg rgb(255,248,230)`、`color rgb(138,91,0)`、内联 SVG 灯泡、右侧「2 天前」 |

### 2.5 消息（2 + 4）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| M-01 | 首页不存在消息卡片（铃铛+订单预览） | 通过 | `.x-msgcard=0`、铃铛卡=0、指向 messages 的链接=0、首页文本无「消息」 |
| M-02 | 消息移到第 2 个 Tab | 通过 | Tab 文案（去角标数字）`["首页","消息","购物车","我的"]` |
| MS-01 | 每条消息彩色圆形头像（按类型取色，非 emoji） | 通过 | order `rgb(255,138,61)` / aftersale `rgb(47,128,237)` / coupon `rgb(255,39,65)` / system `rgb(138,148,166)`，`border-radius:50%`，内联 SVG |
| MS-02 | 类型 tab 可用（5 类，带未读数，点击过滤） | 通过 | `全部 3 / 订单 1 / 售后 1 / 优惠券 1 / 系统`，点「优惠券」后 1 条、active 正确 |
| MS-03 | 点单条：标记已读 + 跳转 | 通过 | `msg1` 未读 3→2、`read:true`、hash `#/c/orders?tab=shipped` |
| MS-04 | 全部已读可用 | 通过 | 未读 0、头部「已全部读过」、红点 0、未读行 0 |

### 2.6 商品卡（4）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| G-01 | 白卡 + 上方渐变图区（SVG）+ 下方文字区 | 通过 | 白底/12px/shadow；图区 `linear-gradient(150deg,rgba(54,207,201,.18),rgb(247,248,250) 74%)`、`svg=1`、`img=0`、高 148px |
| G-02 | 标签多规格/爆款/热卖 = 橙/橙红/红 + 圆角小标签 | 通过 | `rgb(255,106,0)` / `rgb(255,77,24)` / `rgb(255,39,65)`，radius 6px，10px，白字 |
| G-03 | 商品名 13px 两行截断 | 通过 | 13px、line-height 18.46px、height 37px、`-webkit-line-clamp:2`、overflow hidden |
| G-04 | 价格大号红色粗体、`¥` 更小；右侧「已售 N」灰小字 | 通过 | 17px/800 `rgb(255,39,65)`，`¥` 12px，已售 11px `rgb(154,160,166)`（「已售 5310」） |

### 2.7 底部 Tab（4）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| TB-01 | 4 项顺序 = 首页/消息/购物车/我的 | 通过 | `["首页","消息","购物车","我的"]` |
| TB-02 | 点击真实切换 hash | 通过 | `#/c/home → #/c/messages → #/c/cart → #/c/me` |
| TB-03 | 选中态橙色 | 通过 | 选中「我的」`rgb(255,106,0)` |
| TB-04 | 消息 Tab 未读角标 = `Store.unreadCount()` | 通过 | Store 3、角标「3」 |

### 2.8 购物车 / 我的 / 其他屏（2 + 2 + 2）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| CT-01 | 图 + 名称 + 规格 + 价格 + 步进器，合计正确 | 通过 | 2 行、2 个 SVG 图、2 个步进器、规格「黑色 / L」「白色 / M」、`合计 ¥297（已选 2 种）`（99 + 99×2） |
| ME-01 | 头像 + ID + 订单/地址入口 + 原有入口未丢 | 通过 | 头像 SVG×1、昵称「演示用户」、订单入口/收货地址在、4 格工具齐、原有 4 个 cell 齐 |
| **ME-02** | **我的屏有客服入口** | **不通过** | `.c-page` 文本无「客服」，页面内 `[data-act]/[data-go]` 无客服项 → 见 H-05 |
| X-01 | 列表页（美妆 c6）两列 | 通过 | `grid-template-columns:178px 178px`、2 卡、2 SVG |
| X-02 | 商品详情大图 SVG + 收藏/加购/立即购买 | 通过 | 大图 SVG×1、`fav/addCart/buyNow` 三按钮在、无 emoji |

### 2.9 禁止项（3）

| ID | 判定项 | 结果 | 实测证据 |
| --- | --- | --- | --- |
| E-01 | 首页 + 4 个 Tab 屏 DOM emoji 命中 = 0 | 通过 | 4 屏 `textContent` 命中 0；正则自测 `😀→true / "abc"→false`（确保正则真的在跑）；补充实测 `innerHTML` 命中 0（首页/消息/购物车/我的/列表/详情），6 屏 `img=0`、`svg=27/12/5/18/4/7` |
| F-01 | 无直播/优惠券条/秒杀/排行榜/签到等新增区块 | 通过 | 区块清单只有 `.x-banner / .x-bdots / .x-notice / 「为你推荐 在售 17 件」/ .goods-grid / 「已经到底了」`；除 Banner 副标文案外无 `领券` 等关键词，class 无 live/seckill/rank/sign/couponstrip 命中 |
| F-02 | 首页只允许频道栏末尾那一个「分类」胶囊 | 通过 | 全部「分类」入口 = 1（`.x-chan-cat`，在 header 内） |

---

## 3. 配色与字号取值表（computed style）

| 元素 | 属性 | 实测值 | 期望/对照 |
| --- | --- | --- | --- |
| 页面背景 | `background-color` | `rgb(245,245,247)` = `#F5F5F7` | ✅ |
| 品牌橙 | 按钮/下划线/Tab | `rgb(255,106,0)` = `#FF6A00` | ✅ |
| 促销红 | 商品价 | `rgb(255,39,65)` = `#FF2741` | ✅ |
| 商品卡 | bg / radius / shadow | `#fff` / `12px` / `rgba(17,24,39,.07) 0 1px 6px` | ✅ |
| 频道选中 | color / weight / size | `rgb(17,17,17)` / `700` / `13.5px` | ✅ 黑粗 |
| 频道下划线 | 尺寸 / 圆角 / 颜色 | `16px × 3px` / `2px` / `rgb(255,106,0)` | ✅ 橙色短下划线 |
| 频道未选中 | color / weight | `rgb(107,114,128)` / `400` | ✅ 灰常规 |
| 搜索容器 | bg | `rgb(255,255,255)` | 白 ✅ |
| 搜索输入胶囊 | bg / radius | `rgb(245,245,247)` / `999px` | ❌ 要求白色（H-04） |
| 搜索按钮 | bg / radius | `rgb(255,106,0)` / `999px` | ✅ |
| Banner 背景 | `background-image` | `linear-gradient(120deg, rgb(255,122,24), rgb(255,77,77))` | ✅ 橙红 |
| Banner tag | bg / color | `rgba(255,255,255,.24)` / `rgb(255,255,255)` | ✅ 半透明白 |
| Banner 标题 | size / weight / color | `19px` / `800` / `rgb(255,255,255)` | ✅ |
| Banner 副标 | size / color | `12px` / `rgba(255,255,255,.92)` | ✅ |
| 指示点 未选 | bg / w×h | `rgb(217,221,229)` / `5px` | ❌ 要求半透明白（H-03） |
| 指示点 选中 | bg / w×h / radius | `rgb(255,106,0)` / `14px×5px` / `3px` | ❌ 要求白色实心（H-03） |
| 提示条 | bg / color | `rgb(255,248,230)` = `#FFF8E6` / `rgb(138,91,0)` | ✅ 深棕字 |
| 商品名 | size / line-height / clamp | `13px` / `18.46px` / `2`（height 37px） | ✅ |
| 商品价 | size / weight / color | `17px` / `800` / `rgb(255,39,65)` | ✅ |
| 价格符号 `¥` | size | `12px` | ✅ 更小 |
| 已售 | size / color | `11px` / `rgb(154,160,166)` | ✅ 灰小字 |
| 商品标签 | size / radius | `10px` / `6px` | ✅ 圆角小标签 |

---

## 4. 禁止项核查

1. **emoji = 0**：正则 `[\u{1F300}-\u{1FAFF}\u2600-\u27BF\uFE0F]` 先自测（`😀` 命中、`abc` 不命中），再扫首页 / 消息 / 购物车 / 我的 `textContent` 与 6 屏 `innerHTML`，全部 0；6 屏 `<img>` 均为 0，图形全部为内联 `<svg>`。**通过**
2. **未新增模块**：首页区块清单仅 6 项（Banner / 指示点 / 提示条 / 「为你推荐」标题 / 商品网格 / 「已经到底了」），无直播、优惠券条、秒杀、排行榜、签到。**通过**
3. **分类入口不重复**：全页「分类」= 1 处（频道栏末尾 `.x-chan-cat`）。**通过**

---

## 5. 回归命令结果（全部真跑）

| # | 命令 | 结果 |
| --- | --- | --- |
| 1 | `node --check assets/{store,ui,thumbs,app,admin-screens}.js` | **5/5 OK** |
| 2 | `node tools/scan.js`（420×900） | 31 路由：空屏 **无** / 可疑文案 **无** / 横向溢出 **无** |
| 3 | `CDP_VIEWPORT=390x844 node tools/scan.js` | 同上：空屏 **无** / 可疑 **无** / 溢出 **无** |
| 4 | `node tools/r1.js` | **15/15 通过**，console.error=0，未捕获异常=0 |
| 5 | `node tools/fix-check.js` | **7/7 通过**（P-01/P-03/P-04 修复仍生效；P-01 申请页与 `Store.refundAmount` 同源） |
| 6 | `node tools/store-fix-check.js` | **PASS 22 / FAIL 0**（含超时支付被拒、关单释放锁量/券、关单日志） |
| 7 | `node tools/seed-check.js` | 未付款 3 笔（o100 3min、o101 12min 可付；o102 45min 已超时） |
| 8 | `git status --porcelain -- prototype/tools` | ❌ **非空**：` M prototype/tools/cdp.js`（+13/−3，仅新增 `CDP_VIEWPORT` / `--window-size` / `setDeviceMetricsOverride`）→ H-06 |
| 9 | `git diff --stat -- prototype/tools` | `1 file changed, 13 insertions(+), 3 deletions(-)` |
| — | 逐条比对脚本 `CDP_VIEWPORT=390x844 node .shots/t10-audit.js` | **34/39 通过**，console.error=0，未捕获异常=2（均为 H-02） |

---

## 6. 截图清单（390×844，`prototype/.shots/`）

| 文件 | 字节 | 尺寸 | 颜色数 | 非白像素 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `t10-home-390.png` | 106396 | 390×844 | 4781 | 51.1% | 首页首屏 |
| `t10-home-scroll600.png` | 100412 | 390×844 | 3229 | 58.8% | 滚动 600px（H-01 现场：只看得见演示栏，频道栏被吞） |
| `t10-home-scroll1200.png` | 102007 | 390×844 | 3593 | 62.8% | 滚动 1200px（同 H-01） |
| `t10-messages-390.png` | 51736 | 390×844 | 1546 | 21.6% | 消息中心 |
| `t10-cart-390.png` | **19548** | 390×844 | 1038 | **86.6%** | 购物车（<20KB 已用像素证据排除空白：非白 86.6%、颜色 1038） |
| `t10-me-390.png` | 69017 | 390×844 | 2862 | 19.9% | 我的（4 格工具，无客服入口） |
| `t10-list-c6-390.png` | 49753 | 390×844 | 2021 | 78.9% | 列表页·美妆 |
| `t10-goods-390.png` | 42428 | 390×844 | 1745 | 39.6% | 商品详情 |
| `t10-goods-scroll420.png` | 32927 | 390×844 | 1444 | 44.2% | 详情滚动 420px（返回键被吞，H-01 现场） |

全部 9 张为 390×844，无纯白空图。

---

## 7. 缺陷表

### H-01（high）滚动后所有 C 屏的固定头部被演示顶栏完全覆盖，返回键不可见/不可点

> **状态（t12 复核）**：**成立**（captain 于 m00909 撤回「不成立」误判并确认）→ **已修**（t13，`assets/theme.css:963-978` 新增 `@media (max-width:640px){.c-head{top:52px}.tabs-scroll{top:100px}.x-listbar{top:100px}}`）→ **复验通过**（§11.2 / §11.3）。

- **文件:行**：`assets/theme.css:56-59`（`.demo-bar{position:sticky;top:0;z-index:900;height:52px}`）、`assets/theme.css:106-108`（`.c-head{position:sticky;top:0;z-index:40;height:48px}`）、`assets/theme.css:506-519`（≤640 改为整页滚动，头部 sticky 相对视口）
- **复现**（390×844，服务 `http://127.0.0.1:8765`）：
  1. 打开 `#/c/home`，`window.scrollTo(0,600)`；
  2. `document.querySelector('.x-hhead').getBoundingClientRect().top` → `0`，但 `document.elementFromPoint(195,8)` / `(195,30)` 返回 `.demo-bar` 里的 `BUTTON`（`covered:true`）；
  3. 打开 `#/c/goods/g1`，`scrollTo(0,420)`，返回键中心 `elementFromPoint` → `.brand`（demo-bar 元素），返回键点不到。
- **期望**：滚动 600/1200 后频道栏（首页）与返回键（其余屏）可见、可点，且不与演示栏重叠。
- **实际**：`.c-head` 的 sticky `top:0` 与 demo-bar 的 sticky `top:0` 重叠，demo-bar `z-index:900` 在 40 之上；重叠高度 = 52px > 首页频道栏 44px、详情页头部 48px → 频道栏全遮、详情页返回键全遮（搜索栏仅露出下半 37px）。这是一条跨所有 C 屏的问题，不只是首页。
- **建议修法**（二选一，改动都很小）：
  - A（贴合「频道栏 top=0」）：`@media(max-width:640px){ .demo-bar{position:static} }`，让演示栏随页面滚走，`.c-head` 真正贴上沿；
  - B（保留演示栏常驻）：`@media(max-width:640px){ .c-head{top:52px} }`，头部贴在演示栏下方。
  - 另建议给 `.c-page` 或 `html` 加 `scroll-padding-top:52px`，避免锚点跳转落到演示栏下面。

### H-02（high）Banner 指示点状态永不更新；每次切换（含每 4s 自动轮播）抛未捕获 TypeError

> **状态（t12 复核）**：**已修**（`assets/app.js:47-48` 改为 `Array.prototype.forEach.call(list || [], fn)`）→ 复验 B-03 / B-04 通过（§11.4）。

- **文件:行**：`assets/app.js:47`（`function each(list, fn) { (list || []).forEach(fn); }`）、`assets/app.js:766`（`each(dots.children, …)`）、`assets/app.js:774-777`（`ctx.every(4000, …)` 自动轮播）
- **复现**：
  1. 打开 `#/c/home`，等 9 秒；
  2. Banner 标题已从「咖啡茶饮 满 99 减 30」切到「数码好物 新品首发」，但 `#home-dots i` 的 class 恒为 `["on","",""]`；
  3. 点第 2/3 个指示点：标题会换，指示点仍停在第 1 个；
  4. 页面 `errors` 捕获 2 条：
     ```
     TypeError: (list || []).forEach is not a function
         at each (assets/app.js:47:42)
         at paint (assets/app.js:766:13)
         at assets/app.js:776:13   ← ctx.every 自动轮播
     ```
- **期望**：点第 N 个 / 自动轮播到第 N 张时，第 N 个指示点高亮；首页不应每 4 秒报一次异常。
- **实际**：`dots.children` 是 `HTMLCollection`，没有 `forEach`，`paint()` 在设置标题/背景之后抛错，指示点更新永远执行不到；异常每 4 秒复现一次（本次 9 秒抓到 2 次）。
- **建议修法**：`each(dots.children, …)` → `[].slice.call(dots.children).forEach(…)`；或把 `each` 改成 `function each(list, fn){ Array.prototype.forEach.call(list || [], fn); }`（`sum/list` 等其它调用方不受影响）。修完请把 `each` 的类数组用法补一条断言，避免回归。

### H-03（medium）Banner 指示点样式与要求不符（要求白色实心 + 半透明白）

> **状态（t12 复核）**：**已修**（白实心 `rgb(255,255,255)` + 半透明白 `rgba(255,255,255,0.45)`，6px，落 `.x-bwrap` 内）→ 复验 B-03 通过（§11.4）。

- **文件:行**：`assets/theme.css:883-885`（`.x-bdots i{width:5px;height:5px;border-radius:50%;background:#D9DDE5}` / `.x-bdots i.on{width:14px;border-radius:3px;background:var(--brand)}`）
- **复现**：打开 `#/c/home`，读 `#home-dots i` 的 `backgroundColor`、`width`。
- **期望**：第 1 个白色实心（`#FFFFFF`），其余半透明白（如 `rgba(255,255,255,.5)`），位于 Banner 之上。
- **实际**：`bg=["rgb(255,106,0)","rgb(217,221,229)","rgb(217,221,229)"]`、`width=["14px","5px","5px"]` —— 橙色胶囊 + 灰点，且指示器位于 Banner 下方的白色区域（`.x-bdots` 在 banner 外），不是叠在 Banner 上。
- **建议修法**：`.x-bdots i{background:rgba(255,255,255,.5)}`，`.x-bdots i.on{background:#fff;width:14px;border-radius:3px}`；若要求叠在 Banner 内，需把 `.x-bdots` 绝对定位到 `.x-banner` 底部居中（`position:absolute;bottom:8px;left:0;right:0`）并给 `.x-banner` `position:relative`。

### H-04（low）搜索输入胶囊不是白色（要求「白色胶囊」）

> **状态（t12 复核）**：**已修**（`.x-sbox` = `rgb(255,255,255)` + 内描边，radius 999px）→ 复验 T-05b 通过（§11.4）。

- **文件:行**：`assets/theme.css:847-850`（`.x-sbox{background:#F5F5F7}`）
- **复现**：打开 `#/c/home`，读 `.x-sbox` 的 `backgroundColor` → `rgb(245,245,247)`。
- **期望**：白色胶囊 `#FFFFFF`（外层搜索栏已为白色 `rgb(255,255,255)`）。
- **实际**：输入胶囊是浅灰 `#F5F5F7`，白底容器 + 灰胶囊（淘宝常见样式，但与本次「白色胶囊」要求不一致）。
- **建议修法**：`.x-sbox{background:#fff;box-shadow:inset 0 0 0 1px rgba(0,0,0,.06)}`，保留 `#F5F5F7` 作为页底或搜索占位字色；若产品确认沿用灰胶囊，请回写需求口径，避免验收口径漂移。

### H-05（low）「我的」屏没有客服入口

> **状态（t12 复核）**：**已修**（HEAD 右侧「客服」+ cell「客服 / 在线客服 9:00–21:00」+ `actions.service` toast）→ 复验 ME-01 / ME-02 通过（§11.4）。

- **文件:行**：`assets/app.js:2649-2660`（`.x-tools` 4 格：收藏/关注店铺/足迹/我的优惠券）、`assets/app.js:2695-2705`（cell 列表：我的优惠券/收货地址/我的评价/我的售后）
- **复现**：打开 `#/c/me`，`document.querySelector('.c-page').innerText.includes('客服')` → `false`；页面内无 `data-act="service"` / 指向客服的 `data-go`。
- **期望**：我的屏有「客服」入口（清单要求「头像+ID+订单/地址/客服入口」）。
- **实际**：客服只在商品详情（`app.js:1263`，`data-act="service"`）和订单详情（`app.js:2121`，「联系客服」）出现；我的屏缺失。注：`git show HEAD:prototype/assets/app.js` 同位置也没有该入口，属**既有缺口**，不是本次改版删掉的。
- **建议修法**：在 `.x-tools` 或 cell 列表补一项「客服」（`data-act="service"`，复用商品详情里的占位 toast「客服为占位入口（需求未定义会话能力）」），保持与其它入口一致的占位口径。

### H-06（medium）验收工具被改动且未提交，不满足「git status tools 必须为空」硬条件

> **状态（t12 复核）**：**沿用**——`git status --porcelain -- prototype/tools` 仍为 ` M prototype/tools/cdp.js`（+13/−3，仅为 `CDP_VIEWPORT` 视口支持）。captain 口径：该改动不削弱断言、不计入本次缺陷，但作为交付前提仍需决定是否提交（§11.6）。

- **文件:行**：`prototype/tools/cdp.js`（工作区 vs `0618203`：+13/−3）
- **复现**：`git status --porcelain -- prototype/tools` → ` M prototype/tools/cdp.js`；`git diff -- prototype/tools` 内容为新增 `CDP_VIEWPORT` 解析、`--window-size=W,H`、`open()` 内 `Emulation.setDeviceMetricsOverride({width:w,height:h,deviceScaleFactor:1,mobile:true})`、`launch()` 多返回 `w,h`。
- **期望**：`git status --porcelain -- prototype/tools` 为空、`git diff` 无 tools 改动。
- **实际**：非空。改动本身**只是新增视口参数、未删除/放宽任何断言**（`click/clickText/clickText` 的命中校验、`_clearToasts` 重试逻辑均未变，r1 仍 15/15、fix-check 仍 7/7），但按任务硬性条件判不通过；同时说明 t9 的验证依赖未提交的工具改动，别人 clone 后无法复现 390×844。
- **建议修法**：把 `tools/cdp.js` 的视口支持与 t9 业务改动一起提交（并保留默认 420×900 以兼容历史脚本）；或在 `README` 记录 `CDP_VIEWPORT` 用法后提交，确保工作区 tools 干净。

> 备注：除 H-06 外，本次改版的业务文件（`assets/app.js`、`assets/store.js`、`assets/theme.css`、`index.html`、新增 `assets/thumbs.js`、`assets/README.md`）相对 `0618203` 也仍是未提交状态 —— 这是 t9 的交付状态问题，不属于本次只读验收的判定对象，但提交时请一并处理。

---

## 8. 复验命令（可直接粘贴，工作目录 `D:\litemall-next\prototype`）

```bash
# 0) 起静态服务（若未起）
cd /d/litemall-next/prototype && python -m http.server 8765

# 1) 语法
for f in assets/store.js assets/ui.js assets/thumbs.js assets/app.js assets/admin-screens.js; do node --check $f || echo FAIL $f; done

# 2) 31 路由扫描（两种视口）
node tools/scan.js
CDP_VIEWPORT=390x844 node tools/scan.js

# 3) 业务回归
node tools/r1.js            # 期望 15/15
node tools/fix-check.js     # 期望 7/7
node tools/store-fix-check.js  # 期望 PASS 22 / FAIL 0
node tools/seed-check.js       # 3 笔未付款：2 可付 / 1 超时

# 4) 本次逐条比对（39 项）
CDP_VIEWPORT=390x844 node .shots/t10-audit.js   # 期望修复后 39/39

# 5) 只读硬条件
git status --porcelain -- prototype/tools       # 期望为空

# 6) 截图（9 张，390×844）
CDP_VIEWPORT=390x844 node .shots/t10-shots.js

# 单点复核 H-01（头部遮挡）
#   打开 #/c/home → scrollTo(0,600) → elementFromPoint(195,8) 的 className 不应是 demo-bar 内的元素
# 单点复核 H-02（指示点 + 异常）
#   打开 #/c/home 等 9s → #home-dots i 的 class 应跟随标题变化；页面不应出现 TypeError
```

---

## 9. 局限

1. **只做只读验收**：未修改 `assets/**`、`index.html`、`tools/**`、既有 `VERIFY*.md`；本次新增文件只有本报告与 `prototype/.shots/` 下的一次性脚本/截图（`.shots/` 不入库）。
2. **未做真机/触摸滑动验证**：分类横滚条、列表 chips、Banner 的滑动均为鼠标事件与 `scrollWidth/clientWidth` 判定；iOS/Android 的惯性滚动、`-webkit-overflow-scrolling` 未覆盖。
3. **H-01 的修复方向需要产品确认**：是「演示栏随页面滚走（头部真 top=0）」还是「头部贴在演示栏下（top:52px）」，两者都满足「不被遮挡」，但视觉不同；本报告只给建议，不替产品拍板。
4. **H-04 涉及口径**：白色胶囊 vs 淘宝式灰胶囊属于设计口径问题；若产品确认灰胶囊，请同步更新需求文档，而不是让代码与文档长期不一致。
5. **未覆盖后台 10 屏的本次改版**（本次范围仅手机端首页 + 四 Tab）；后台沿用 t2 验收结论（`VERIFY-UI-v2.md`）。
6. **异常捕获口径**：`tools/cdp.js` 只收集 `Runtime.exceptionThrown` 与 `console.error`；`console.warn` 与网络级错误未纳入统计（本项目纯静态、无网络请求，影响可忽略）。
---

## 11. 修订复核（t12：H-01 判定更正 + t11/t13 修复复验）

> 本节是 t12 的追加复核记录。§1–§9 保持首轮原文不动，只在其对应缺陷处补了「状态」行；本节的数字全部来自本轮真实运行（`node .shots/t10-audit.js` / `.shots/t12-wide.js` / `.shots/t12-seg.js` / `tools/*`）。

### 11.1 H-01 判定沿革（原判定成立 → captain 误推翻 → 复测确认成立 → t13 已修）

| 阶段 | 结论 | 依据 |
| --- | --- | --- |
| t10 首轮（本报告 §7 H-01） | **成立** | 390×844 下 `document.scrollingElement` 滚 600 后 `.c-head` = 0..89 / 0..48，`elementFromPoint` 命中 demo-bar |
| captain 中途复测 | 判「不成立」（sticky 到 52 属正确设计） | 复测时滚的是 `.phone-scroll`；但 390 窄屏它 `overflow:visible`，真正滚动容器是 `document.scrollingElement` → 页面没动，head 停在 52 是假象 |
| captain 更正（m00909） | **撤回误判，确认成立** | 用 `document.scrollingElement.scrollTop=600` 复测：首页 head 0..89、`elementFromPoint(195,6)/(195,44)` 命中 BUTTON；详情页 head 0..48、返回键 9..39 中心命中 BUTTON |
| t13 修复 | 已落盘 | `assets/theme.css:963-978`：`@media (max-width:640px){ .c-head{top:52px} .tabs-scroll{top:100px} .x-listbar{top:100px} }` |
| t12 复验（本节） | **修复有效，H-01 关闭** | A 路径 390×844 与 B 路径 1200×900 两路实测均通过（§11.2 / §11.3） |

**关键事实（决定「谁在滚」）**：390×844 下 `document.scrollingElement` 可滚（`scrollHeight > clientHeight`），而 `.phone-scroll` 不可滚（`overflow:visible`）；1200×900 反之，由 `.phone-scroll` 在画框内滚。两种视口的滚动容器不同，这是本轮必须分 A/B 两路测的原因。

### 11.2 A 路径：窄屏 390×844（`document.scrollingElement` 滚动）

| 路由 | 状态 | `.c-head` rect | 头部内部点（top+5 / 中点 / bottom−5）命中 | 是否被遮挡 |
| --- | --- | --- | --- | --- |
| `#/c/home` | 未滚 | 52..141（h89） | `x-chan-i` / `INPUT` / `x-hsearch`（均 inside） | 否 |
| `#/c/home` | 滚 600 | 52..141 | 同上 | 否 |
| `#/c/home` | 滚 1200 | 52..141 | 同上 | 否 |
| `#/c/goods/g1` | 滚 420 | 52..100（h48） | 返回键 60.5..90.5，中心命中 head（`covered:false`） | 否 |
| `#/c/orders` | 未滚 / 滚 900 | 52..100 | 返回键命中 head；`.tabs-scroll` 100..139 sticky | 否 |
| `#/c/list` | 未滚 | 52..100 | `.x-listbar` 135..178.3 | 否 |
| `#/c/list` | 滚 900 | 52..100 | `.x-listbar` 100..143.3（贴在头部下沿，不重叠） | 否 |
| `#/c/me` | 滚 105 | 52..100 | 头部完整可见 | 否 |
| `#/c/messages` | 滚 432 | 52..100 | `.tabs.scroll` 100..139 | 否 |

- 复验脚本口径：`t10-audit.js` 的 **T-07 已按新方案改写**为「滚动 600/1200 后头部贴演示顶栏下沿（top=52）且头部内部点不被 demo-bar 命中」，T-07b 保持「详情页滚 420 后返回键可点」。原口径「head.top 必须 =0」在「头部让位 52px」方案下已不适用，改写说明在此留痕。
- 首屏间距：未滚时 `head.bottom=141`、`banner.top=151`（gap 10），无重叠。

### 11.3 B 路径：宽屏 1200×900（`.phone-scroll` 内滚）

| 路由 | 状态 | `.phone` | `.c-head` | head − phone.top | 返回键 | 其他吸顶 |
| --- | --- | --- | --- | --- | --- | --- |
| `#/c/home` | 未滚 / 滚 900 | 78..878 | 88（h89） | 10 | —（Tab 屏无返回键） | — |
| `#/c/goods/g1` | 未滚 / 滚 900 | 78..878 | 88（h48） | 10 | 96.5..126.5，命中 ok | — |
| `#/c/orders` | 未滚 / 滚 900 | 78..878 | 88（h48） | 10 | 命中 ok | `.tabs-scroll` 136 sticky |
| `#/c/list` | 未滚 / 滚 900 | 78..878 | 88（h48） | 10 | 命中 ok | `.x-listbar` 171 → 136 sticky |

- **宽屏没有出现 52px 空隙**：`head.top − phone.top` 恒为 10（画框内边距），滚动前后不变 → `top:52px` 只在 `≤640px` 生效，宽屏路径未被污染。
- 证据：`.shots/t12-wide.json`（脚本 `.shots/t12-wide.js`）。

### 11.4 其余缺陷复验（H-02 / H-03 / H-04 / H-05）

| 缺陷 | 修复点 | 复验断言 | 实测证据 | 结论 |
| --- | --- | --- | --- | --- |
| H-02 | `assets/app.js:47-48` `Array.prototype.forEach.call(list || [], fn)` | B-03 点击 2/3 个点、B-04 自动轮播 9.2s | B-03：`on [T,F,F] → [F,T,F] → [F,F,T]`，标题同步为「秋冬上新 低至 5 折」「数码好物 新品首发」；B-04：等 9.2s 后 `idx 2→1`、标题=「秋冬上新 低至 5 折」、`errors 0→0` | 通过 |
| H-03 | `.x-bdots i` 白实心 / 半透明白 | B-03 样式取色 | `bg=["rgb(255,255,255)","rgba(255,255,255,0.45)","rgba(255,255,255,0.45)"]`、`width 6px ×3`、位于 `.x-bwrap` 内 | 通过 |
| H-04 | `.x-sbox{background:#fff;box-shadow:inset …}` | T-05b | `bg=rgb(255,255,255)`、`border=rgb(154,160,166)`、`radius=999px`；T-05 同时确认扫码在左、相机在右、按钮橙色 | 通过 |
| H-05 | 我的屏 HEAD「客服」+ cell「客服 / 在线客服 9:00–21:00」+ `actions.service` | ME-01 / ME-02 | ME-01：`cells=["我的优惠券","收货地址","我的评价","我的售后","客服"]`、`hasService:true`、`tools:4`；ME-02：点客服 → toast「客服为占位入口（需求未定义会话能力）」 | 通过 |

### 11.5 最终逐项比分

- **40/40 通过**（首轮 39 项 + 本轮新增 **B-04**「轮播自动切换 ≥9s 稳定性」；T-07 按新口径改写，断言数不变）。
- 首轮 5 个不通过项全部转为通过：T-05b（H-04）、T-07 / T-07b（H-01）、B-03（H-02/H-03）、ME-02（H-05）。
- 未通过项：**0**。
- 运行期：`console.error = 0`、未捕获异常 = 0（含 B-04 耐心等待 9.2s 的整段轮播）。

### 11.6 回归与硬条件

| 命令 | 结果 |
| --- | --- |
| `node --check assets/{store,ui,app,admin-screens,thumbs}.js` | 5/5 OK |
| `node tools/scan.js`（420×900） | 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0 |
| `CDP_VIEWPORT=390x844 node tools/scan.js` | 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0 |
| `node tools/r1.js` | **15/15**（13 步 + R1-2b；`console.error=0`、未捕获异常 0） |
| `node tools/fix-check.js` | **7/7** |
| `node tools/store-fix-check.js` | **PASS 22 / FAIL 0** |
| `node tools/seed-check.js` | 未付款 3 笔（可付 2 / 超时 1：o100 3min、o101 12min、o102 45min） |

**H-06（硬条件）仍然存在**：`git status --porcelain -- prototype/tools` = ` M prototype/tools/cdp.js`（+13/−3，`CDP_VIEWPORT` 视口支持）。captain 口径：不算本次缺陷（未削弱断言），但**交付前需决定提交或回退**——否则别人 clone 后无法在 390×844 下复现本报告。产品侧改动量：`assets/app.js +350 / store.js +19 / theme.css +191 / index.html 8 行`（t9 + t11 + t13 合计），`assets/thumbs.js` 新增未跟踪。

### 11.7 截图清单（390×844，`prototype/.shots/`）

| 文件 | 场景 | 字节 | 非白像素 | 备注 |
| --- | --- | --- | --- | --- |
| `t12-home-top.png` | 首页首屏 | 107525 | 74.6% | |
| `t12-home-scroll600.png` | 首页滚 600（头部贴 52） | 94765 | 59.8% | H-01 修复后视觉证据 |
| `t12-messages.png` | 消息屏 | 50135 | 25.9% | |
| `t12-cart.png` | 购物车 | **17893** | 88.4% | **<20KB**，Pillow 复核非空白（956 色） |
| `t12-me.png` | 我的（含客服） | 68282 | 38.2% | |
| `t12-list-c6.png` | 美妆列表 | 48466 | 83.7% | |
| `t12-goods-g1.png` | 商品详情（sticky 头 + 返回键） | 42262 | 58.6% | |
| `t12-orders-scroll.png` | 订单滚 600（分栏吸顶 100） | 48483 | 26.2% | |
| `t12-list-scroll.png` | 列表滚 600（筛选条 100） | 95024 | 67.8% | |

### 11.8 复验命令（工作目录 `D:\litemall-next\prototype`）

```bat
node --check assets/store.js && node --check assets/ui.js && node --check assets/app.js && node --check assets/admin-screens.js && node --check assets/thumbs.js
CDP_VIEWPORT=390x844  node .shots/t10-audit.js      :: 40/40；T-07 为 A 路径吸顶断言
CDP_VIEWPORT=1200x900 node .shots/t12-wide.js       :: B 路径：head−phone.top 恒 10，返回键命中 ok
CDP_VIEWPORT=390x844  node .shots/t12-shots.js      :: 9 张 390×844 截图
node tools/scan.js ; CDP_VIEWPORT=390x844 node tools/scan.js
node tools/r1.js ; node tools/fix-check.js ; node tools/store-fix-check.js ; node tools/seed-check.js
git status --porcelain -- prototype/tools            :: 仍为  M prototype/tools/cdp.js（H-06）
```

### 11.9 遗留观察（不计缺陷）

1. **宽屏装饰刘海**：`.phone::after`（`theme.css:96-99`，120×24、`z-index:60`）在 1200×900 时位于画框顶部 8..32，与 `.c-head`（88..136）中段重叠，会截获该 120px 区域的点击。属既有装饰层（`≤640px` 时 `display:none`），不影响返回键/列表交互，也非 t9 引入，仅记录。
2. **首帧瞬态**：demo-bar 内 `.seg` 在极少数首帧（样式未就绪）会量到 69px 高、溢出顶栏 8.5px；稳态在 375/390/420 三种宽度均为 34px、无溢出（`.shots/t12-seg.js` 复测 4 次），因此不作为缺陷。
3. **T-07 口径变更留痕**：原断言要求「滚动后 head.top=0」，t13 采用「头部让位 52px」，两者互斥；本轮按 captain 口径改写断言并在 §11.1 说明，原始 39 项表（§2）保留不动。
4. **触摸/真机未覆盖**：与 §9 同，吸顶与横滑均在 headless Chrome 用鼠标与程序化滚动验证。

### 11.10 最终结论

**可接受（acceptable）**——首轮判「需修订后接受」的 5 项全部修复并经本轮复验：**40/40 通过**，H-01 在 A（390×844 整页滚动）与 B（1200×900 画框内滚）两路均无遮挡、无 52px 空隙，回归 5 项全绿，运行期零异常。
**唯一交付前提**：H-06（`prototype/tools/cdp.js` 未提交）需 captain 决定提交或回退，否则 390×844 视口的复验不可被他人复现。
