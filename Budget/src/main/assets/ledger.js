'use strict';
const dateKey=d=>d.toISOString().slice(0,10);
const day=k=>new Date(k+'T00:00:00Z');
const add=(k,n)=>dateKey(new Date(day(k).getTime()+86400000*n));
const monthEnd=k=>dateKey(new Date(Date.UTC(+k.slice(0,4),+k.slice(5,7),0)));
function occurs(item,k){
  if(k<item.date)return false;
  const n=(day(k)-day(item.date))/86400000;
  switch(item.cycle){
    case 'Weekly': return n%7===0;
    case 'Biweekly': return n%14===0;
    case 'Monthly': case 'Yearly':
      return (item.cycle==='Monthly'||item.date.slice(5,7)===k.slice(5,7)) &&
        +k.slice(8)===Math.min(+item.date.slice(8),+monthEnd(k).slice(8));
    default:return n===0;
  }
}
function ledgerEvents(db,from,to){
  const result=[];
  for(let date=from;date<=to;date=add(date,1)){
    for(const item of db.items){
      const key=item.id+'@'+date;
      if(occurs(item,date)&&!db.posted[key])result.push({...item,when:date,key,done:false});
    }
  }
  for(const [key,item] of Object.entries(db.posted)){
    if(item.when>=from&&item.when<=to)result.push({...item,key,done:true});
  }
  return result.sort((a,b)=>a.when.localeCompare(b.when)||a.name.localeCompare(b.name));
}
function post(db,event){
  if(db.posted[event.key])return;
  const a=db.accounts.find(a=>a.id===event.account);
  if(!a)throw Error('Select a payment account first.');
  a.balance+=(event.kind==='Payday'?1:-1)*event.amount;
  db.posted[event.key]={...event,done:true};
}
function outlook(db,today,mode){
  const next=ledgerEvents(db,add(today,1),add(today,366)).find(i=>i.kind==='Payday'&&!i.done);
  const end=mode==='Paycheck'&&next?add(next.when,-1):monthEnd(today);
  const start=db.items.reduce((a,i)=>i.date<a?i.date:a,today);
  const pending=ledgerEvents(db,start,end).filter(i=>!i.done);
  const bills=pending.filter(i=>i.kind!=='Payday');
  const reserve=db.reserves[mode+'@'+end]||0;
  const cash=db.accounts.reduce((a,i)=>a+i.balance,0);
  const due=bills.reduce((a,i)=>a+i.amount,0);
  const income=pending.filter(i=>i.kind==='Payday').reduce((a,i)=>a+i.amount,0);
  return {end,bills,reserve,cash,due,income,next,available:cash-due-reserve};
}
