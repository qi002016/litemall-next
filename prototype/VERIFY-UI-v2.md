# 界面改版 v3.1 · 五项独立验收报告（VERIFY-UI-v2）

- 验收对象：`D:\litemall-next\prototype`（单 SPA · 纯静态零依赖），t5 交付的 5 项界面改版
- 验收方式：只读代码 + `node --check` + 31 路由 DOM 扫描 + 31 张逐屏截图 + CDP 真实点击走查（27 项）+ R1 13 步回归 + 数据层规则脚本
- 验收时间：2026-10-31
- 环境：Windows 10 / node v24.14.0 / Chrome headless（`--headless=new`，420×900 手机端、1440×900 后台）/ 静态服务 `python -m http.server 8765`（根 = `prototype/`）
- 基线：commit `bccfcf3` + t5 工作区改动（`git diff --stat -- prototype/`：app.js +538、store.js +125、theme.css +287、README.md +59、tools/scan.js +5）

---

## 0. 结论：**可接受**

| 维度 | 结果 |
| --- | --- |
| 5 项界面改版 | **5/5 通过**（27 项真实点击断言全绿，0 未捕获异常） |
| 31 屏渲染 | 31/31 有内容，空屏 0、`undefined`/`NaN`/`[object Object]`/`TODO` 0、横向溢出 0 |
| 截图 | 31/31 非空白；3 张 <20 KB 已单独核验为「空态短页」而非白屏 |
| R1 主链路回归 | **15/15 通过**（console.error=0、未捕获异常=0） |
| 既有缺陷回归（P-01/P-03/P-04） | fix-check **7/7**、store-fix-check **PASS 22 / FAIL 0** |
| 三条硬规则（只锁不扣 / 关单释放 / 退款按行实付） | 通过（源码 + 实测双证据，见 §6） |
| 阻断问题 | **0**（无 blocker / high / medium） |
| 遗留 | 1 条 **low**：验收工具固定调试端口导致的假失败（`prototype/tools/cdp.js:10`，只影响自动化脚本，不影响交付物，见 §4 U-01） |

**判定依据**：本报告 §2 的 5 项逐项证据 + §3 命令输出 + §5 截图。所有"通过"均为真实点击/真实渲染结果，无推测项。

---

## 1. 交付物与改动边界

| 文件 | 行数 | 本次验收是否改动 |
| --- | --- | --- |
| `prototype/index.html` | 88 | 未改（只读） |
| `prototype/assets/app.js` | 3014 | 未改（只读） |
| `prototype/assets/store.js` | 1266 | 未改（只读） |
| `prototype/assets/ui.js` | 268 | 未改（只读） |
| `prototype/assets/admin-screens.js` | 1628 | 未改（只读） |
| `prototype/assets/theme.css` | 798 | 未改（只读） |
| `prototype/tools/*.js` | cdp 149 / r1 458 / scan 59 / fix-check 203 | 未改（§7 逐文件比对） |
| `prototype/VERIFY-UI-v2.md` | 本文件 | **新增（唯一交付物）** |
| `prototype/.shots/v2-*.png`、`.shots/walk-v2.js`、`.shots/walk-results.json` | — | 新增（验收产物，非交付代码） |

未执行 `git commit`，未修改需求文档。

---

## 2. 五项逐项判定（全部通过）

### 2.1 ① 首页分类横滚条：搜索框上方 + 末项「全部分类」

| ID | 断言 | 实测证据 | 判定 |
| --- | --- | --- | --- |
| A1 | 分类条在搜索框上方 | `.x-catstrip.bottom = 98.0` ≤ `.home-search.top = 98.0` | 通过 |
| A2 | 真溢出、可横滑、末项为「全部分类」 | `.x-catstrip-in` scrollWidth **501** > clientWidth **414**；computed `overflow-x: auto`；6 项 `["👕服饰","🏠家居","🎧数码","☕食品","🏃运动","全部分类 ›"]`，末项 class 含 `more` | 通过 |
| A3 | 点分类 pill 进列表并带 `catId` | 点「服饰」→ `#/c/list?catId=c1` | 通过 |
| A4 | 末项进分类页 | 点「全部分类」→ `#/c/category` | 通过 |

- 代码：`assets/app.js:503-510`（`catStrip()`）、`:663`（首页在搜索框前插入）、`:505`（`data-go="#/c/list?catId=…"`）、`:509`（`.x-catpill.more` → 分类页）
- 截图：`.shots/v2-c01-home.png`（91.8 KB）

### 2.2 ② 首页消息入口卡 + 消息中心 `#/c/messages`

| ID | 断言 | 实测证据 | 判定 |
| --- | --- | --- | --- |
| B1 | 旧 5 宫格已移除、换成消息卡 | `.x-msgcard` = 1；旧容器（`.grid5/.kingkong/.nav-grid/.x-grid5`）= **0**；卡内文案「3 消息 / 40 分钟前 / 订单已发货 · 「重磅纯棉oversize T恤」已由顺丰发出，运单号 SF2000000091」 | 通过 |
| B2 | 入口卡红点 = 未读数 | `.x-msgcard .dot` = `3`，`Store.unreadCount()` = `3` | 通过 |
| B3 | 点卡进消息中心，未读条目数与 tab 数一致 | hash = `#/c/messages`；`.x-msg.unread` = 3 = Store 3；类型 tab = 5 = `Store.MSG_TYPES.length`；「全部已读」按钮存在 | 通过 |
| B4 | 点单条：标记已读 + 跳转 | 点 `msg1`：未读 3→2、`read=true`、跳 `#/c/orders?tab=shipped` | 通过 |
| B5 | 全部已读：清零 + 头部文案 + 首页红点消失 | `unreadCount()=0`；头部「已全部读过」；首页 `.dot` = 无 | 通过 |
| B6 | 类型 tab 过滤生效 | 点「订单」tab → `#/c/messages?type=order`，DOM 1 条 = `Store.messages('order')` 1 条 | 通过 |
| B7 | 单条已读后首页红点同步 | 单条已读后 `unreadCount()=2`，首页红点 = `2` | 通过 |

- 代码：`assets/app.js:512-525`（`msgEntry()`，`.x-msgcard` + `.dot` + 最新一条 + 「查看全部 ›」）、`:681`（插入首页）、`:726-775`（消息屏：类型 tab、`.x-msg.unread`、`x-reddot`、`readAll`、`data-act="open"`）、`assets/store.js:1141-1156`（`messages`/`unreadCount`/`markRead`/`markAllRead`）
- 截图：`.shots/v2-c18-messages.png`（49.1 KB）、`.shots/v2-c01-home.png`

### 2.3 ③「我的」4 格工具区 + 收藏/关注/足迹三屏 + 详情收藏与足迹

| ID | 断言 | 实测证据 | 判定 |
| --- | --- | --- | --- |
| C1 | 详情页收藏真实两态 | 初始「已收藏」(`isFav=true`) → 点「收藏」(`false`) → 再点「已收藏」(`true`) | 通过 |
| C2 | 我的页 4 格数字与 Store 一致 | 4 格 = 收藏 3 / 关注店铺 3 / 足迹 8 / 我的优惠券 0；Store `{fav:3, follow:3, fp:8, cp:0}`；4 个 `data-go` 分别指向 favorites/follows/footprints/coupons | 通过 |
| C3 | 收藏屏条数一致、取消后计数同步 | 头部「共 3 件」、卡片 3；取消 `g1` 后头部「共 2 件」、Store 2、卡片 2 | 通过 |
| C4 | 关注店铺/品牌可切换且文案联动 | 店铺「已关注」→「+ 关注」→「已关注」，`isFollow` 同步 true→false→true；品牌关注数 3→2，页面「已关注品牌 2 个」一致 | 通过 |
| C5 | 足迹屏分组/条数/清空二次确认 | 最近足迹 = `g1`（先进 g2 再进 g1，验证"最前"）；8 条 = Store 8；含「今天」分组；点「清空」弹确认层「清空足迹」→ 确认后 Store 0、空态「还没有浏览记录」 | 通过 |

- 代码：`assets/app.js:2589-2595`（`.x-tools` 4 格 + `.ic .n` 角标）、`:2563`（me 路由）、`:2662-2688`（favorites）、`:2693-2741`（follows）、`:2743-2790`（footprints）、`:1140`（`faved`）、`:1198-1199`（收藏按钮两态）、`:1316-1319`（`fav` action → `Store.toggleFav`）、`:1137-1139`（足迹写入，顶端相同不重复写）
- store：`assets/store.js:1163-1204`（`favGoods`/`toggleFav`/`followList`/`isFollow`/`toggleFollow`/`footprintList`/`addFootprint`/`clearFootprints`）
- 截图：`.shots/v2-c15-me.png`（65.6 KB）、`v2-c19-favorites.png`（43.8 KB）、`v2-c20-follows.png`（28.9 KB）、`v2-c21-footprints.png`（48.2 KB）

### 2.4 ④ 返回键：「我的售后」及所有非 Tab 屏

| ID | 断言 | 实测证据 | 判定 |
| --- | --- | --- | --- |
| D1 | 除 4 个 Tab 屏外所有 C 屏都有可见返回键 | 逐屏导航 17 屏（search/list/goods/checkout/pay/orders/order/as-apply/as-detail/aftersales/review/coupons/address/messages/favorites/follows/footprints），**17/17** 命中可见 `[data-go="back"]` | 通过 |
| D2 | 消息屏返回回到来源页 | `#/c/home` → 点消息卡 → `#/c/messages` → 点返回 → `#/c/home` | 通过 |
| D3 | 「我的售后」返回回到「我的」 | `#/c/me` → 点「售后」快捷 → `#/c/aftersales` → 点返回 → `#/c/me` | 通过 |

- 代码：`assets/ui.js:200-207`（`cHeader`：`.back[data-go="back"]`；`noBack` 仅 Tab 屏使用）
- 4 个 Tab 屏（home/category/cart/me）按设计无返回键，不计缺陷。

### 2.5 ⑤ 列表页两列网格 + chips + 筛选 sheet + URL 落条件

| ID | 断言 | 实测证据 | 判定 |
| --- | --- | --- | --- |
| E1 | 两列网格 | computed `grid-template-columns` = 2 列；15 张卡 / 8 行，每行最多 2 张 | 通过 |
| E2 | chips 单行完整 + 筛选按钮常驻（captain 口径） | `.x-chips` computed `overflow-x: auto`；5 项 `["综合","销量","价格","上新","仅看有货"]` 单行（top 唯一）；最右 320 ≤ 420；`.x-filter-btn` 右 402、可见 | 通过 |
| E3 | 销量排序 | 点「销量」→ `#/c/list?sort=sales`；已售序列 5310/3821/3410/2650/2210/1820… 递减 | 通过 |
| E4 | 筛选实时 N + 应用回写 URL + 列表一致 | 初始「查看 15 件商品」→ 快捷「100-200」变「查看 4 件商品」→ 手输 50/200 变「查看 8 件商品」→ 应用后 hash `#/c/list?minP=50&maxP=200`、列表 8 件 = N | 通过 |
| E5 | 刷新后条件与结果保留 | 重新加载同 hash：hash 保留、8 件、`.x-filter-btn.on` 高亮、条件数 1 | 通过 |
| E6 | 仅看有货 | hash `#/c/list?onlyStock=1`、chip 高亮、缺货卡 0 | 通过 |
| E7 | 上下限倒置自动交换（BR-SEARCH-05） | `#/c/list?minP=200&maxP=50` → 页面提示「价格区间上下限已自动交换（BR-SEARCH-05）」 | 通过 |
| E8 | 筛选重置恢复全量 | 「查看 8 件商品」→ 点「重置」→「查看 15 件商品」→ 应用后 hash `#/c/list`、列表 15 = 全量 | 通过 |

- 代码：`assets/app.js:941-943`（交换逻辑）、`:953-966`（chips + 筛选按钮）、`:969`（`.goods-grid` 两列）、`:984-987`（`.x-listbar`）、`:990-1075`（筛选 sheet、`#fx-min/#fx-max/#fx-stock/#fx-reset/#fx-apply`、`paint()` 实时 N）、`:982`（交换提示）
- 截图：`.shots/v2-c04-list.png`（28.3 KB）

---

## 3. 必跑命令与结果

| # | 命令（工作目录 `D:\litemall-next\prototype`） | 结果 | 关键输出 |
| --- | --- | --- | --- |
| 1 | `node --check assets/store.js` / `ui.js` / `app.js` / `admin-screens.js` | 4/4 通过 | `ALL_JS_OK` |
| 2 | `node tools/scan.js` | 通过 | 31 路由；`空屏(<20字): 无`、`含可疑文案: 无`、`横向溢出: 无` |
| 3 | `node tools/r1.js` | **15/15 通过** | 6.1 十三步 + 规格价格随动；`运行期 console.error=0，未捕获异常=0`；R1-8 售后 `amount=9650`（= 行实付 ¥96.50） |
| 4 | `node tools/fix-check.js` | **7/7 通过** | P-03 规格切换、P-04/04b/04c 超时关单、P-01 预计退款与 `Store.refundAmount` 同源（qty1 12400 / qty2 24800 分） |
| 5 | `node .shots/store-fix-check.js` | **PASS 22 / FAIL 0** | 含「超时支付被拒：该订单已超过 30 分钟未支付，已自动关单（锁量已释放、券已退回），请重新下单」、关单日志 |
| 6 | `node .shots/seed-check.js` | 通过 | 未付款 3 笔：o100 3 分钟前可付、o101 12 分钟前可付、o102 45 分钟前超时 |
| 7 | `node .shots/walk-v2.js`（本轮新增的真实点击走查） | **27/27 通过** | A1-A4 / B1-B7 / C1-C5 / D1-D3 / E1-E8；未捕获异常 0；结果 `walk-results.json` |
| 8 | 31 张逐路由截图（`chrome --headless --screenshot --window-size=420,900` / `1440,900`） | 31/31 非空白 | 见 §5，`<20KB` 3 张已用 PIL 复核 |

截图生成脚本：`bash .shots/shoot-all-v2.sh`（可复跑，输出到 `.shots/v2-<route>.png`）。

> 说明：`tools/seed-check.js` 与 `tools/store-fix-check.js` 在仓库里以 `.shots/` 副本存在（`tools/` 仅有 cdp/r1/scan/fix-check），本轮按实际路径运行，未改脚本内容。

---

## 4. 缺陷表

| ID | 严重级 | 文件:行 | 现象 | 复现步骤 | 期望 vs 实际 | 建议修法 |
| --- | --- | --- | --- | --- | --- | --- |
| U-01 | low（仅验收工具，不影响交付物） | `prototype/tools/cdp.js:10`（`const PORT = 9333;`） | 端口固定 + `launch()` 只等 `/json/list` 就复用连接：若上一轮 headless Chrome 未退出，会复用到**旧浏览器/旧 profile/旧页面状态**，导致 `r1.js` 级联假失败（实测 7/15，报 `[data-id="null"]`、`orderById(undefined)`） | ① 保留任一带 `--remote-debugging-port=9333` 的 headless Chrome；② `node tools/r1.js` → 7/15；③ `Stop-Process -Id <pid> -Force`；④ 再跑 → 15/15（连跑 2 次均 15/15） | 期望：每次启动独立实例或复用时校验 target 属于本次启动；实际：静默复用旧实例 | 端口随机化（如 `9333 + rand(500)`）并在 `/json/list` 校验 `user-data-dir` 与本次一致；或在 `launch()` 前探测并杀掉占用进程。**已闭合，见 §10** |

**其余 5 项验收未发现产品缺陷。** 以下 2 条为已裁定/已核验的观察项，**不计入缺陷**：

1. 列表页 `.x-chips` 5 项 scrollWidth = clientWidth = 318，不可横滑 —— captain 已裁定为设计决策（5 项在 420px 下完整可见，容器 `overflow-x: auto`，新增维度时自然可滑），本报告按该口径判通过（E2）。
2. `v2-c07-checkout`（17.6 KB）、`v2-c08-pay`（18.7 KB）、`v2-c11-as-apply`（16.6 KB）小于 20 KB —— 经 PIL 复核为**短空态页**：非白像素占比 94.8% / 85.8% / 94.8%，颜色数 767 / 857 / 994，非白屏（详见 §5）。

---

## 5. 31 屏截图清单（`prototype/.shots/`）

手机端 420×900（21 张，含 4 张新增屏）：`<20KB` 用 ⚠ 标注并附非空白证据。

| 文件 | 字节 | 备注 |
| --- | --- | --- |
| `v2-c01-home.png` | 91802 | 分类条 + 消息入口卡 |
| `v2-c02-search.png` | 24573 | |
| `v2-c03-category.png` | 24031 | |
| `v2-c04-list.png` | 28314 | 两列 + chips |
| `v2-c05-goods.png` | 63819 | |
| `v2-c06-cart.png` | 20338 | 空购物车空态 |
| `v2-c07-checkout.png` | 17573 ⚠ | 空购物车空态；非白 94.8%、颜色 767、dark 6.97% |
| `v2-c08-pay.png` | 18712 ⚠ | 无效订单提示短页；非白 85.8%、颜色 857、dark 10.24% |
| `v2-c09-orders.png` | 43510 | |
| `v2-c10-order.png` | 37331 | |
| `v2-c11-as-apply.png` | 16572 ⚠ | 无效订单空态；非白 94.8%、颜色 994、dark 6.9% |
| `v2-c12-as-detail.png` | 34801 | |
| `v2-c13-aftersales.png` | 45895 | |
| `v2-c14-review.png` | 26361 | |
| `v2-c15-me.png` | 65562 | 4 格工具区 |
| `v2-c16-coupons.png` | 34583 | |
| `v2-c17-address.png` | 27624 | |
| `v2-c18-messages.png` | 49120 | 新增：消息中心 |
| `v2-c19-favorites.png` | 43804 | 新增：收藏 |
| `v2-c20-follows.png` | 28852 | 新增：关注店铺/品牌 |
| `v2-c21-footprints.png` | 48218 | 新增：足迹 |

后台 1440×900（10 张）：`a01-login` 45020、`a02-dashboard` 125341、`a03-orders` 147873、`a04-aftersales` 146482、`a05-goods` 129344、`a06-catalog` 91734、`a07-stock` 112980、`a08-coupons` 77414、`a09-comments` 133153、`a10-demo` 114966（均 `v2-` 前缀，无 <20KB 项）。

---

## 6. 三条硬规则复核（源码 + 实测）

| 规则 | 源码证据 | 实测证据 | 结论 |
| --- | --- | --- | --- |
| 下单只锁不扣 | `assets/store.js:440` `lockedQty()` 仅统计 `status === 'unpaid'` 行；`:656` `submitOrder()` 全函数不修改 `sku.number` | `r1.js` R1-4：下单前后库存仍 10/8，支付成功后 9/7（锁 → 实扣一次性） | 通过 |
| 支付后才实扣（且只一次） | `assets/store.js:714` `payOrder()`：非 unpaid 拒绝；`:720-723` 超时先按关单逻辑关闭并拒绝；`:728-729` `mode==='fail'` 只写日志不改状态；`:734` `f.sku.number = Math.max(0, f.sku.number - it.qty)` | R1-13：模拟失败后订单仍 unpaid、锁量仍 1；再支付成功 → wait_ship | 通过 |
| 关单/取消释放锁量与券（同一套逻辑） | `assets/store.js:742` `releaseCouponOf(o)`、`:748` `closeOrder(orderId, by)`（仅 unpaid、写 timeline、释放券）、`:760` `cancelOrder = closeOrder(id,'用户取消')`、`:762` `demoCloseUnpaid = closeOrder(id,'超时关闭') + log` | R1-11：一键关单 4 笔 → 订单 closed/超时关闭、锁量 2→0、cp2 回 unused、demoLog 含单号；R1-12：手动取消 → closed/用户取消、锁量释放 | 通过 |
| 退款金额按行实付向下取整 | `assets/store.js:833` `itemPaid = Math.floor(price*qty - couponAmount*(price*qty)/goodsAmount)`；`:840-842` `refundAmountOf = max(0, floor(itemPaid*qty/lineQty))`；`:868` `amount = refundAmountOf(...)` | R1-8：申请页预计「退款 ¥96.50」= 售后单 `amount=9650`；fix-check P-01：qty1 12400 / qty2 24800 分与页面同源 | 通过（t3 的 P-01 已修复） |

补充：退款回补矩阵 `assets/store.js:970` `shouldRestock = (a.type === 'return') || (o && o.status === 'wait_ship')`；回补失败停 refunding 并标「库存待回补」`:964`；重试 `:996 asRetryRestock` —— 属既有规则抽查，本轮回归由 `store-fix-check.js`（PASS 22）与 `r1.js` R1-9 覆盖。

---

## 7. `tools/` 完整性（防削弱脚本）

- `git diff -- prototype/tools` **非空但只有一处**：`tools/scan.js` +5 行（注释 1 行 + `c18-messages/c19-favorites/c20-follows/c21-footprints` 4 条新路由），**未删除/放宽任何既有断言**（diff 全文见下）。

```diff
@@ -13,7 +13,10 @@ const C = [
-  ['c17-address', '#/c/address']
+  ['c17-address', '#/c/address'],
+  // 界面改版新增 4 屏（消息 / 收藏 / 关注 / 足迹）
+  ['c18-messages', '#/c/messages'], ['c19-favorites', '#/c/favorites'],
+  ['c20-follows', '#/c/follows'], ['c21-footprints', '#/c/footprints']
 ];
```

- `tools/cdp.js`、`tools/r1.js`、`tools/fix-check.js` 与 t3 时副本（`.shots/verify/`）**逐字节一致**（`diff -q` 无差异），且未出现在 `git status` 中 → 断言未被削弱。
- R1 脚本仍为 15 项（13 步 + R1-2b），本轮实跑 15/15 通过，与 t5 自报口径一致。

---

## 8. 复验命令清单

```bash
# 0) 静态服务（根目录必须是 prototype/）
cd D:\litemall-next\prototype && python -m http.server 8765

# 1) 语法
node --check assets/store.js && node --check assets/ui.js \
  && node --check assets/app.js && node --check assets/admin-screens.js

# 2) 31 路由空屏 / 可疑文案 / 横向溢出
node tools/scan.js

# 3) R1 13 步主链路（15 项断言）
node tools/r1.js

# 4) 既有缺陷回归
node tools/fix-check.js          # 7/7
node .shots/store-fix-check.js   # PASS 22 / FAIL 0
node .shots/seed-check.js        # 未付款 3 笔：2 可付 / 1 超时

# 5) 界面改版 5 项真实点击（27 项）
node .shots/walk-v2.js           # 输出 .shots/walk-results.json

# 6) 逐路由截图
bash .shots/shoot-all-v2.sh

# 7) 若复用调试端口出现假失败，先清端口
netstat -ano | grep ':9333.*LISTENING'   # 有则 Stop-Process -Id <pid> -Force
```

---

## 9. 局限与未覆盖

1. 仅验证 420×900（C）/ 1440×900（后台）两个视口，未覆盖 375/768/桌面宽屏；平板断点不在本期验收项。
2. 原型零依赖、数据在 `Store` 内存 + localStorage，未接后端；"真实服务端"行为不在验收范围。
3. 未验证跨刷新后收藏/关注/足迹的持久化往返（不在 5 项验收项内；列表筛选的刷新保留已验，见 E5）。
4. 消息卡"最新一条"取数、红点数值均以 Store 为准核对，未做多端并发写入场景（原型无并发场景）。
5. `U-01` 是验收工具问题，不阻塞交付；但建议 t5 顺手修（端口随机化）以免后续回归误报。---

## 10. U-01 修订记录（captain 接管 t7，2026-10-31）

**处置决定**：U-01 位于 `tools/`，而 `tools/` 是 captain 保留目录（成员任务一律禁改 `tools/**`，避免验收脚本被"顺手放宽"）。因此 t7 由 captain 接管、不派发给 `proto-fix`。

**改法**（唯一改动文件 `prototype/tools/cdp.js`）：
1. 删除 `const PORT = 9333;`。
2. 新增 `async function freePort()`：`net.createServer().listen(0,'127.0.0.1')` 取系统分配的空闲端口后立即 `close`，返回该端口。
3. `launch()` 内改为 `const PORT = await freePort();`，`--remote-debugging-port` 与 `/json/list` 探测**同源于这个本次端口**；相对旧的固定端口，遗留实例的端口已被新实例占用，故不可能再被静默复用。
4. 启动失败路径补 `cleanup()`（杀子进程），错误信息带端口号：`chrome devtools not reachable on port N`。

**验证**（先清空遗留 headless Chrome，再连跑；完整日志 `prototype/.shots/suite-l01.log`）：

| 命令 | 结果 |
| --- | --- |
| `node --check` × 10（4 资产 + 6 工具） | 10/10 OK |
| `node tools/r1.js` | **15/15 通过**（console.error 0、未捕获异常 0） |
| `node tools/scan.js` | 31 路由：空屏 0 / 可疑文案 0 / 横向溢出 0 |
| `node tools/fix-check.js` | **7/7** |
| `node tools/store-fix-check.js` | **PASS 22 / FAIL 0** |
| `node tools/seed-check.js` | 未付款 3 笔：可付 2 / 超时 1 |

**结论**：U-01 闭合。§8 第 7 条"若复用调试端口出现假失败，先清端口"自此非必需，保留作历史参考。

**边界（如实记录）**：
- 端口随机化消除的是"复用遗留实例"这条路径；若同一台机器并行跑两套验收，两套各自取不同端口，互不干扰。
- 未覆盖"headless Chrome 启动即崩溃"的场景 —— 该场景会以显式报错 `chrome devtools not reachable on port N` 失败，不会静默误报。
- 本次复跑均在 `prototype/` 下执行，未改 `assets/store.js` 逻辑、未改断言、未改任何受保护文件。