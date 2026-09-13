'use strict';
// Os mesmos componentes de tela atendem aos quatro cadastros.
const definitions = {
  clientes: {
    title:'Clientes', singular:'cliente', description:'Pessoas que confiam na sua oficina.', icon:'◉', hint:'Organize os contatos e dados de atendimento.', columns:[['nome','Nome'],['cpf','CPF'],['telefone','Telefone'],['email','E-mail']], fields:[
    ['nome','Nome completo','text',true,120,'wide'],['cpf','CPF','text',true,11,'','Somente os 11 números.'],['telefone','Telefone','tel',false,20],['email','E-mail','email',false,160],['endereco','Endereço','text',false,255,'wide']]
  }
  ,
  motocicletas: {
    title:'Motocicletas',singular:'motocicleta',description:'As motocicletas atendidas pela sua oficina.',icon:'◇',hint:'Tenha as informações de cada moto à mão.',columns:[['placa','Placa'],['marca','Marca'],['modelo','Modelo'],['ano','Ano'],['cor','Cor']],fields:[
    ['placa','Placa','text',true,7,'','Sem hífen. Ex.: ABC1D23'],['chassi','Chassi','text',true,17,'','Informe os 17 caracteres.'],['marca','Marca','text',true,80],['modelo','Modelo','text',true,100],['ano','Ano','number',true,null,'','Entre 1900 e 2100.'],['cor','Cor','text',false,50]]
  }
  ,
  produtos: {
    title:'Produtos',singular:'produto',description:'Seu catálogo de peças e produtos, sempre organizado.',icon:'▧',hint:'Consulte os itens e seus preços de venda.',columns:[['codigo','Código'],['descricao','Descrição'],['precoVenda','Preço de venda','money']],fields:[
    ['codigo','Código do produto','text',true,40],['precoVenda','Preço de venda (R$)','number',true,null],['descricao','Descrição','text',true,180,'wide']]
  }
  ,
  servicos: {
    title:'Serviços',singular:'serviço',description:'Os serviços que a sua oficina oferece.',icon:'⚒',hint:'Mantenha os serviços e valores atualizados.',columns:[['nome','Serviço'],['descricao','Descrição'],['preco','Preço','money']],fields:[
    ['nome','Nome do serviço','text',true,120,'wide'],['preco','Preço (R$)','number',true,null],['descricao','Descrição','text',false,255,'wide']]
  }
}
;
const $ = id => document.getElementById(id);
const section = document.body.dataset.secao;
const money = new Intl.NumberFormat('pt-BR', {
  style:'currency',currency:'BRL'
}
);
let records = [], editingId = null, loadFailed = false, loading = false, saving = false;
const dialog = $('record-dialog');
dialog.addEventListener('cancel', event => {
  if (saving) event.preventDefault();
}
);
const notice = (message, error=false) => {
  $('notice').textContent=message;
  $('notice').classList.toggle('error',error);
  $('notice').hidden=false;
}
;
async function api(url, options= {
}
) {
  const headers = new Headers(options.headers || {
  }
  );
  headers.set('Accept','application/json');
  if (options.body) headers.set('Content-Type','application/json');
  headers.set(document.querySelector('meta[name="csrf-header"]').content,document.querySelector('meta[name="csrf-token"]').content);
  let response;
  try {
    response = await fetch(url, {
      ...options,headers,credentials:'same-origin'
    }
    );
  }
  catch (_) {
    throw {
      mensagem:'Não foi possível conectar. Confira se a aplicação está aberta e tente novamente.'
    }
    ;
  }
  if (response.status === 401 || response.redirected) {
    window.location.assign('/login');
    throw {
      mensagem:'Sua sessão terminou. Entre novamente.'
    }
    ;
  }
  let data;
  try {
    data = await response.json();
  }
  catch (_) {
    data= {
    }
    ;
  }
  if (!response.ok) throw data.mensagem ? data : {
    mensagem: response.status===403 ? 'Acesso não permitido ou sessão expirada. Entre novamente.' : 'Não foi possível concluir. Tente novamente.'
  }
  ;
  return data;
}
function buildDashboard() {
  $('dashboard').hidden=false;
  Object.entries(definitions).forEach(([key,def])=> {
    const card=document.createElement('a');
    card.className='module-card';
    card.href='/'+key;
    const top=document.createElement('div');
    top.className='module-top';
    const symbol=document.createElement('span');
    symbol.className='module-symbol';
    symbol.textContent=def.icon;
    const arrow=document.createElement('span');
    arrow.className='module-arrow';
    arrow.textContent='↗';
    arrow.setAttribute('aria-hidden','true');
    top.append(symbol,arrow);
    const count=document.createElement('strong');
    count.textContent='…';
    const title=document.createElement('h3');
    title.textContent=def.title;
    const hint=document.createElement('p');
    hint.textContent=def.hint;
    card.append(top,count,title,hint);
    $('module-grid').append(card);
    api('/api/'+key).then(data=> {
      count.textContent=data.length;
    }
    ).catch(error=> {
      count.textContent='—';
      notice(error.mensagem,true);
    }
    );
  }
  );
}
function addField(field) {
  const [name,label,type,required,maxLength,width,help]=field;
  const wrap=document.createElement('div');
  wrap.className='form-field'+(width==='wide'?' wide':'');
  const labelEl=document.createElement('label');
  labelEl.htmlFor='field-'+name;
  labelEl.textContent=label;
  if(required) {
    const star=document.createElement('span');
    star.className='required';
    star.textContent=' *';
    labelEl.append(star);
  }
  const input=document.createElement('input');
  input.id='field-'+name;
  input.name=name;
  input.type=type;
  input.required=required;
  input.autocomplete='off';
  if(maxLength)input.maxLength=maxLength;
  if(type==='number') {
    input.min=name==='ano'?'1900':'0';
    input.max=name==='ano'?'2100':'999999999999.99';
    input.step=name==='ano'?'1':'0.01';
  }
  if(name==='cpf') {
    input.pattern='[0-9]{11}';
    input.inputMode='numeric';
  }
  if(name==='placa')input.pattern='[A-Za-z0-9]{7}';
  if(name==='chassi')input.pattern='[A-Za-z0-9]{17}';
  const err=document.createElement('span');
  err.className='field-error';
  err.id='error-'+name;
  input.setAttribute('aria-describedby',err.id+(help?' help-'+name:''));
  wrap.append(labelEl,input);
  if(help) {
    const hint=document.createElement('small');
    hint.id='help-'+name;
    hint.className='field-help';
    hint.textContent=help;
    wrap.append(hint);
  }
  wrap.append(err);
  $('form-fields').append(wrap);
}
function openForm(record=null) {
  if (saving) return;
  editingId=record ? record.id : null;
  $('record-form').reset();
  $('form-error').hidden=true;
  document.querySelectorAll('.field-error').forEach(el=>el.textContent='');
  definitions[section].fields.forEach(([name])=> {
    const input=$('field-'+name);
    input.removeAttribute('aria-invalid');
    input.value=record?.[name] ?? '';
  }
  );
  $('form-title').textContent=(record?'Editar ':'Novo cadastro de ')+definitions[section].singular;
  dialog.showModal();
  $('form-fields').querySelector('input').focus();
}
function renderRows() {
  const def=definitions[section];
  const term=$('search').value.trim().toLocaleLowerCase('pt-BR');
  const filtered=records.filter(record => def.columns.some(([key]) => String(record[key]??'').toLocaleLowerCase('pt-BR').includes(term)));
  $('table-body').replaceChildren();
  filtered.forEach(record=> {
    const tr=document.createElement('tr');
    def.columns.forEach(([key,label,format])=> {
      const td=document.createElement('td');
      const value=record[key];
      td.textContent=format==='money'?money.format(value):(value||'—');
      tr.append(td);
    }
    );
    const td=document.createElement('td');
    const actions=document.createElement('div');
    actions.className='row-actions';
    const edit=document.createElement('button');
    edit.type='button';
    edit.className='row-action';
    edit.textContent='Editar';
    edit.setAttribute('aria-label','Editar '+(record.nome||record.placa||record.codigo));
    edit.addEventListener('click',()=>openForm(record));
    actions.append(edit);
    td.append(actions);
    tr.append(td);
    $('table-body').append(tr);
  }
  );
  $('empty-state').hidden=filtered.length>0 || loading || loadFailed;
  const isFiltered=term;
  $('empty-title').textContent=isFiltered?'Nenhum resultado encontrado':'Seu primeiro cadastro começa aqui';
  $('empty-description').textContent=isFiltered?'Tente outro termo ou ajuste o filtro.':'Adicione um registro para começar a organizar sua oficina.';
  $('empty-add').hidden=!!isFiltered;
  $('record-count').textContent=loadFailed?'Não foi possível carregar os registros':`${filtered.length} de ${records.length} registro${records.length===1?'':'s'}`;
}
async function loadRecords() {
  loading=true;
  loadFailed=false;
  $('loading').hidden=false;
  $('empty-state').hidden=true;
  $('refresh').disabled=true;
  try {
    records=await api('/api/'+section);
  }
  catch(error) {
    loadFailed=true;
    notice(error.mensagem,true);
  }
  finally {
    loading=false;
    $('loading').hidden=true;
    $('refresh').disabled=false;
    renderRows();
  }
}
async function saveRecord(event) {
  event.preventDefault();
  const form=event.target;
  $('form-error').hidden=true;
  document.querySelectorAll('.field-error').forEach(el=>el.textContent='');
  definitions[section].fields.forEach(([name])=>$('field-'+name).removeAttribute('aria-invalid'));
  if(!form.reportValidity())return;
  const data= {
  }
  ;
  definitions[section].fields.forEach(([name,label,type])=> {
    const value=$('field-'+name).value.trim();
    data[name]=type==='number'?(value===''?null:Number(value)):value;
  }
  );
  const button=$('save-record');
  saving=true;
  button.disabled=true;
  $('close-dialog').disabled=true;
  $('cancel-dialog').disabled=true;
  button.textContent='Salvando…';
  try {
    await api('/api/'+section+(editingId?'/'+editingId:''), {
      method:editingId?'PUT':'POST',body:JSON.stringify(data)
    }
    );
    dialog.close();
    notice('Cadastro salvo com sucesso.');
    await loadRecords();
  }
  catch(error) {
    $('form-error').textContent=error.mensagem;
    $('form-error').hidden=false;
    Object.entries(error.campos|| {
    }
    ).forEach(([name,message])=> {
      if($('error-'+name)) {
        $('error-'+name).textContent=message;
        $('field-'+name).setAttribute('aria-invalid','true');
      }
    }
    );
    const invalid=dialog.querySelector('[aria-invalid="true"]');
    if(invalid)invalid.focus();
    else $('form-error').scrollIntoView( {
      block:'nearest'
    }
    );
  }
  finally {
    saving=false;
    button.disabled=false;
    $('close-dialog').disabled=false;
    $('cancel-dialog').disabled=false;
    button.textContent='Salvar cadastro';
  }
}
$('menu-toggle').addEventListener('click',()=> {
  const expanded=$('sidebar').classList.toggle('open');
  $('menu-toggle').setAttribute('aria-expanded',expanded);
  $('menu-toggle').setAttribute('aria-label',expanded?'Fechar menu':'Abrir menu');
}
);
document.addEventListener('keydown',event=> {
  if(event.key==='Escape') {
    $('sidebar').classList.remove('open');
    $('menu-toggle').setAttribute('aria-expanded','false');
  }
}
);
if(section==='inicio') {
  buildDashboard();
}
else {
  const def=definitions[section];
  document.title=def.title+' · OFICYNIC';
  $('breadcrumb').textContent=def.title;
  $('page-title').textContent=def.title;
  $('eyebrow').textContent='CADASTROS DA OFICINA';
  $('page-description').textContent=def.description;
  $('listing').hidden=false;
  $('new-record').hidden=false;
  $('new-label').textContent='Cadastrar '+def.singular;
  const tr=document.createElement('tr');
  [...def.columns,['acoes','Ações']].forEach(([,label])=> {
    const th=document.createElement('th');
    th.scope='col';
    th.textContent=label;
    tr.append(th);
  }
  );
  $('table-head').append(tr);
  def.fields.forEach(addField);
  if(def.note)$('form-description').textContent+=' '+def.note;
  $('new-record').addEventListener('click',()=>openForm());
  $('empty-add').addEventListener('click',()=>openForm());
  $('search').addEventListener('input',renderRows);
  $('refresh').addEventListener('click',loadRecords);
  $('record-form').addEventListener('submit',saveRecord);
  $('close-dialog').addEventListener('click',()=>dialog.close());
  $('cancel-dialog').addEventListener('click',()=>dialog.close());
  loadRecords();
}
