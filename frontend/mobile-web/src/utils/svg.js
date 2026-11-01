/** 少量内联 SVG（与 prototype/assets/app.js 中的 SVG_SCAN / SVG_CAM / SVG_BULB 一致） */

export const SVG_SCAN =
  '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2M4 12h16"/></svg>'

export const SVG_CAM =
  '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 8.6A2.6 2.6 0 0 1 5.6 6h1.1l1-1.6h6.6l1 1.6h1.1A2.6 2.6 0 0 1 21 8.6v7.8A2.6 2.6 0 0 1 18.4 19H5.6A2.6 2.6 0 0 1 3 16.4z"/><circle cx="12" cy="12.4" r="3.2"/></svg>'

export const SVG_BULB =
  '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="flex:none"><path d="M9.5 18h5M10.5 21h3M12 3a6 6 0 0 0-3.6 10.8c.5.4.9 1 .9 1.6V17h5.4v-1.6c0-.6.4-1.2.9-1.6A6 6 0 0 0 12 3z"/></svg>'

/** 首页 banner 右侧装饰插图（购物袋 + 星星，纯装饰，不承载数据） */
export const BANNER_ART =
  '<svg viewBox="0 0 100 78" fill="none" xmlns="http://www.w3.org/2000/svg">' +
  '<ellipse cx="50" cy="68" rx="30" ry="5" fill="#0F172A" opacity="0.10"/>' +
  '<path d="M28 26h44l-4 42H32z" fill="#FFFFFF" fill-opacity="0.92"/>' +
  '<path d="M40 30V21a10 10 0 0 1 20 0v9" stroke="#FFFFFF" stroke-width="3.4" stroke-linecap="round"/>' +
  '<path d="M38 40h24" stroke="#FF6A00" stroke-opacity="0.55" stroke-width="3" stroke-linecap="round"/>' +
  '<circle cx="78" cy="18" r="5" fill="#FFFFFF" fill-opacity="0.65"/>' +
  '<circle cx="16" cy="48" r="3" fill="#FFFFFF" fill-opacity="0.5"/>' +
  '</svg>'

export default { SVG_SCAN, SVG_CAM, SVG_BULB, BANNER_ART }