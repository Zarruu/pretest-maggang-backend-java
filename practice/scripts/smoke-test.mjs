// Node.js 18+; run against three running services backed by PostgreSQL 15.
// Creates isolated demo records with random IDs; never deletes existing data.
import assert from 'node:assert/strict';
import { randomUUID as uuid } from 'node:crypto';

const catalog = process.env.CATALOG_URL || 'http://localhost:8081';
const cart = process.env.CART_URL || 'http://localhost:8082';
const transaction = process.env.TRANSACTION_URL || 'http://localhost:8083';
const internalKey = process.env.INTERNAL_KEY || 'local-practice-internal-key';
let checks = 0;
function equal(actual, expected, message) { assert.deepEqual(actual, expected, message); checks++; }
async function api(base, path, { method='GET', body, customer, key, internal, expected=200 }={}) {
  const headers = { 'Content-Type': 'application/json' };
  if (customer) headers['X-Customer-Id']=customer;
  if (key) headers['Idempotency-Key']=key;
  if (internal) headers['X-Internal-Key']=internalKey;
  const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  const text=await response.text();
  const data=text?JSON.parse(text):null;
  equal(response.status,expected,`${method} ${path}: ${text}`);
  return data;
}
const post=(base,path,body,options={})=>api(base,path,{method:'POST',body,...options});
const add=(customer,variantId,quantity=1)=>post(cart,'/api/cart/items',{variantId,quantity,note:'test note'},{customer,expected:201});
const request=itemIds=>({itemIds,recipientName:'Demo Buyer',recipientPhone:'08123456789',shippingAddress:'Bandung',courier:'REGULAR'});
async function makeProduct(storeId,name,price,stock) {
  const data=await post(catalog,'/api/products',{storeId,name,description:'Test product',category:'Electronics',imageUrl:''},{expected:201});
  const variant=await post(catalog,`/api/products/${data.product.id}/variants`,{sku:uuid(),variantName:'Default',price,stock},{expected:201});
  return {product:data.product,variant};
}

for(const base of [catalog,cart,transaction]) equal((await api(base,'/actuator/health')).status,'UP','Service healthy');
const customer=uuid(),other=uuid();
const a=await post(catalog,'/api/stores',{name:'Store A '+uuid(),city:'Bandung'},{expected:201});
const b=await post(catalog,'/api/stores',{name:'Store B '+uuid(),city:'Jakarta'},{expected:201});
const first=await makeProduct(a.id,'Laptop practice',100000,10);
const second=await makeProduct(b.id,'Charger practice',40000,10);
const extra=await makeProduct(a.id,'Cable practice',5000,10);
const results=await api(catalog,`/api/products?storeId=${a.id}&q=Laptop&sort=price_asc`);
equal(results.totalElements,1,'Search restricted to one store');
equal(results.content[0].startingPrice,100000,'Catalog price');
equal((await api(catalog,`/api/products/${first.product.id}`)).variants.length,1,'Product variants');
await api(catalog,'/api/products?size=0',{expected:400});
await api(catalog,'/api/products?sort=invalid',{expected:400});
await api(catalog,`/api/products/${uuid()}`,{expected:404});
await post(catalog,'/api/stores',{name:'',city:'Bandung'},{expected:400});
await post(catalog,`/internal/stock-operations/${uuid()}`,{items:[{variantId:first.variant.id,quantity:1}]},{expected:403});
await api(cart,'/api/cart',{expected:400});
await add(customer,first.variant.id);
const itemA=await add(customer,first.variant.id);
equal(itemA.quantity,2,'Same variant merges');
const itemB=await add(customer,second.variant.id);
const untouched=await add(customer,extra.variant.id);
await api(cart,`/api/cart/items/${itemA.id}`,{method:'PATCH',customer:other,body:{quantity:1,note:''},expected:404});
await api(cart,`/api/cart/items/${itemA.id}`,{method:'PATCH',customer,body:{quantity:0,note:''},expected:400});
await api(cart,`/api/cart/items/${itemA.id}`,{method:'PATCH',customer,body:{quantity:100,note:''},expected:409});
const basket=await api(cart,'/api/cart',{customer});
equal(basket.subtotal,245000,'Cart total recalculated by server');
const key=uuid(),checkout=request([itemA.id,itemB.id]);
await post(transaction,'/api/checkouts',checkout,{customer:other,key:uuid(),expected:400});
await post(transaction,'/api/checkouts',request([itemA.id,itemA.id]),{customer,key:uuid(),expected:400});
const purchased=await post(transaction,'/api/checkouts',checkout,{customer,key,expected:201});
equal(purchased.status,'CREATED','Purchase created');
equal(purchased.orders.length,2,'One order per store');
equal(purchased.totalAmount,270000,'Items plus shipping per store');
equal(purchased.cartCleaned,true,'Selected cart items removed');
equal((await api(cart,'/api/cart',{customer})).items.map(i=>i.id),[untouched.id],'Unselected item preserved');
equal((await api(catalog,`/api/variants/${first.variant.id}`)).stock,8,'Stock reduced');
const retried=await post(transaction,'/api/checkouts',checkout,{customer,key,expected:201});
equal(retried.id,purchased.id,'Retry returns same transaction');
equal((await api(catalog,`/api/variants/${first.variant.id}`)).stock,8,'Retry does not reduce stock twice');
await post(transaction,'/api/checkouts',{...checkout,shippingAddress:'Different address'},{customer,key,expected:409});
await api(transaction,`/api/transactions/${purchased.id}`,{customer:other,expected:404});
equal((await api(transaction,'/api/transactions?status=CREATED&q=Laptop',{customer})).totalElements,1,'Transaction search');
equal((await api(transaction,'/api/transactions',{customer:other})).totalElements,0,'Customer isolation');
await api(transaction,'/api/transactions?status=INVALID',{customer,expected:400});
await api(catalog,`/api/products/${first.product.id}`,{method:'PUT',body:{storeId:a.id,name:'Renamed product',description:'Changed',category:'Electronics',imageUrl:''}});
const detail=await api(transaction,`/api/transactions/${purchased.id}`,{customer});
equal(detail.orders.flatMap(o=>o.items).find(i=>i.variantId===first.variant.id).productName,'Laptop practice','Historical product snapshot');

// Two customers competing for one remaining item; a third checkout checks rollback.
const scarce=await makeProduct(a.id,'Last item',20000,1);
const safe=await makeProduct(a.id,'Rollback item',30000,2);
const c1=uuid(),c2=uuid(),c3=uuid();
const [i1,i2,i3,safeItem]=await Promise.all([add(c1,scarce.variant.id),add(c2,scarce.variant.id),add(c3,scarce.variant.id),add(c3,safe.variant.id)]);
async function race(customer,item){
  const response=await fetch(transaction+'/api/checkouts',{method:'POST',headers:{'Content-Type':'application/json','X-Customer-Id':customer,'Idempotency-Key':uuid()},body:JSON.stringify(request([item.id]))});
  return {status:response.status,body:await response.json()};
}
const concurrent=await Promise.all([race(c1,i1),race(c2,i2)]);
equal(concurrent.map(r=>r.status).sort(),[201,409],'Only one buyer succeeds');
equal((await api(catalog,`/api/variants/${scarce.variant.id}`)).stock,0,'No overselling');
const rejected=await post(transaction,'/api/checkouts',request([i3.id,safeItem.id]),{customer:c3,key:uuid(),expected:409});
equal(rejected.status,'FAILED','Insufficient stock is a definitive failure');
equal(rejected.orders.length,0,'Rejected checkout creates no orders');
equal((await api(catalog,`/api/variants/${safe.variant.id}`)).stock,2,'Failed multi-item checkout rolls back all stock changes');
equal((await api(cart,'/api/cart',{customer:c3})).items.length,2,'Rejected items remain in cart');

// Delayed cleanup must not delete a cart item edited since checkout began.
const c4=uuid(),edit=await add(c4,extra.variant.id);
await api(cart,`/api/cart/items/${edit.id}`,{method:'PATCH',customer:c4,body:{quantity:2,note:'Changed after snapshot'}});
await post(cart,'/internal/carts/cleanup',{customerId:c4,items:[{id:edit.id,version:edit.version}]},{internal:true,expected:204});
equal((await api(cart,'/api/cart',{customer:c4})).items[0].quantity,2,'Stale cleanup preserves new edit');
await api(cart,`/api/cart/items/${edit.id}`,{method:'DELETE',customer:c4,expected:204});
equal((await api(cart,'/api/cart',{customer:c4})).items.length,0,'Delete item');

console.log(`PASS: ${checks} assertions; catalog, cart, checkout, history, ownership, concurrency, rollback, idempotency, and cleanup.`);
