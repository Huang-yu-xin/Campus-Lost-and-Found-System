const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ROOT = path.resolve(__dirname, '../..')
const noop = () => {}
const deferred = () => { let resolve, reject; const promise = new Promise((r,j) => { resolve=r; reject=j }); return {promise,resolve,reject} }

// Execute the actual SFC methods, replacing only platform/network adapters.
function component(file, stubs = {}) {
  const source = fs.readFileSync(path.join(ROOT,'apps/miniapp/src',file),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1]
  const globals = { result:null, postApi:{}, userApi:{}, claimApi:{}, leadApi:{}, authApi:{},
    getToken:()=> 'token', clearToken:noop, setToken:noop, fileUrl:id=>'file/'+id,
    pickCheckedImages:async()=>[], loadPrivateImage:async()=>'', uploadFile:async()=>1,
    localDateTime:noop, localDateBoundary:noop, claimStatusLabel:noop, postStatusLabel:noop, typeLabel:noop, leadStatusLabel:noop,
    uni:{showToast:noop,navigateTo:noop,navigateBack:noop,switchTab:noop,showModal:o=>o.success({confirm:false})},
    setTimeout:noop, ...stubs }
  vm.runInNewContext(source.replace(/^import .+$/mg,'').replace('export default','result ='),globals)
  const option=globals.result, instance=option.data()
  for(const [key,method] of Object.entries(option.methods)) instance[key]=method.bind(instance)
  return {instance,option}
}
for(const [file,method] of [['pages/index/index.vue','list'],['pages/search/search.vue','search']]) {
  test(file+' ignores late pagination after new filter', async()=>{
    const old=deferred(),fresh=deferred();let calls=0
    const {instance:p}=component(file,{postApi:{[method]:()=>++calls===1?old.promise:fresh.promise}})
    const more=p.loadMore(),reload=p.reload()
    fresh.resolve({items:[{id:99}]});await reload
    old.resolve({items:[{id:12}]});await more
    assert.deepEqual(Array.from(p.list,x=>x.id),[99]);assert.equal(p.page,1);assert.equal(p.loading,false)
  })
  test(file+' keeps page after failed pagination and supports retry',async()=>{
    let fail=true
    const {instance:p}=component(file,{postApi:{[method]:async()=>{if(fail)throw Error('502');return {items:[{id:2}]}}}})
    await p.loadMore();assert.equal(p.page,1);assert.equal(p.loading,false)
    fail=false;await p.loadMore();assert.equal(p.page,2);assert.equal(p.list[0].id,2)
  })
}
test('mine ignores old tab pagination',async()=>{
  const old=deferred(),fresh=deferred()
  const {instance:p}=component('pages/mine/mine.vue',{userApi:{myPosts:()=>old.promise,myClaims:()=>fresh.promise}})
  const more=p.loadMore(),reload=p.switchTab('claims');fresh.resolve({items:[{id:99}]});await reload
  old.resolve({items:[{id:12}]});await more
  assert.deepEqual(Array.from(p.items,x=>x.id),[99]);assert.equal(p.page,1)
})
test('profile network failure is retryable and is not a logged-out view',async()=>{
  const {instance:p}=component('pages/mine/mine.vue',{userApi:{me:async()=>{throw {statusCode:502}}}})
  await p.loadMe();assert.equal(p.profileError,true);assert.equal(p.profileLoading,false)
})
test('profile 401 uses logged-out view',async()=>{
  const {instance:p}=component('pages/mine/mine.vue',{userApi:{me:async()=>{throw {statusCode:401}}}})
  await p.loadMe();assert.equal(p.profileError,false);assert.equal(p.me,null)
})
test('mock login ignores double clicks and resets loading',async()=>{
  const pending=deferred();let calls=0
  const {instance:p}=component('pages/login/login.vue',{authApi:{mockLogin:()=>{calls++;return pending.promise}}})
  p.testUser='user';const first=p.doMockLogin(),second=p.doMockLogin()
  assert.equal(calls,1);assert.equal(p.loading,true)
  pending.resolve({accessToken:'test'});await Promise.all([first,second]);assert.equal(p.loading,false)
})
test('publish ignores double clicks and returns after editing',async()=>{
  const pending=deferred();let creates=0,backs=0
  const {instance:p}=component('pages/publish/publish.vue',{postApi:{create:()=>{creates++;return pending.promise}}})
  p.form={type:'FOUND',title:'t',category:'c',publicDescription:'d'}
  const first=p.submit(),second=p.submit();assert.equal(creates,1)
  pending.resolve({});await Promise.all([first,second]);assert.equal(p.submitting,false)
  const {instance:edit}=component('pages/publish/publish.vue',{postApi:{update:async()=>{}},setTimeout:f=>f(),uni:{showToast:noop,navigateBack:()=>backs++}})
  edit.editId=1;edit.form=p.form;await edit.submit();assert.equal(backs,1)
})
test('manual lost selection can fetch page 2 when filtered first page is empty',async()=>{
  const {instance:p}=component('pages/claim/detail.vue',{userApi:{myPosts:async(page)=>({items:page===1?Array.from({length:20},(_,id)=>({id,type:'FOUND',status:'ACTIVE'})):[{id:99,type:'LOST',status:'ACTIVE'}]})}})
  await p.loadManualPosts();assert.equal(p.manualPosts.length,0);assert.equal(p.manualNoMore,false)
  const template=fs.readFileSync(path.join(ROOT,'apps/miniapp/src/pages/claim/detail.vue'),'utf8')
  assert.match(template,/v-if="!manualNoMore"/)
  await p.loadManualPosts();assert.equal(p.manualPosts[0].id,99);assert.equal(p.manualNoMore,true)
})
test('cancelled mark-found and lead-close do not issue mutations',async()=>{
  let calls=0
  await component('pages/detail/detail.vue',{postApi:{markFound:async()=>calls++}}).instance.markFound()
  await component('pages/lead/detail.vue',{leadApi:{review:async()=>calls++}}).instance.closeLead()
  assert.equal(calls,0)
})
for(const file of ['pages/detail/detail.vue','pages/claim/detail.vue','pages/lead/detail.vue']) {
  test(file+' presents error state on 404',async()=>{
    const reject=async()=>{throw {statusCode:404}}
    const {instance:p}=component(file,{postApi:{detail:reject},claimApi:{detail:reject},leadApi:{detail:reject}})
    await p.load();assert.equal(p.error,true)
  })
}
test('detail refreshes onShow after editing',()=>{
  const {instance:p,option}=component('pages/detail/detail.vue');let calls=0
  p.id=1;p.load=()=>calls++;option.onShow.call(p);assert.equal(calls,1)
})
function moduleSource(file, globals, tail) {
  const src=fs.readFileSync(path.join(ROOT,file),'utf8').replace(/^import .*$/mg,'').replace(/export function /g,'function ').replace(/export const /g,'const ').replace(/export default request/,'')
  return vm.runInNewContext(src+'\n'+tail,globals)
}
test('HTTP 502 rejects and mini request has 15-second timeout',async()=>{
  let request
  const api=moduleSource('apps/miniapp/src/utils/request.js',{API_BASE:'test',uni:{getStorageSync:()=>'',showToast:noop,request:o=>{request=o;o.success({statusCode:502,data:'gateway'})}}},';({http})')
  await assert.rejects(api.http.get('/posts'),e=>e.code==='HTTP_502');assert.equal(request.timeout,15000)
})
test('admin bad login does not suppress later expired-session handling',async()=>{
  const handlers={};const store=new Map(),location={pathname:'/login'};let warnings=0,redirects=0
  moduleSource('apps/admin-web/src/api/request.js',{axios:{create:()=>({interceptors:{request:{use:noop},response:{use:(ok,bad)=>handlers.bad=bad}}})},
    ElMessage:{warning:()=>warnings++},localStorage:{getItem:k=>store.get(k),removeItem:k=>store.delete(k)},location,setTimeout:()=>redirects++},';true')
  await handlers.bad({response:{status:401,data:{code:'ADMIN_LOGIN_FAILED'}}}).catch(noop)
  store.set('clf_admin_token','new');location.pathname='/dashboard'
  await handlers.bad({response:{status:401,data:{code:'UNAUTHENTICATED'}}}).catch(noop)
  assert.equal(store.has('clf_admin_token'),false);assert.equal(warnings,1);assert.equal(redirects,1)
  await handlers.bad({response:{status:401,data:{code:'UNAUTHENTICATED'}}}).catch(noop)
  assert.equal(warnings,1);assert.equal(redirects,1)
  store.set('clf_admin_token','another-new')
  await handlers.bad({response:{status:401,data:{code:'UNAUTHENTICATED'}}}).catch(noop)
  assert.equal(warnings,2);assert.equal(redirects,2)
})

function adminComponent(name, method) {
  const calls=[]
  const source=fs.readFileSync(path.join(ROOT,'apps/admin-web/src/views/'+name+'.vue'),'utf8')
    .match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/mg,'')
  const api=vm.runInNewContext(source+'\n;({onPage,reload,page,items})',{
    ref:value=>({value}), computed:f=>({get value(){return f()}}),
    onMounted:noop,onUnmounted:noop,useRouter:()=>({push:noop}),
    currentAdminId:()=>1,label:v=>v,statusFormatter:noop,
    adminApi:{[method]:async params=>{calls.push(params);return {items:[{id:params.page}],total:41}}},
    ElMessage:{error:noop,success:noop},ElMessageBox:{},fetchFileObjectUrl:noop
  })
  calls.length=0
  return {api,calls}
}
for (const [name,method] of [['Posts','listPosts'],['Users','listUsers'],['Disputes','listDisputes']]) {
  test('admin '+name+' requests page 2 then resets filter to page 1',async()=>{
    const {api,calls}=adminComponent(name,method)
    api.onPage(2);await new Promise(setImmediate)
    assert.equal(calls[0].page,2);assert.equal(api.items.value[0].id,2)
    api.reload();await new Promise(setImmediate)
    assert.equal(calls[1].page,1);assert.equal(api.page.value,1)
  })
}
test('mini production build selects HTTPS base and dev selects localhost',()=>{
  const read=env=>moduleSource('apps/miniapp/src/utils/config.js',{process:{env:{NODE_ENV:env}}},';API_BASE')
  assert.match(read('production'),/^https:\/\//)
  assert.doesNotMatch(read('production'),/localhost/)
  assert.match(read('development'),/^http:\/\/localhost:/)
})
