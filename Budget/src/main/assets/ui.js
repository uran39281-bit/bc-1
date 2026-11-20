'use strict';
const $=id=>document.getElementById(id);
const esc=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=n=>(n/100).toLocaleString('en-US',{style:'currency',currency:'USD'});
const now=new Date(),today=dateKey(new Date(Date.UTC(now.getFullYear(),now.getMonth(),now.getDate())));
const fmt=(k,opts={month:'short',day:'numeric'})=>day(k).toLocaleDateString('en-US',{...opts,timeZone:'UTC'});
const db=JSON.parse((window.Ice?Ice.load():localStorage.getItem('blackIceBudget'))||'null')||{accounts:[],items:[],posted:{},reserves:{},mode:'Paycheck'};
let tab='Calendar',selection=today,month=today.slice(0,7)+'-01',editId='',activeKind='';
function persist(){const s=JSON.stringify(db);if(window.Ice){if(!Ice.save(s))throw Error('Could not save to device.');}else localStorage.setItem('blackIceBudget',s);}
function change(fn){const old=structuredCloneSafe(db);try{fn();persist();}catch(e){Object.assign(db,old);throw e;}}
function structuredCloneSafe(x){return JSON.parse(JSON.stringify(x));}
const sections=['Calendar','Subscriptions','Budget','Accounts'];
const btn=(act,text,id='')=>'<button type="button" data-action="'+act+'" data-id="'+esc(id)+'">'+esc(text)+'</button>';
const empty=text=>'<p class="empty">'+esc(text)+'</p>';
const accountName=id=>db.accounts.find(a=>a.id===id)?.name||'Account';
const stat=(label,value)=>'<div class="row stat"><span>'+esc(label)+'</span><b>'+esc(value)+'</b></div>';
function eventRow(e,manage=false){
 const sub=e.when?fmt(e.when):e.cycle+' · From '+fmt(e.date);
 return '<article class="entry"><div class="row"><div><strong>'+esc(e.name)+'</strong><small>'+esc(sub)+' · '+esc(accountName(e.account))+'</small></div><b class="money">'+(e.kind==='Payday'?'+':'')+money(e.amount)+'</b></div><div class="actions">'+(manage?btn('edit','Edit',e.id)+btn('remove','Remove',e.id):e.done?'<small>'+ (e.kind==='Payday'?'Received':'Paid')+'</small>':btn('post',e.kind==='Payday'?'Mark received':'Mark paid',e.key))+'</div></article>';
}
function render(){
 const o=outlook(db,today,db.mode),end=monthEnd(month),monthly=ledgerEvents(db,month,end);
 const totals=Array.from({length:+end.slice(8)},()=>0);
 for(const e of monthly)if(e.kind!=='Payday')totals[+e.when.slice(8)-1]+=e.amount;
 if(window.Ice)Ice.chart(JSON.stringify(totals),fmt(month,{month:'short',year:'numeric'}).toUpperCase());
 $('nav').innerHTML=sections.map(name=>'<button class="'+(tab===name?'active':'')+'" data-action="tab" data-id="'+name+'">'+name+'</button>').join('');
 const hero='<div class="card hero"><small>'+(db.mode==='Paycheck'&&o.next?'Available until payday':'Available through month end')+'</small><strong>'+money(o.available)+'</strong><small>After bills and reserves through '+fmt(o.end)+'</small></div>';
 let html='';
 if(tab==='Calendar'){
   html=hero+'<div class="row"><h1>'+fmt(month,{month:'long',year:'numeric'})+'</h1><div class="steps">'+btn('prev','‹')+btn('today','Today')+btn('next','›')+'</div></div>';
   let cells=['S','M','T','W','T','F','S'].map(n=>'<span>'+n+'</span>').join('')+'<span></span>'.repeat(day(month).getUTCDay());
   for(let k=month;k<=end;k=add(k,1)){
     const events=monthly.filter(e=>e.when===k),bill=totals[+k.slice(8)-1],pay=events.some(e=>e.kind==='Payday');
     cells+='<button class="'+(k===selection?'selected':'')+'" aria-label="'+esc(fmt(k)+(bill?' bills '+money(bill):'')+(pay?' payday':''))+'" data-action="day" data-id="'+k+'">'+Number(k.slice(8))+'<small>'+(pay?'+ ':'')+(bill?money(bill):pay?'Pay':'')+'</small></button>';
   }
   html+='<section class="grid">'+cells+'</section><h2>'+fmt(selection,{weekday:'short',month:'short',day:'numeric'})+'</h2>';
   html+=monthly.filter(e=>e.when===selection).map(e=>eventRow(e)).join('')||empty('Nothing scheduled for this day.');
   html+='<h2>Upcoming · next 30 days</h2>'+(ledgerEvents(db,today,add(today,29)).filter(e=>!e.done).map(e=>eventRow(e)).join('')||empty('No upcoming entries.'));
   const overdue=o.bills.filter(e=>e.when<today);
   if(overdue.length)html+='<h2>Overdue bills</h2>'+overdue.map(e=>eventRow(e)).join('');
 }else if(tab==='Subscriptions'){
   html='<div class="row"><h1>Subscriptions</h1>'+btn('new','Add subscription','Subscription')+'</div>';
   html+=db.items.filter(i=>i.kind==='Subscription').map(i=>eventRow(i,true)).join('')||empty('Add a subscription to see its renewals on the calendar.');
 }else if(tab==='Budget'){
   html='<h1>Budget</h1><div class="actions">'+['Paycheck','Month'].map(m=>'<button class="'+(db.mode===m?'active':'')+'" data-action="mode" data-id="'+m+'">'+m+'</button>').join('')+'</div>'+hero;
   if(db.mode==='Paycheck'&&!o.next)html+=empty('Add your next payday to use a paycheck period. Showing this month for now.');
   html+=stat('Current balances',money(o.cash))+stat('Unpaid bills',money(o.due))+stat('Reserved spending and savings',money(o.reserve))+stat('Expected income through '+fmt(o.end),money(o.income));
   if(o.next)html+=stat('Next payday · '+fmt(o.next.when),money(o.next.amount));
   html+='<div class="actions">'+btn('reserve','Set reserve')+btn('new','Add payday','Payday')+'</div><h2>Paydays and expenses</h2>'+(db.items.filter(i=>i.kind!=='Subscription').map(i=>eventRow(i,true)).join('')||empty('No paydays or expenses entered.'));
 }else{
   html='<div class="row"><h1>Accounts</h1>'+btn('new','Add account','Account')+'</div>';
   html+=db.accounts.map(a=>'<article class="entry"><div class="row"><strong>'+esc(a.name)+'</strong><b>'+money(a.balance)+'</b></div><small>After unpaid bills through '+fmt(o.end)+' · '+money(a.balance-o.bills.filter(e=>e.account===a.id).reduce((n,e)=>n+e.amount,0))+'</small>'+btn('account','Edit balance',a.id)+'</article>').join('')||empty('Add your first account and its current balance.');
 }
 $('main').innerHTML=html;
}
const label=(n,field)=>'<label>'+n+field+'</label>';
const input=(id,type,value,extra='')=>'<input required id="'+id+'" type="'+type+'" value="'+esc(value)+'" '+extra+'>';
const select=(id,options,value)=>'<select id="'+id+'">'+options.map(([v,n])=>'<option value="'+esc(v)+'" '+(String(v)===String(value)?'selected':'')+'>'+esc(n)+'</option>').join('')+'</select>';
function openForm(kind,id=''){
 activeKind=kind;editId=id;
 const i=db.items.find(i=>i.id===id)||{},a=db.accounts.find(a=>a.id===id)||{};
 if(!db.accounts.length&&kind!=='Account'&&kind!=='Reserve'){openForm('Account');$('error').textContent='Add a payment account first.';return;}
 $('title').textContent=kind==='Reserve'?'Period reserve':(id?'Edit ':'Add ')+kind.toLowerCase();
 const kinds=['Subscription','Expense','Payday','Account'];
 let fields=id||kind==='Reserve'?'':label('Entry type',select('kind',kinds.map(x=>[x,x]),kind));
 if(kind==='Reserve'){
   fields+=label('Amount reserved for this period',input('amount','number',(outlook(db,today,db.mode).reserve/100).toFixed(2),'min="0" max="999999999" step=".01"'));
 }else{
   fields+=label('Name',input('name','text',kind==='Account'?a.name||'':i.name||'','maxlength="80"'));
   const val=kind==='Account'?a.balance:i.amount;
   fields+=label(kind==='Account'?'Current balance ($)':'Amount ($)',input('amount','number',val==null?'':(val/100).toFixed(2),'max="999999999" step=".01" min="'+(kind==='Account'?'-999999999':'0.01')+'"'));
   if(kind!=='Account'){
     fields+=label('Account',select('account',db.accounts.map(a=>[a.id,a.name]),i.account))+label('First date',input('date','date',i.date||selection,'min="2000-01-01" max="2100-12-31"'));
     const cycles=['Once','Weekly','Biweekly','Monthly','Yearly'];
     fields+=label('Frequency',select('cycle',cycles.map(x=>[x,x]),i.cycle||(kind==='Subscription'?'Monthly':kind==='Payday'?'Biweekly':'Once')));
     if(kind==='Subscription')fields+=label('Renewal reminder',select('remind',[[-1,'Off'],[0,'Day of renewal'],[1,'1 day before'],[3,'3 days before'],[7,'7 days before']],i.remind??-1));
   }
 }
 $('fields').innerHTML=fields;$('error').textContent='';if(!$('sheet').open)$('sheet').showModal();
 if($('kind'))$('kind').onchange=()=>openForm($('kind').value);
}
function closeSheet(){if(!$('sheet').open)return false;$('sheet').close();return true;}
$('plus').onclick=()=>openForm(db.accounts.length?'Subscription':'Account');
$('cancel').onclick=closeSheet;
$('form').onsubmit=e=>{
 e.preventDefault();
 try{
 const n=Math.round(Number($('amount').value)*100),kind=activeKind;
 const name=$('name')?.value.trim();
 if(kind!=='Reserve'&&!name)throw Error('Enter a name.');
 const id=editId||crypto.randomUUID(),remind=Number($('remind')?.value??-1);
 change(()=>{
   if(kind==='Reserve')db.reserves[db.mode+'@'+outlook(db,today,db.mode).end]=n;
   else if(kind==='Account'){
     const a={id,name,balance:n};if(editId)Object.assign(db.accounts.find(v=>v.id===id),a);else db.accounts.push(a);
   }else{
     const item={id,name,amount:n,kind,date:$('date').value,cycle:$('cycle').value,account:$('account').value,remind};
     if(editId)Object.assign(db.items.find(v=>v.id===id),item);else db.items.push(item);
   }
 });
 if(remind>=0&&window.Ice)Ice.notifyAccess();
 closeSheet();render();
 }catch(e){$('error').textContent=e.message;}
};
document.addEventListener('click',e=>{
 const b=e.target.closest('[data-action]');if(!b)return;
 const id=b.dataset.id,act=b.dataset.action;
 try{
 if(act==='tab')tab=id;
 if(act==='day')selection=id;
 if(act==='today'){selection=today;month=today.slice(0,7)+'-01';}
 if(act==='next'||act==='prev'){const d=day(month);d.setUTCMonth(d.getUTCMonth()+(act==='next'?1:-1));month=selection=dateKey(d);}
 if(act==='mode')change(()=>db.mode=id);
 if(act==='new'||act==='reserve'||act==='account'||act==='edit'){openForm(act==='new'?id:act==='reserve'?'Reserve':act==='account'?'Account':db.items.find(i=>i.id===id).kind,['account','edit'].includes(act)?id:'');return;}
 if(act==='remove'&&confirm('Remove this schedule? Recorded payments stay in your history.'))change(()=>db.items=db.items.filter(i=>i.id!==id));
 if(act==='post'){const [itemId,date]=id.split('@'),item=db.items.find(i=>i.id===itemId);if(item&&confirm('Record '+money(item.amount)+' for '+item.name+' on '+fmt(date)+'?'))change(()=>post(db,{...item,key:id,when:date}));}
 render();if(act==='tab')window.scrollTo(0,0);
 }catch(err){alert(err.message);}
});
render();
