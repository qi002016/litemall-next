/* ============================================================================
 * 演示用数据层：内存状态 + localStorage 持久化，纯前端，无任何服务端调用。
 * 规则来源：D:\litemall-next\需求文档.md（BR-* 编号在注释里标注出处）
 * 金额单位一律为「分」。
 * ==========================================================================*/
(function () {
  'use strict';

  var KEY = 'litemall-proto-v3';
  var DAY = 86400000;
  var MIN = 60000;

  /* ---------------- 状态字典（全站共用，页面上不要再自己写中文） ------------- */
  var ORDER_STATUS = {
    unpaid: '未付款',
    wait_ship: '待发货',
    shipped: '已发货',
    received: '已收货',
    done: '已完成',
    closed: '已关闭'
  };
  var AS_STATUS = {
    apply: '申请中',
    wait_send: '待寄回',
    wait_receive: '待收货',
    refunding: '退款中',
    done: '退款完成',
    closed: '已关闭',
    rejected: '已驳回',
    canceled: '已撤销'
  };
  var AS_TERMINAL = ['done', 'closed', 'rejected', 'canceled'];
  var AS_OPEN = ['apply', 'wait_send', 'wait_receive', 'refunding'];

  var AS_REASONS = ['不想要了', '商品与描述不符', '尺码/颜色选错', '质量问题', '少件/漏发', '其他'];
  var REFUND_TYPES = { refund: '仅退款', return: '退货退款' };

  /* ---------------- 工具 ---------------- */
  var seq = 0;
  function uid(p) {
    seq += 1;
    return p + Date.now().toString(36) + seq.toString(36);
  }
  function clone(o) {
    return JSON.parse(JSON.stringify(o));
  }
  function money(n) {
    return (n / 100).toFixed(2);
  }

  /* ---------------- 种子数据 ---------------- */
  function seedGoods() {
    // pics: 用渐变色块 + emoji 代替图片，保证离线可看
    return [
      { id: 'g1', name: '重磅纯棉oversize T恤 男女同款', brief: '260g 精梳棉，不透不塌，四季可穿', catId: 'c11', brandId: 'b1', onSale: true, sales: 3821, rate: 98, tags: ['多规格', '热卖'], pics: ['#FF7A45', '👕'], detail: '面料：260g 双纱精梳棉；版型：落肩 oversize；建议洗涤：30℃ 以下机洗。',
        skus: [
          { id: 'g1-1', specs: { '颜色': '黑色', '尺码': 'L' }, price: 9900, number: 10 },
          { id: 'g1-2', specs: { '颜色': '白色', '尺码': 'M' }, price: 9900, number: 8 },
          { id: 'g1-3', specs: { '颜色': '黑色', '尺码': 'XL' }, price: 10900, number: 4 },
          { id: 'g1-4', specs: { '颜色': '杏色', '尺码': 'M' }, price: 9900, number: 0 }
        ] },
      { id: 'g2', name: '加绒连帽卫衣', brief: '420g 抓绒内里，秋冬常备', catId: 'c11', brandId: 'b1', onSale: true, sales: 1204, rate: 96, tags: ['多规格'], pics: ['#9AA5B1', '🧥'], detail: '厚度：420g 抓绒；帽绳：同色织带；洗涤：冷水手洗。',
        skus: [
          { id: 'g2-1', specs: { '颜色': '灰色', '尺码': 'M' }, price: 15900, number: 7 },
          { id: 'g2-2', specs: { '颜色': '灰色', '尺码': 'L' }, price: 15900, number: 3 },
          { id: 'g2-3', specs: { '颜色': '藏青', '尺码': 'L' }, price: 16900, number: 6 }
        ] },
      { id: 'g3', name: '直筒水洗牛仔裤', brief: '直筒不挑腿型，洗水做旧', catId: 'c12', brandId: 'b2', onSale: true, sales: 890, rate: 95, tags: ['多规格'], pics: ['#5B8FF9', '👖'], detail: '面料：98% 棉 2% 氨纶；版型：直筒；工艺：洗水做旧。',
        skus: [
          { id: 'g3-1', specs: { '颜色': '蓝色', '尺码': '30' }, price: 19900, number: 5 },
          { id: 'g3-2', specs: { '颜色': '蓝色', '尺码': '32' }, price: 19900, number: 2 },
          { id: 'g3-3', specs: { '颜色': '黑灰', '尺码': '32' }, price: 20900, number: 0 }
        ] },
      { id: 'g4', name: '复古帆布鞋 情侣款', brief: '硫化工艺，开口笑鞋头', catId: 'c13', brandId: 'b2', onSale: true, sales: 2210, rate: 97, tags: ['热卖'], pics: ['#52C41A', '👟'], detail: '鞋面：12oz 帆布；鞋底：硫化橡胶；可选尺码见规格。',
        skus: [
          { id: 'g4-1', specs: { '颜色': '米白', '尺码': '40' }, price: 12900, number: 9 },
          { id: 'g4-2', specs: { '颜色': '米白', '尺码': '42' }, price: 12900, number: 6 },
          { id: 'g4-3', specs: { '颜色': '黑色', '尺码': '41' }, price: 12900, number: 3 }
        ] },
      { id: 'g5', name: '316 不锈钢保温杯 500ml', brief: '12 小时保温，弹跳盖', catId: 'c21', brandId: 'b3', onSale: true, sales: 3410, rate: 99, tags: ['爆款'], pics: ['#13C2C2', '🥤'], detail: '材质：316 医用级不锈钢；容量：500ml；保温：12h≥55℃。',
        skus: [{ id: 'g5-1', specs: { '款式': '经典款' }, price: 6900, number: 26 }] },
      { id: 'g6', name: '中深烘焙意式咖啡豆 250g', brief: '坚果/黑巧风味，手冲意式都能打', catId: 'c41', brandId: 'b4', onSale: true, sales: 1520, rate: 96, tags: ['新品'], pics: ['#8C5A3B', '☕'], detail: '产区：云南保山；烘焙度：中深；建议赏味期：开封后 30 天。',
        skus: [{ id: 'g6-1', specs: { '规格': '250g 整豆' }, price: 5800, number: 40 }] },
      { id: 'g7', name: '主动降噪无线耳机', brief: '40dB 降噪，30 小时续航', catId: 'c31', brandId: 'b5', onSale: true, sales: 762, rate: 94, tags: ['数码'], pics: ['#2F54EB', '🎧'], detail: '降噪：-40dB；续航：单次 8h，含仓 30h；蓝牙 5.3。',
        skus: [
          { id: 'g7-1', specs: { '颜色': '星空黑' }, price: 29900, number: 5 },
          { id: 'g7-2', specs: { '颜色': '云朵白' }, price: 30900, number: 1 }
        ] },
      { id: 'g8', name: '客制化机械键盘 87 键', brief: '热插拔，Gasket 结构', catId: 'c32', brandId: 'b5', onSale: true, sales: 433, rate: 93, tags: ['多规格'], pics: ['#722ED1', '⌨️'], detail: '配列：87 键 TKL；结构：Gasket；支持热插拔三脚五脚轴。',
        skus: [
          { id: 'g8-1', specs: { '轴体': '青轴' }, price: 39900, number: 4 },
          { id: 'g8-2', specs: { '轴体': '红轴' }, price: 40900, number: 0 }
        ] },
      { id: 'g9', name: '大豆蜡香薰蜡烛', brief: '木质香调，燃烧 40 小时', catId: 'c22', brandId: 'b6', onSale: true, sales: 1180, rate: 97, tags: ['多规格'], pics: ['#FAAD14', '🕯️'], detail: '蜡材：天然大豆蜡；香调：雪松/佛手柑；燃烧时长：约 40h。',
        skus: [
          { id: 'g9-1', specs: { '规格': '小杯 120g' }, price: 4800, number: 12 },
          { id: 'g9-2', specs: { '规格': '大杯 240g' }, price: 7800, number: 5 }
        ] },
      { id: 'g10', name: 'TPE 双人瑜伽垫 8mm', brief: '防滑回弹，附收纳带', catId: 'c51', brandId: 'b7', onSale: true, sales: 640, rate: 95, tags: [], pics: ['#EB2F96', '🧘'], detail: '材质：TPE；厚度：8mm；尺寸：183×61cm。',
        skus: [{ id: 'g10-1', specs: { '颜色': '雾紫' }, price: 8800, number: 0 }] },
      { id: 'g11', name: '通勤双肩包 15.6 寸', brief: '防泼水，独立电脑仓', catId: 'c52', brandId: 'b7', onSale: true, sales: 980, rate: 96, tags: [], pics: ['#434343', '🎒'], detail: '容量：22L；可放 15.6 寸笔记本；面料：防泼水尼龙。',
        skus: [{ id: 'g11-1', specs: { '颜色': '曜石黑' }, price: 15900, number: 8 }] },
      { id: 'g12', name: '冷萃果茶礼盒 12 包', brief: '0 糖 0 卡，办公室泡水神器', catId: 'c42', brandId: 'b4', onSale: true, sales: 2650, rate: 98, tags: ['热卖'], pics: ['#FF7875', '🍵'], detail: '净含量：12×8g；冲泡：冷水 500ml 静置 5 分钟。',
        skus: [{ id: 'g12-1', specs: { '口味': '混合装' }, price: 4900, number: 33 }] },
      { id: 'g13', name: '磨砂防摔手机壳', brief: '全包边，镜头加高', catId: 'c33', brandId: 'b8', onSale: true, sales: 5310, rate: 99, tags: ['多规格'], pics: ['#36CFC9', '📱'], detail: '材质：TPU+PC；工艺：磨砂；支持机型见规格。',
        skus: [
          { id: 'g13-1', specs: { '机型': 'iPhone 15' }, price: 2900, number: 20 },
          { id: 'g13-2', specs: { '机型': 'iPhone 15 Pro' }, price: 2900, number: 14 },
          { id: 'g13-3', specs: { '机型': '小米 14' }, price: 2900, number: 0 }
        ] },
      { id: 'g14', name: '护眼台灯 无极调光', brief: '国 AA 级照度，无频闪', catId: 'c23', brandId: 'b8', onSale: true, sales: 512, rate: 94, tags: [], pics: ['#FADB14', '💡'], detail: '照度：国 AA；色温：3000–6000K 无极；显色指数：Ra95。',
        skus: [{ id: 'g14-1', specs: { '颜色': '象牙白' }, price: 21900, number: 3 }] },
      { id: 'g15', name: '纯棉中筒袜 3 双装', brief: '吸汗不臭脚', catId: 'c12', brandId: 'b1', onSale: true, sales: 1820, rate: 97, tags: [], pics: ['#BFBFBF', '🧦'], detail: '材质：精梳棉 78%；尺码：均码 38–44。',
        skus: [{ id: 'g15-1', specs: { '组合': '黑白灰各一' }, price: 3900, number: 18 }] },
      { id: 'g16', name: '收纳分隔抽屉盒（下架示例）', brief: '该商品已下架，用于演示"已下架"态', catId: 'c24', brandId: 'b6', onSale: false, sales: 120, rate: 90, tags: [], pics: ['#D9D9D9', '📦'], detail: '已下架商品：C 端列表与详情不可访问。',
        skus: [{ id: 'g16-1', specs: { '规格': '中号' }, price: 3600, number: 5 }] }
    ];
  }

  function seedState() {
    var t = Date.now();
    var cats = [
      { id: 'c1', name: '服饰', parentId: null, sort: 1 },
      { id: 'c2', name: '家居', parentId: null, sort: 2 },
      { id: 'c3', name: '数码', parentId: null, sort: 3 },
      { id: 'c4', name: '食品', parentId: null, sort: 4 },
      { id: 'c5', name: '运动', parentId: null, sort: 5 },
      { id: 'c11', name: '上装', parentId: 'c1', sort: 1 },
      { id: 'c12', name: '下装', parentId: 'c1', sort: 2 },
      { id: 'c13', name: '鞋靴', parentId: 'c1', sort: 3 },
      { id: 'c21', name: '杯壶', parentId: 'c2', sort: 1 },
      { id: 'c22', name: '香氛', parentId: 'c2', sort: 2 },
      { id: 'c23', name: '灯具', parentId: 'c2', sort: 3 },
      { id: 'c24', name: '收纳', parentId: 'c2', sort: 4 },
      { id: 'c31', name: '耳机', parentId: 'c3', sort: 1 },
      { id: 'c32', name: '键盘', parentId: 'c3', sort: 2 },
      { id: 'c33', name: '配件', parentId: 'c3', sort: 3 },
      { id: 'c41', name: '咖啡', parentId: 'c4', sort: 1 },
      { id: 'c42', name: '茶饮', parentId: 'c4', sort: 2 },
      { id: 'c51', name: '瑜伽', parentId: 'c5', sort: 1 },
      { id: 'c52', name: '包袋', parentId: 'c5', sort: 2 }
    ];
    var brands = [
      { id: 'b1', name: 'BASIC 基础主义' },
      { id: 'b2', name: 'DENIM LAB' },
      { id: 'b3', name: 'THERMO' },
      { id: 'b4', name: '山与咖啡' },
      { id: 'b5', name: 'SOUNDX' },
      { id: 'b6', name: 'MORI' },
      { id: 'b7', name: 'FLOW' },
      { id: 'b8', name: 'NUBY' }
    ];

    var state = {
      v: 3,
      user: { id: 'u1', nick: '演示用户', phone: '138****8888' },
      admin: { name: 'admin', role: '超级管理员' },
      cats: cats,
      brands: brands,
      goods: seedGoods(),
      banners: [
        { id: 'bn1', title: '秋冬上新 低至 5 折', sub: '重磅卫衣 / 直筒牛仔', bg: 'linear-gradient(135deg,#FF7A45,#FF2D55)', emoji: '🧥', link: { type: 'cat', id: 'c11' } },
        { id: 'bn2', title: '咖啡茶饮 满 99 减 30', sub: '领券下单更划算', bg: 'linear-gradient(135deg,#8C5A3B,#4E342E)', emoji: '☕', link: { type: 'coupon' } },
        { id: 'bn3', title: '数码好物 新品首发', sub: '降噪耳机 / 客制化键盘', bg: 'linear-gradient(135deg,#2F54EB,#722ED1)', emoji: '🎧', link: { type: 'cat', id: 'c3' } }
      ],
      topics: [
        { id: 'tp1', title: '通勤一周穿搭', desc: '5 件单品搭出 7 天不重样', emoji: '👔', goodsIds: ['g1', 'g3', 'g11'] },
        { id: 'tp2', title: '办公室续命清单', desc: '咖啡、果茶、保温杯', emoji: '☕', goodsIds: ['g5', 'g6', 'g12'] }
      ],
      notice: { text: '演示环境：所有支付均为模拟，不产生真实扣款。', at: t - 2 * DAY },
      coupons: [
        { id: 'cp1', name: '新人无门槛券', amount: 500, threshold: 0, total: 999, claimed: 12, expireAt: t + 30 * DAY, disabled: false },
        { id: 'cp2', name: '满 99 减 10', amount: 1000, threshold: 9900, total: 500, claimed: 87, expireAt: t + 15 * DAY, disabled: false },
        { id: 'cp3', name: '满 199 减 30', amount: 3000, threshold: 19900, total: 300, claimed: 300, expireAt: t + 15 * DAY, disabled: false },
        { id: 'cp4', name: '满 299 减 50', amount: 5000, threshold: 29900, total: 200, claimed: 41, expireAt: t + 7 * DAY, disabled: true }
      ],
      userCoupons: [],       // {id, couponId, status: unused|used|expired, orderId}
      cart: [],              // {key, goodsId, skuId, qty, checked}
      addresses: [
        { id: 'ad1', name: '张小满', phone: '13800008888', region: '浙江省 杭州市 西湖区', detail: '文三路 90 号 数字大厦 12 楼', isDefault: true },
        { id: 'ad2', name: '李演示', phone: '13900009999', region: '上海市 上海市 浦东新区', detail: '张江高科 碧波路 690 号 3 号楼', isDefault: false }
      ],
      orders: [],
      aftersales: [],
      reviews: [],
      demoLog: [],
      lastAsSn: 0,
      lastOrderSn: 0
    };

    /* ---- 种子订单：30 笔覆盖 6 种主状态（Q21） ---- */
    var g = state.goods;
    function lineOf(goodsId, skuId, qty, idx) {
      var goods = g.find(function (x) { return x.id === goodsId; });
      var sku = goods.skus.find(function (x) { return x.id === skuId; });
      var specText = Object.keys(sku.specs).map(function (k) { return sku.specs[k]; }).join(' / ');
      return {
        id: 'it' + idx,
        goodsId: goods.id,
        skuId: sku.id,
        name: goods.name,
        specText: specText,
        emoji: goods.pics[1],
        bg: goods.pics[0],
        price: sku.price,
        qty: qty,
        refundedQty: 0,
        reviewed: false
      };
    }

    var plan = [
      ['unpaid', 3], ['wait_ship', 6], ['shipped', 5], ['received', 8], ['done', 5], ['closed', 3]
    ];
    var combos = [
      ['g1', 'g1-1'], ['g1', 'g1-2'], ['g2', 'g2-1'], ['g3', 'g3-1'], ['g4', 'g4-1'],
      ['g5', 'g5-1'], ['g6', 'g6-1'], ['g7', 'g7-1'], ['g12', 'g12-1'], ['g13', 'g13-1'],
      ['g15', 'g15-1'], ['g11', 'g11-1']
    ];
    var c = 0;
    plan.forEach(function (p) {
      for (var i = 0; i < p[1]; i++) {
        var combo = combos[c % combos.length];
        var skuPrice = 0;
        var goods = g.find(function (x) { return x.id === combo[0]; });
        goods.skus.forEach(function (s) { if (s.id === combo[1]) skuPrice = s.price; });
        var qty = 1 + (c % 3);
        var item = lineOf(combo[0], combo[1], qty, c);
        var goodsAmount = skuPrice * qty;
        var couponAmount = 0;
        if (c % 4 === 0 && goodsAmount >= 9900) couponAmount = 1000;
        // 未付款种子单要同时撑住两个演示场景：C 端「去支付」能走通（未超时），
        // 后台又能演示 D1 超时关单。故 i=0 → 3 分钟前（可支付）、i=1 → 12 分钟前（可支付、倒计时紧张）、
        // i=2 → 45 分钟前（已超时，打开支付页即按「超时关闭」落库）。
        var created = p[0] === 'unpaid'
          ? t - [3, 12, 45][i] * MIN
          : t - (c + 1) * 8 * 3600000;
        var paid = created + 3 * MIN;
        var o = {
          id: 'o' + (100 + c),
          sn: 'LM' + String(20261031000 + c),
          status: p[0],
          items: [item],
          address: state.addresses[c % 2],
          couponId: couponAmount ? 'cp2' : null,
          goodsAmount: goodsAmount,
          couponAmount: couponAmount,
          freight: 0,
          payAmount: Math.max(0, goodsAmount - couponAmount),
          remark: '',
          createdAt: created,
          paidAt: p[0] === 'unpaid' || p[0] === 'closed' ? null : paid,
          shippedAt: ['shipped', 'received', 'done'].indexOf(p[0]) >= 0 ? paid + 6 * 3600000 : null,
          receivedAt: ['received', 'done'].indexOf(p[0]) >= 0 ? paid + 2 * DAY : null,
          closedAt: p[0] === 'closed' ? created + 30 * MIN : null,
          closedBy: p[0] === 'closed' ? '超时关闭' : null,
          tracking: ['shipped', 'received', 'done'].indexOf(p[0]) >= 0
            ? { company: ['顺丰速运', '中通快递', '京东物流'][c % 3], no: 'SF' + (1000000000 + c * 137) }
            : null,
          hidden: false,
          timeline: [],
          logs: []
        };
        o.timeline.push({ at: created, text: '提交订单' });
        if (o.paidAt) o.timeline.push({ at: o.paidAt, text: '支付成功（演示）' });
        if (o.shippedAt) o.timeline.push({ at: o.shippedAt, text: '商家发货 ' + o.tracking.company + ' ' + o.tracking.no });
        if (o.receivedAt) o.timeline.push({ at: o.receivedAt, text: '确认收货' });
        if (p[0] === 'done') o.timeline.push({ at: o.receivedAt + DAY, text: '评价完成' });
        if (p[0] === 'closed') o.timeline.push({ at: o.closedAt, text: '超时未付款，订单关闭' });
        state.orders.push(o);
        c++;
      }
    });

    /* ---- 已完成订单补评价（≥15 条） ---- */
    state.orders.filter(function (o) { return o.status === 'done'; }).forEach(function (o) {
      o.items.forEach(function (it) {
        it.reviewed = true;
        state.reviews.push({
          id: uid('rv'),
          goodsId: it.goodsId,
          orderId: o.id,
          itemId: it.id,
          rate: [5, 5, 4, 5, 4][state.reviews.length % 5],
          content: ['面料很舒服，尺码正，回购第二次了', '物流快，包装好，颜色比图片还好看', '性价比可以，就是稍微有点线头', '用了一周来评价，真的不错', '符合预期，会推荐给朋友'][state.reviews.length % 5],
          user: '演示用户',
          at: o.receivedAt + DAY,
          reply: null
        });
      });
    });
    // 再补几条无订单历史评价，凑够 15+
    for (var r = 0; r < 6; r++) {
      state.reviews.push({
        id: uid('rv'),
        goodsId: ['g5', 'g6', 'g12', 'g4', 'g9', 'g13'][r],
        orderId: null,
        itemId: null,
        rate: [5, 4, 5, 5, 4, 5][r],
        content: ['保温效果实测 12 小时还烫手', '咖啡很香，办公室同事都问链接', '果茶无糖也好喝，清爽', '鞋子有点偏大，建议拍小一码', '香薰味道高级，不刺鼻', '手机壳手感好，就是有点重'][r],
        user: '用户' + (1000 + r),
        at: t - (3 + r) * DAY,
        reply: r === 1 ? '感谢支持，近期还会有新产区上架～' : null
      });
    }

    /* ---- 种子售后单 8 笔：覆盖 6 种状态（含退款中/待寄回/已关闭） ---- */
    var asSource = state.orders.filter(function (o) {
      return ['received', 'done', 'shipped'].indexOf(o.status) >= 0;
    });
    var asPlan = [
      { status: 'apply', type: 'refund', qty: 1 },
      { status: 'apply', type: 'return', qty: 1 },
      { status: 'wait_send', type: 'return', qty: 1 },
      { status: 'wait_receive', type: 'return', qty: 1 },
      { status: 'refunding', type: 'refund', qty: 1, restockPending: true },
      { status: 'done', type: 'return', qty: 1 },
      { status: 'done', type: 'refund', qty: 1 },
      { status: 'closed', type: 'return', qty: 1 }
    ];
    asPlan.forEach(function (p, i) {
      var o = asSource[i];
      if (!o) return;
      var it = o.items[0];
      var asId = 'as' + (100 + i);
      o.items[0].refundedQty = p.status === 'done' ? p.qty : 0;
      var base = {
        id: asId,
        sn: 'AS' + (20261031000 + i),
        orderId: o.id,
        orderSn: o.sn,
        itemId: it.id,
        goodsId: it.goodsId,
        skuId: it.skuId,
        name: it.name,
        specText: it.specText,
        emoji: it.emoji,
        bg: it.bg,
        type: p.type,
        qty: p.qty,
        amount: it.price * p.qty,
        status: p.status,
        reason: AS_REASONS[i % AS_REASONS.length],
        note: '',
        photos: p.type === 'return' ? 1 : 0,
        returnAddress: null,
        trackingNo: null,
        restockPending: !!p.restockPending,
        restocked: p.status === 'done',
        createdAt: t - (i + 1) * 12 * 3600000,
        updatedAt: t - (i + 1) * 11 * 3600000,
        timeline: []
      };
      base.timeline.push({ at: base.createdAt, text: '用户提交售后申请（' + REFUND_TYPES[base.type] + '）', by: '消费者' });
      if (['wait_send', 'wait_receive', 'refunding', 'done'].indexOf(p.status) >= 0) {
        base.returnAddress = p.type === 'return'
          ? { name: '小满商城售后组', phone: '0571-88888888', region: '浙江省 杭州市 余杭区', detail: '仓前街道 电商仓 3 号门' }
          : null;
        base.timeline.push({ at: base.createdAt + 3600000, text: '商家同意' + (p.type === 'return' ? '，已提供退货地址' : ''), by: '运营' });
      }
      if (['wait_receive', 'refunding', 'done'].indexOf(p.status) >= 0) {
        base.trackingNo = 'SF' + (2000000000 + i * 91);
        base.timeline.push({ at: base.createdAt + 2 * 3600000, text: '用户已填写退货单号 ' + base.trackingNo, by: '消费者' });
      }
      if (['refunding', 'done'].indexOf(p.status) >= 0) {
        if (p.status === 'done') {
          base.timeline.push({ at: base.updatedAt, text: '退款完成，库存已回补', by: '系统' });
        } else {
          base.timeline.push({ at: base.updatedAt - 60 * MIN, text: '确认收货，退款处理中', by: '运营' });
          base.timeline.push({ at: base.updatedAt, text: '资金已退，库存回补失败（待重试）', by: '系统' });
        }
      }
      if (p.status === 'closed') {
        base.timeline.push({ at: base.createdAt + 2 * DAY, text: '待寄回超过 7 天未填单号，售后单自动关闭', by: '系统' });
      }
      state.aftersales.push(base);
    });

    return state;
  }

  /* ---------------- 载入 / 持久化 ---------------- */
  var state;
  var listeners = [];
  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* file:// 下可能不可用，降级为内存 */ }
    listeners.forEach(function (fn) { try { fn(); } catch (e) {} });
  }
  function load() {
    var raw = null;
    try { raw = localStorage.getItem(KEY); } catch (e) {}
    if (raw) {
      try {
        var s = JSON.parse(raw);
        if (s && s.v === 3) return s;
      } catch (e) {}
    }
    var fresh = seedState();
    try { localStorage.setItem(KEY, JSON.stringify(fresh)); } catch (e) {}
    return fresh;
  }
  state = load();

  /* ---------------- 内部计算 ---------------- */
  function lockedQty(skuId) {
    // 锁量 = 未付款订单占用的数量（BR-ORDER-02：下单即锁，支付成功后消失）
    var n = 0;
    state.orders.forEach(function (o) {
      if (o.status !== 'unpaid') return;
      o.items.forEach(function (it) { if (it.skuId === skuId) n += it.qty; });
    });
    return n;
  }
  function skuOf(skuId) {
    for (var i = 0; i < state.goods.length; i++) {
      var s = state.goods[i].skus.find(function (x) { return x.id === skuId; });
      if (s) return { goods: state.goods[i], sku: s };
    }
    return null;
  }
  function sellable(skuId) {
    var f = skuOf(skuId);
    if (!f) return 0;
    return Math.max(0, f.sku.number - lockedQty(skuId));
  }
  function goodsOf(id) {
    return state.goods.find(function (g) { return g.id === id; });
  }
  function specText(sku) {
    return Object.keys(sku.specs).map(function (k) { return sku.specs[k]; }).join(' / ');
  }
  function displayPrice(goods) {
    // BR-GOODS-05：展示价 = 可售规格最低价
    var avail = goods.skus.filter(function (s) { return sellable(s.id) > 0; });
    var arr = (avail.length ? avail : goods.skus).map(function (s) { return s.price; });
    return arr.length ? Math.min.apply(null, arr) : 0;
  }
  function totalStock(goods) {
    return goods.skus.reduce(function (a, s) { return a + sellable(s.id); }, 0);
  }
  function orderById(id) {
    return state.orders.find(function (o) { return o.id === id; });
  }
  function asById(id) {
    return state.aftersales.find(function (a) { return a.id === id; });
  }
  function couponById(id) {
    return state.coupons.find(function (c) { return c.id === id; });
  }
  function nowText() { return Date.now(); }
  function log(text) {
    state.demoLog.unshift({ at: Date.now(), text: text, by: state.admin.name });
    if (state.demoLog.length > 60) state.demoLog.pop();
  }

  /* ---------------- 商品 / 分类 ---------------- */
  function goodsList(opt) {
    opt = opt || {};
    var list = state.goods.filter(function (g) { return g.onSale; });
    if (opt.catId) {
      var cid = opt.catId;
      var kids = state.cats.filter(function (c) { return c.parentId === cid; }).map(function (c) { return c.id; });
      list = list.filter(function (g) { return g.catId === cid || kids.indexOf(g.catId) >= 0; });
    }
    if (opt.kw) {
      var kw = String(opt.kw).trim();
      if (kw) {
        list = list.filter(function (g) {
          return g.name.indexOf(kw) >= 0 || g.brief.indexOf(kw) >= 0 || (g.tags || []).join(',').indexOf(kw) >= 0;
        });
      }
    }
    if (typeof opt.minP === 'number') list = list.filter(function (g) { return displayPrice(g) >= opt.minP; });
    if (typeof opt.maxP === 'number') list = list.filter(function (g) { return displayPrice(g) <= opt.maxP; });
    var sort = opt.sort || 'default';
    if (sort === 'priceAsc') list.sort(function (a, b) { return displayPrice(a) - displayPrice(b); });
    else if (sort === 'priceDesc') list.sort(function (a, b) { return displayPrice(b) - displayPrice(a); });
    else if (sort === 'new') list.sort(function (a, b) { return b.id.localeCompare(a.id); });
    else list.sort(function (a, b) { return b.sales - a.sales; });
    return list;
  }

  /* ---------------- 购物车 ---------------- */
  function cartAdd(goodsId, skuId, qty) {
    var g = goodsOf(goodsId);
    if (!g || !g.onSale) return { ok: false, msg: '商品已下架' };
    var av = sellable(skuId);
    if (av <= 0) return { ok: false, msg: '该规格暂时缺货' };
    var key = goodsId + '|' + skuId;
    var line = state.cart.find(function (x) { return x.key === key; });
    if (line) {
      line.qty = Math.min(999, line.qty + qty);
      line.checked = true;
    } else {
      state.cart.push({ key: key, goodsId: goodsId, skuId: skuId, qty: qty, checked: true });
    }
    save();
    return { ok: true, msg: '已加入购物车', count: cartCount() };
  }
  function cartUpdateQty(key, qty) {
    var line = state.cart.find(function (x) { return x.key === key; });
    if (!line) return { ok: false, msg: '行不存在' };
    if (qty < 1) return { ok: false, msg: '数量至少为 1' };
    if (qty > 999) return { ok: false, msg: '单行最多 999 件（BR-CART-02）' };
    line.qty = qty;
    save();
    return { ok: true };
  }
  function cartToggle(key, v) {
    var line = state.cart.find(function (x) { return x.key === key; });
    if (!line) return;
    line.checked = typeof v === 'boolean' ? v : !line.checked;
    save();
  }
  function cartToggleAll(v) {
    state.cart.forEach(function (l) { if (!cartLineInvalid(l)) l.checked = v; });
    save();
  }
  function cartRemove(key) {
    state.cart = state.cart.filter(function (x) { return x.key !== key; });
    save();
  }
  function cartClear() { state.cart = []; save(); }
  function cartLineInvalid(l) {
    var g = goodsOf(l.goodsId);
    if (!g || !g.onSale) return '已下架';
    var s = g.skus.find(function (x) { return x.id === l.skuId; });
    if (!s) return '规格已删除';
    if (sellable(l.skuId) <= 0) return '缺货';
    return null;
  }
  function cartLines() {
    return state.cart.map(function (l) {
      var g = goodsOf(l.goodsId);
      var s = g ? g.skus.find(function (x) { return x.id === l.skuId; }) : null;
      return {
        key: l.key, goodsId: l.goodsId, skuId: l.skuId, qty: l.qty, checked: l.checked,
        name: g ? g.name : '商品已删除',
        specText: s ? specText(s) : '',
        emoji: g ? g.pics[1] : '❓', bg: g ? g.pics[0] : '#eee',
        price: s ? s.price : 0,
        invalid: cartLineInvalid(l),
        sellable: sellable(l.skuId)
      };
    });
  }
  function cartCount() { return state.cart.reduce(function (a, l) { return a + l.qty; }, 0); }

  /* ---------------- 优惠券 ---------------- */
  function claimCoupon(couponId) {
    var c = couponById(couponId);
    if (!c) return { ok: false, msg: '券不存在' };
    if (c.disabled) return { ok: false, msg: '该券已停用' };
    if (Date.now() > c.expireAt) return { ok: false, msg: '该券已过期' };
    var mine = state.userCoupons.filter(function (u) { return u.couponId === couponId; });
    if (mine.length > 0) return { ok: false, msg: '你已领过这张券（BR-CP-02）' };   // 与"已领完"必须可区分
    if (c.claimed >= c.total) return { ok: false, msg: '已领完' };
    c.claimed += 1;
    state.userCoupons.push({ id: uid('uc'), couponId: couponId, status: 'unused', orderId: null });
    save();
    return { ok: true, msg: '领取成功，可在"我的优惠券"查看' };
  }
  function myCoupons(status) {
    var now = Date.now();
    var list = state.userCoupons.map(function (u) {
      var c = couponById(u.couponId);
      var st = u.status;
      if (st === 'unused' && c && now > c.expireAt) st = 'expired';
      return { u: u, c: c, status: st };
    });
    if (status) list = list.filter(function (x) { return x.status === status; });
    return list;
  }
  function usableCoupons(goodsAmount) {
    // BR-CP-05：门槛按商品金额（不含运费）
    return myCoupons('unused').map(function (x) {
      var ok = x.c.amount <= goodsAmount && goodsAmount >= x.c.threshold;
      var reason = !ok ? (goodsAmount < x.c.threshold ? '满 ' + money(x.c.threshold) + ' 可用' : '不可用') : '';
      return { coupon: x.c, userCoupon: x.u, ok: ok, reason: reason };
    });
  }

  /* ---------------- 地址 ---------------- */
  function addressSave(a) {
    if (a.id) {
      var i = state.addresses.findIndex(function (x) { return x.id === a.id; });
      if (i >= 0) state.addresses[i] = Object.assign(state.addresses[i], a);
    } else {
      a.id = uid('ad');
      state.addresses.push(a);
    }
    if (a.isDefault) {
      state.addresses.forEach(function (x) { x.isDefault = (x.id === a.id); });
    }
    save();
    return { ok: true, id: a.id };
  }
  function addressRemove(id) {
    state.addresses = state.addresses.filter(function (x) { return x.id !== id; });
    save();
  }
  function defaultAddress() {
    return state.addresses.find(function (x) { return x.isDefault; }) || state.addresses[0] || null;
  }

  /* ---------------- 下单 / 支付 / 履约 ---------------- */
  function previewOrder(opt) {
    var lines = opt.lines || cartLines().filter(function (l) { return l.checked && !l.invalid; });
    var goodsAmount = lines.reduce(function (a, l) { return a + l.price * l.qty; }, 0);
    var coupon = opt.couponId ? couponById(opt.couponId) : null;
    var couponAmount = 0;
    if (coupon && goodsAmount >= coupon.threshold && coupon.amount <= goodsAmount) couponAmount = coupon.amount;
    var freight = 0; // BR-ORDER-11
    var payAmount = Math.max(0, goodsAmount + freight - couponAmount);
    return {
      lines: lines, goodsAmount: goodsAmount, couponAmount: couponAmount,
      freight: freight, payAmount: payAmount, address: defaultAddress()
    };
  }

  function submitOrder(opt) {
    opt = opt || {};
    var addr = opt.addressId ? state.addresses.find(function (x) { return x.id === opt.addressId; }) : defaultAddress();
    if (!addr) return { ok: false, msg: '请先选择收货地址' };
    var lines = opt.lines || cartLines().filter(function (l) { return l.checked && !l.invalid; });
    if (!lines.length) return { ok: false, msg: '请选择要结算的商品' };
    if (lines.length > 50) return { ok: false, msg: '单笔订单最多 50 行（BR-ORDER-09）' };
    // BR-ORDER-04：任一规格可售不足则整单失败，并指出是哪一行
    for (var i = 0; i < lines.length; i++) {
      var l = lines[i];
      var av = sellable(l.skuId);
      if (av < l.qty) {
        return { ok: false, msg: l.name + '（' + l.specText + '）仅剩 ' + av + ' 件' };
      }
    }
    var pv = previewOrder({ lines: lines, couponId: opt.couponId });
    if (opt.couponId) {
      var c = couponById(opt.couponId);
      if (!c) return { ok: false, msg: '券不存在' };
      if (pv.goodsAmount < c.threshold) return { ok: false, msg: '未达到券使用门槛' };
    }
    state.lastOrderSn += 1;
    var order = {
      id: uid('o'),
      sn: 'LM' + new Date().getFullYear() + String(Date.now()).slice(-8) + state.lastOrderSn,
      status: 'unpaid',
      items: lines.map(function (l, idx) {
        return {
          id: 'it' + Date.now() + idx,
          goodsId: l.goodsId, skuId: l.skuId, name: l.name, specText: l.specText,
          emoji: l.emoji, bg: l.bg, price: l.price, qty: l.qty, refundedQty: 0, reviewed: false
        };
      }),
      address: clone(addr),
      couponId: opt.couponId || null,
      goodsAmount: pv.goodsAmount,
      couponAmount: pv.couponAmount,
      freight: pv.freight,
      payAmount: pv.payAmount,
      remark: opt.remark || '',
      createdAt: Date.now(),
      paidAt: null, shippedAt: null, receivedAt: null, closedAt: null, closedBy: null,
      tracking: null, hidden: false,
      timeline: [{ at: Date.now(), text: '提交订单' }],
      logs: []
    };
    state.orders.unshift(order);
    if (opt.couponId) {
      var uc = state.userCoupons.find(function (u) { return u.couponId === opt.couponId && u.status === 'unused'; });
      if (uc) { uc.status = 'used'; uc.orderId = order.id; }
    }
    // BR-CART-07：已下单行从购物车移除
    var keys = lines.map(function (l) { return l.key; }).filter(Boolean);
    state.cart = state.cart.filter(function (l) { return keys.indexOf(l.key) < 0; });
    save();
    return { ok: true, orderId: order.id, sn: order.sn };
  }

  function payOrder(orderId, mode) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    if (o.status !== 'unpaid') return { ok: false, msg: '该订单无需支付（BR-PAY-02）' };
    // D1 / BR-ORDER-07：超过 30 分钟未付款即超时。先按「超时关闭」同一套逻辑关单
    //（释放锁量、券退回未使用且不延长有效期），再拒绝支付，避免支付入口成为超时订单的后门。
    if (orderExpired(orderId)) {
      demoCloseUnpaid(orderId);
      return { ok: false, msg: '该订单已超过 30 分钟未支付，已自动关单（锁量已释放、券已退回），请重新下单' };
    }
    if (mode === 'fail') {
      // BR-PAY-04/05：失败不改状态、不释放库存、不退券
      o.logs.push({ at: Date.now(), text: '模拟支付失败（演示）' });
      save();
      return { ok: false, msg: '支付失败（演示分支）：订单仍为未付款，可在 30 分钟内重试' };
    }
    o.status = 'wait_ship';
    o.paidAt = Date.now();
    // BR-ORDER-03：锁 → 实扣，一次性
    o.items.forEach(function (it) {
      var f = skuOf(it.skuId);
      if (f) f.sku.number = Math.max(0, f.sku.number - it.qty);
    });
    o.timeline.push({ at: o.paidAt, text: '支付成功（演示，不产生真实扣款）' });
    save();
    return { ok: true, msg: '支付成功' };
  }

  function releaseCouponOf(o) {
    if (!o.couponId) return;
    var uc = state.userCoupons.find(function (u) { return u.orderId === o.id; });
    if (uc) { uc.status = 'unused'; uc.orderId = null; }   // BR-CP-06 / BR-AS-20：有效期不延长
  }

  function closeOrder(orderId, by) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    if (o.status !== 'unpaid') return { ok: false, msg: '只有未付款订单可以关闭' };
    o.status = 'closed';
    o.closedAt = Date.now();
    o.closedBy = by || '用户取消';
    o.timeline.push({ at: o.closedAt, text: o.closedBy === '超时关闭' ? '超时未付款，订单关闭' : '用户取消订单' });
    releaseCouponOf(o);          // BR-ORDER-07：同一套释放逻辑（锁量随状态消失）
    save();
    return { ok: true, msg: '订单已关闭' };
  }
  function cancelOrder(orderId) { return closeOrder(orderId, '用户取消'); }

  function demoCloseUnpaid(orderId) {
    var r = closeOrder(orderId, '超时关闭');
    if (r.ok) { log('演示工具「立即关单」：订单 ' + orderId + ' 按超时逻辑关闭（锁量已释放、券已退回）'); save(); }
    return r;
  }

  function confirmReceive(orderId) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    if (o.status !== 'shipped') return { ok: false, msg: '只有已发货订单可以确认收货（BR-ORD-04）' };
    o.status = 'received';
    o.receivedAt = Date.now();
    o.timeline.push({ at: o.receivedAt, text: '确认收货' });
    save();
    return { ok: true, msg: '已确认收货' };
  }
  function shipOrder(orderId, info) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    if (o.status !== 'wait_ship') return { ok: false, msg: '只有待发货订单可以发货（BR-ORD-02）' };
    if (!info.company || !info.no) return { ok: false, msg: '物流公司与单号均不能为空' };
    o.status = 'shipped';
    o.shippedAt = Date.now();
    o.tracking = { company: info.company, no: info.no };
    o.timeline.push({ at: o.shippedAt, text: '商家发货 ' + info.company + ' ' + info.no });
    save();
    return { ok: true, msg: '发货成功' };
  }
  function editTracking(orderId, info) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    if (!o.tracking) return { ok: false, msg: '尚未发货' };
    if (o.trackingEdited) return { ok: false, msg: '物流单号发货后仅允许修改 1 次（BR-ORD-10）' };
    o.tracking = { company: info.company, no: info.no };
    o.trackingEdited = true;
    o.logs.push({ at: Date.now(), text: '修改物流单号 → ' + info.company + ' ' + info.no, by: state.admin.name });
    save();
    return { ok: true, msg: '已修改物流单号（仅此一次）' };
  }
  function deleteOrder(orderId) {
    var o = orderById(orderId);
    if (o) { o.hidden = true; save(); }
    return { ok: true, msg: '订单已删除（仅本人不可见，不影响后台与报表）' };
  }

  /* ---------------- 售后 ---------------- */
  function canApply(orderId, itemId) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    if (o.status === 'unpaid' || o.status === 'closed') return { ok: false, msg: '该订单状态不可申请售后（BR-AS-03）' };
    var it = o.items.find(function (x) { return x.id === itemId; });
    if (!it) return { ok: false, msg: '订单行不存在' };
    var remain = it.qty - it.refundedQty;
    var used = state.aftersales.filter(function (a) {
      return a.itemId === itemId && AS_OPEN.indexOf(a.status) >= 0;
    }).reduce(function (a, x) { return a + x.qty; }, 0);
    if (remain - used <= 0) return { ok: false, msg: '该订单行没有可申请的数量' };
    if (state.aftersales.some(function (a) { return a.itemId === itemId && AS_OPEN.indexOf(a.status) >= 0; })) {
      return { ok: false, msg: '该商品已有处理中的售后（BR-AS-04）' };
    }
    if (o.status === 'received' || o.status === 'done') {
      var limit = (o.receivedAt || Date.now()) + 7 * DAY;
      if (Date.now() > limit) return { ok: false, msg: '已超过售后申请窗口（确认收货后 7 天，BR-AS-03）' };
    }
    var types = [];
    if (o.status === 'wait_ship') types = ['refund'];            // 未发货只有仅退款
    else types = ['refund', 'return'];                           // 已发货 / 已收货 / 已完成
    return {
      ok: true, item: it, maxQty: remain - used, types: types,
      // 行实付（7.2 / D9）：行商品金额 − 该行分摊券额，运费不参与商品退款。
      // BR-AS-05 要求「向下取整到分」，所以用 Math.floor（不是 Math.round）。
      itemPaid: Math.floor(it.price * it.qty - (o.couponAmount * (it.price * it.qty) / Math.max(1, o.goodsAmount)))
    };
  }

  /* BR-AS-05：退款金额 = 该行实付 × 申请数量 ÷ 该行购买数量，向下取整到分。
     itemPaid 由 canApply 给出（已扣券分摊），这里只做按件均摊——
     避免「一行买 2 件只退 1 件却按整行实付退」的高估。 */
  function refundAmountOf(item, itemPaid, qty) {
    var lineQty = Math.max(1, item.qty);
    return Math.max(0, Math.floor((itemPaid || 0) * qty / lineQty));
  }

  /* 申请页「预计退款」与落库口径同源，避免同屏两个金额。 */
  function refundPreview(orderId, itemId, qty) {
    var chk = canApply(orderId, itemId);
    if (!chk.ok) return 0;
    return refundAmountOf(chk.item, chk.itemPaid, qty);
  }

  /* D1 / BR-ORDER-07：未付款 30 分钟即超时。原型没有服务端定时器，
     所以把「超时」做成可查询状态，由支付入口与页面按钮兜底关单。 */
  function orderExpired(orderId) {
    var o = orderById(orderId);
    return !!o && o.status === 'unpaid' && (Date.now() - o.createdAt) > 30 * MIN;
  }

  function aftersaleApply(opt) {
    var chk = canApply(opt.orderId, opt.itemId);
    if (!chk.ok) return chk;
    if (chk.types.indexOf(opt.type) < 0) return { ok: false, msg: '该订单状态不支持「' + REFUND_TYPES[opt.type] + '」' };
    if (!(opt.qty >= 1) || opt.qty > chk.maxQty) return { ok: false, msg: '申请数量需在 1–' + chk.maxQty + ' 之间' };
    if (opt.type === 'return' && (!opt.photos || !opt.photos.length)) return { ok: false, msg: '退货退款必须上传至少 1 张凭证（BR-AS-21）' };
    if (!opt.reason) return { ok: false, msg: '请选择售后原因' };
    var it = chk.item;
    // BR-AS-05：按「行实付 × 申请数量 ÷ 行数量」向下取整，不能用「单价 × 数量」
    var amount = refundAmountOf(it, chk.itemPaid, opt.qty);
    state.lastAsSn += 1;
    var as = {
      id: uid('as'),
      sn: 'AS' + new Date().getFullYear() + String(Date.now()).slice(-8) + state.lastAsSn,
      orderId: opt.orderId,
      orderSn: orderById(opt.orderId).sn,
      itemId: it.id,
      goodsId: it.goodsId, skuId: it.skuId, name: it.name, specText: it.specText,
      emoji: it.emoji, bg: it.bg,
      type: opt.type, qty: opt.qty,
      amount: amount,
      status: 'apply',
      reason: opt.reason,
      note: opt.note || '',
      photos: opt.photos || [],
      returnAddress: null, trackingNo: null,
      restockPending: false, restocked: false,
      createdAt: Date.now(), updatedAt: Date.now(),
      timeline: [{ at: Date.now(), text: '用户提交售后申请（' + REFUND_TYPES[opt.type] + '）', by: '消费者' }]
    };
    state.aftersales.unshift(as);
    save();
    return { ok: true, id: as.id, sn: as.sn };
  }

  function asApprove(id) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'apply') return { ok: false, msg: '只有"申请中"可以同意' };
    if (a.type === 'return') {
      a.status = 'wait_send';
      a.returnAddress = { name: '小满商城售后组', phone: '0571-88888888', region: '浙江省 杭州市 余杭区', detail: '仓前街道 电商仓 3 号门' };
      a.timeline.push({ at: Date.now(), text: '商家同意，已提供退货地址', by: '运营' });
    } else {
      a.status = 'refunding';
      a.timeline.push({ at: Date.now(), text: '商家同意退款，处理中', by: '运营' });
    }
    a.updatedAt = Date.now();
    save();
    return { ok: true, msg: a.type === 'return' ? '已同意，等待用户寄回' : '已同意，进入退款处理' };
  }
  function asReject(id, reason) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'apply') return { ok: false, msg: '只有"申请中"可以驳回' };
    if (!reason || !reason.trim()) return { ok: false, msg: '驳回必须填写理由（BR-AS-22）' };
    a.status = 'rejected';
    a.rejectReason = reason.trim();
    a.timeline.push({ at: Date.now(), text: '商家驳回：' + reason.trim(), by: '运营' });
    a.updatedAt = Date.now();
    save();
    return { ok: true, msg: '已驳回' };
  }
  function asFillTracking(id, no) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'wait_send') return { ok: false, msg: '只有"待寄回"可以填写单号' };
    if (!no || !no.trim()) return { ok: false, msg: '请填写退货物流单号' };
    a.trackingNo = no.trim();
    a.status = 'wait_receive';
    a.timeline.push({ at: Date.now(), text: '用户已填写退货单号 ' + a.trackingNo, by: '消费者' });
    a.updatedAt = Date.now();
    save();
    return { ok: true, msg: '已提交，等待商家收货' };
  }
  function asConfirmReceive(id) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'wait_receive') return { ok: false, msg: '只有"待收货"可以确认收货' };
    a.status = 'refunding';
    a.timeline.push({ at: Date.now(), text: '商家确认收货，退款处理中', by: '运营' });
    a.updatedAt = Date.now();
    save();
    return { ok: true, msg: '已确认收货' };
  }
  function asSkipReceive(id, reason) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (['wait_send', 'wait_receive'].indexOf(a.status) < 0) return { ok: false, msg: '只有"待寄回/待收货"可以跳过收货' };
    a.status = 'refunding';
    a.timeline.push({ at: Date.now(), text: '运营跳过收货直接退款' + (reason ? '：' + reason : ''), by: '运营' });
    a.updatedAt = Date.now();
    log('售后单 ' + a.sn + ' 一键跳过收货（原因：' + (reason || '未填') + '）');
    save();
    return { ok: true, msg: '已跳过收货，进入退款处理' };
  }

  // 退款完成：资金 + 库存回补同一事务（BR-AS-16），回补按矩阵（BR-AS-14）
  function asRefund(id) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'refunding') return { ok: false, msg: '只有"退款中"可以执行退款' };
    var o = orderById(a.orderId);
    var f = skuOf(a.skuId);
    if (!f) {
      a.restockPending = true;
      a.timeline.push({ at: Date.now(), text: '规格已被删除，库存回补失败（BR-GOODS-03）', by: '系统' });
      save();
      return { ok: false, msg: '规格已删除，无法回补库存；请先恢复规格或联系商品负责人' };
    }
    // 矩阵：未发货仅退款 → 回补；已发货/已收货退货退款（含跳过收货）→ 回补；已发货/已收货仅退款不寄回 → 不回补
    var shouldRestock = (a.type === 'return') || (o && o.status === 'wait_ship');
    if (shouldRestock) f.sku.number += a.qty;   // BR-AS-15：回补到该行规格，不到商品级
    var it = o ? o.items.find(function (x) { return x.id === a.itemId; }) : null;
    if (it) it.refundedQty = Math.min(it.qty, (it.refundedQty || 0) + a.qty);
    a.restocked = shouldRestock;
    a.restockPending = false;
    a.status = 'done';
    a.updatedAt = Date.now();
    a.timeline.push({
      at: Date.now(),
      text: '退款完成' + (shouldRestock ? '，库存已回补 ' + a.qty + ' 件（' + specText(f.sku) + '）' : '，商品未寄回，库存不回补（BR-AS-14）'),
      by: '系统'
    });
    // BR-AS-18/19：全部行退款完成才退券
    if (o) {
      var allRefunded = o.items.every(function (x) { return (x.refundedQty || 0) >= x.qty; });
      if (allRefunded) {
        o.fullyRefunded = true;
        releaseCouponOf(o);   // D5：整单全退才退券
        o.timeline.push({ at: Date.now(), text: '全部订单行退款完成，订单打「已全额退款」标记', by: '系统' });
      }
    }
    save();
    return { ok: true, msg: '退款完成' + (shouldRestock ? '，库存已回补 ' + a.qty + ' 件' : '（商品未寄回，不回补库存）') };
  }

  function asRetryRestock(id) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'refunding') return { ok: false, msg: '只有"退款中"可以重试回补' };
    var f = skuOf(a.skuId);
    if (!f) return { ok: false, msg: '规格已删除，仍无法回补' };
    return asRefund(id);
  }
  function asCancel(id) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'apply') return { ok: false, msg: '只有"申请中"可以撤销（BR-AS-11）' };
    a.status = 'canceled';
    a.timeline.push({ at: Date.now(), text: '用户撤销申请', by: '消费者' });
    a.updatedAt = Date.now();
    save();
    return { ok: true, msg: '已撤销' };
  }
  function asAutoClose(id) {
    var a = asById(id);
    if (!a) return { ok: false, msg: '售后单不存在' };
    if (a.status !== 'wait_send') return { ok: false, msg: '只有"待寄回"会超时关闭' };
    a.status = 'closed';
    a.timeline.push({ at: Date.now(), text: '待寄回超过 7 天未填单号，售后单自动关闭', by: '系统' });
    a.updatedAt = Date.now();
    save();
    return { ok: true, msg: '已按超时关闭' };
  }

  /* ---------------- 评价 ---------------- */
  function reviewSubmit(orderId, list) {
    var o = orderById(orderId);
    if (!o) return { ok: false, msg: '订单不存在' };
    var n = 0;
    list.forEach(function (r) {
      var it = o.items.find(function (x) { return x.id === r.itemId; });
      if (!it || it.reviewed) return;
      if ((it.refundedQty || 0) >= it.qty) return;   // BR-AS-17：已退款行不可评价
      it.reviewed = true;
      n++;
      state.reviews.unshift({
        id: uid('rv'), goodsId: it.goodsId, orderId: o.id, itemId: it.id,
        rate: r.rate || 5, content: r.content || '默认好评', user: state.user.nick,
        at: Date.now(), reply: null
      });
    });
    if (o.status === 'received') {
      var allReviewed = o.items.every(function (x) {
        if ((x.refundedQty || 0) >= x.qty) return true;   // 已退款行不参与（BR-ORD-09）
        return x.reviewed;
      });
      if (allReviewed) {
        o.status = 'done';
        o.timeline.push({ at: Date.now(), text: '评价完成，订单已完成' });
      }
    }
    save();
    return { ok: true, msg: n ? '评价成功，感谢反馈' : '没有可评价的行', count: n };
  }
  function reviewReply(reviewId, text) {
    var r = state.reviews.find(function (x) { return x.id === reviewId; });
    if (!r) return { ok: false, msg: '评价不存在' };
    r.reply = text;
    save();
    return { ok: true, msg: '已回复' };
  }

  /* ---------------- 后台：库存 / 报表 ---------------- */
  function stockRows(opt) {
    opt = opt || {};
    var rows = [];
    state.goods.forEach(function (g) {
      g.skus.forEach(function (s) {
        var locked = lockedQty(s.id);
        rows.push({
          goodsId: g.id, goodsName: g.name, onSale: g.onSale,
          skuId: s.id, specText: specText(s), specs: s.specs,
          price: s.price, number: s.number, locked: locked,
          sellable: Math.max(0, s.number - locked),
          low: Math.max(0, s.number - locked) <= 5
        });
      });
    });
    if (opt.kw) rows = rows.filter(function (r) { return (r.goodsName + r.specText).indexOf(opt.kw) >= 0; });
    if (opt.lowOnly) rows = rows.filter(function (r) { return r.low; });
    if (opt.goodsId) rows = rows.filter(function (r) { return r.goodsId === opt.goodsId; });
    return rows;
  }
  function stockUpdate(skuId, number) {
    var f = skuOf(skuId);
    if (!f) return { ok: false, msg: '规格不存在' };
    if (number < 0) return { ok: false, msg: '库存不能为负（BR-ORDER-12）' };
    var locked = lockedQty(skuId);
    if (number < locked) return { ok: false, msg: '已锁 ' + locked + ' 件，库存不能低于已锁数量（BR-ORDER-12）' };
    f.sku.number = number;
    save();
    return { ok: true, msg: '库存已更新' };
  }
  function goodsSave(g) {
    var i = state.goods.findIndex(function (x) { return x.id === g.id; });
    if (i >= 0) state.goods[i] = g; else { g.id = uid('g'); state.goods.push(g); }
    save();
    return { ok: true, id: g.id };
  }
  function goodsToggle(goodsId) {
    var g = goodsOf(goodsId);
    if (!g) return { ok: false, msg: '商品不存在' };
    if (!g.skus.length) return { ok: false, msg: '上架商品至少需要 1 个规格（BR-GOODS-01）' };
    g.onSale = !g.onSale;
    save();
    return { ok: true, msg: g.onSale ? '已上架' : '已下架' };
  }

  function stats() {
    var since = Date.now() - 30 * DAY;
    var paid = state.orders.filter(function (o) { return o.paidAt && o.paidAt >= since; });
    var gmv = paid.reduce(function (a, o) { return a + o.payAmount; }, 0);
    var refunded = state.aftersales.filter(function (a) { return a.status === 'done'; })
      .reduce(function (a, x) { return a + x.amount; }, 0);
    var pendingAs = state.aftersales.filter(function (a) { return AS_OPEN.indexOf(a.status) >= 0; }).length;
    var waitShip = state.orders.filter(function (o) { return o.status === 'wait_ship'; }).length;
    var lowStock = stockRows({ lowOnly: true }).length;
    var unpaid = state.orders.filter(function (o) { return o.status === 'unpaid'; }).length;
    return {
      gmv: gmv, orderCount: paid.length, refunded: refunded,
      pendingAs: pendingAs, waitShip: waitShip, lowStock: lowStock, unpaid: unpaid,
      avgPrice: paid.length ? Math.round(gmv / paid.length) : 0
    };
  }

  function resetAll() {
    state = seedState();
    save();
  }

  /* ---------------- 对外 API ---------------- */
  window.Store = {
    ORDER_STATUS: ORDER_STATUS,
    AS_STATUS: AS_STATUS,
    AS_OPEN: AS_OPEN,
    AS_TERMINAL: AS_TERMINAL,
    AS_REASONS: AS_REASONS,
    REFUND_TYPES: REFUND_TYPES,
    money: money,
    get state() { return state; },
    subscribe: function (fn) { listeners.push(fn); },
    save: save,
    // 商品
    goodsList: goodsList,
    goodsOf: goodsOf,
    skuOf: skuOf,
    displayPrice: displayPrice,
    totalStock: totalStock,
    sellable: sellable,
    lockedQty: lockedQty,
    specText: specText,
    // 购物车
    cartAdd: cartAdd, cartUpdateQty: cartUpdateQty, cartToggle: cartToggle,
    cartToggleAll: cartToggleAll, cartRemove: cartRemove, cartClear: cartClear,
    cartLines: cartLines, cartCount: cartCount,
    // 券
    claimCoupon: claimCoupon, myCoupons: myCoupons, usableCoupons: usableCoupons, couponById: couponById,
    // 地址
    addressSave: addressSave, addressRemove: addressRemove, defaultAddress: defaultAddress,
    // 订单
    previewOrder: previewOrder, submitOrder: submitOrder, payOrder: payOrder,
    cancelOrder: cancelOrder, closeOrder: closeOrder, confirmReceive: confirmReceive,
    deleteOrder: deleteOrder, orderById: orderById,
    shipOrder: shipOrder, editTracking: editTracking,
    demoCloseUnpaid: demoCloseUnpaid, demoLog: function () { return state.demoLog; },
    orderExpired: orderExpired,
    // 售后
    canApply: canApply, aftersaleApply: aftersaleApply, asById: asById,
    refundAmount: refundPreview,
    asApprove: asApprove, asReject: asReject, asFillTracking: asFillTracking,
    asConfirmReceive: asConfirmReceive, asSkipReceive: asSkipReceive,
    asRefund: asRefund, asRetryRestock: asRetryRestock, asCancel: asCancel, asAutoClose: asAutoClose,
    // 评价
    reviewSubmit: reviewSubmit, reviewReply: reviewReply,
    // 后台
    stockRows: stockRows, stockUpdate: stockUpdate, goodsSave: goodsSave, goodsToggle: goodsToggle,
    stats: stats, log: log, resetAll: resetAll
  };
})();