# 小满商城 · 交互原型（手机端 + 后台）

纯静态零依赖页面原型：单个 SPA，一个数据层，顶部演示顶栏切换「手机端 17 屏 / 后台 10 屏」。
只做交互设计，**不接后端、不写服务端逻辑**，所有业务规则都在 `assets/store.js` 里用 localStorage 模拟。

## 1. 目录与启动

```
prototype/
├─ index.html            外壳：演示顶栏 + #root + 脚本顺序
└─ assets/
   ├─ theme.css          全部样式（演示顶栏 / 手机壳 / 后台 / 组件）
   ├─ store.js           window.Store：数据 + 业务规则（金额单位=分）
   ├─ ui.js              window.UI：esc/price/time/icon/toast/sheet/confirm/...
   ├─ app.js             window.App + 路由 + 手机端 17 屏（本文件）
   └─ admin-screens.js   window.ADMIN_ROUTES：后台 10 屏（由 proto-admin 维护）
```

打开方式：直接双击 `prototype/index.html`（`file://` 可用，无需服务器）。
脚本顺序固定为 **store.js → ui.js → admin-screens.js → app.js**（`ADMIN_ROUTES` 必须在 app.js 之前注册）。

演示数据存在 localStorage 的 `litemall-proto-v3` 键下；顶栏「重置演示数据」会二次确认后调 `Store.resetAll()` 回到种子态。

## 2. 路由契约（后续接手必读）

### 2.1 hash 形式

| 形式 | 说明 |
| --- | --- |
| `#/c/home` | 手机端。`c` 之后是 path，支持 `:id` 段与 query |
| `#/c/goods/g1` | `path: 'goods/:id'` → `ctx.params.id === 'g1'` |
| `#/c/orders?tab=unpaid` | `ctx.query.tab === 'unpaid'` |
| `#/admin/dashboard` | 后台。path 表由 `assets/admin-screens.js` 的 `window.ADMIN_ROUTES` 提供 |

空 hash 或非法 hash 时，`app.js` 启动会落到 `#/c/home`；未注册的 path 渲染一个「屏幕未注册」兜底页（不空白）。

### 2.2 屏幕注册

```js
// 手机端：app.js 末尾统一挂载
window.C_ROUTES = [{ path: 'home', render(ctx) {...} }, ...];
// 后台：assets/admin-screens.js 自己挂
window.ADMIN_ROUTES = [{ path: 'dashboard', render(ctx) {...} }, ...];
```

`matchRoute()` 先比静态段再比 `:param` 段，`:id` 段优先级低于静态段（`aftersale/apply` 不会被 `aftersale/:id` 抢走）。

### 2.3 屏函数的返回值

```js
render(ctx) => {
  html,        // 必填：整屏 HTML（手机端必须含 UI.cHeader(...)）
  action,      // 可选：底部固定动作条 HTML，渲染在手机壳内、Tab 栏之上
  tab,         // 可选：'home' | 'messages' | 'cart' | 'me'，用于高亮底部 Tab；不返回 = 不显示 Tab 栏
  title,       // 可选：后台顶栏标题（后台屏用）
  mount(root, ctx),  // 可选：挂事件。把 root 内元素绑好；也可返回一个 cleanup 函数
  onUnmount(),       // 可选：同 mount 的返回值，二选一即可
}
```

- `mount` 的返回值会被注册为 cleanup，下次渲染 / 切屏 / 切端时调用；`onCleanup(fn)` 可追加多个。
- 屏内自己 `render()`（局部重渲染）时不会重复触发 cleanup，只有路由级渲染才会清理。
- 定时器统一走 `ctx.every(fn, ms)` 或 `onCleanup(() => clearInterval(id))`，`render()` 入口会 `clearTimers()`。

### 2.4 ctx 字段

`ctx = { scope, path, segs, params, query, hash, go, refresh, back, every, onCleanup }`

| 字段 | 说明 |
| --- | --- |
| `scope` | `'c'` 或 `'admin'` |
| `path` | 匹配到的路由 path 模板，如 `goods/:id` |
| `params` | `{id:'g1'}` |
| `query` | `{tab:'unpaid'}`（字符串值，未做类型转换） |
| `hash` | 当前完整 hash |
| `go(hash)` / `refresh()` / `back()` | 等价 `App.go` / `App.refresh` / `App.back` |

### 2.5 导航与事件委托

- 任意元素加 `data-go="#/c/cart"` 即跳转；`data-go="back"` 返回上一屏（`app.js` 维护 `backStack`，只在同 scope 内回退）。
- `data-act="xxx"` 由各屏在 `mount` 里自己绑定；`data-step="+" | "-"` 交给 `UI.stepper` 的使用方自己处理。
- `input[data-enter="actName"]` 支持回车触发同名 `data-act` 按钮（用于「搜索」这类输入框）。
- 全局 `window.App` = `{ go, back, refresh, resetDemo, parseHash, matchRoute, getBuyNow, setBuyNow, clearBuyNow, toast }`。

### 2.6 全局壳层

- **演示顶栏**：品牌「小满商城 · 交互原型」+ 分段控件「手机端 / 后台」（跳 `#/c/home` / `#/admin/dashboard`）+「重置演示数据」+「演示工具」（跳 `#/admin/demo`）。
- **手机端底部 Tab**（t9 起）：固定 4 项 **首页 / 消息 / 购物车 / 我的**（原来的「分类」Tab 已由消息 Tab 取代；分类唯一入口改为首页频道栏末尾的「分类」胶囊 → `#/c/category`）。消息 Tab 带未读红点（`Store.unreadCount()`，>99 显示 `99+`），购物车 Tab 带件数角标（`Store.cartCount()`，0 不显示）。**没有 AI 助手入口**（需求裁定 R1–R3 期间隐藏）。
- **后台**：`.admin > .aside`（9 项菜单，售后带待处理角标 `Store.stats().pendingAs`）+ `.admin-main`（`.admin-head` 显示 `title` +「我是运营 / 客服（演示身份）」+ 返回手机端按钮，`.admin-body` 渲染屏幕）。`#/admin/login` 例外：不渲染侧栏，整屏居中登录卡。

## 3. 手机端 21 屏清单（17 主流程 + 4 屏界面改版新增）

| # | 路由 | 屏幕 | 需求编号 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | `#/c/home` | 首页（t9 淘宝式改版） | 5.2 导购 / BR-GOODS-05、08、10 | 固定头（sticky）= 白色频道栏（推荐/服饰/家居/食品/运动/美妆/数码，选中黑粗 + 橙色 16×3px 下划线；末项「分类」胶囊 → `#/c/category`）+ 白胶囊搜索栏（左扫码 / 右相机 / 橙色「搜索」按钮 / 购物车图标带角标）；主体 = 3 张渐变 Banner 轮播（半透明 tag + 大标题 + 副标 + 内联 SVG 插图 + 3 个指示点，4s 自动播）+ 提示条（`notice.text`，`#FFF8E6` 底 + 灯泡 SVG + `UI.fromNow`）+「为你推荐」两列商品卡 + 到底标注；下架不出现，全售罄标「暂时缺货」 |
| 2 | `#/c/search` | 搜索 | BR-SEARCH-01~03、07 | 输入框 + 热门词 + 历史词（localStorage `proto-search-history`，最多 8 条，可清空）；提交跳 `#/c/list?kw=` |
| 3 | `#/c/category` | 分类 | 5.2 | 左侧一级竖排 + 右侧二级网格，点击进 `#/c/list?catId=` |
| 4 | `#/c/list?kw=&catId=&brandId=&onlyStock=&sort=&minP=&maxP=` | 列表（淘宝式） | BR-SEARCH-01/03/04/05、BR-GOODS-05 | 两列商品网格 + 顶部横滚 chips（综合 / 销量 / 价格↑↓（点两次切换）/ 上新 / 仅看有货）+ 右侧固定「筛选」按钮（有生效条件时高亮并显示个数）；点筛选弹底部 `.sheet`：价格区间（两个输入框 + 0-50/50-100/100-200/200 以上快捷档）、一级分类 chips 单选、8 个品牌 chips 单选、仅看有货开关，底部「重置 / 查看 N 件商品」N 随草稿实时变化；应用后写回 URL；上下限填反自动交换并提示（BR-SEARCH-05）；空结果 `.empty` + 清除筛选 |
| 5 | `#/c/goods/:id` | 商品详情 | BR-GOODS-01~06、09、BR-CART-02 | 展示价=可售规格最低价；规格可选、售罄置灰、切换联动价格与库存；未选全规格不能加购/立即购买；数量步进；详情/参数/评价三 Tab；动作条 客服 / 收藏 / 加入购物车 / 立即购买 |
| 6 | `#/c/cart` | 购物车 | BR-CART-01~07、BR-ORDER-08 | 店铺分组、全选/单选、数量增减、删除、失效区（已下架/规格已删除/缺货，不参与结算）、合计只算勾选、去结算；空车引导去逛逛 |
| 7 | `#/c/checkout` | 确认订单 | BR-ORDER-02/04/09/10/11、BR-CP-04/05 | 地址卡（无地址提示新增）→ 商品行 → 选券弹层（门槛不足/已领过/已领完/已停用四种不可用原因）→ 金额明细（商品金额/优惠/运费 ¥0/应付）→ 备注 → `Store.submitOrder` 成功跳支付页 |
| 8 | `#/c/pay/:orderId` | 演示支付 | BR-PAY-01~07、BR-ORDER-06 | 应付金额 + 30 分钟倒计时 + 「演示模式：不产生真实扣款」常驻标注；「模拟支付成功」→ 待发货，「模拟支付失败」→ 提示可重试且订单状态不变；超过 30 分钟（`Store.orderExpired`）自动按「超时关闭」落库，页面换成关闭告警 + 两个禁用按钮 + 「重新下单」 |
| 9 | `#/c/orders?tab=` | 订单列表 | BR-ORD-01/06/07/09、BR-AS-17/18 | 7 分栏（全部/待付款/待发货/待收货/待评价/已完成/售后）；按状态给操作：去支付·取消订单·查看物流·确认收货·申请售后·去评价·再次购买·删除订单·查看售后；已全额退款打标且不进「已完成」 |
| 10 | `#/c/order/:id` | 订单详情 | BR-ORD-01~08/10、BR-AS-02/03 | 状态区 + 未付款倒计时（到点自动按「超时关闭」落库并 toast 提示，BR-ORDER-06）+ 地址 + 商品行（单行申请售后）+ 金额明细 + 订单信息 + `.timeline` + 底部操作条 |
| 11 | `#/c/aftersale/apply?orderId=&itemId=` | 申请售后 | BR-AS-01~05、21 | 类型按 `Store.canApply().types` 限制（仅退款 / 退货退款）、数量 ≤ `maxQty`、原因取 `Store.AS_REASONS`、说明、上传凭证占位（退货退款强制 ≥1 张）；提交后跳售后详情 |
| 12 | `#/c/aftersale/:id` | 售后详情 | BR-AS-06~16、19/20/22 | 8 态状态条（主流程 + 终态两行，跳过的节点标注）+ 商品行 + 退款金额 + 处理进度时间线 + 按状态按钮（撤销申请/填写退货单号/查看物流）；`refunding && restockPending` 显示红色「库存待回补」 |
| 13 | `#/c/aftersales` | 我的售后 | BR-AS-06/13 | 按状态筛选（全部/处理中/已完成/已关闭）+ 卡片 + 空态引导 |
| 14 | `#/c/review?orderId=` | 评价 | BR-CM-01~06、BR-ORD-09 | 对「待评价」逐行星级 + 文字 + 上传占位；提交后该行不可再评（已评价行置灰），全部行评完订单转「已完成」；输入内容在重渲染间保留 |
| 15 | `#/c/me` | 我的 | 5.10 | 演示身份卡 + **4 格工具区（收藏 / 关注店铺 / 足迹 / 我的优惠券，带数量角标）** + 订单快捷入口（4 状态 + 售后）+ 我的地址/评价 + 演示模式说明 |
| 16 | `#/c/coupons` | 优惠券中心 | BR-CP-01~03、08~10 | 可领取列表 + 「我的券」未使用/已使用/已过期分栏；领取提示三态可区分：「领取成功…」/「你已领过这张券（BR-CP-02）」/「已领完」，停用券提示「该券已停用」 |
| 17 | `#/c/address` | 地址管理 | 5.10 | 列表 + 新增/编辑弹层（收货人/手机号/地区/详细地址/默认开关）+ 删除（默认地址不可删时给出提示） |
| 18 | `#/c/messages?type=` | 消息中心 | 演示增强（需求文档 v3 未定义，不参与交易状态机） | 5 个类型 tab（全部 / 订单 / 售后 / 优惠券 / 系统，带未读数）+ 列表项（类型徽标 / 标题 / 时间 / 正文两行 / 未读红点）+ 点单条先 `markRead` 再跳 `href` + 右上「全部已读」（无未读时变「已全部读过」）+ 空态 |
| 19 | `#/c/favorites` | 我的收藏 | 演示增强 | 两列商品卡（复用 `goodsCard`）+ 每卡「取消收藏」+ 顶部「共 N 件」+ 空态去逛逛 |
| 20 | `#/c/follows` | 关注店铺 / 品牌 | 演示增强 | 官方店关注卡（在售件数）+ 8 个品牌列表（在售件数 + 「+ 关注 / 已关注」）；切换后 toast 用 `Store.toggleFollow` 返回的 `msg` |
| 21 | `#/c/footprints` | 浏览足迹 | 演示增强 | 今天 / 昨天 / 更早分组，项 = 缩略图 + 名称 + 价格 + 浏览时间，点击进商品详情；右上「清空」→ `UI.confirm` 二次确认 → `Store.clearFootprints()`；空态 |

后台 10 屏（`login / dashboard / orders / aftersales / goods / catalog / stock / coupons / comments / demo`）的路由表与实现都在 `assets/admin-screens.js`，本文件不重复；外壳只负责侧栏、顶栏与 `.admin-body` 挂载。

## 4. 如何新增一屏

1. 在 `assets/app.js` 里写一个 `{ path, render(ctx) }` 对象：

```js
{
  path: 'brand/:id',
  render(ctx) {
    const id = ctx.params.id;
    return {
      tab: 'home',                      // 需要底部 Tab 就写，不要就不写
      html: UI.cHeader({ title: '品牌页', right: '' }) +
        `<div class="c-body"><div class="card pad">品牌 ${esc(id)}</div></div>`,
      action: `<div class="c-actionbar"><button class="btn primary block" data-act="go">去逛逛</button></div>`,
      mount(root) {
        root.querySelector('[data-act="go"]').onclick = () => App.go('#/c/home');
        onCleanup(() => { /* 清理定时器/监听 */ });
      }
    };
  }
}
```

2. 把它加进 `C_ROUTES` 数组（顺序无关，`matchRoute` 会挑最匹配的一条）。
3. 跳转入口加 `data-go="#/c/brand/b1"`，或在代码里 `App.go(...)`。
4. 数据变更后调 `App.refresh()`（保持当前路由重渲染）或屏内局部 `render()`。
5. 跑一次 `node --check prototype/assets/app.js`，再用 Chrome headless 截一张图确认不是空白页。

**后台加屏**：在 `assets/admin-screens.js` 里往 `window.ADMIN_ROUTES` 追加 `{ path:'xxx', render(ctx) }`，返回 `{ html, title, mount }` 即可，侧栏菜单在 `app.js` 的 `ADMIN_NAV` 里加一条。

## 5. 数据与工具约定

- 金额**一律「分」**：展示必须 `UI.price(cent)`（红字带 ¥ 符号）或 `UI.yuan(cent)`（纯数字串）。不要自己 `¥` + `n/100`。
- 时间统一 `UI.time(ts)`（`yyyy-MM-dd HH:mm`）、`UI.fromNow(ts)`、`UI.countdown(ms)`。
- 写操作一律看返回值：`const r = Store.xxx(); if (r && r.ok === false) UI.toast(r.msg, 'err'); else UI.toast('已xxx', 'ok')`（内部封装：`okToast(r)`）。
- HTML 一律 `esc()` 转义用户可见文本；不拼内联事件（用 `data-act` + `mount` 绑定），因为 CSP 友好且便于测试。
- 不新增全局状态：页面级临时态放模块变量（如 `qtyCache / selCache / asDraft / reviewText / coRemark`），`resetDemo` 时一并清空。

## 6. 已知缺口（发现 `store.js / ui.js / theme.css` 不足，但按约定未改它们，用现有 API 绕开）

1. **多店铺分组**：`Store` 是单商户模型，购物车「按店铺分组」用固定店名「小满商城自营」渲染分组头，未在数据层引入 storeId。
2. **加购不限可售库存**：`Store.cartAdd` 只校验 1–999（BR-CART-02），不校验 `sellable`。页面侧用 `inCartQty(skuId) + qty > Store.sellable(skuId)` 拦截并提示（保留 BR-GOODS-04 语义）。
3. **规格组合可选性无 API**：`Store` 没有「某规格组合是否可售」的方法，详情页用 `skus + 已选维度` 推导 `optEnabled()`。置灰项区分两种原因：该维度值的所有 SKU 可售为 0 → 真售罄，点选提示「「<值>」已售罄」；只是与已选的其他维度没有组合 → 点选会自动取消冲突维度并选中，提示已切换（BR-GOODS-04/05）。
4. **订单分栏无 API**：`Store` 不提供按「待评价 / 已完成 / 售后」过滤的方法，也未在订单对象上持久化 `fullyRefunded` 之外的判定；页面侧用 `isFullRefunded() / hasUnreviewed() / openAsOfOrder()` 实现 BR-ORD-09（已退款行不参与全部评价判定）。
5. **`UI.statusTag(order)` 依赖 `order.fullyRefunded`**：Store 只在整单全退时写这个字段，所以列表/详情调用时传的是页面侧算出的 `{status, fullyRefunded}` 视图对象，避免「部分退款」被误打「已全额退款」。
6. **物流轨迹无 API**：只有 `order.tracking{company,no}` + `shippedAt`，所以「查看物流」用弹层展示承运商/单号/发货时间 + 订单时间线（BR-ORD-03），不做真实轨迹。
7. **上传凭证是占位**：无上传接口，`UI.uploads()` 的槽点击后生成演示文件名写入 `photos`，用于满足 BR-AS-21「退货退款 ≥1 张」的校验与展示。
8. **未付款超时靠页面侧触发**：`Store` 没有后台定时器，但已提供 `Store.orderExpired(id)` 与 `closeOrder(id,'超时关闭')`；支付页与订单详情在挂载/每秒 tick 时判定并发起关单（释放锁量、券退回、写时间线）。没有后台调度，所以「无人在页面停留」时不会自动关单——这符合 7.1 只允许两个演示手段的约束；后台另有单笔「立即关单」（订单列表行/详情弹层）与演示工具里的批量入口（BR-ORDER-06/13）。
9. **下架商品在 C 端不可见**：`Store.goodsList` 只返回 `onSale`，所以首页/搜索/列表拿不到 `g16`（符合 BR-GOODS-08）；购物车通过 `cartLines().invalid === '已下架'` 展示失效区。
10. **券面额用 `UI.yuan`**：券卡片的大号面额是 22px 的 `.x-price-in`，用 `¥` + `UI.yuan()` 拼装，因为 `.price` 的字号层级不匹配券面视觉；**其余所有金额仍是 `UI.price()`**。
11. **客服 / 收藏 / 提醒发货**：需求未定义交互，统一 `UI.toast('演示原型：xxx暂未实现')` 占位，不做假页面。
12. **图片资源**：零依赖。t9 起商品图统一走 `assets/thumbs.js`（`THUMBS.of(goodsId)` 返回内联 SVG，白底渐变），轮播插图用 `banners[].illus` 经 `illusOf()` 内联 SVG；`store.js` 里 `pics:[bg, emoji]` 的 emoji 仍在数据中，但**页面不再渲染任何 emoji 字符**（t9 硬要求）。
13. **AI 助手入口**：按需求裁定 R1–R3 期间隐藏，全站不出现。
14. **一级分类无 emoji / 图标字段**：`Store.state.cats` 只有 `{id,name,parentId,sort}`；t9 起首页不再用分类图标（t5 的 `CAT_EMOJI` 映射已删除），频道栏文案与顺序在 `app.js` 的 `HOME_CHANNELS` 常量里写死（推荐 / 服饰 / 家居 / 食品 / 运动 / 美妆 / 数码 → null / c1 / c2 / c4 / c5 / c6 / c3）。分类页仍按 `sort` 升序读 `cats`。
15. **品牌无商品数字段**：`Store.state.brands` 只有 `{id,name}`，「在售 N 件」由页面自算 `goods.filter(g => g.brandId===b.id && g.onSale).length`。
16. **销量排序 / 品牌过滤 / 仅看有货无 API**：`Store.goodsList` 只支持 `default|priceAsc|priceDesc|new`（BR-SEARCH-04 只要求按展示价排序），所以 `sort=sales` 在页面侧按 `g.sales` 降序后置排序，`brandId` 用 `g.brandId` 后置过滤、`onlyStock` 用 `Store.totalStock(g) > 0` 后置过滤；三者都不改 `store.js`，也不写入 Store 状态（刷新/返回靠 URL 保持）。
17. **消息 / 收藏 / 关注 / 足迹是演示增强**：需求文档 v3 无对应 BR，数据都在 `Store` 里但不进入订单/售后/库存状态机；消息 `href` 为空串时只标已读不跳转。
18. **足迹写入时机**：商品详情 `mount` 时调 `Store.addFootprint(goodsId)`，先比对 `Store.footprintList()[0].goodsId` 再写，避免屏内局部重渲染重复记账（`Store` 本身已做去重置顶、上限 30）。
19. **只有一家店铺可关注**：`Store` 单商户模型下 `shop1` 是唯一店铺，店铺卡与品牌走同一套 `toggleFollow`，取消后再点可恢复关注。

## 7. R1 验收 13 步走通情况

图例：✅ 已在原型里实测点通；◐ 部分步骤在本端完成；⏳ 该步在后台屏（proto-admin 维护），数据与规则同源。

| 步 | 内容 | 状态 | 证据 / 说明 |
| --- | --- | --- | --- |
| 1 | 后台新建 T 恤（2 规格） | ⏳ | 后台「商品与规格」屏（`Store.goodsSave`） |
| 2 | C 端搜「T 恤」→ 详情选 黑色/L | ✅ | 搜索 1 条结果；详情 2 组规格（颜色/尺码）；未选全规格点加购 toast「请先选全规格」，购物车数量不变 |
| 3 | 加购两个规格 → 购物车出现两行 | ✅ | 同一商品不同规格各占一行（行键 = 用户 + 规格，BR-CART-01） |
| 4 | 结算用券提交 + 演示支付成功 | ✅ | 领 cp1 后结算选券：商品 ¥99 − 券 ¥5 = 应付 ¥94（9400 分），支付成功 → 待发货，券变 `used`，页面上「演示模式：不产生真实扣款」可见 |
| 5 | 后台订单列表金额一致 | ⏳ | 后台「订单管理」屏读同一份 `Store.state.orders` |
| 6 | 发货填物流 | ⏳ | 后台「订单管理 → 发货」（`Store.shipOrder`）；C 端确认后订单详情显示已发货 + 承运商/单号 |
| 7 | C 端确认收货 | ✅ | 二次确认后 `received`，时间线追加 |
| 8 | 对黑色/L 行申请退货退款 + 1 张凭证 | ✅ | 售后申请页类型按 `canApply().types` 给 2 项、原因 6 项、上传 1 张后可提交 → 申请中 |
| 9 | 后台同意→用户填单号→运营确认收货→退款完成 | ◐ | 已验证：后台同意 → 待寄回 → C 端「填写退货单号」→ 待收货；后续「确认收货 / 退款完成」在后台屏（`Store.asConfirmReceive` / `Store.asRefund`），退款完成后订单主状态仍是「已收货」（BR-ORD-08） |
| 10 | 后台库存视图：黑色/L 9→10，白色/M 仍 7 | ⏳ | 后台「库存视图」屏；回补矩阵在 `Store.asRefund` 内实现（BR-AS-14/15） |
| 11 | 另开未付款单 → 演示工具「立即关单」 | ⏳ | 后台「演示工具」屏（`Store.demoCloseUnpaid`）：释放锁定量、券回未使用、写日志 |
| 12 | 手动取消同结果 | ✅ | 订单列表「取消订单」→ 二次确认 → `closed`，`closedBy='用户取消'`（与超时同一套释放逻辑） |
| 13 | 演示支付失败仍为未付款可重试 | ✅ | 支付页「模拟支付失败」→ 提示「支付失败（演示分支）：订单仍为未付款，可在 30 分钟内重试」，订单状态不变，可再次点成功 |

结论：**C 端可独立走通 2、3、4、7、8、12、13 共 7 步，第 9 步走到「用户填单号」；第 1、5、6、10、11 步在后台屏，数据层完全共用，后台就绪后 13 步可连续走通。**

## 8. 自检记录（可复跑）

```bash
# 1) 语法
node --check prototype/assets/app.js            # => 通过（无输出）

# 2) 逐路由截图（17 张，确认非空白页）
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --window-size=420,900 --virtual-time-budget=3000 \
  --screenshot=D:/litemall-next/prototype/.shots/c-home.png \
  "file:///D:/litemall-next/prototype/index.html#/c/home"
# 其余 16 条把 c-home 换成：search / category / list / goods / cart / checkout / pay /
# orders / order / as-apply / as-detail / aftersales / review / me / coupons / address

# 3) DOM 健康检查（逐路由 --dump-dom 后 grep 'undefined|NaN|\[object Object\]|TODO'）
```

```bash
# 4) 汇总回归（需先在 prototype 下起静态服务：python -m http.server 8765）
cd prototype
node tools/scan.js          # 31 路由（C 21 + 后台 10）：空屏 / 可疑文案 / 横向溢出
node tools/r1.js            # R1 验收脚本 15 项
node tools/fix-check.js     # P-01~P-05 复验 7 项
node tools/seed-check.js    # 种子订单超时判定
node tools/store-fix-check.js  # 数据层 22 项
```

最近一次结果（t9 交付时实测）：`node --check` 5 个 assets 文件全通过（store / ui / app / thumbs / admin-screens）；21 个 C 屏截图全部生成（最小 19KB，无 1KB 级空白页）；`tools/scan.js` 31 路由空屏 0 / 可疑文案 0 / 横向溢出 0；`tools/r1.js` 15/15（console.error=0，未捕获异常=0）；`tools/fix-check.js` 7/7；`tools/store-fix-check.js` PASS 22 / FAIL 0；`tools/seed-check.js` 未付款 3 笔（可付 2 / 超时 1）。t9 的 390×844 专项断言 `.shots/t9-check.js` 40/40、`.shots/t9-extra.js` 6/6。

截图目录 `prototype/.shots/` 只是自检产物，**不入库**。

## 9. 缺陷修复记录（VERIFY.md P-01 ~ P-05，t4）

| 编号 | 级别 | 问题 | 修复位置 | 复验 |
| --- | --- | --- | --- | --- |
| P-01 | high | 售后单金额用「单价×数量」，丢券分摊与行数量，与申请页「预计退款」矛盾 | 数据侧 `Store.refundAmount()`（行实付×qty÷行数量，向下取整）；页面侧 `app.js` 申请页两处预计退款改用同一函数 | `node .shots/verify/r1.js` R1-8：申请页 ¥96.50 == 售后单 `amount=9650` |
| P-02 | high | 后台只有批量「一键关闭全部未付款订单」，缺单笔关单语义 | `admin-screens.js`：订单表未付款行 + 订单详情弹层各加「立即关单」，走 `Store.demoCloseUnpaid(id)` + 二次确认，只关当前单 | `node .shots/verify/fix-check.js`：关 o100 后 o101/o102 仍 `unpaid`，toast「已关单 1 笔」，`demoLog` 有记录 |
| P-03 | medium | 规格置灰一律提示「该规格组合暂时缺货」，与真实库存不符 | `app.js` 规格动作：真售罄 → 「「<值>」已售罄」；跨维度无此组合 → 自动清空冲突维度并提示已切换；`title` 同步区分 | `fix-check.js`：白色点选自动切到「颜色=白色」并提示，杏色提示已售罄 |
| P-04 | medium | 30 分钟超时只改文案「已超时」，不关单且仍可支付 | 数据侧 `Store.orderExpired()` + `payOrder` 超时拒绝；页面侧支付页/订单详情挂载与 tick 判定后 `Store.closeOrder(id,'超时关闭')`，支付页换禁用态 + 「重新下单」 | `fix-check.js`：超时单自动 `closed/closedBy=超时关闭`，两个支付按钮 `disabled`；未超时单倒计时与按钮正常 |
| P-05 | low | 后台「确认收到退货」无二次确认，与同类动作不一致 | `admin-screens.js` `as-receive` 改为 `U.confirm`（含售后单号/商品/数量与回补矩阵说明）后再 `asConfirmReceive` | `node .shots/verify/r1.js` R1-9 通过弹层「确认收到」完成确认 |

复验总账：`node --check` 四文件全通过；`node .shots/verify/r1.js` 15/15；`node .shots/verify/fix-check.js` 7/7；`node .shots/verify/scan.js` 27 路由空屏 0 / 可疑文案 0 / 横向溢出 0；`node .shots/store-fix-check.js` PASS 22 / FAIL 0。截图见 `.shots/fix-admin-orders-close.png`、`fix-c-goods-spec.png`、`fix-c-pay-expired.png`。
## 10. 界面改版 v3.1（t5）落点

> 说明：本节记录 t5 当时的落点，作为历史留档。其中「首页一级分类横滚条」「消息入口卡」「Tab 里放分类」等已在 t9 被替换（见第 11 节），行号以 t5 版本为准。

手机端 5 项界面改版，只动 `assets/app.js` + `assets/theme.css` 新增类 + 本文件；`store.js / ui.js / index.html / tools/**` 未改（数据侧 API 由 captain 先行扩好）。

| 改动 | 落点 | 说明 |
| --- | --- | --- |
| ① 首页分类横滚条 | `app.js:496` `CAT_EMOJI` / `:503` `catStrip()` / 首页 `:663` | 一级分类按 `sort` 升序，emoji + 名称，末项固定「全部分类 ›」（`#/c/category`），其余进 `#/c/list?catId=<id>`；单行横滑、隐藏滚动条，不参与页面横向溢出 |
| ② 消息入口 + 消息中心 | `app.js:512` `msgEntry()` / 首页 `:681` / 新屏 `:724` | 删掉原 `.kingkong` 5 宫格，原位换消息卡（最新 1 条摘要 + `Store.unreadCount()` 气泡，未读 0 不出气泡）；新屏 `#/c/messages` 5 个类型 tab（带未读数）+ 8 条种子 + 点单条先 `markRead` 再跳 `href` + 「全部已读」+ 空态 |
| ③ 我的工具区 + 收藏/关注/足迹三屏 | `app.js:2584`（工具区数据）/ `:2589` `toolsHtml` → 插在「我的订单」卡上方 / 新屏 `:2660` favorites、`:2691` follows、`:2734` footprints（`fpGroupName` 在 `:2735`） | 工具区 4 格带数量角标（`favGoods()` / `followList()` / `footprintList()` / 可用券数）；收藏页两列 `goodsCard` + 每卡「取消收藏」；关注页官方店卡 + 8 品牌（toast 用 `Store.toggleFollow` 返回 `msg`）；足迹页今天/昨天/更早分组 + 清空二次确认 |
| ③b 商品详情收藏/足迹 | `app.js:1140`（`faved` 两态）/ `:1137` 足迹写入 / `:1198` 按钮两态 / `:1316` `fav` 动作 | 收藏改为真调 `Store.toggleFav`，按钮在「收藏 / 已收藏」间切换并 toast Store 返回 `msg`；进详情调 `Store.addFootprint`（先比对 `footprintList()[0]` 防重复记账） |
| ④ 返回键统一 | `app.js:1953`（我的订单去掉 `noBack`）/ `:2435`（我的售后改为始终有返回键） | 除 `home / category / cart / me` 4 个 Tab 主屏外，全部 C 屏都有 `.c-head .back` |
| ⑤ 列表页淘宝化 | `app.js:891` 起整段重写：`LIST_SORT:892`、`PRICE_TIPS`、`storeSortOf:906`、`listQuery:910`、`activeFilterCount:920`、屏 `:930`、chips `:952`、筛选按钮 `:964`、`:984` 横滚条、`openFilter():991`（底部 `.sheet`，`:1008` 快捷档、`:1062` 弹层内点选、`:1110` `filter` 动作） | 两列 `goods-grid` + chips（综合/销量/价格↑↓/上新/仅看有货）+ 右侧固定「筛选」（有生效条件时高亮 + 个数）；筛选 sheet 含价格区间（输入 + 4 快捷档）、一级分类、8 品牌、仅看有货，「查看 N 件商品」随草稿实时变化；应用写回 `listUrl`（新增 `brandId`、`onlyStock`，`app.js:549-550`），BR-SEARCH-05 上下限交换提示保留 |

新增 CSS 类（全部在 `theme.css:514` 之后追加，未改任何既有类）：
`.x-catstrip / .x-catstrip-in / .x-catpill(.more) / .x-msgcard(.ic/.dot) / .x-msg.unread / .x-reddot / .x-tools / .x-tool(.ic/.n) / .x-fav / .x-fav-del / .x-shopcard(.avatar) / .x-follow-row / .x-fp-group / .x-fp / .x-listbar / .x-chips / .x-chip(.on) / .x-filter-btn(.on/.n) / .x-fsec / .x-fhd / .x-fft`。

返回键自查（`.c-head .back` 存在性，17/17）：

```text
#/c/search  #/c/list  #/c/goods/g1  #/c/checkout  #/c/pay/o100  #/c/orders  #/c/order/o114
#/c/aftersale/apply?orderId=o114&itemId=it0  #/c/aftersale/as100  #/c/aftersales
#/c/review?orderId=o114  #/c/coupons  #/c/address  #/c/messages  #/c/favorites  #/c/follows  #/c/footprints
```

无返回键（底部 Tab 导航，符合设计）：`#/c/home`、`#/c/messages`、`#/c/cart`、`#/c/me`（t9 起「分类」不再是 Tab 主屏，`#/c/category` 由首页胶囊进入，**有**返回键）。例外：商品详情只有底部动作条、没有 Tab 栏，但有返回键。

真点击验收（CDP 真点，32/32 通过，`console.error=0`、未捕获异常 `0`）：横滚条 6 项且 `overflow=0`、消息气泡 3 → 点单条已读并跳 `#/c/orders?tab=shipped` → 全部已读归零、收藏 3 → 取消剩 2、关注页 8 品牌切换、足迹 8 条分组 → 清空为空态、详情收藏两态、列表 chips 五种状态 + 价格↑↓ + 销量降序（5310 > 3821）+ 仅看有货 14 件 + 筛选 sheet 应用后 URL 带 `catId/brandId/onlyStock/minP/maxP` 命中 2 件 + 上下限交换提示。

## 11. 首页改版与四 Tab（t9）

### 11.1 改动落点（`prototype/assets/app.js`，t9 后 3080 行）

| 小节 | 位置 | 说明 |
| --- | --- | --- |
| 底部四 Tab | `app.js:183` `C_TABS` / `:190` `cTabBar()` | Tab 由「首页 / 分类 / 购物车 / 我的」改为「首页 / **消息** / 购物车 / 我的」；图标外包 `.x-tab-ic`，消息未读走 `Store.unreadCount()`、购物车件数走 `Store.cartCount()`（0 不显示角标） |
| 内联图标与缩略图 | `app.js:484` `SVG_SCAN` / `SVG_CAM` / `SVG_BULB`、`:489` `illusOf()`、`:493` `goodsThumb()`、`:503` `emp()`、`:515` `starSVG()`、`:520` `starsRow()` | 扫码 / 相机 / 灯泡用内联 SVG；商品图统一 `THUMBS.of(id)`；空态 `emp()` 用 `UI.icon` 图标（替换原 emoji 入参）；星级用 SVG，不再用 `★` |
| 商品卡 | `app.js:528` `goodsCard()` | 白卡 12px 圆角 + 轻投影；图区 `goodsThumb` + 左上标签（`goods.tags` 最多 2 个，`.x-gtag-multi/hot/sale/new/other/gray`）；名称 13px 两行截断；价格 `--price` 红粗（`¥` 小号）；右侧「已售 N」。首页 / 列表 / 搜索 / 收藏共用 |
| 首页频道栏 + 搜索栏 | `app.js:680` `HOME_CHANNELS` / `:689` `homeChannels()` / `:697` `homeSearchBar()` | 顺序固定 推荐/服饰/家居/食品/运动/美妆/数码 → null/c1/c2/c4/c5/c6/c3；选中黑粗 + 橙色 16×3px 下划线；末项「分类」胶囊（`UI.icon('grid')`）→ `#/c/category`，是**唯一分类入口**。搜索栏杆位含扫码、输入框 `#home-kw`（`data-enter="homeSearch"`）、相机、橙色「搜索」按钮、购物车图标 + 件数角标 |
| 首页整屏 | `app.js:720` 起（`path: 'home'`）→ `:730` Banner、`:733` 指示点、`:737` 提示条、`:740` 为你推荐、`:742` `.goods-grid`、`:743` 到底标注、`:746` `tab:'home'` | 频道栏 + 搜索栏放进 `.c-head.x-hhead` 实现 sticky；Banner 4s 自动轮播（`mount` 里 setInterval，`render` 会清定时器）；点指示点手动切换；`.x-notice` 提示条保留 `notice.text` |
| 消息屏 | `app.js:785` 起 | 彩色圆形头像 `.x-mav.<type>`（order 橙 / aftersale 蓝 / coupon 红 / system 灰，头像内类型图标）+ 5 类 tab（带未读数）+ 未读红点 + `otab` / `readAll` / `open` 三个 demo action；t9 起有 `tab: 'messages'` 与 `noBack: true`（是 Tab 主屏） |
| 我的 | `app.js:2655` `x-tools` 4 格工具区、`:2668` `.x-mavatar` 头像 | 头像由 emoji 换成 `UI.icon('user',28)`；关注店铺图标换 `UI.icon('shop',20)`；4 格工具区（收藏 / 关注店铺 / 足迹 / 我的优惠券）沿用 t5 |
| 商品详情大图 | `app.js:1284` | 原 `.thumb.lg` + `pics[1]` emoji → `goodsThumb(g.id,'lg')` |
| 分类页 | `app.js` `path: 'category'` | 去掉 `tab:'category'`（分类已不是 Tab 主屏），恢复返回键；二级分类 emoji 改 `UI.icon` |

### 11.2 新增 CSS（`theme.css`，只在文件末尾 append，既有类零修改）

`--brand:#ff6a00 / --brand-dark / --brand-soft`、`--price:#ff2741`、`--bg:#f5f5f7`；`.c-tab`（白底 + 1px 灰线 + 选中橙）、`.c-tab a .x-tab-ic`、`.x-hhead`、`.x-chan / .x-chan-i(.on) / .x-chan-cat`、`.x-hsearch / .x-sbox / .x-sbtn / .x-cartbtn`、`.x-banner / .x-btag / .x-btxt / .x-bt / .x-bs / .x-bimg / .x-bwrap / .x-bdots`（`.x-bdots` 的最终样式见 §12 H-03：白实心 + 半透明白、统一 6px）、`.x-notice`、`.x-gcard / .x-gpic / .x-gtags / .x-gtag-{multi,hot,sale,new,other,gray} / .x-ginfo / .x-gnm / .x-gmeta`、`.x-thumb(.sm/.lg)`、`.x-mav.{order,aftersale,coupon,system}`、`.x-mavatar`、`.empty .ico`。

窄屏（≤640px，`theme.css` 的 `@media (max-width: 640px)` 块内追加）改为**整页滚动 + sticky 底栏**，让 `.c-head` 的 `position: sticky` 相对视口生效：`.phone / .phone-scroll { overflow: visible }`、`.c-tab { position: sticky; bottom: 0 }`、`.c-actionbar { position: sticky; bottom: 56px }`。

同时 `index.html` 里 `.phone .c-page` 的 `overflow-x: hidden` 改为 `overflow-x: clip` —— `hidden` 会把另一轴的 `visible` 计算成 `auto`，使 `.c-page` 变成 scrollport，从而让 `.c-head` 的 sticky 整个失效（这是 t9 排查到的一个隐藏坑，改动已附注释）。

### 11.3 t9 自检（真实输出）

```text
node --check assets/{store,ui,app,thumbs,admin-screens}.js      → 5/5 通过
node tools/scan.js              → 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0
node tools/r1.js                → 15/15 通过；console.error=0；未捕获异常=0
node tools/fix-check.js         → 7/7 通过
node tools/store-fix-check.js   → PASS 22 / FAIL 0
node tools/seed-check.js        → 未付款 3 笔（可付 2 / 超时 1）
CDP_VIEWPORT=390x844 node .shots/t9-check.js   → 40 passed, 0 failed
CDP_VIEWPORT=390x844 node .shots/t9-extra.js   → 6 passed, 0 failed
```

`t9-check.js` 覆盖：频道栏 7 项文案与顺序、选中项 `font-weight:700` + `rgb(17,17,17)` + `::after 16×3px rgb(255,106,0)`、搜索按钮 `rgb(255,106,0)`、`body` `rgb(245,245,247)`、价格 `rgb(255,39,65)`、商品卡圆角 12px、底部 Tab 文案「首页/消息/购物车/我的」+ 未读角标、Banner 结构（tag / 标题 / 副标 / SVG / 3 点 / 渐变）+ 点指示点切换、提示条底色、首页零 emoji（正则类 `[\u{1F300}-\u{1FAFF}\u2600-\u27BF\u{2B00}-\u{2BFF}\uFE0F]` 命中 0）、滚动 600px 后频道栏 `top=0` 且内容不与固定头重叠（gap=0）、四 Tab 点击后 hash 与高亮一致、消息屏彩色头像、列表/详情零 emoji + SVG 图、5 条主路径横向溢出 0。

截图（390×844，`.shots/`，不入库）：`t9-home-top.png` 106KB、`t9-home-scrolled.png` 100KB、`t9-messages.png` 54KB、`t9-cart.png` 19KB（空车态）、`t9-cart-filled.png` 45KB、`t9-me.png` 69KB、`t9-list.png` 109KB、`t9-goods.png` 46KB。

### 11.4 t9 相关取舍 / 遗留

1. **分类不再是 Tab**：需求要求底部四 Tab 为「首页 / 消息 / 购物车 / 我的」，分类降级为首页频道栏末尾胶囊（唯一入口）；`#/c/category` 路由保留、有返回键。
2. **Banner 一次只渲染 1 张**：轮播用同一容器换内容 + 3 个指示点（符合「3 张可点轮播 + 指示点」），点击整块按 `bannerHref()` 跳分类或领券页。
3. **`store.js` 的 `pics:[bg,emoji]` 仍在数据里**：按约定不改数据层，页面层不再渲染该 emoji；如需彻底清理应由数据层负责人处理。
4. **频道栏映射写死在 `app.js` 的 `HOME_CHANNELS`**：新增一级分类时需同步这张表（未列入的分类不会出现在首页频道栏，但仍可在分类页与列表页访问）。
5. **`.c-actionbar` 与 `.c-tab` 同时存在时**（购物车屏）：窄屏下 actionbar 固定在 `bottom:56px`，Tab 在 `bottom:0`，二者不重叠，已用 390×844 截图人工核对。
## 12. 首页改版缺陷修复（VERIFY-HOME-v3，t11）

对照 `prototype/VERIFY-HOME-v3.md`（H-01 ~ H-06）的处置与落点，证据均为真实运行输出。

| 编号 | 结论 | 修法 | 落点 |
| --- | --- | --- | --- |
| H-01 | **不改**（captain 裁定：`.demo-bar` 是演示 harness 控件条必须常驻；head sticky 属设计） | 未改动任何 `position/z-index/top`；曾加的 `@media(max-width:640px){.demo-bar{position:static}}` 已回滚到 t9 交付状态 | `theme.css:508`（media 块与 t9 一致） |
| H-02 | 必修，已修 | `each()` 改为 `Array.prototype.forEach.call(list \|\| [], fn)`，全站一处修好（`dots.children` 是 HTMLCollection，原本无 `forEach` → 每 4s 抛 `TypeError`） | `app.js:47-48` |
| H-03 | 必修，已修 | 指示点改为纯白系：off `rgba(255,255,255,.45)` / on `#fff` 实心，尺寸统一 6×6px 圆点（去掉橙色 `var(--brand)` 与 14px 胶囊）；点叠在 Banner **底部内侧**才在橙红底上可见 → 新增 `.x-bwrap` 包裹层，`.x-bdots` 绝对定位 `bottom:8px` 居中 | `theme.css:886-889`、`app.js:732-737`（Banner + 指示点同包一层 `.x-bwrap`） |
| H-04 | 必修，已修 | 搜索胶囊 `.x-sbox` 背景 `#F5F5F7` → `#fff`（input 仍透明），补 1px 内描边保证白底可分 | `theme.css:847-852` |
| H-05 | 必修，已修 | 「我的」补「客服」入口：HEAD 右侧 `UI.icon('comment')` + 「客服」文字，cell 列表「我的售后」之后加一行「客服 / 在线客服 9:00–21:00」；两处共用 `data-act="service"`，行为是占位 `toast('客服为占位入口（需求未定义会话能力）')`（与商品详情尾部客服同一口径，未新增业务模块） | `app.js:2668-2671`、`app.js:2709-2711`、`app.js:2732-2734` |
| H-06 | 不处理 | `M prototype/tools/cdp.js` 是 captain 的 `CDP_VIEWPORT` 视口支持，归 captain 提交，`tools/**` 本次未改 | — |

`theme.css:886` 的 `.x-bdots` 现为 `.x-bwrap` 内的绝对定位层：**指示点不在 `.x-banner` 内部**，因此 Banner 的 `mount` 里 `el.innerHTML = bannerHTML(b)` 重绘不会抹掉指示点，点击指示点也不会冒泡触发 Banner 的 `data-go` 跳转。

### 12.1 t11 自检（真实输出，390×844 手机视口）

```text
node --check assets/{store,ui,app,thumbs,admin-screens}.js   → 5/5 通过
node tools/scan.js                       → 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0
CDP_VIEWPORT=390x844 node tools/scan.js  → 同上，全绿
node tools/r1.js                         → 15/15 通过；console.error=0；未捕获异常=0
node tools/fix-check.js                  → 7/7 通过（P-01/P-02/P-03/P-04/P-04b/P-04c + 200ms 用例）
node tools/store-fix-check.js            → PASS 22 / FAIL 0
node tools/seed-check.js                 → 未付款 3 笔（可付 2 / 超时 1）
CDP_VIEWPORT=390x844 node .shots/t11-check.js → 9/9 通过（.shots 为自检产物，不入库）
CDP_VIEWPORT=390x844 node .shots/t9-check.js → 40 passed, 0 failed（H-02/H-03 改动后回归）
CDP_VIEWPORT=390x844 node .shots/t9-extra.js → 6 passed, 0 failed（回归）
```

`t11-check.js` 断言内容：等 9.2s 后无未捕获异常且 `.on` 下标从 0 走到 2（H-02 轮播不再抛错）、点第 3 个指示点 → 第 3 个 `on`；指示点 1 个 `rgb(255,255,255)` + 2 个 `rgba(255,255,255,.45)`、统一 6×6px、`bottom` 落在 Banner 内且中心命中 `.x-bdots`（H-03）；`.x-sbox` 背景 `rgb(255,255,255)`（H-04）；「我的」`data-act="service"` 命中 2 处（HEAD + cell）且点击出「客服为占位入口」toast（H-05）；`page.errors=[]`、`page.consoleErrors=[]`。

截图（390×844，`.shots/`，不入库）：`t11-home-390.png` 109KB、`t11-me-390.png` 68KB、`t11-h01-home-scroll600.png` 98KB、`t11-h01-goods-scroll420.png` 32KB。像素抽检：Banner 底部点区 24 个近白像素落在 `rgb(255,90,62)` 橙红底上，白点可见。

### 12.2 H-01 实测记录（供 captain 复核，未据此改动）

在 `CDP_VIEWPORT=390x844` 下实测（滚动窗口 600px 后）：`.demo-bar` rect 0..52（sticky, z-index:900）、`.c-head` rect **0..89**（sticky top:0, z-index:40）→ 头部上沿 52px 被演示顶栏覆盖；`document.elementFromPoint(195,60)` 命中 `.seg`；详情页返回键 rect `top:9`、中心命中 `.brand`（演示顶栏品牌区）。

这与 captain 复测的「head = 52..100、y=60 命中 head、返回键中心命中自身」不一致，可能因滚动容器不同（窄屏为整页滚动 `document.scrollingElement.scrollTop=600`，宽屏为 `.c-page` 内滚）。按裁定的「不改 `.demo-bar`/`.c-head` 的 position/z-index/top」执行，未做任何改动；如需按窄屏整页滚动再校准，建议单独开一条修复项（可选做法：窄屏把 `.c-head` 的 sticky `top` 设为 52px，或把演示顶栏从手机舞台内移出）。

> **后续（t13）**：captain 已撤回上述裁定，H-01 判定为真缺陷并已在 **§13** 修复（窄屏 `.c-head{top:52px}`）。本节保留当时实测数据作为「修前现状」记录。

## 13. H-01 修复：窄屏吸顶头部让位演示顶栏（t13）

**判定更正**：captain 先前以 `.phone-scroll` 为滚动容器复测，但 390 窄屏下 `.phone-scroll` 的 `overflow-y` 是 `visible`、真正滚动的是 `document.scrollingElement`，所以页面其实没滚动、头部自然停在 52 —— **原「H-01 不是缺陷」的裁定已撤回，H-01 是真缺陷**。实测真值（修前）：首页滚 600 后 `.c-head` = 0..89、`elementFromPoint(195,6/44)` 命中 `BUTTON`（演示顶栏）；详情页滚 420 后 `.c-head` = 0..48、返回键 rect 9..39、中心命中 `.brand` → 看不见也点不到。

### 13.1 落点（`theme.css`，只加窄屏媒体查询，不动任何 `position/z-index`）

文件末尾新增 `@media (max-width: 640px)` 块（`theme.css:971-985`，21 行）：

| 选择器 | 改前 | 改后 | 说明 |
| --- | --- | --- | --- |
| `.c-head` | `top: 0`（`theme.css:107`，全局） | `top: 52px`（仅 ≤640px） | 头部贴演示顶栏下沿（顶栏 0..52） |
| `.tabs-scroll` | `top: 48px`（`theme.css:290`） | `top: 100px`（仅 ≤640px） | 订单页状态分栏顺延（52 + 头高 48） |
| `.x-listbar` | `top: 48px`（`theme.css:718-720`） | `top: 100px`（仅 ≤640px） | 列表页筛选条顺延 |

同一块里还修掉一个**伴生问题**（不修则 A1 命中测试仍过不了）：`@media (max-width: 640px)` 下演示顶栏被 flex 压缩，`.seg button`（原 `padding:6px 16px`）逐字折行、整条 `.seg` 长到 69px，从 52px 高的 `.demo-bar` 里溢出到 `-8..61`，压在 `top:52` 的头部上 9px。改为「不换行 + 不参与收缩 + 窄屏紧凑字号」（`.demo-bar{flex:none;white-space:nowrap}`、`.seg button{flex:none;white-space:nowrap;padding:6px 10px;font-size:12px}`、`.demo-btn{flex:none;white-space:nowrap;padding:6px 8px;font-size:11.5px}`、`.brand{flex:none;white-space:nowrap;max-width:92px;text-overflow:ellipsis}`），实测 `.seg` 回到 34px（9..43）、`.demo-bar.scrollWidth == clientWidth == 390`（无横向溢出）。

宽屏（>640px）完全不受影响：手机是固定画框、由 `.phone-scroll` 内滚，头部 sticky 相对画框顶部贴齐，若这里也加 52px 会凭空多出一道空隙。

### 13.2 A 组实测：390×844（滚 `document.scrollingElement`）

```text
首页 before  head {"t":52,"b":141} headRel=0   bar {"t":0,"b":52}  frameScroll=0
首页 after   head {"t":52,"b":141} headRel=0   docScroll=600       ← 修前是 {"t":0,"b":89}
命中 headTop+6  → DIV.x-chan-i（head 内部）    ← 修前是 DIV.seg（演示顶栏）
命中 head 中线  → INPUT（head 搜索框）
详情 after   head {"t":52,"b":100} back {"t":61,"b":91} hitOwn=true   ← 修前 back {"t":9..39,"hitOwn":false}
列表页 .x-listbar top=100（head 52..100）      订单页 .tabs-scroll top=100
购物车滚到底：.c-tab clickable（rect 788..844）  详情页 .c-actionbar clickable
```

`CDP_VIEWPORT=390x844 node .shots/t13-check.js` → **9 passed, 0 failed**（A1 头部 top=52 + 两处命中在 head 内、A2 返回键 top≥52 且中心命中自身、A3 底部 Tab/动作条可见可点、A4 两条吸顶条 top=100）。

像素抽检（`.shots/t13-a-*.png`，390×844）：y 0..51 均值 `rgb(52,45,43)`（深色演示顶栏）、y 53..99 均值 `rgb(244,244,245)`（白色头部）→ 头部不再被顶栏覆盖。

### 13.3 B 组实测：1200×900（滚 `.phone-scroll`，宽屏不受影响）

```text
首页 before  head {"t":88,"b":177} headRel=0  frameScroll=0
首页 after   head {"t":88,"b":177} headRel=0  frameScroll=600   ← 相对画框顶不变，无 52px 空隙
命中画框内头部 → DIV.x-chan-i / INPUT
详情 after   head {"t":88,"b":136} back {"t":97,"b":127} hitOwn=true
```

`CDP_VIEWPORT=1200x900 node .shots/t13-check.js` → **5 passed, 0 failed**。

### 13.4 回归（改后真实输出）

```text
node --check assets/{store,ui,app,thumbs,admin-screens}.js → 5/5 OK
node tools/scan.js                     → 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0
CDP_VIEWPORT=390x844 node tools/scan.js → 同上全绿
node tools/r1.js                       → 15/15；console.error=0；未捕获异常=0
node tools/fix-check.js                → 7/7
node tools/store-fix-check.js          → PASS 22 / FAIL 0
node tools/seed-check.js               → 未付款 3（可付 2 / 超时 1）
CDP_VIEWPORT=390x844 node .shots/t9-check.js   → 40 passed, 0 failed
CDP_VIEWPORT=390x844 node .shots/t9-extra.js   → 6 passed, 0 failed
CDP_VIEWPORT=390x844 node .shots/t11-check.js  → 9/9
```

自检脚本同步：`.shots/t9-check.js:82` 的断言由「sticky 贴视口顶 `headTop===0`」改为「贴演示顶栏下沿 `headTop===52`」——原断言把缺陷当成了预期，已在注释里标注（`.shots/` 为自检产物，不入库）。

证据截图（390×844，`.shots/`）：`t13-a-home-scroll600.png` 95KB、`t13-a-goods-scroll420.png` 35KB、`t13-a-orders-scroll400.png` 48KB。
