import { redisGet } from './redis-get.mjs'
const B='http://127.0.0.1:8000'
const call=async(m,p,{token,body}={})=>{const r=await fetch(B+p,{method:m,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});const t=await r.text();try{return JSON.parse(t)}catch{return {raw:t}}}
const phone='135'+String(Date.now()).slice(-8)
await call('POST','/app/auth/captcha',{body:{phone,type:'register'}})
const code=await redisGet('captcha:'+phone)
const reg=await call('POST','/app/auth/register',{body:{username:'q'+String(Date.now()).slice(-6),password:'qq123456',mobile:phone,code}})
const token=reg.data.token
const list=await call('GET','/app/goods/list?pageNum=1&pageSize=5',{token})
const g=list.data.list.find(x=>!x.soldOut)
const det=await call('GET',`/app/goods/detail?goodsId=${g.id}`,{token})
const sku=det.data.skus.find(s=>s.sellable>0)
await call('POST','/app/cart/add',{token,body:{goodsId:String(det.data.id),specId:String(sku.id),number:1}})
const cart=await call('GET','/app/cart/list',{token})
const row=(cart.data.list||cart.data)[0]
await call('POST','/app/address/create',{token,body:{name:'q',tel:'13800000000',province:'浙江省',city:'杭州市',county:'西湖区',addressDetail:'x',isDefault:true}})
const addressId=((await call('GET','/app/address',{token})).data||[])[0].id
const idem=await call('POST','/app/order/idem-token',{token})
const sub=await call('POST','/app/order/submit',{token,body:{cartIds:[String(row.id)],addressId:String(addressId),idemToken:idem.data.idemToken}})
if(!sub.data){console.log('submit fail',JSON.stringify(sub).slice(0,200));process.exit(1)}
const orderId=sub.data.orderId
console.log('order',orderId)
console.log('fail branch:',JSON.stringify(await call('POST','/app/pay/wechat',{token,body:{orderId:String(orderId),mode:'fail'}})).slice(0,200))
console.log('detail after fail:',JSON.stringify((await call('GET',`/app/order/detail?orderId=${orderId}`,{token})).data?.status))
console.log('success branch:',JSON.stringify(await call('POST','/app/pay/wechat',{token,body:{orderId:String(orderId),mode:'success'}})).slice(0,220))
