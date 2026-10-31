const fs=require('fs'),path=require('path');
global.window={};global.localStorage={getItem:()=>null,setItem:()=>{},removeItem:()=>{}};
eval(fs.readFileSync(path.join(__dirname,'..','assets/store.js'),'utf8'));
const S=window.Store;
const un=S.state.orders.filter(o=>o.status==='unpaid');
for(const o of un){
  const mins=Math.round((Date.now()-o.createdAt)/60000);
  console.log(o.id,'下单于',mins,'分钟前','expired=',S.orderExpired(o.id));
}
console.log('未付款单数=',un.length,'应可支付=',un.filter(o=>!S.orderExpired(o.id)).length,'已超时=',un.filter(o=>S.orderExpired(o.id)).length);
