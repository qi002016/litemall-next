# 原型独立验收报告（t3 · 只读走查 + R1 13 步验收）

| 项 | 值 |
| --- | --- |
| 验收对象 | `D:\litemall-next\prototype`（index.html + assets/*.js + assets/theme.css，纯静态单 SPA） |
| 需求基线 | `D:\litemall-next\需求文档.md`（v3，699 行）；主标尺为 6.1 R1 13 步，规则核对第 5 章 + 7.2/7.3 |
| 验收方式 | 真实运行：`node --check`、Chrome headless 逐路由截图、`--dump-dom` 文案扫描、CDP 真实点击跑 6.1 全流程、源码逐条核对规则 |
| 执行 | proto-verify（只读；唯一新增文件为本报告）；未修改 index.html / assets/*.js / assets/theme.css；未 git 提交 |
| 环境 | Windows；node v24.14.0；Chrome headless（`--headless=new`）；静态服务 `http://127.0.0.1:8765`（以 `prototype` 为根）；视口 C 端 420×900、后台 1440×900 |
| 代码规模 | store.js 1106 行 / ui.js 268 / app.js 2566 / admin-screens.js 1591 / index.html 88 |
| 证据 | `prototype/.shots/verify-*.png`（27 张）、`.shots/verify/r1-results.json`、`.shots/verify/dom-scan.json`（自检产物，不入库、不提交） |

---

## 0. 结论：**需修订**

**判据**：可交付的最低线是「6.1 R1 13 步能在页面上连续点通 + 27 屏无空屏/无控制台报错」。这两条**全部满足**（R1 15/15 通过、27/27 屏非空、运行期 console.error=0、未捕获异常=0、断链数 0）。因此**不是「不可交付」**。

但存在 **2 个 high 级缺陷直接落在 R1 主链路上**，都违反需求文档的硬性条款，不能以「原型可点通」为由放行：

1. **P-01 退款金额口径错误**：R1 第 8 步生成的售后单 `amount` 用的是「单价 × 申请数量」，丢掉了 7.2 / BR-AS-05 的「行实付 − 券分摊、再按申请数量占比、向下取整」。同一屏上「预计退款 ¥96.50」与售后单 `¥99` 自相矛盾，演示时一眼可见。
2. **P-02 缺「立即关单」单笔入口**：6.1 第 11 步要求对**单笔**未付款订单点「立即关单」，原型只有演示工具里的「一键关闭全部未付款订单」，会连带关掉 3 笔种子单（实测 toast「已关单 4 笔」），第 11 步的语义不成立。

另有 1 个 medium（P-03 规格置灰提示误导，会打断 R1 第 2 步的「切规格」体验）、1 个 medium（P-04 30 分钟超时只改文案、超时后仍可支付）、1 个 low（P-05 后台确认收到退货无二次确认）。

**修订建议优先级**：P-01 → P-02 → P-03 → P-04 → P-05。前两项修完即可重新验收（复验只需重跑第 5 节命令）。

---

## 1. 验收证据总览

| 检查项 | 结果 | 命令 / 方式 | 证据 |
| --- | --- | --- | --- |
| JS 语法 | 4/4 通过 | `node --check assets/{store,ui,app,admin-screens}.js` | 退出码 0，无输出 |
| 逐路由截图（C 17 + 后台 10） | 27/27 有内容 | `bash .shots/verify/shoot-all.sh` | 每张 20.6–35.4KB（<20KB 视为可疑，无一命中） |
| 空白页 / 报错文案扫描 | 27/27 通过 | `node tools/scan.js` → `dom-scan.json` | `#root` 文本 49–2667 字符；`undefined`/`NaN`/`[object Object]`/`TODO`/`null` 均 0 命中；`.phone`/`.admin-body` 横向溢出 0 |
| file:// 直开 | 通过 | `chrome --headless --dump-dom "file:///D:/litemall-next/prototype/index.html#/c/home"` | 标题「首页 · 小满商城原型」，`#root` 10.6KB，含 `class="c-page"` |
| R1 全流程真实点击 | **15/15 通过** | `node tools/r1.js`（CDP 鼠标事件，非 eval 直调） | `r1-results.json`；13 步 + R1-2b；运行期 console.error 0、未捕获异常 0 |
| 规则一致性抽查 | 15/15 与源码一致 | 源码逐条核对（见第 4 节，带 file:line） | — |
| 6.1「断链数」 | 0 | 由 R1 全程连续点击 + 27 屏截图共同支撑 | 每条跳转均有对应屏，无死路由 |

> 说明：R1 全程走 `http://127.0.0.1:8765`（与 file:// 同一份文件，file:// 另行用 dump-dom 复核可加载）；脚本用 `Store.state` **只读**断言，不写业务代码。

---

## 2. 缺陷表

### P-01 退款金额未按「行实付 ÷ 行数量」计算，金额偏高

| 项 | 内容 |
| --- | --- |
| 严重级 | **high** |
| 位置 | `prototype/assets/store.js:798`、`prototype/assets/store.js:809`（对照 `store.js:786` 的 `itemPaid`） |
| 需求依据 | `需求文档.md:528`（7.2「行实付 = 行商品金额 − 该行分摊券额」）、`需求文档.md:297`（BR-AS-05「退款金额 = 该行实付 × 申请数量 ÷ 该行购买数量，向下取整到分」）、`需求文档.md:615`（D9 已定稿） |
| 复现步骤 | 1) 后台 `#/admin/goods` → 新建「验收T恤」，SKU 黑色/L ¥99 库存 10、白色/M ¥99 库存 8（或直接用种子 g1：黑/L ¥99、白/M ¥99）<br>2) C 端 `#/c/goods/<id>` 黑/L 加购 1、白/M 加购 1 → `#/c/cart` 合计 ¥198<br>3) `#/c/coupons` 领 cp1（¥5）→ `#/c/checkout` 应付 ¥193 / 已优惠 ¥5 → 提交 → `#/c/pay/<id>` 点「模拟支付成功」<br>4) 后台发货 → C 端 `#/c/order/<id>` 确认收货<br>5) 对「黑色 / L」行点「申请售后」→ 类型「退货退款」、数量 1、上传 1 张凭证 → 提交 |
| 期望 | 行实付 = 9900 − round(500 × 9900 / 19800) = 9650 分；申请 1/1 件 → 售后单 `amount = 9650`（¥96.50），与申请页「预计退款金额」一致 |
| 实际 | 申请页显示「退款 ¥96.50」（取 `canApply.itemPaid`，`store.js:786` 算法正确）；提交后售后单 `amount = 9900`（¥99）。`store.js:798` 的 `var unit = opt.type === 'refund' ? it.price : it.price;` 两个分支相同，券分摊被完全丢弃；`store.js:809` `amount: unit * opt.qty` 只用单价 × 申请数量，**既没扣券分摊、也没除行数量**——一行买 2 件只退 1 件时会退整件单价而非 1/2。 |
| 建议修法 | 在 `aftersaleApply` 内复用判定阶段的结果：先算 `can`，取 `can.itemPaid`，`amount = Math.floor(can.itemPaid * opt.qty / it.qty)`；同时把 `store.js:786` 的 `Math.round` 改为 `Math.floor`（BR-AS-05 要求向下取整到分），并保留「累计退款 ≤ 订单实付」校验。改完 R1-8 的断言应为 `amount === 9650`。 |

### P-02 缺「立即关单」单笔入口，只能用「一键关闭全部未付款订单」

| 项 | 内容 |
| --- | --- |
| 严重级 | **high** |
| 位置 | `prototype/assets/admin-screens.js:1474-1497`（`demo-close-unpaid` 批量关单）、`admin-screens.js:325-326`（订单行只有 详情 / 发货 / 改单号） |
| 需求依据 | `需求文档.md` 6.1 第 11 步「另开一个未付款订单，后台点『立即关单』」；7.1 演示手段「后台立即关单」必须留日志；6.1 不通过判定含「立即关单未按取消同一套逻辑释放」 |
| 复现步骤 | 1) C 端下一笔未付款单（结算提交后不支付）<br>2) 后台 `#/admin/orders?tab=unpaid`：每行只有「详情」按钮，**没有关单按钮**<br>3) 到 `#/admin/demo` 点「一键关闭全部超时未付款订单（4 笔）」→ 确认框 okText「立即关单」→ toast「已关单 4 笔，释放库存 7 件」 |
| 期望 | 目标单行内有「立即关单」，只关闭该单，锁量释放、券退回未使用、演示日志新增 1 条 |
| 实际 | 无单笔入口；唯一路径是批量，实测把 3 笔种子未付款单（o100–o102）一并关闭，无法验证「只关这一单」。6.1 第 11 步的单笔语义不成立。 |
| 建议修法 | `#/admin/orders` 的 `unpaid` 行增加 `<button data-aact="ord-close" data-id>`，ACT 表加 `'ord-close': id => U.confirm({okText:'立即关单'}).then(ok => ok && S().demoCloseUnpaid(id) …)`，复用现成的 `closeOrder` / `releaseCouponOf` / 日志；`#/admin/demo` 的批量入口保留作为便利功能。 |

### P-03 规格置灰时提示「该规格组合暂时缺货」，与实际有货矛盾

| 项 | 内容 |
| --- | --- |
| 严重级 | **medium** |
| 位置 | `prototype/assets/app.js:1025`（toast 文案）、`prototype/assets/app.js:579-590`（`optEnabled` 置灰判定） |
| 需求依据 | 6.1 第 2 步「选黑色 / L，价格和库存随规格变化」；5.1 BR-GOODS-05 / 7.3 可售口径 |
| 复现步骤 | 1) `#/c/goods/g1`（重磅T恤）<br>2) 点「黑色」→ 点「L」（此时白/M 的「白色」被置灰）<br>3) 点「白色」→ toast「该规格组合暂时缺货」 |
| 期望 | 「白色 + 已选 L」无此 SKU，应引导用户先切换尺码（提示「请先取消尺码」或自动清空冲突维度），而不是断言缺货——白色/M 明明有 8 件。 |
| 实际 | 统一 toast「该规格组合暂时缺货」，与页面自身「库存 8 件」矛盾；用户须自行猜出「先点掉 L」才能选白色，R1 第 2 步的规格切换体验被打断（自动化脚本也不得不先清空维度）。 |
| 建议修法 | 区分两种禁用原因：目标值自身无任何可售 SKU → 「该规格已售罄」；仅与当前已选维度组合不存在 → 「请先取消<当前维度>再选」。或直接在被点击时清空冲突维度并选中目标（`clearOtherDims` 已有等价逻辑可复用）。 |

### P-04 30 分钟超时只把文案改成「已超时」，不关单，且超时后仍可支付

| 项 | 内容 |
| --- | --- |
| 严重级 | **medium** |
| 位置 | `prototype/assets/app.js:18`（`PAY_WINDOW`）、`app.js:1436-1441`（支付页倒计时）、`app.js:1780-1786`（订单详情倒计时）、`prototype/assets/store.js:674-676`（`payOrder` 只校验 `status !== 'unpaid'`） |
| 需求依据 | 5.5 BR-ORDER-06「30 分钟未支付自动关单」、BR-ORDER-07（取消与超时同一套释放逻辑） |
| 复现步骤 | 1) 生成一笔未付款单并进入 `#/c/pay/<id>`<br>2) 等倒计时归零（或把系统时间/`createdAt` 提前；**页面内 30 分钟不可加速**，故本条为代码级判定 + 倒计时到点表现）<br>3) 归零后文案变「已超时」，但「模拟支付成功」按钮仍在，点击仍可 `payOrder` 成功 |
| 期望 | 超时即按 `closeOrder(o.id,'超时关闭')` 关闭：状态 closed、锁量释放、券退回、写日志；之后支付被拒。 |
| 实际 | 无任何定时器（`every()` 只驱动倒计时显示）；`payOrder` 不校验时间，超时单仍可支付成功；锁量与券一直占用，只能靠后台「一键关单」人工兜底。倒计时文案与后端行为不一致，演示时会让人以为已自动关单。 |
| 建议修法 | 在 `payOrder` 开头加 `if (Date.now() > o.createdAt + 30*60000) { S().closeOrder(o.id,'超时关闭'); return {ok:false,msg:'订单已超时关闭，请重新下单'} }`；并让倒计时 tick 到 0 时调 `Store.closeOrder` / 刷新页面。 |

### P-05 后台「确认收到退货」无二次确认（其余售后动作都有）

| 项 | 内容 |
| --- | --- |
| 严重级 | **low** |
| 位置 | `prototype/assets/admin-screens.js:1366-1371` |
| 需求依据 | 5.8 BR-AS-08（wait_receive → refunding 单向推进）、5.7 BR-ORD-01（售后状态不可回退） |
| 复现步骤 | 后台 `#/admin/aftersales?tab=wait_receive` → 任意一条点「确认收到退货」→ 立即执行并 toast，无确认框（对比「同意 / 驳回 / 跳过收货 / 退款」都弹 `U.confirm` 或表单） |
| 期望 | 与同类不可逆动作一致，先 `U.confirm`（okText「确认收到退货」）再推进。 |
| 实际 | 单击即推进到 `refunding`，误点后无 UI 路径回退。 |
| 建议修法 | `'as-receive'` 分支包一层 `U.confirm`，与 `as-approve` 写法对齐。 |

---

## 3. R1 13 步判定表（6.1 主标尺）

判定口径：订单/售后/商品 id 均由 `uid()` 运行时生成，**每次重跑都会变**，文中 `<orderId>` 等为占位、括注为本次运行的示例值；稳定证据以 `.shots/verify/r1-results.json` 为准。全部步骤为**真实点击**（CDP 派发鼠标事件 + `elementFromPoint` 命中校验），断言读 `Store.state`。截图目录 `.shots/`。

| 步 | 脚本内容 | 判定 | 证据（路由 / 关键文案 / 截图） |
| --- | --- | --- | --- |
| 1 | 后台新建 T恤（两个规格） | **通过** | `#/admin/goods` → 新建弹层填 `#gd-name`=验收T恤、2 行 SKU（黑/L ¥99 库存10、白/M ¥99 库存8）→「创建商品」；toast「商品已创建」，Store 生成 2 条 SKU。截图 `verify-a05-goods.png` |
| 2 | C 端搜「T恤」→ 详情 → 选黑色/L，价格库存随规格变；未选全不能加购 | **通过** | `#/c/search` 输入 `#kw`=T恤 + Enter → `#/c/list?kw=T%E6%81%A4` 命中；`#/c/goods/<id>` 4 个规格项，未选全点加购 toast「请先选全规格」且 cart=0；黑/L 显示「库存 10 件」、白/M「库存 8 件」。截图 `verify-c02-search.png` / `verify-c04-list.png` / `verify-c05-goods.png` |
| 2b | （补充）切规格价格随动 | **通过** | g1 黑/XL → ¥109；切回 黑/L → ¥99 |
| 3 | 加购两个规格，购物车两行 | **通过** | 两次 toast「已加入购物车」；`#/c/cart` 两行「黑色 / L×1」「白色 / M×1」，合计 ¥198。截图 `verify-c06-cart.png` |
| 4 | 结算用券 → 提交 → 演示支付成功 | **通过** | `#/c/coupons` 领 cp1 toast「领取成功，可在"我的优惠券"查看」；`#/c/checkout` 应付「¥193 已优惠 ¥5」；`#/c/pay/<orderId>`（本次运行 omvw9owy7p） 常驻「演示模式：不产生真实扣款」；支付成功 → `wait_ship`、goods 19800 / cp 500 / pay 19300、库存 [9,7]、购物车清空、cp1=used。截图 `verify-c07-checkout.png` / `verify-c08-pay.png` |
| 5 | 后台待发货列表金额与 C 端一致 | **通过** | `#/admin/orders?tab=wait_ship` 行「LM…（本次 LM2026430591511）… 共 2 件 ¥193 已抵扣 5 待发货」，与 C 端一致。截图 `verify-a03-orders.png` |
| 6 | 发货填公司+单号 → C 端可见 | **通过** | 空提交 → 弹层内红字「物流公司与单号均不能为空」且订单仍 `wait_ship`；填「顺丰速运 / SF99887766」→ `shipped`；`#/c/order/<orderId>` 可见物流。截图 `verify-c10-order.png` |
| 7 | C 端确认收货 → 出现售后入口 | **通过** | 确认框「确认收货」→ `received`；商品行出现 2 个「申请售后」按钮。 |
| 8 | 黑色/L 退货退款 + 1 张凭证 | **通过（金额缺陷见 P-01）** | 0 凭证提交被拒 toast「退货退款必须上传至少 1 张凭证（BR-AS-21）」；上传后 1/6；提交 → `#/c/aftersale/<asId>`（本次 asmvw9p3k5q），`{status:apply, type:return, qty:1, amount:9900, photos:1}`；申请页「预计退款金额 ¥96.50」与 `amount=9900` 不一致。截图 `verify-c11-as-apply.png` / `verify-c12-as-detail.png` |
| 9 | 后台同意 → 给地址 → 填单号 → 确认收货 → 退款完成（行已退款、主状态仍已收货） | **通过** | 同意 → `wait_send`（C 端展示退货地址）；填 SF55667788 → `wait_receive`；后台「确认收到退货」→ `refunding`；「执行退款」→ `done`；订单仍 `received`、行「已退款 1 件」、`fullyRefunded=false`、黑/L 库存回到 10。截图 `verify-a04-aftersales.png` |
| 10 | 库存视图：黑/L 由 9 → 10，白/M 仍 7 | **通过** | `#/admin/stock`「验收T恤 黑色 / L ¥99 … 10」「白色 / M ¥99 … 7」；Store [10,7]。截图 `verify-a07-stock.png` |
| 11 | 另开未付款单 → 后台「立即关单」→ 已关闭、锁量释放、券回未使用、有日志 | **通过（须用一键全关，见 P-02）** | 批量按钮「一键关闭全部超时未付款订单（4 笔）」→ 确认「立即关单」→ toast「已关单 4 笔，释放库存 7 件」；目标单 `{status:closed, closedBy:'超时关闭'}`；g1-1 锁量 1（下单后 2）→ 关单后 0；cp2 回 `unused`；`demoLog` 含该单号。截图 `verify-a10-demo.png` |
| 12 | C 端手动取消，结果同第 11 步 | **通过** | 确认框「取消后订单关闭，占用的库存立即释放、已使用的优惠券退回（BR-ORDER-07/08）。」→ `closed` / `closedBy:'用户取消'`；锁量释放、券回未使用 |
| 13 | 模拟支付失败 → 仍未付款、可重试 | **通过** | toast「支付失败（演示分支）：订单仍为未付款，可在 30 分钟内重试」；订单仍 `unpaid`、锁量仍 1；再点「模拟支付成功」→ `wait_ship` |

**判定汇总**：13/13 步在原型上可完成（第 8 步金额、第 11 步单笔入口有缺陷，但不阻断流程）；无「无法验证」项。唯一无法在 UI 内等待的是 P-04 的 30 分钟超时分支（已按代码级判定，见缺陷表）。

---

## 4. 规则一致性抽查（15 条，均核对源码）

| # | 规则 | 位置 | 核对结论 |
| --- | --- | --- | --- |
| 1 | 下单**只锁不扣** | `store.js:400-408`（`lockedQty` 只累加 `status==='unpaid'` 的订单行）、`store.js:616-672`（`submitOrder` 全函数无 `sku.number` 写入） | 一致 |
| 2 | 支付成功才**实扣**，且只转一次 | `store.js:674-694`；实扣仅 `store.js:689` `if (f) f.sku.number = Math.max(0, f.sku.number - it.qty);`；非 `unpaid` 在 `store.js:675` 直接拒绝 | 一致 |
| 3 | 取消 / 超时关单释放券与锁量（同一套逻辑） | `store.js:702-712 closeOrder` → `store.js:696-700 releaseCouponOf`；`cancelOrder = closeOrder(id,'用户取消')` `store.js:714`；`demoCloseUnpaid = closeOrder(id,'超时关闭')` + 日志 `store.js:716-720` | 一致（锁量随状态离开 `unpaid` 自动释放） |
| 4 | 售后 8 态流转 | `store.js:22-33`（AS_STATUS/AS_OPEN）；`asApprove 824-838`、`asFillTracking 852`、`asConfirmReceive 864`、`asRefund 887`、`asRetryRestock 926`、`asCancel 934`、`asAutoClose 944`，各自只接受前驱态 | 一致（仅退款 apply→refunding→done；退货退款 apply→wait_send→wait_receive→refunding→done） |
| 5 | 库存回补矩阵：不寄回的仅退款**不回补** | `store.js:900` `var shouldRestock = (a.type === 'return') \|\| (o && o.status === 'wait_ship');` | 一致（退货退款必回补；未发货仅退款回补；已发货/已收货仅退款不回补） |
| 6 | 退款与回补同事务，回补失败停 `refunding` 并标红 | `store.js:891-899`（规格不存在 → `restockPending=true` + 时间线「库存回补失败」+ 返回失败）、`store.js:926-933`（重试仍走同一事务）；`app.js:1943-2084` 渲染红色「库存待回补」 | 一致 |
| 7 | 整单全退才退券 | `store.js:913-920`：全部行退完 → `o.fullyRefunded = true` + `releaseCouponOf(o)` | 一致 |
| 8 | 一人一券：已领过 vs 已领完提示可区分 | `store.js:551-552`：「你已领过这张券（BR-CP-02）」/「已领完」/「该券已停用」/「该券已过期」 | 一致（券页实测四类不可用原因同时可辨） |
| 9 | 已全额退款订单不进「已完成」，打「已全额退款」 | `app.js:1479-1489 orderTabOk`：`done` 要求 `!fr`、`after` 含 `fr`；`app.js:1533-1537` `UI.statusTag({status, fullyRefunded})`；`app.js:1802` 文案「打「已全额退款」标记（BR-AS-18）」 | 一致 |
| 10 | 已退款行不可再申请、不可评价 | `store.js:770-777`（可申请量 = qty − refundedQty − 进行中）；`app.js:2153`、`app.js:2162-2170` 评价入口过滤 `!isFullRefunded` + 行未退完 | 一致 |
| 11 | 物流单号发货后只允许改 1 次 | `store.js:748-750` `if (o.trackingEdited) return {ok:false,msg:'物流单号发货后仅允许修改 1 次（BR-ORD-10）'}`；`admin-screens.js:328` 改完即 disabled | 一致 |
| 12 | 库存不得为负、不得低于锁量；可售=实物−锁定 | `store.js:1014-1021`（`stockUpdate` 校验 `< locked` 拒绝）、`store.js:416-420`（`sellable = Math.max(0, number - lockedQty)`） | 一致 |
| 13 | 券门槛按**商品金额**（不含运费） | `store.js:572-573`（`goodsAmount >= c.threshold`）、`store.js:633`（提交前复验） | 一致 |
| 14 | AI 助手入口 / 团购入口确实不存在 | 全仓 grep `AI\|助手\|团购`：仅 `app.js:1949` 的 `MAIN_FLOW` 误命中，无任何入口/按钮/路由 | 一致（6.1 不通过判定未触发） |
| 15 | 搜索价格区间下限 > 上限自动交换；分类删除提示 | `app.js:800-801`（区间交换）；`admin-screens.js:1398`「该分类下还有 N 个商品，请先移走商品」 | 一致 |

补充核对（未列入表格但已看）：`app.js:1507-1516` 未付款行同时给「取消订单 / 去支付」；`store.js:545-557` 领券四类失败提示（停用 / 过期 / 已领过 / 已领完）互不混用；`7.2` 金额全部以「分」计算、`ui.js:20-24` 统一格式化（整数不带小数、非整数保留两位，故断言写 ¥193 / ¥96.50）。

---

## 5. 复验命令清单

前置：`cd D:\litemall-next\prototype`；静态服务已起（若未起：`python -m http.server 8765 --bind 127.0.0.1`，以 `prototype` 为根）。

```bash
# 1) 语法
node --check assets/store.js && node --check assets/ui.js \
  && node --check assets/app.js && node --check assets/admin-screens.js

# 2) 逐路由截图（C 端 17 屏 420x900、后台 10 屏 1440x900，输出 .shots/verify-*.png）
bash .shots/verify/shoot-all.sh
#    单屏示例（注意 --screenshot 必须给 Windows 绝对路径）
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --screenshot="D:\litemall-next\prototype\.shots\verify-c01-home.png" \
  --window-size=420,900 --virtual-time-budget=3000 \
  "file:///D:/litemall-next/prototype/index.html#/c/home"

# 3) 空白页 / 报错文案扫描（undefined / NaN / [object Object] / TODO / 横向溢出）
node .shots/verify/scan.js          # → .shots/verify/dom-scan.json

# 4) R1 13 步真实点击（+ R1-2b），输出 15 条判定与证据
node .shots/verify/r1.js            # → .shots/verify/r1-results.json

# 5) 单屏 DOM 快查（文件协议直开）
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --dump-dom "file:///D:/litemall-next/prototype/index.html#/c/orders" \
  | grep -c "undefined\|NaN\|\[object Object\]"   # 期望 0
```

复验通过标准：步骤 1 退出码 0；步骤 2 产出 27 张 >20KB；步骤 3 扫描 27 路由 `bad=0`、`overflow=0`；步骤 4 输出 15/15 通过且 `consoleErrors=0`、`pageErrors=0`；步骤 5 计数为 0。

---

## 6. 附：截图清单（27 屏，尺寸与字节取自实测）

C 端 420×900（`#/c/*`）：

| 屏 | 路由 | 文件 | 字节 |
| --- | --- | --- | --- |
| 首页 | `#/c/home` | `verify-c01-home.png` | 20868 |
| 搜索 | `#/c/search` | `verify-c02-search.png` | 20906 |
| 分类 | `#/c/category` | `verify-c03-category.png` | 21090 |
| 列表 | `#/c/list?kw=T恤` | `verify-c04-list.png` | 20661 |
| 商品详情 | `#/c/goods/g1` | `verify-c05-goods.png` | 21076 |
| 购物车 | `#/c/cart` | `verify-c06-cart.png` | 20842 |
| 结算 | `#/c/checkout` | `verify-c07-checkout.png` | 20916 |
| 支付 | `#/c/pay/o113` | `verify-c08-pay.png` | 21158 |
| 订单列表 | `#/c/orders` | `verify-c09-orders.png` | 20967 |
| 订单详情 | `#/c/order/o113` | `verify-c10-order.png` | 21110 |
| 申请售后 | `#/c/aftersale/apply?orderId=o114&itemId=...` | `verify-c11-as-apply.png` | 21474 |
| 售后详情 | `#/c/aftersale/as104` | `verify-c12-as-detail.png` | 21323 |
| 售后列表 | `#/c/aftersales` | `verify-c13-aftersales.png` | 20965 |
| 评价 | `#/c/review?orderId=o114` | `verify-c14-review.png` | 20886 |
| 我的 | `#/c/me` | `verify-c15-me.png` | 20814 |
| 优惠券 | `#/c/coupons` | `verify-c16-coupons.png` | 21092 |
| 地址 | `#/c/address` | `verify-c17-address.png` | 20892 |

后台 1440×900（`#/admin/*`）：

| 屏 | 路由 | 文件 | 字节 |
| --- | --- | --- | --- |
| 登录 | `#/admin/login` | `verify-a01-login.png` | 35159 |
| 工作台 | `#/admin/dashboard` | `verify-a02-dashboard.png` | 35335 |
| 订单 | `#/admin/orders` | `verify-a03-orders.png` | 35190 |
| 售后 | `#/admin/aftersales` | `verify-a04-aftersales.png` | 35371 |
| 商品 | `#/admin/goods` | `verify-a05-goods.png` | 35091 |
| 分类品牌 | `#/admin/catalog` | `verify-a06-catalog.png` | 35187 |
| 库存 | `#/admin/stock` | `verify-a07-stock.png` | 35203 |
| 优惠券 | `#/admin/coupons` | `verify-a08-coupons.png` | 35358 |
| 评价 | `#/admin/comments` | `verify-a09-comments.png` | 35366 |
| 演示工具 | `#/admin/demo` | `verify-a10-demo.png` | 34983 |

---

## 7. 验收限制（如实记录）

1. **P-04 的 30 分钟分支无法在 UI 内等待**：页面无时间加速开关，本条按「倒计时到 0 的实际表现 + `payOrder` 源码」判定，未做 30 分钟挂机实测；若需强证据，可在验收环境中把浏览器时钟快进 31 分钟后再试一次支付。
2. R1 断言读 `Store.state`（只读），不排除「界面显示与 Store 数据不一致」的极端情况；本次 27 屏 dump-dom 文案扫描未发现此类迹象。
3. 截图与脚本均放在 `prototype/.shots/`（自检产物，未纳入交付、未 git 提交）；`.shots/verify/` 下另有验收脚本（cdp.js / r1.js / scan.js / shoot-all.sh / probe-goods.js），可原地复跑。
4. `chrome.exe --version` 在本机会挂起，故未记录 Chrome 版本号；截图与 CDP 均用同一份 `chrome.exe`（`--headless=new`）。

---

## 8. 修订复核（captain，缺陷闭合记录）

> 本节由 captain 在本报告中**追加**，不改动前面任何验收结论。所有结论来自下列命令的当次输出。

**分工说明**：P-01 与 P-04 的根因在数据层 `assets/store.js`（t4 的禁区、由 captain 持有），故这两条的数据侧由 captain 直接修；其余由 `proto-fix`（t4）修；最后 captain 统一复跑全部脚本。

| 缺陷 | 修法要点 | 复验证据 | 状态 |
| --- | --- | --- | --- |
| P-01 退款金额口径（high） | `store.js`：`canApply().itemPaid` 改 `Math.floor`（BR-AS-05 向下取整）；新增 `Store.refundAmount(orderId,itemId,qty)`；`aftersaleApply` 落库改用它，删掉「单价×数量」。`app.js:1924/1936` 预计退款改用同一 API（随数量随动） | `node tools/store-fix-check.js` → **PASS 22 / FAIL 0**（`24800/2=12400`、`20700/3=6900`、`13700/3=4566`）；`r1.js` R1-8 → 申请页「退款 ¥96.50」== 售后单 `amount=9650` | 已闭合 |
| P-02 缺单笔「立即关单」（high） | `admin-screens.js:327`：未付款行 + 详情弹层各加「立即关单」，`closeOneOrder()` 二次确认后调 `Store.demoCloseUnpaid(id)`；批量入口保留 | `node tools/fix-check.js` → 关 o100 后 o101/o102 仍 `unpaid`、`demoLog` 有记录 | 已闭合 |
| P-03 规格置灰提示误导（medium） | `app.js`：区分「该规格真的售罄」与「只是跨维度无此组合」；后者自动清空冲突维度并提示「已切换为「白色」：原已选「尺码：L」没有这个组合，已自动取消」 | `fix-check.js` P-03 项通过；截图 `.shots/fix-c-goods-spec.png` | 已闭合 |
| P-04 超时只改文案、仍可支付（medium） | `store.js`：新增 `Store.orderExpired(id)`；`payOrder` 超时先按「超时关闭」同一套逻辑关单（释放锁量、券退回）再拒付。`app.js:1448-1481` 支付页与订单详情新增超时分支（禁用支付、提示「订单已超时关闭」、可重新下单） | `store-fix-check.js`：31 分钟前订单支付被拒且 `status=closed/closedBy=超时关闭`；`fix-check.js` P-04/P-04b/P-04c 通过（未超时单倒计时「27:59」且按钮可用） | 已闭合 |
| P-05 确认收到退货无二次确认（low） | `admin-screens.js` `as-receive` 补 `U.confirm`（含单号/商品/数量/回补矩阵说明） | `r1.js` R1-9 通过 | 已闭合 |

**连带修正（captain 裁定 t4 的遗留问题）**：种子未付款单原为 8/16/24 小时前，新超时规则下「去 demo 支付」会立刻被关单。已改为 o100=3 分钟前、o101=12 分钟前（两笔可正常支付）、o102=45 分钟前（保留一笔已超时用于演示 D1 关单）。证据 `node .shots/seed-check.js` → `o100 expired=false / o101 expired=false / o102 expired=true`，未付款单 3 笔、可支付 2 笔、已超时 1 笔。

**收口复跑（全量，captain 执行）**

> 下面 6 个脚本已作为交付物收录到 `prototype/tools/`（`cdp.js` / `r1.js` / `scan.js` / `fix-check.js` / `store-fix-check.js` / `seed-check.js`），本报告其他章节写的 `.shots/verify/xxx.js` 与之同源；`.shots/` 只在本机保留（截图与一次性探针脚本，已被 `.gitignore` 排除）。复跑前需以 `prototype` 为根起一个静态服务：`python -m http.server 8765`，然后在 `prototype/` 下执行下表命令。

| 命令 | 结果 |
| --- | --- |
| `node --check assets/{store,ui,app,admin-screens}.js` | 4/4 通过 |
| `node tools/r1.js` | **15/15 通过**，运行期 `console.error=0`、未捕获异常 0 |
| `node tools/scan.js` | 27 路由：空屏 0 / 可疑文案 0 / 横向溢出 0 |
| `node tools/fix-check.js` | 7/7 通过 |
| `node tools/store-fix-check.js` | PASS 22 / FAIL 0 |
| `node tools/seed-check.js` | 种子未付款单时序符合预期 |

**结论：原报告「需修订」的 5 条缺陷（2 high / 2 medium / 1 low）全部闭合，可交付。** 遗留未闭合项仅 1 条，且属验收手段限制而非缺陷：P-04 的「挂机 30 分钟」未做真实等待实测，改为「把种子单 `createdAt` 改到 31 分钟前」的方式取证（等价性见上表）。