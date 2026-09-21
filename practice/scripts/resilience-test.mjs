// Optional fault-injection integration test. Start Transaction Service with:
// CATALOG_URL=http://localhost:8181 CART_URL=http://localhost:8182
// Keep Catalog and Cart on 8081/8082. No other Transaction Service instance may run.
import http from 'node:http';
import assert from 'node:assert/strict';
import {randomUUID as uuid} from 'node:crypto';

const catalog=process.env.REAL_CATALOG_URL || 'http://localhost:8081';
const cart=process.env.REAL_CART_URL || 'http://localhost:8082';
const tx=process.env.TRANSACTION_URL || 'http://localhost:8083';
let hideStockResult=true,blockCleanup=true,stockCommitted=false;
let assertions=0;
function check(actual,expected,message){assert.deepEqual(actual,expected,message);assertions++;}
function proxy(port,target,kind){
  const server=http.createServer(async(req,res)=>{
    try {
      const buffers=[];for await(const chunk of req)buffers.push(chunk);
      if(kind==='cart'&&blockCleanup&&req.url==='/internal/carts/cleanup'){
        res.writeHead(503,{'Content-Type':'application/json'});res.end('{}');return;
      }
      const headers={...req.headers};delete headers.host;delete headers.connection;delete headers['content-length'];
      const body=Buffer.concat(buffers);
      const upstream=await fetch(target+req.url,{method:req.method,headers,body:body.length?body:undefined});
      const response=await upstream.text();
      if(kind==='catalog'&&hideStockResult&&req.url.startsWith('/internal/stock-operations/')&&upstream.ok){
        stockCommitted=true;res.writeHead(503,{'Content-Type':'application/json'});res.end('{}');return;
      }
      res.writeHead(upstream.status,{'Content-Type':upstream.headers.get('content-type')||'application/json'});res.end(response);
    }catch(error){res.writeHead(503,{'Content-Type':'application/json'});res.end('{}');}
  });
  return new Promise(resolve=>server.listen(port,'127.0.0.1',()=>resolve(server)));
}
const proxies=await Promise.all([proxy(8181,catalog,'catalog'),proxy(8182,cart,'cart')]);
async function api(base,path,method='GET',body,headers={},status=200){
  const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...headers},body:body?JSON.stringify(body):undefined});
  const text=await response.text();check(response.status,status,text);return text?JSON.parse(text):null;
}
async function until(read,predicate){
  const deadline=Date.now()+45000;
  while(Date.now()<deadline){const result=await read();if(predicate(result))return result;await new Promise(resolve=>setTimeout(resolve,500));}
  throw Error('Recovery deadline exceeded');
}
try {
  const customer=uuid(),headers={'X-Customer-Id':customer},key=uuid();
  const store=await api(catalog,'/api/stores','POST',{name:'Recovery '+uuid(),city:'Bandung'},{},201);
  const product=await api(catalog,'/api/products','POST',{storeId:store.id,name:'Recovery item',description:'Recovery test',category:'Test',imageUrl:''},{},201);
  const variant=await api(catalog,`/api/products/${product.product.id}/variants`,'POST',{sku:uuid(),variantName:'Default',price:10000,stock:3},{},201);
  const item=await api(cart,'/api/cart/items','POST',{variantId:variant.id,quantity:1,note:''},headers,201);
  const body={itemIds:[item.id],recipientName:'Buyer',recipientPhone:'08123',shippingAddress:'Bandung',courier:'REGULAR'};
  const pending=await api(tx,'/api/checkouts','POST',body,{...headers,'Idempotency-Key':key},202);
  check(stockCommitted,true,'Catalog committed before its response was hidden');
  check(pending.status,'PENDING','Ambiguous response remains pending');
  check((await api(catalog,`/api/variants/${variant.id}`)).stock,2,'One stock deduction');
  hideStockResult=false;
  const recovered=await until(()=>api(tx,`/api/transactions/${pending.id}`,'GET',undefined,headers),result=>result.status==='CREATED');
  check(recovered.cartCleaned,false,'Purchase survives unavailable Cart Service');
  check(recovered.orders.length,1,'No duplicate order');
  check((await api(catalog,`/api/variants/${variant.id}`)).stock,2,'Recovery did not deduct stock twice');
  blockCleanup=false;
  await until(()=>api(tx,`/api/transactions/${pending.id}`,'GET',undefined,headers),result=>result.cartCleaned);
  check((await api(cart,'/api/cart','GET',undefined,headers)).items.length,0,'Cleanup eventually completed');
  const replay=await api(tx,'/api/checkouts','POST',body,{...headers,'Idempotency-Key':key},201);
  check(replay.id,pending.id,'Replay still returns the same purchase');
  console.log(`PASS: ${assertions} assertions; ambiguous stock response, automatic recovery, delayed cleanup, and no double deduction.`);
} finally {for(const server of proxies){server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}}
