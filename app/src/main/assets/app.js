const PERIODS=[['WEEK','Per week'],['SALARY_PERIOD','Per salarisperiode'],['MONTH','Per maand'],['YEAR','Per jaar'],['ONCE','Eenmalig']];
const DEFAULT_POTS=[
{name:'Vrije uitgaven',budgetCents:8000,periodType:'WEEK',active:true,hiddenFromOverview:false,sortOrder:0},
{name:'Boodschappen',budgetCents:18000,periodType:'SALARY_PERIOD',active:true,hiddenFromOverview:false,sortOrder:1},
{name:'Brandstof',budgetCents:6000,periodType:'SALARY_PERIOD',active:true,hiddenFromOverview:false,sortOrder:2},
{name:'Elektronica',budgetCents:0,periodType:'MONTH',active:true,hiddenFromOverview:true,sortOrder:3},
{name:'Games / hobby',budgetCents:0,periodType:'MONTH',active:true,hiddenFromOverview:true,sortOrder:4},
{name:'Auto / motor',budgetCents:0,periodType:'MONTH',active:true,hiddenFromOverview:true,sortOrder:5},
{name:'Kleding',budgetCents:0,periodType:'MONTH',active:true,hiddenFromOverview:true,sortOrder:6},
{name:'Huishouden',budgetCents:0,periodType:'MONTH',active:true,hiddenFromOverview:true,sortOrder:7},
{name:'Anders',budgetCents:0,periodType:'MONTH',active:true,hiddenFromOverview:true,sortOrder:8}
];
const EXTRA_CATEGORIES=['Vaste lasten','Inkomen'];
const KEY='budgetAppPreviewV4';
const OLD_KEYS=['budgetAppPreviewV3','budgetAppStateV1'];
const MIGRATION_KEY='budgetAppSubjectPotsV4';
const native=typeof BudgetAppAndroid!=='undefined';
let state=null;
let activeScreen='home';
let txFilter={period:'ALL',category:''};
let toastTimer;
const euro=new Intl.NumberFormat('nl-NL',{style:'currency',currency:'EUR'});
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt=c=>euro.format((Number(c)||0)/100);
const num=v=>Math.round((Number(String(v??'').replace(/\./g,'').replace(',','.').replace(/[^\d.-]/g,''))||0)*100);
const uid=()=>Date.now()+Math.floor(Math.random()*1000000);
const now=()=>Date.now();

function seed(){
 return{
  dataVersion:4,
  pots:DEFAULT_POTS.map((p,i)=>({...p,id:i+1})),
  transactions:[],
  fixedCosts:[],
  goal:{id:1,name:'Spaardoel',currentCents:0,targetCents:3000000,active:true},
  unknown:[],
  knownBalanceCents:null,
  platform:'preview'
 };
}
function normalizeState(s){
 const x=s&&typeof s==='object'?s:seed();
 if(!Array.isArray(x.pots))x.pots=[];
 if(!Array.isArray(x.transactions))x.transactions=[];
 if(!Array.isArray(x.fixedCosts))x.fixedCosts=[];
 if(!Array.isArray(x.unknown))x.unknown=[];
 if(!x.goal)x.goal={id:1,name:'Spaardoel',currentCents:0,targetCents:3000000,active:true};
 x.pots=x.pots.map((p,i)=>({
  id:Number(p.id)||uid(),
  name:String(p.name||'').trim(),
  budgetCents:Math.max(0,Number(p.budgetCents)||0),
  periodType:p.periodType||'MONTH',
  active:p.active!==false,
  hiddenFromOverview:!!p.hiddenFromOverview,
  sortOrder:Number.isFinite(Number(p.sortOrder))?Number(p.sortOrder):i,
  spentCents:Number.isFinite(Number(p.spentCents))?Number(p.spentCents):undefined
 }));
 return x;
}
function previewLoad(){
 try{
  let raw=localStorage.getItem(KEY);
  if(!raw){
   for(const old of OLD_KEYS){
    raw=localStorage.getItem(old);
    if(raw)break;
   }
  }
  return raw?normalizeState(JSON.parse(raw)):seed();
 }catch{return seed()}
}
function previewSave(){
 state.dataVersion=4;
 localStorage.setItem(KEY,JSON.stringify(state));
}
function migrateSubjectPots(s){
 if(localStorage.getItem(MIGRATION_KEY)==='1')return s;
 const existing=new Set(s.pots.map(p=>p.name.trim().toLocaleLowerCase('nl-NL')));
 const missing=DEFAULT_POTS.filter(p=>!existing.has(p.name.toLocaleLowerCase('nl-NL')));
 if(native){
  for(const p of missing)BudgetAppAndroid.savePot(JSON.stringify({...p,id:0}));
  localStorage.setItem(MIGRATION_KEY,'1');
  try{return normalizeState(JSON.parse(BudgetAppAndroid.getState()))}catch{return s}
 }
 for(const p of missing)s.pots.push({...p,id:uid()});
 localStorage.setItem(MIGRATION_KEY,'1');
 previewSave();
 return s;
}
const Store={
 load(){if(native){try{return normalizeState(JSON.parse(BudgetAppAndroid.getState()))}catch(e){showToast('Opslagfout',String(e));return seed()}}return previewLoad()},
 savePot(p){
  if(native){BudgetAppAndroid.savePot(JSON.stringify(p));reload()}
  else{
   if(p.id){const i=state.pots.findIndex(x=>String(x.id)===String(p.id));if(i>=0)state.pots[i]=p;else{p.id=uid();state.pots.push(p)}}
   else{p.id=uid();state.pots.push(p)}
   previewSave();renderAll();
  }
 },
 deletePot(id){
  if(native){BudgetAppAndroid.deletePot(Number(id));reload()}
  else{
   state.pots=state.pots.filter(x=>String(x.id)!==String(id));
   state.transactions.forEach(t=>{if(String(t.potId)===String(id))t.potId=null});
   previewSave();renderAll();
  }
 },
 saveTx(t){
  if(native){BudgetAppAndroid.saveTransaction(JSON.stringify(t));reload()}
  else{
   if(t.id){const i=state.transactions.findIndex(x=>String(x.id)===String(t.id));if(i>=0)state.transactions[i]=t;else{t.id=uid();state.transactions.push(t)}}
   else{t.id=uid();state.transactions.push(t)}
   previewSave();renderAll();
  }
 },
 deleteTx(id){
  if(native){BudgetAppAndroid.deleteTransaction(Number(id));reload()}
  else{state.transactions=state.transactions.filter(x=>String(x.id)!==String(id));previewSave();renderAll()}
 },
 saveFixed(f){
  if(native){BudgetAppAndroid.saveFixedCost(JSON.stringify(f));reload()}
  else{
   if(f.id){const i=state.fixedCosts.findIndex(x=>String(x.id)===String(f.id));if(i>=0)state.fixedCosts[i]=f;else{f.id=uid();state.fixedCosts.push(f)}}
   else{f.id=uid();state.fixedCosts.push(f)}
   previewSave();renderAll();
  }
 },
 deleteFixed(id){
  if(native){BudgetAppAndroid.deleteFixedCost(Number(id));reload()}
  else{state.fixedCosts=state.fixedCosts.filter(x=>String(x.id)!==String(id));previewSave();renderAll()}
 },
 saveGoal(g){
  if(native){BudgetAppAndroid.saveGoal(JSON.stringify(g));reload()}
  else{state.goal={...g};previewSave();renderAll()}
 },
 assignUnknown(id,category,potId){
  if(native){BudgetAppAndroid.assignUnknown(Number(id),category,potId?Number(potId):0);reload()}
  else{
   const u=state.unknown.find(x=>String(x.id)===String(id));
   if(u){
    const tx=[...state.transactions].sort((a,b)=>b.occurredAt-a.occurredAt).find(t=>Math.abs(t.amountCents)===u.amountCents&&!t.category);
    if(tx){tx.category=category;tx.potId=potId||null}
   }
   state.unknown=state.unknown.filter(x=>String(x.id)!==String(id));
   previewSave();renderAll();
  }
 },
 receiptLines(id){if(native){try{return JSON.parse(BudgetAppAndroid.getReceiptLines(Number(id)))}catch{return[]}}return[]},
 saveReceiptLine(line){if(native){BudgetAppAndroid.saveReceiptLine(JSON.stringify(line));reload()}},
 scanReceipt(){if(native)BudgetAppAndroid.scanReceipt();else showToast('Alleen op Android','Bon scannen gebruikt de lokale camera en OCR van de Android-app.')},
 importPdf(){if(native)BudgetAppAndroid.pickPdf();else showToast('Alleen op Android','PDF import gebruikt lokale Android PDF-rendering en OCR.')},
 notificationSettings(){if(native)BudgetAppAndroid.openNotificationSettings();else showToast('Alleen op Android','Rabobank- en Wallet-notificaties worden lokaal op Android verwerkt.')}
};
function loadState(){state=migrateSubjectPots(Store.load())}
function reload(){loadState();renderAll()}

function range(period,time=now()){
 const d=new Date(time),s=new Date(d),e=new Date(d);
 s.setHours(0,0,0,0);e.setHours(23,59,59,999);
 if(period==='WEEK'){
  const n=(s.getDay()+6)%7;s.setDate(s.getDate()-n);e.setTime(s.getTime());e.setDate(e.getDate()+6);e.setHours(23,59,59,999);
 }else if(period==='SALARY_PERIOD'){
  if(s.getDate()>=23){s.setDate(23);e.setFullYear(s.getFullYear(),s.getMonth()+1,22)}
  else{e.setDate(22);s.setFullYear(s.getFullYear(),s.getMonth()-1,23)}
  e.setHours(23,59,59,999);
 }else if(period==='YEAR'){
  s.setMonth(0,1);e.setMonth(11,31);
 }else if(period==='ONCE')return[0,Number.MAX_SAFE_INTEGER];
 else{
  s.setDate(1);e.setFullYear(s.getFullYear(),s.getMonth()+1,0);e.setHours(23,59,59,999);
 }
 return[s.getTime(),e.getTime()];
}
function salaryInfo(){
 const[a,b]=range('SALARY_PERIOD'),s=new Date(a),e=new Date(b),today=new Date();
 const days=Math.max(0,Math.ceil((new Date(e.getFullYear(),e.getMonth(),e.getDate())-new Date(today.getFullYear(),today.getMonth(),today.getDate()))/86400000));
 const short=d=>d.toLocaleDateString('nl-NL',{day:'numeric',month:'short'}).replace('.','');
 return{range:`${short(s)} – ${short(e)}`,remain:days===0?'laatste dag':`nog ${days} dag${days===1?'':'en'}`};
}
function potSpent(p){
 if(Number.isFinite(Number(p.spentCents)))return Number(p.spentCents);
 const[a,b]=range(p.periodType);
 return state.transactions.filter(t=>String(t.potId)===String(p.id)&&!t.excludeFromPots&&t.amountCents<0&&t.occurredAt>=a&&t.occurredAt<=b).reduce((n,t)=>n+Math.abs(t.amountCents),0);
}
function balance(){return state.knownBalanceCents==null?state.transactions.filter(t=>t.affectsBalance!==false).reduce((n,t)=>n+t.amountCents,0):state.knownBalanceCents}
function monthStats(){
 const[a,b]=range('MONTH');
 const list=state.transactions.filter(t=>t.affectsBalance!==false&&t.occurredAt>=a&&t.occurredAt<=b);
 return{income:list.filter(t=>t.amountCents>0).reduce((n,t)=>n+t.amountCents,0),expense:list.filter(t=>t.amountCents<0).reduce((n,t)=>n+Math.abs(t.amountCents),0),list};
}
function trend(){
 const cutoff=now()-90*86400000;
 const net=state.transactions.filter(t=>t.affectsBalance!==false&&t.occurredAt>=cutoff).reduce((n,t)=>n+t.amountCents,0);
 return Math.max(0,Math.round(net/3));
}
function periodLabel(p){return{WEEK:'p/w',SALARY_PERIOD:'p/periode',MONTH:'p/mnd',YEAR:'p/jr',ONCE:'eenmalig'}[p]||p}
function periodLong(p){return{WEEK:'Per week',SALARY_PERIOD:'Per salarisperiode',MONTH:'Per maand',YEAR:'Per jaar',ONCE:'Eenmalig'}[p]||p}
function dateLabel(ms){return new Date(ms).toLocaleDateString('nl-NL',{day:'numeric',month:'short',year:'numeric'}).replace('.','')}
function timeLabel(ms){return new Date(ms).toLocaleTimeString('nl-NL',{hour:'2-digit',minute:'2-digit'})}
function initials(name){
 const s=(name||'?').replace(/[^A-Za-z0-9 ]/g,' ').trim().split(/\s+/).filter(Boolean);
 return(s.length?s.slice(0,2).map(x=>x[0]).join(''):'?').toUpperCase();
}
function potById(id){return state.pots.find(p=>String(p.id)===String(id))}
function categoryNames(selected=''){
 const names=[...state.pots.map(p=>p.name.trim()).filter(Boolean),...EXTRA_CATEGORIES];
 if(selected&&!names.includes(selected))names.push(selected);
 return[...new Set(names)];
}
function categoryOptions(selected=''){return categoryNames(selected).map(c=>`<option value="${esc(c)}" ${c===selected?'selected':''}>${esc(c)}</option>`).join('')}
function potOptions(selected=null,allowNone=true){
 const options=state.pots.filter(p=>p.active).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0)).map(p=>`<option value="${p.id}" ${String(p.id)===String(selected)?'selected':''}>${esc(p.name)}${p.budgetCents>0?'':' · budget instellen'}</option>`).join('');
 return`${allowNone?'<option value="">Geen potje</option>':''}${options}`;
}
function periodOptions(selected){return PERIODS.map(([v,n])=>`<option value="${v}" ${v===selected?'selected':''}>${n}</option>`).join('')}
function txRow(t){
 const name=t.merchant||t.description||'Transactie',unknown=!t.category&&t.amountCents<0;
 return`<div class="tx ${unknown?'unknown':''}" onclick="openTxDetail(${t.id})"><div class="txicon">${esc(initials(name))}</div><div><div class="txname">${esc(name)}</div><div class="txsub">${dateLabel(t.occurredAt)}${t.category?` · ${esc(t.category)}`:' · Nog indelen'}</div></div><div class="txamt ${t.amountCents>=0?'pos':'neg'}">${fmt(t.amountCents)}</div></div>`;
}
function potCard(p,i,si){
 const spent=potSpent(p);
 if(p.budgetCents<=0){
  return`<div class="card pot ${i>=2?'wide':''}" onclick="openPot(${p.id})"><div><div class="potname">${esc(p.name)} <span class="period">${periodLabel(p.periodType)}</span></div><div class="left">Budget instellen</div></div><div><div class="meta">${spent>0?`${fmt(spent)} geregistreerd · `:''}Tik om bedrag en periode in te stellen</div><div class="meter"><span style="width:0%"></span></div>${p.periodType==='SALARY_PERIOD'?`<div class="salaryperiod"><span>${si.range}</span><strong>${si.remain}</strong></div>`:''}</div></div>`;
 }
 const remain=p.budgetCents-spent,pc=Math.min(100,spent/p.budgetCents*100);
 return`<div class="card pot ${i>=2?'wide':''}" onclick="openPot(${p.id})"><div><div class="potname">${esc(p.name)} <span class="period">${periodLabel(p.periodType)}</span></div><div class="left ${remain<0?'neg':''}">${fmt(remain)}</div></div><div><div class="meta">${fmt(spent)} van ${fmt(p.budgetCents)} gebruikt${remain<0?' · budget overschreden':''}</div><div class="meter"><span style="width:${pc}%;${remain<0?'background:var(--red)':''}"></span></div>${p.periodType==='SALARY_PERIOD'?`<div class="salaryperiod"><span>${si.range}</span><strong>${si.remain}</strong></div>`:''}</div></div>`;
}
function renderHome(){
 const e=document.getElementById('home'),m=monthStats(),si=salaryInfo(),g=state.goal||{currentCents:0,targetCents:3000000,name:'Spaardoel'};
 const left=Math.max(0,g.targetCents-g.currentCents),pct=g.targetCents>0?Math.min(100,g.currentCents/g.targetCents*100):0,tr=trend(),months=left===0?'Doel bereikt':tr>0?`± ${Math.ceil(left/tr)} maanden`:'Nog geen schatting';
 const pots=state.pots.filter(p=>p.active&&!p.hiddenFromOverview).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0));
 const potHtml=pots.length?pots.map((p,i)=>potCard(p,i,si)).join(''):'<div class="card empty wide">Geen potjes zichtbaar op Home.</div>';
 const latest=[...state.transactions].sort((a,b)=>b.occurredAt-a.occurredAt).slice(0,3);
 e.innerHTML=`<div class="section card balance"><div class="eyebrow">${state.knownBalanceCents==null?'Totaal geregistreerd saldo':'Bekend banksaldo'}</div><div class="big">${fmt(balance())}</div><div class="grid2"><div class="mini"><small>Inkomsten deze maand</small><strong class="pos">+ ${fmt(m.income)}</strong></div><div class="mini"><small>Uitgaven deze maand</small><strong class="neg">- ${fmt(m.expense)}</strong></div></div></div><div class="section card goal"><div class="row"><div><div class="title">${esc(g.name||'Spaardoel')}</div><div class="sub">Doel ${fmt(g.targetCents)}</div></div><button class="link" onclick="event.stopPropagation();openGoal()">Aanpassen</button></div><div class="bar"><span style="width:${pct}%"></span></div><div class="goalfoot"><span>${fmt(g.currentCents)}</span><span>${fmt(left)} te gaan</span></div><div class="goalestimate"><span>Geschatte tijd bij huidige netto spaartrend</span><strong>${months}</strong></div></div><div class="section"><button class="cta" onclick="openSpend()">Wat wil je uitgeven?<small>Controleer vooraf tegen het actuele budget van een potje</small></button></div><div class="head"><div class="title">Budgetpotjes</div><button class="link" onclick="showScreen('budgets')">Alles bekijken</button></div><div class="periodbanner"><span>Salarisperiode</span><strong>${si.range} · ${si.remain}</strong></div><div class="pots">${potHtml}</div>${state.unknown.length?`<div class="section card alert" onclick="showScreen('review')"><div class="dot"></div><div class="grow"><strong>${state.unknown.length} item${state.unknown.length===1?'':'s'} nog indelen</strong><small>Tik om categorie en potje te kiezen</small></div><div class="badge">${state.unknown.length}</div></div>`:''}<div class="head"><div class="title">Laatste transacties</div><button class="link" onclick="showScreen('transactions')">Alles</button></div><div class="card txlist">${latest.length?latest.map(txRow).join(''):'<div class="empty">Nog geen transacties.</div>'}</div><div class="head"><div class="title">Snel toevoegen</div></div><div class="quick"><button onclick="Store.scanReceipt()"><b>▣</b>Bon scannen</button><button onclick="Store.importPdf()"><b>PDF</b>PDF importeren</button><button onclick="openTransaction()"><b>＋</b>Handmatig</button></div>`;
}
function renderBudgets(){
 const e=document.getElementById('budgets'),si=salaryInfo();
 const pots=[...state.pots].sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0));
 const costs=[...state.fixedCosts].sort((a,b)=>a.name.localeCompare(b.name));
 e.innerHTML=`<div class="head"><div class="title">Budgetten & potjes</div><button class="link" onclick="openPot()">Nieuw potje</button></div><div class="periodbanner"><span>Huidige salarisperiode</span><strong>${si.range} · ${si.remain}</strong></div><div class="card list">${pots.length?pots.map(p=>`<div class="setrow" onclick="openPot(${p.id})"><div><strong>${esc(p.name)}</strong><small>${p.budgetCents>0?`${fmt(p.budgetCents)} ${periodLong(p.periodType).toLowerCase()}`:`Nog geen budget · ${periodLong(p.periodType).toLowerCase()}`}${p.active?'':' · inactief'}${p.hiddenFromOverview?' · niet op Home':''}</small></div><div class="value">${p.budgetCents>0?fmt(p.budgetCents):'Instellen'}</div></div>`).join(''):'<div class="empty">Nog geen potjes.</div>'}</div><div class="head"><div class="title">Vaste lasten</div><button class="link" onclick="openFixed()">Toevoegen</button></div><div class="card list">${costs.length?costs.map(f=>`<div class="setrow" onclick="openFixed(${f.id})"><div><strong>${esc(f.name)}</strong><small>${periodLong(f.periodType)}${f.annualLevy?' · jaarlijkse heffing':''}${f.active?'':' · inactief'}</small></div><div class="value">${fmt(f.amountCents)}</div></div>`).join(''):'<div class="empty">Nog geen vaste lasten ingevoerd.</div>'}</div>`;
}
function filteredTransactions(){
 let list=[...state.transactions],t=now();
 if(txFilter.period!=='ALL'){const[a,b]=range(txFilter.period,t);list=list.filter(x=>x.occurredAt>=a&&x.occurredAt<=b)}
 if(txFilter.category)list=list.filter(x=>x.category===txFilter.category);
 return list.sort((a,b)=>b.occurredAt-a.occurredAt);
}
function renderTransactions(){
 const e=document.getElementById('transactions'),list=filteredTransactions();
 e.innerHTML=`<div class="head"><div class="title">Transacties</div><button class="link" onclick="openFilter()">Filter</button></div><button class="cta" onclick="openTransaction()">Handmatig toevoegen<small>Inkomst of uitgave lokaal opslaan</small></button><div class="section card txlist">${list.length?list.map(txRow).join(''):'<div class="empty">Geen transacties binnen dit filter.</div>'}</div>`;
}
function renderReview(){
 const e=document.getElementById('review');
 e.innerHTML=`<div class="head"><div class="title">Nog indelen</div><div class="badge">${state.unknown.length}</div></div><div class="card txlist">${state.unknown.length?state.unknown.map(u=>`<div class="tx unknown" onclick="openCategorize(${u.id})"><div class="txicon">?</div><div><div class="txname">${esc(u.displayText||'Onbekend')}</div><div class="txsub">${fmt(-Math.abs(u.amountCents))}</div></div><div class="badge">Kies</div></div>`).join(''):'<div class="empty">Alles is ingedeeld.</div>'}</div><div class="sub" style="margin-top:10px;line-height:1.5">Onbekende betalingen tellen mee in het saldo. Na jouw keuze worden onderwerp en potje lokaal onthouden.</div>`;
}
function renderOverview(){
 const e=document.getElementById('overview'),m=monthStats(),groups={};
 m.list.filter(t=>t.amountCents<0).forEach(t=>{const k=t.category||potById(t.potId)?.name||'Nog indelen';groups[k]=(groups[k]||0)+Math.abs(t.amountCents)});
 const rows=Object.entries(groups).sort((a,b)=>b[1]-a[1]);
 e.innerHTML=`<div class="head"><div class="title">Overzicht</div></div><div class="card balance"><div class="eyebrow">Deze maand</div><div class="big" style="font-size:31px">${fmt(m.income-m.expense)}</div><div class="grid2"><div class="mini"><small>Inkomsten</small><strong class="pos">${fmt(m.income)}</strong></div><div class="mini"><small>Uitgaven</small><strong class="neg">${fmt(m.expense)}</strong></div></div></div><div class="head"><div class="title">Uitgaven per onderwerp</div></div><div class="card list">${rows.length?rows.map(([k,v])=>`<div class="setrow"><div><strong>${esc(k)}</strong><small>${m.expense?Math.round(v/m.expense*100):0}%</small></div><div class="value">${fmt(v)}</div></div>`).join(''):'<div class="empty">Nog geen uitgaven deze maand.</div>'}</div>`;
}
function renderAll(){renderHome();renderBudgets();renderTransactions();renderReview();renderOverview()}
function showScreen(id){
 activeScreen=id;
 document.querySelectorAll('.screen').forEach(x=>x.classList.toggle('active',x.id===id));
 document.querySelectorAll('.nav button').forEach(x=>x.classList.remove('active'));
 const map={home:'nhome',budgets:'nbudget',transactions:'ntx',review:'ntx',overview:'nover'};
 document.getElementById(map[id])?.classList.add('active');
 scrollTo(0,0);
}
function modal(title,html){
 document.getElementById('modalTitle').textContent=title;
 document.getElementById('modalBody').innerHTML=html;
 document.getElementById('modal').classList.add('open');
}
function closeModal(){document.getElementById('modal').classList.remove('open')}
function showToast(title,text){
 document.getElementById('toastTitle').textContent=title;
 document.getElementById('toastText').textContent=text;
 const e=document.getElementById('toast');e.classList.add('show');
 clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.classList.remove('show'),3500);
}
function field(label,id,value='',type='text',extra=''){return`<div class="field"><label>${esc(label)}</label><input id="${id}" class="input" type="${type}" value="${esc(value)}" ${extra}></div>`}
function selectField(label,id,options,extra=''){return`<div class="field"><label>${esc(label)}</label><select id="${id}" class="select" ${extra}>${options}</select></div>`}

function openPot(id=null){
 const p=id?potById(id):null;
 modal(p?'Budgetpotje aanpassen':'Budgetpotje toevoegen',`${field('Naam','pName',p?.name||'')}${field('Budget','pBudget',p&&p.budgetCents>0?String((p.budgetCents/100).toFixed(2)).replace('.',','):'','text','inputmode="decimal" placeholder="Leeg of 0 = nog niet ingesteld"')}${selectField('Periode','pPeriod',periodOptions(p?.periodType||'MONTH'))}<label class="checkrow"><input id="pActive" type="checkbox" ${p?.active!==false?'checked':''}> Actief</label><label class="checkrow"><input id="pHidden" type="checkbox" ${p?.hiddenFromOverview?'checked':''}> Verbergen op Home</label><button class="primary" onclick="savePot(${p?.id||'null'})">Opslaan</button>${p?'<button class="dangerbtn" onclick="removePot('+p.id+')">Verwijderen</button>':''}`);
}
function savePot(id){
 const name=document.getElementById('pName').value.trim();
 if(!name){showToast('Controleer invoer','Naam is verplicht.');return}
 const budget=Math.max(0,Math.abs(num(document.getElementById('pBudget').value)));
 const old=id?potById(id):null;
 Store.savePot({id:old?.id||0,name,budgetCents:budget,periodType:document.getElementById('pPeriod').value,active:document.getElementById('pActive').checked,hiddenFromOverview:document.getElementById('pHidden').checked,sortOrder:old?.sortOrder??state.pots.length});
 closeModal();
}
function removePot(id){
 if(!confirm('Dit potje verwijderen? Transacties blijven bestaan maar worden losgekoppeld.'))return;
 Store.deletePot(id);closeModal();
}
function openFixed(id=null){
 const f=id?state.fixedCosts.find(x=>String(x.id)===String(id)):null;
 modal(f?'Vaste last aanpassen':'Vaste last toevoegen',`${field('Naam','fName',f?.name||'')}${field('Bedrag','fAmount',f?String((f.amountCents/100).toFixed(2)).replace('.',','):'','text','inputmode="decimal"')}${selectField('Periode','fPeriod',periodOptions(f?.periodType||'MONTH'))}${field('Vervaldag (1-31)','fDue',f?.dueDay||1,'number','min="1" max="31"')}<label class="checkrow"><input id="fActive" type="checkbox" ${f?.active!==false?'checked':''}> Actief</label><label class="checkrow"><input id="fAnnual" type="checkbox" ${f?.annualLevy?'checked':''}> Jaarlijkse heffing</label><button class="primary" onclick="saveFixed(${f?.id||'null'})">Opslaan</button>${f?'<button class="dangerbtn" onclick="removeFixed('+f.id+')">Verwijderen</button>':''}`);
}
function saveFixed(id){
 const name=document.getElementById('fName').value.trim(),amount=Math.abs(num(document.getElementById('fAmount').value)),due=Math.max(1,Math.min(31,Number(document.getElementById('fDue').value)||1));
 if(!name||!amount){showToast('Controleer invoer','Naam en bedrag zijn verplicht.');return}
 Store.saveFixed({id:id||0,name,amountCents:amount,periodType:document.getElementById('fPeriod').value,dueDay:due,active:document.getElementById('fActive').checked,annualLevy:document.getElementById('fAnnual').checked});
 closeModal();
}
function removeFixed(id){
 if(!confirm('Deze vaste last verwijderen?'))return;
 Store.deleteFixed(id);closeModal();
}
function openGoal(){
 const g=state.goal||{name:'Spaardoel',currentCents:0,targetCents:3000000};
 modal('Spaardoel aanpassen',`${field('Naam','gName',g.name||'Spaardoel')}${field('Huidige stand','gCurrent',String((g.currentCents/100).toFixed(2)).replace('.',','),'text','inputmode="decimal"')}${field('Doelbedrag','gTarget',String((g.targetCents/100).toFixed(2)).replace('.',','),'text','inputmode="decimal"')}<button class="primary" onclick="saveGoal()">Opslaan</button>`);
}
function saveGoal(){
 const name=document.getElementById('gName').value.trim()||'Spaardoel',current=Math.abs(num(document.getElementById('gCurrent').value)),target=Math.abs(num(document.getElementById('gTarget').value));
 if(!target){showToast('Controleer invoer','Doelbedrag moet groter dan nul zijn.');return}
 Store.saveGoal({id:state.goal?.id||0,name,currentCents:current,targetCents:target,active:true});
 closeModal();
}
function openTransaction(id=null){
 const t=id?state.transactions.find(x=>String(x.id)===String(id)):null,isIncome=t?t.amountCents>=0:false,currentPot=t?.potId||null;
 modal(t?'Transactie aanpassen':'Handmatige transactie',`${selectField('Type','tType',`<option value="EXPENSE" ${!isIncome?'selected':''}>Uitgave</option><option value="INCOME" ${isIncome?'selected':''}>Inkomst</option>`)}${field('Naam / winkel','tMerchant',t?.merchant||'')}${field('Omschrijving','tDesc',t?.description||'')}${field('Bedrag','tAmount',t?String((Math.abs(t.amountCents)/100).toFixed(2)).replace('.',','):'','text','inputmode="decimal"')}${selectField('Onderwerp','tCategory',`<option value="">Nog indelen</option>${categoryOptions(t?.category||'')}`)}${selectField('Budgetpotje','tPot',potOptions(currentPot,true),'onchange="syncTransactionPot()"')}${field('Datum','tDate',new Date(t?.occurredAt||now()).toISOString().slice(0,10),'date')}${field('Tijd','tTime',timeLabel(t?.occurredAt||now()),'time')}<button class="primary" onclick="saveTransaction(${t?.id||'null'})">Opslaan</button>${t?'<button class="dangerbtn" onclick="removeTransaction('+t.id+')">Verwijderen</button>':''}`);
}
function syncTransactionPot(){
 const p=potById(document.getElementById('tPot')?.value),c=document.getElementById('tCategory');
 if(p&&c&&!c.value)c.value=p.name;
}
function saveTransaction(id){
 const amount=Math.abs(num(document.getElementById('tAmount').value));
 if(!amount){showToast('Controleer invoer','Bedrag is verplicht.');return}
 const type=document.getElementById('tType').value,date=document.getElementById('tDate').value,time=document.getElementById('tTime').value||'00:00',stamp=new Date(`${date}T${time}:00`).getTime(),old=id?state.transactions.find(x=>String(x.id)===String(id)):null,potId=document.getElementById('tPot').value?Number(document.getElementById('tPot').value):null;
 let category=document.getElementById('tCategory').value;
 if(!category&&potId)category=potById(potId)?.name||'';
 if(type==='INCOME'&&!category)category='Inkomen';
 Store.saveTx({...(old||{}),id:old?.id||0,source:old?.source||'MANUAL',importedAt:old?.importedAt||now(),occurredAt:Number.isFinite(stamp)?stamp:now(),amountCents:type==='INCOME'?amount:-amount,merchant:document.getElementById('tMerchant').value.trim(),description:document.getElementById('tDesc').value.trim(),category,potId,cardReference:old?.cardReference||'',bankReference:old?.bankReference||'',dateText:'',timeText:'',dedupeKey:old?.dedupeKey||'',affectsBalance:old?.affectsBalance!==false,excludeFromPots:old?.excludeFromPots||false,matchedBankTransactionId:old?.matchedBankTransactionId||null,balanceAfterCents:old?.balanceAfterCents||null});
 closeModal();
}
function removeTransaction(id){
 if(!confirm('Deze transactie verwijderen?'))return;
 Store.deleteTx(id);closeModal();
}
function openTxDetail(id){
 const t=state.transactions.find(x=>String(x.id)===String(id));if(!t)return;
 const lines=Store.receiptLines(id),rows=[['Bedrag',fmt(t.amountCents)],['Categorie',t.category],['Budgetpotje',potById(t.potId)?.name||''],['Datum',dateLabel(t.occurredAt)],['Tijd',timeLabel(t.occurredAt)],['Bron',t.source],['Omschrijving',t.description],['Kaart',t.cardReference],['Referentie',t.bankReference]].filter(x=>x[1]);
 modal(t.merchant||'Transactie',`<div class="detail">${rows.map(([k,v])=>`<div class="drow"><span>${esc(k)}</span><span>${esc(v)}</span></div>`).join('')}</div>${lines.length?`<div class="head"><div class="title">Bonregels</div></div><div class="card" style="padding:0 12px">${lines.map(l=>`<div class="receiptline" onclick="openReceiptLine(${l.id},${id})"><strong>${esc(l.description)}</strong><small>${fmt(l.amountCents)}${l.category?` · ${esc(l.category)}`:' · Nog indelen'}${l.potId&&potById(l.potId)?` · ${esc(potById(l.potId).name)}`:''}</small></div>`).join('')}</div>`:''}<button class="ghost" onclick="openTransaction(${id})">Transactie bewerken</button>`);
}
function openReceiptLine(lineId,txId){
 const l=Store.receiptLines(txId).find(x=>String(x.id)===String(lineId));if(!l)return;
 modal('Bonregel indelen',`<div class="mini"><strong>${esc(l.description)}</strong><small>${fmt(l.amountCents)}</small></div>${selectField('Onderwerp','rlCategory',categoryOptions(l.category||''))}${selectField('Budgetpotje','rlPot',potOptions(l.potId,true),'onchange="syncCategoryFromPot(\'rlPot\',\'rlCategory\')"')}<label class="checkrow"><input id="rlLearn" type="checkbox" checked> Deze productomschrijving voortaan onthouden</label><button class="primary" onclick="saveReceiptLine(${lineId},${txId})">Opslaan</button>`);
}
function saveReceiptLine(id,txId){
 const potId=document.getElementById('rlPot').value?Number(document.getElementById('rlPot').value):null;
 let category=document.getElementById('rlCategory').value;
 if(!category&&potId)category=potById(potId)?.name||'';
 Store.saveReceiptLine({id,transactionId:txId,category,potId,learn:document.getElementById('rlLearn').checked});
 closeModal();
}
function syncCategoryFromPot(potSelectId,categorySelectId){
 const p=potById(document.getElementById(potSelectId)?.value),c=document.getElementById(categorySelectId);
 if(p&&c)c.value=p.name;
}
function openCategorize(id){
 const u=state.unknown.find(x=>String(x.id)===String(id));if(!u)return;
 modal('Indelen',`<div class="mini"><strong>${esc(u.displayText||'Onbekend')}</strong><small>${fmt(-Math.abs(u.amountCents))}</small></div>${selectField('Onderwerp','uCategory',categoryOptions('Anders'))}${selectField('Budgetpotje','uPot',potOptions(null,true),'onchange="syncCategoryFromPot(\'uPot\',\'uCategory\')"')}<div class="hint">Deze keuze wordt lokaal onthouden voor dezelfde winkel of hetzelfde bonitem.</div><button class="primary" onclick="saveUnknown(${id})">Opslaan</button>`);
}
function saveUnknown(id){
 const potId=document.getElementById('uPot').value?Number(document.getElementById('uPot').value):null;
 let category=document.getElementById('uCategory').value;
 if(!category&&potId)category=potById(potId)?.name||'';
 Store.assignUnknown(id,category,potId);closeModal();
}
function openSpend(){
 const active=state.pots.filter(p=>p.active).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0));
 modal('Wat wil je uitgeven?',`${selectField('Onderwerp / potje','sPot',potOptions(active[0]?.id,false))}${field('Bedrag','sAmount','','text','inputmode="decimal"')}<button class="primary" onclick="checkSpend()">Controleren</button><div id="spendResult" class="result"><strong id="spendTitle"></strong><small id="spendText"></small></div>`);
}
function checkSpend(){
 const amount=Math.abs(num(document.getElementById('sAmount').value)),p=potById(document.getElementById('sPot').value),r=document.getElementById('spendResult'),t=document.getElementById('spendTitle'),x=document.getElementById('spendText');
 if(!amount||!p){r.className='result show no';t.textContent='Controleer de invoer';x.textContent='Kies een potje en vul een bedrag in.';return}
 if(p.budgetCents<=0){r.className='result show no';t.textContent='Budget nog niet ingesteld';x.textContent=`Stel eerst een budget en periode in voor ${p.name}.`;return}
 const remaining=p.budgetCents-potSpent(p),after=remaining-amount;
 if(after>=0){r.className='result show ok';t.textContent='Past binnen dit budget';x.textContent=`Na ${fmt(amount)} blijft in ${p.name} ${fmt(after)} over.`}
 else{r.className='result show warn';t.textContent='Budget wordt overschreden';x.textContent=`Dit gaat ${fmt(Math.abs(after))} over het actuele budget van ${p.name}.`}
}
function openFilter(){
 modal('Filter',`${selectField('Periode','filterPeriod',`<option value="ALL" ${txFilter.period==='ALL'?'selected':''}>Alles</option><option value="WEEK" ${txFilter.period==='WEEK'?'selected':''}>Deze week</option><option value="MONTH" ${txFilter.period==='MONTH'?'selected':''}>Deze maand</option><option value="YEAR" ${txFilter.period==='YEAR'?'selected':''}>Dit jaar</option>`)}${selectField('Onderwerp','filterCategory',`<option value="">Alle onderwerpen</option>${categoryOptions(txFilter.category)}`)}<button class="primary" onclick="applyFilter()">Toepassen</button>`);
}
function applyFilter(){
 txFilter={period:document.getElementById('filterPeriod').value,category:document.getElementById('filterCategory').value};
 closeModal();renderTransactions();showScreen('transactions');
}
function openSettings(){
 modal('Instellingen',`<div class="card list" style="margin-top:12px"><div class="setrow"><div><strong>Financiële periode</strong><small>Van salaris tot salaris</small></div><div class="value">23e → 22e</div></div><div class="setrow"><div><strong>Weekbudget reset</strong><small>Weekpotjes</small></div><div class="value">Maandag</div></div><div class="setrow"><div><strong>Lokale opslag</strong><small>${native?'Room/SQLite op Android':'Browseropslag alleen voor Pages-preview'}</small></div><div class="pos">Actief</div></div></div><button class="ghost" onclick="Store.notificationSettings()">Notificatietoegang Rabobank / Wallet</button><div class="hint">Alle bedragen, periodes, namen en zichtbaarheid van potjes zijn vrij aanpasbaar. GitHub Pages is alleen de tijdelijke interfacepreview; de Android-app bewaart financiële gegevens lokaal.</div>`);
}
window.BudgetAppNative={
 refresh(){reload()},
 onPdfImport(json){reload();try{const r=JSON.parse(json);showToast('PDF geïmporteerd',`${r.added} toegevoegd · ${r.skipped} dubbelen overgeslagen${r.balanceChecked?` · saldo ${r.balanceValid?'klopt':'wijkt af'}`:''}`)}catch{showToast('PDF geïmporteerd','Import afgerond.')}},
 onPdfError(msg){showToast('PDF import mislukt',msg)},
 onReceiptImport(json){reload();try{const r=JSON.parse(json);showToast('Bon opgeslagen',`${r.merchant||'Bon'} · ${fmt(-Math.abs(r.totalCents||0))} · ${r.lines||0} regels`)}catch{showToast('Bon opgeslagen','Bon is lokaal verwerkt.')}},
 onReceiptError(msg){showToast('Bon scannen mislukt',msg)},
 openReview(){reload();showScreen('review')},
 handleBack(){
  if(document.getElementById('modal').classList.contains('open')){closeModal();return true}
  if(activeScreen!=='home'){showScreen('home');return true}
  if(native){BudgetAppAndroid.finishApp();return true}
  return false;
 }
};
document.querySelectorAll('.nav button').forEach(b=>b.addEventListener('click',()=>showScreen(b.dataset.screen)));
document.getElementById('settingsButton').onclick=openSettings;
document.getElementById('modalClose').onclick=closeModal;
document.getElementById('modal').addEventListener('click',e=>{if(e.target.id==='modal')closeModal()});
loadState();
renderAll();
showScreen('home');
