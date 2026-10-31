/* 商品 / 轮播矢量插图库（纯内联 SVG，无 emoji、无位图、无外部引用）
   契约：window.THUMBS[id] = '<svg ...>...</svg>'，另有 coffee 与 _fallback，of(id) 取用。
   颜色取 store.js 里各商品 pics[0] 的主色，白色商品带浅灰描边以便在浅底可见。 */
(function (root) {
  'use strict';

  function svg(vb, inner) {
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ' + vb + ' ' + vb +
      '" width="100%" height="100%" preserveAspectRatio="xMidYMid meet">' + inner + '</svg>';
  }
  /* 统一柔和落地阴影，保证同一尺寸下视觉重量接近 */
  var shadow = '<ellipse cx="50" cy="87" rx="28" ry="5" fill="#0F172A" opacity="0.07"/>';
  var shadowBig = '<ellipse cx="60" cy="101" rx="34" ry="6" fill="#0F172A" opacity="0.07"/>';

  var THUMBS = {
    /* 1 T恤：衫身 + 领口 + 胸前高光 */
    g1: svg(100, shadow +
      '<path d="M36 24 L20 33 L14 50 L29 56 L31 84 H69 L71 56 L86 50 L80 33 L64 24 Q50 36 36 24 Z" fill="#FF7A45"/>' +
      '<path d="M36 24 Q50 36 64 24 L60 19 Q50 28 40 19 Z" fill="#FF9A6C"/>' +
      '<path d="M45 42 h10" stroke="#FFFFFF" stroke-opacity="0.5" stroke-width="3" stroke-linecap="round"/>'),

    /* 2 连帽卫衣：兜帽 + 衫身 + 前袋 + 抽绳 */
    g2: svg(100, shadow +
      '<path d="M36 32 Q50 14 64 32 Q50 41 36 32 Z" fill="#7C8794"/>' +
      '<path d="M31 34 h38 a9 9 0 0 1 9 9 v33 a7 7 0 0 1 -7 7 H29 a7 7 0 0 1 -7 -7 V43 a9 9 0 0 1 9 -9 Z" fill="#9AA5B1"/>' +
      '<path d="M36 60 h28 a4 4 0 0 1 4 4 v14 a4 4 0 0 1 -4 4 H36 a4 4 0 0 1 -4 -4 V64 a4 4 0 0 1 4 -4 Z" fill="#8A95A1"/>' +
      '<path d="M46 33 v12 M54 33 v12" stroke="#E8EDF2" stroke-width="2.5" stroke-linecap="round"/>'),

    /* 3 牛仔裤：裤腰 + 两条裤腿 + 门襟 + 口袋弧线 */
    g3: svg(100, shadow +
      '<path d="M30 22 H70 L67 84 H55 L52 54 H48 L45 84 H33 Z" fill="#5B8FF9"/>' +
      '<rect x="30" y="20" width="40" height="10" rx="3" fill="#4A78D8"/>' +
      '<path d="M50 30 v12" stroke="#3F63B8" stroke-width="2.5" stroke-linecap="round"/>' +
      '<path d="M36 34 q6 7 12 0 M64 34 q-6 7 -12 0" stroke="#4A78D8" stroke-width="2" fill="none" stroke-linecap="round"/>'),

    /* 4 帆布鞋：鞋面 + 鞋底 + 鞋带 + 鞋头弧线 */
    g4: svg(100, shadow +
      '<path d="M17 62 q4 -16 19 -19 h12 q9 2 15 9 l14 7 q9 4 9 12 v6 H17 Z" fill="#52C41A"/>' +
      '<rect x="15" y="73" width="72" height="10" rx="5" fill="#3FA110"/>' +
      '<path d="M33 52 l8 -8 M40 54 l8 -8 M47 56 l8 -8" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" opacity="0.85"/>' +
      '<path d="M63 58 q8 6 8 13" stroke="#3FA110" stroke-width="2" fill="none" opacity="0.55"/>'),

    /* 5 保温杯：杯身 + 杯盖 + 提环 + 高光 */
    g5: svg(100, shadow +
      '<rect x="34" y="30" width="32" height="52" rx="9" fill="#13C2C2"/>' +
      '<rect x="31" y="19" width="38" height="13" rx="6" fill="#0E9E9E"/>' +
      '<path d="M66 35 q12 2 12 12 q0 10 -12 12" stroke="#0E9E9E" stroke-width="4" fill="none" stroke-linecap="round"/>' +
      '<path d="M42 40 v30" stroke="#FFFFFF" stroke-width="4" stroke-opacity="0.35" stroke-linecap="round"/>'),

    /* 6 咖啡豆袋：封口 + 袋身 + 标签 + 两颗咖啡豆 */
    g6: svg(100, shadow +
      '<rect x="30" y="19" width="40" height="12" rx="3" fill="#6B4226"/>' +
      '<path d="M29 31 h42 v45 a8 8 0 0 1 -8 8 H37 a8 8 0 0 1 -8 -8 Z" fill="#8C5A3B"/>' +
      '<rect x="38" y="43" width="24" height="22" rx="4" fill="#F5E9DC"/>' +
      '<ellipse cx="45" cy="54" rx="4" ry="6" fill="#5A3620" transform="rotate(-18 45 54)"/>' +
      '<ellipse cx="55" cy="54" rx="4" ry="6" fill="#5A3620" transform="rotate(18 55 54)"/>'),

    /* 7 头戴耳机：头梁 + 左右耳罩 + 耳垫 */
    g7: svg(100, shadow +
      '<path d="M24 58 V46 a26 26 0 0 1 52 0 v12" stroke="#2F54EB" stroke-width="7" fill="none" stroke-linecap="round"/>' +
      '<rect x="15" y="50" width="17" height="28" rx="8" fill="#1F3FC0"/>' +
      '<rect x="68" y="50" width="17" height="28" rx="8" fill="#1F3FC0"/>' +
      '<rect x="20" y="56" width="8" height="16" rx="4" fill="#8AA0FF"/>' +
      '<rect x="72" y="56" width="8" height="16" rx="4" fill="#8AA0FF"/>'),

    /* 8 机械键盘：外壳 + 键位区 + 两排键帽 + 空格键 */
    g8: svg(100, shadow +
      '<rect x="13" y="33" width="74" height="42" rx="9" fill="#722ED1"/>' +
      '<rect x="18" y="38" width="64" height="28" rx="5" fill="#9254DE"/>' +
      '<g fill="#F3ECFF">' +
        '<rect x="22" y="41" width="7" height="7" rx="2"/><rect x="31" y="41" width="7" height="7" rx="2"/>' +
        '<rect x="40" y="41" width="7" height="7" rx="2"/><rect x="49" y="41" width="7" height="7" rx="2"/>' +
        '<rect x="58" y="41" width="7" height="7" rx="2"/><rect x="67" y="41" width="7" height="7" rx="2"/>' +
        '<rect x="22" y="51" width="7" height="7" rx="2"/><rect x="31" y="51" width="7" height="7" rx="2"/>' +
        '<rect x="40" y="51" width="7" height="7" rx="2"/><rect x="49" y="51" width="7" height="7" rx="2"/>' +
        '<rect x="58" y="51" width="7" height="7" rx="2"/><rect x="67" y="51" width="7" height="7" rx="2"/>' +
      '</g>' +
      '<rect x="33" y="60" width="34" height="5" rx="2.5" fill="#F3ECFF"/>'),

    /* 9 香薰蜡烛：玻璃罐 + 蜡体 + 罐口 + 烛芯 + 火苗与光晕 */
    g9: svg(100, shadow +
      '<circle cx="50" cy="26" r="11" fill="#FFD666" opacity="0.3"/>' +
      '<rect x="32" y="42" width="36" height="42" rx="9" fill="#FAAD14"/>' +
      '<rect x="35" y="46" width="30" height="24" rx="5" fill="#FFE9B0"/>' +
      '<rect x="30" y="37" width="40" height="9" rx="4" fill="#D98E0A"/>' +
      '<path d="M50 37 v-5" stroke="#6B4A1B" stroke-width="2.5" stroke-linecap="round"/>' +
      '<path d="M50 32 q7 -7 0 -15 q-7 8 0 15 Z" fill="#FF7A45"/>'),

    /* 10 瑜伽垫卷：卷体 + 端面 + 卷芯 + 收纳带 */
    g10: svg(100, shadow +
      '<rect x="22" y="44" width="48" height="30" rx="15" fill="#EB2F96"/>' +
      '<ellipse cx="70" cy="59" rx="9" ry="15" fill="#FF6BB5"/>' +
      '<ellipse cx="70" cy="59" rx="4" ry="7" fill="#C2186F"/>' +
      '<rect x="42" y="41" width="9" height="36" rx="4" fill="#8C1358"/>'),

    /* 11 双肩包：包身 + 提手 + 前袋 + 侧背带 */
    g11: svg(100, shadow +
      '<path d="M31 34 a6 6 0 0 1 6 -6 h26 a6 6 0 0 1 6 6 v44 a7 7 0 0 1 -7 7 H38 a7 7 0 0 1 -7 -7 Z" fill="#434343"/>' +
      '<path d="M42 28 q8 -9 16 0" stroke="#2B2B2B" stroke-width="4" fill="none" stroke-linecap="round"/>' +
      '<rect x="39" y="50" width="22" height="21" rx="7" fill="#5E5E5E"/>' +
      '<path d="M31 40 q-6 8 -6 18 M69 40 q6 8 6 18" stroke="#2B2B2B" stroke-width="3" fill="none" stroke-linecap="round" opacity="0.6"/>'),

    /* 12 果茶礼盒：盒身 + 盒盖 + 竖丝带 + 双色蝴蝶结 */
    g12: svg(100, shadow +
      '<rect x="24" y="44" width="52" height="36" rx="7" fill="#FF7875"/>' +
      '<rect x="22" y="32" width="56" height="14" rx="5" fill="#D9504D"/>' +
      '<rect x="46" y="32" width="8" height="48" fill="#FFF1F0" opacity="0.9"/>' +
      '<path d="M46 33 q-11 -12 -17 -4 q-4 7 9 9 Z" fill="#FFF1F0"/>' +
      '<path d="M54 33 q11 -12 17 -4 q4 7 -9 9 Z" fill="#FFF1F0"/>'),

    /* 13 手机壳：外壳 + 内衬 + 镜头开孔 + 两颗镜头 */
    g13: svg(100, shadow +
      '<rect x="33" y="14" width="34" height="70" rx="10" fill="#36CFC9"/>' +
      '<rect x="37" y="18" width="26" height="62" rx="7" fill="#93E7E2"/>' +
      '<rect x="42" y="21" width="17" height="18" rx="4" fill="#1FA8A0"/>' +
      '<circle cx="47" cy="27" r="3.2" fill="#0E7C76"/>' +
      '<circle cx="47" cy="34" r="3.2" fill="#0E7C76"/>'),

    /* 14 护眼台灯：底座 + 灯杆 + 灯罩 + 灯泡 + 光线 */
    g14: svg(100, shadow +
      '<ellipse cx="50" cy="80" rx="19" ry="6" fill="#E0B90A"/>' +
      '<path d="M50 80 V52" stroke="#E0B90A" stroke-width="5" stroke-linecap="round"/>' +
      '<path d="M37 26 H63 L72 46 H28 Z" fill="#FADB14"/>' +
      '<circle cx="50" cy="52" r="4.5" fill="#FFE58F"/>' +
      '<path d="M31 52 l-8 10 M69 52 l8 10 M50 59 v12" stroke="#FFE58F" stroke-width="2.6" stroke-linecap="round" opacity="0.85"/>'),

    /* 15 袜子（两只）：后袜 + 袜口 + 前袜 + 袜口 */
    g15: svg(100, shadow +
      '<path d="M56 26 h14 v26 q0 7 -7 9 l-9 3 q-7 2 -8 -4 q-1 -6 6 -8 l4 -2 Z" fill="#D9D9D9" stroke="#A8A8A8" stroke-width="1.5"/>' +
      '<rect x="56" y="23" width="14" height="7" rx="3" fill="#AFAFAF"/>' +
      '<path d="M36 30 h14 v28 q0 7 -7 9 l-9 3 q-7 2 -8 -4 q-1 -6 6 -8 l4 -2 Z" fill="#BFBFBF" stroke="#9A9A9A" stroke-width="1.5"/>' +
      '<rect x="36" y="27" width="14" height="7" rx="3" fill="#9A9A9A"/>'),

    /* 16 收纳抽屉盒：盒体 + 抽屉面 + 拉手 + 内部分隔 */
    g16: svg(100, shadow +
      '<rect x="24" y="28" width="52" height="48" rx="8" fill="#D9D9D9" stroke="#A8A8A8" stroke-width="1.5"/>' +
      '<rect x="30" y="50" width="40" height="20" rx="5" fill="#F2F2F2"/>' +
      '<rect x="44" y="57" width="12" height="4" rx="2" fill="#B5B5B5"/>' +
      '<path d="M42 33 v12 M58 33 v12" stroke="#B5B5B5" stroke-width="2.5" stroke-linecap="round"/>'),

    /* 17 口红：管身 + 金环 + 斜切膏体 + 膏体高光 */
    g17: svg(100, shadow +
      '<rect x="40" y="45" width="20" height="37" rx="4" fill="#C2185B"/>' +
      '<rect x="37" y="39" width="26" height="8" rx="3" fill="#E0A82E"/>' +
      '<path d="M44 39 V27 L56 21 V39 Z" fill="#FF4D6D"/>' +
      '<path d="M44 27 L56 21 V26 L44 32 Z" fill="#FF8FA3"/>'),

    /* 18 洁面慕斯瓶：瓶身 + 泵头 + 喷嘴 + 三团泡沫 */
    g18: svg(100, shadow +
      '<rect x="34" y="44" width="32" height="40" rx="10" fill="#69C0FF"/>' +
      '<rect x="44" y="33" width="12" height="12" rx="2" fill="#4A9FE0"/>' +
      '<rect x="36" y="24" width="28" height="10" rx="5" fill="#4A9FE0"/>' +
      '<path d="M36 29 h-8" stroke="#4A9FE0" stroke-width="5" stroke-linecap="round"/>' +
      '<circle cx="50" cy="15" r="7" fill="#FFFFFF" stroke="#BBDDF7" stroke-width="2"/>' +
      '<circle cx="41" cy="20" r="5" fill="#FFFFFF" stroke="#BBDDF7" stroke-width="2"/>' +
      '<circle cx="59" cy="20" r="5" fill="#FFFFFF" stroke="#BBDDF7" stroke-width="2"/>'),

    /* 轮播咖啡杯：淡色圆底 + 咖啡液面 + 白色杯子 + 杯柄 + 三缕热气 */
    coffee: svg(120, shadowBig +
      '<circle cx="60" cy="64" r="46" fill="#EFF6FB"/>' +
      '<ellipse cx="60" cy="95" rx="32" ry="7" fill="#DCE5EE"/>' +
      '<path d="M30 54 h50 v18 a22 22 0 0 1 -22 22 h-6 a22 22 0 0 1 -22 -22 Z" fill="#FFFFFF" stroke="#D7DEE8" stroke-width="2.5"/>' +
      '<path d="M80 62 q16 4 16 14 t-16 12" fill="none" stroke="#D7DEE8" stroke-width="6" stroke-linecap="round"/>' +
      '<ellipse cx="55" cy="55" rx="25" ry="6" fill="#C98A5A"/>' +
      '<ellipse cx="55" cy="55" rx="19" ry="4" fill="#A96B3E"/>' +
      '<g stroke="#C3D3E2" stroke-width="3" stroke-linecap="round" fill="none">' +
        '<path d="M44 42 q-5 -7 0 -14 q5 -7 0 -13"/>' +
        '<path d="M56 40 q-5 -8 0 -15 q5 -7 0 -12"/>' +
        '<path d="M68 42 q-5 -7 0 -14 q5 -7 0 -13"/>' +
      '</g>'),

    /* 兜底占位图：图片框 + 太阳 + 山形（无文字） */
    _fallback: svg(100,
      '<rect x="16" y="22" width="68" height="56" rx="10" fill="#EDF1F6" stroke="#D5DDE8" stroke-width="2"/>' +
      '<circle cx="37" cy="43" r="7" fill="#C3CEDA"/>' +
      '<path d="M22 70 L40 50 L52 62 L62 54 L78 70 Z" fill="#C3CEDA"/>')
  };

  THUMBS.of = function (id) { return THUMBS[id] || THUMBS._fallback; };

  root.THUMBS = THUMBS;
  if (typeof module === 'object' && module.exports) module.exports = THUMBS;
})(typeof window !== 'undefined' ? window : globalThis);