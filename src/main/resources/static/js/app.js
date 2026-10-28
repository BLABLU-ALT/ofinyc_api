'use strict';
// Os mesmos componentes de tela atendem aos quatro cadastros.
const definitions = {
  'ordens-servico': {
    title:'Ordens de serviço', icon:'▤', hint:'Abra e consulte os atendimentos.', api:'/api/ordens-servico/consulta'
  },
  clientes: {
    title:'Clientes', singular:'cliente', description:'Pessoas que confiam na sua oficina.', icon:'◉', hint:'Organize os contatos e dados de atendimento.', columns:[['id','Código'],['nome','Nome'],['cpf','CPF'],['telefone','Telefone'],['email','E-mail']], fields:[
    ['nome','Nome completo','text',true,120,'wide'],['cpf','CPF','text',true,11,'','Somente os 11 números.'],['telefone','Telefone','tel',false,20],['email','E-mail','email',false,160],['endereco','Endereço','text',false,255,'wide']]
  }
  ,
  motocicletas: {
    title:'Motocicletas',singular:'motocicleta',description:'As motocicletas atendidas pela sua oficina.',icon:'◇',hint:'Tenha as informações de cada moto à mão.',note:'O proprietário pode ser vinculado agora ou depois. Informe a placa ou o chassi (ou os dois).',columns:[['clienteNome','Proprietário'],['placa','Placa'],['chassi','Chassi'],['marca','Marca'],['modelo','Modelo'],['ano','Ano'],['cor','Cor']],fields:[
    ['clienteId','Cliente proprietário','select',false],
    ['placa','Placa','text',false,7,'','Sem hífen. Ex.: ABC1D23'],['chassi','Chassi','text',false,17,'','Informe os 17 caracteres.'],['marca','Marca','text',true,80,'','Digite para buscar e selecione uma sugestão.'],['modelo','Modelo','text',true,100,'','Sugestões da marca escolhida. Se não encontrar, digite o modelo.'],['ano','Ano','number',true,null,'','Entre 1900 e 2100.'],['cor','Cor','text',false,50]]
  }
  ,
  produtos: {
    title:'Produtos',singular:'produto',description:'Seu catálogo de peças e produtos, sempre organizado.',icon:'▧',hint:'Consulte os itens, unidades e saldos.',searchKeys:['codigo','descricao'],searchPlaceholder:'Pesquisar por código ou descrição',stockFilter:true,defaultSort:'codigo:asc',sorts:[['codigo:asc','Código (A–Z)'],['codigo:desc','Código (Z–A)'],['descricao:asc','Descrição (A–Z)'],['descricao:desc','Descrição (Z–A)'],['precoVenda:asc','Menor preço'],['precoVenda:desc','Maior preço']],columns:[['codigo','Código'],['descricao','Descrição'],['unidadeMedida','Unidade'],['estoque','Estoque físico'],['empenho','Empenho'],['saldo','Disponível'],['precoVenda','Preço de venda','money']],fields:[
    ['codigo','Código do produto','text',true,40],['precoVenda','Preço de venda (R$)','number',true,null],['unidadeMedida','Unidade de medida','select',true,null,'','Selecione a unidade utilizada no produto.'],['descricao','Descrição','text',true,180,'wide']]
  }
  ,
  funcionarios: {
    title:'Funcionários',singular:'funcionário',description:'Responsáveis pelos atendimentos e movimentações.',icon:'◉',hint:'Cadastre os responsáveis.',searchKeys:['nome'],searchPlaceholder:'Pesquisar por nome',defaultSort:'nome:asc',sorts:[['nome:asc','Nome (A–Z)'],['nome:desc','Nome (Z–A)']],columns:[['id','Código'],['nome','Nome']],fields:[['nome','Nome completo','text',true,120,'wide']]
  },
  servicos: {
    title:'Serviços',singular:'serviço',description:'Os serviços que a sua oficina oferece.',icon:'⚒',hint:'Mantenha os serviços e valores atualizados.',searchKeys:['id','nome','descricao'],searchPlaceholder:'Pesquisar por código, nome ou descrição',defaultSort:'nome:asc',sorts:[['nome:asc','Nome (A–Z)'],['nome:desc','Nome (Z–A)'],['preco:asc','Menor valor'],['preco:desc','Maior valor']],columns:[['id','Código'],['nome','Serviço'],['descricao','Descrição'],['preco','Preço','money']],fields:[
    ['nome','Nome do serviço','text',true,120,'wide'],['preco','Preço (R$)','number',true,null],['descricao','Descrição','text',false,255,'wide']]
  }
}
;
const $ = id => document.getElementById(id);
const section = document.body.dataset.secao;
const unidadesMedida = [['PC','Peça'],['MT','Metro'],['LT','Litro'],['KG','Quilograma'],['PAR','Par'],['JG','Jogo'],['KT','Kit']];
const money = new Intl.NumberFormat('pt-BR', {
  style:'currency',currency:'BRL'
}
);
let records = [], editingId = null, loadFailed = false, loading = false, saving = false;
let somenteConsulta = false;
const filtrosColuna = {};
const secoesComFerramentas=[];
const secoesComFiltrosColuna=['clientes','motocicletas','produtos','funcionarios','servicos'];
let paginaAtual=1,tamanhoPagina=10,ordenacaoLista='',filtroEstoque='';
let aberturaCadastro = 0;
let buscaProprietario = null;
const dialog = $('record-dialog');
dialog.addEventListener('close',()=>aberturaCadastro++);
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
  $('breadcrumb').textContent='Visão geral';
  $('page-title').textContent='Visão geral';
  $('eyebrow').textContent='PAINEL DA OFICINA';
  $('page-description').textContent='Acompanhe as ordens de serviço e acesse os principais fluxos.';
  document.title='Visão geral · OFICYNIC';
  const rotulos={ORCAMENTO:'Orçamento',EM_ANDAMENTO:'Em andamento',CONCLUIDA:'Concluída',CANCELADA:'Cancelada',RETRABALHO:'Retrabalho'};
  let ordensDashboard=null;
  const dataLocal=valor=>valor?new Date(valor+'T12:00:00'):null;
  const periodoAtual=()=>{
    const agora=new Date(),hoje=new Date(agora.getFullYear(),agora.getMonth(),agora.getDate(),12),valor=$('dashboard-period').value;
    if(valor==='all')return null;
    if(valor==='month')return {inicio:new Date(hoje.getFullYear(),hoje.getMonth(),1,12),fim:hoje};
    if(valor==='year')return {inicio:new Date(hoje.getFullYear(),0,1,12),fim:hoje};
    const inicio=new Date(hoje);inicio.setDate(inicio.getDate()-29);return {inicio,fim:hoje};
  };
  const renderizar=()=>{
    if(!ordensDashboard)return;
    const periodo=periodoAtual();
    const lista=ordensDashboard.filter(ordem=>{
      if(!periodo)return true;
      const abertura=dataLocal(ordem.dataAbertura);
      return abertura&&abertura>=periodo.inicio&&abertura<=periodo.fim;
    });
    const quantidade=situacao=>lista.filter(ordem=>ordem.situacao===situacao).length;
    $('metric-total').textContent=lista.length;
    $('metric-progress').textContent=quantidade('EM_ANDAMENTO');
    $('metric-done').textContent=quantidade('CONCLUIDA');
    $('metric-budget').textContent=quantidade('ORCAMENTO');
    const valores=Object.keys(rotulos).map(situacao=>[situacao,quantidade(situacao)]),maior=Math.max(1,...valores.map(([,valor])=>valor));
    $('status-chart').replaceChildren(...valores.map(([situacao,valor])=>{
      const linha=document.createElement('div');linha.className='status-row';
      const topo=document.createElement('div'),nome=document.createElement('span'),numero=document.createElement('strong');nome.textContent=rotulos[situacao];numero.textContent=valor;topo.append(nome,numero);
      const trilho=document.createElement('div'),barra=document.createElement('span');trilho.className='status-track';barra.className='status-bar status-'+situacao.toLocaleLowerCase('pt-BR').replace('_','-');barra.style.width=(valor/maior*100)+'%';trilho.setAttribute('role','img');trilho.setAttribute('aria-label',rotulos[situacao]+': '+valor);trilho.append(barra);linha.append(topo,trilho);return linha;
    }));
  };
  $('dashboard-period').addEventListener('change',renderizar);
  api('/api/ordens-servico/consulta').then(dados=>{ordensDashboard=dados;$('dashboard-loading').hidden=true;renderizar();}).catch(error=>{
    $('dashboard-loading').textContent=error.mensagem||'Não foi possível carregar o painel.';$('dashboard-loading').classList.add('error');
    ['metric-total','metric-progress','metric-done','metric-budget'].forEach(id=>$(id).textContent='—');
    $('status-chart').textContent='Não foi possível carregar as situações das OS.';notice(error.mensagem,true);
  });
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
  const input=document.createElement(type==='select'?'select':'input');
  input.id='field-'+name;
  input.name=name;
  if(type!=='select')input.type=type;
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
  if(section==='motocicletas' && ['marca','modelo'].includes(name)) {
    const list=document.createElement('datalist');
    list.id='sugestoes-'+name;
    input.setAttribute('list',list.id);
    wrap.append(list);
    if(name==='marca')input.addEventListener('input',atualizarSugestoesMoto);
  }
  if(help) {
    const hint=document.createElement('small');
    hint.id='help-'+name;
    hint.className='field-help';
    hint.textContent=help;
    wrap.append(hint);
  }
  wrap.append(err);
  $('form-fields').append(wrap);
  if(name==='unidadeMedida')preencherUnidades();
}
function preencherUnidades(atual='') {
  const seletor=$('field-unidadeMedida');
  seletor.replaceChildren(new Option('Selecione a unidade',''),...unidadesMedida.map(([codigo,descricao])=>new Option(codigo+' — '+descricao,codigo)));
  if(atual&&!unidadesMedida.some(([codigo])=>codigo===atual))seletor.append(new Option(atual+' — Unidade anterior',atual));
  seletor.value=atual||'';
}
function atualizarSugestoesMoto() {
  if(section!=='motocicletas')return;
  const normalizar=valor=>String(valor??'').trim().toLocaleLowerCase('pt-BR');
  const catalogo=typeof catalogoMotos==='undefined'?{}:catalogoMotos;
  const marca=normalizar($('field-marca').value);
  const chave=Object.keys(catalogo).find(nome=>normalizar(nome)===marca);
  const preencher=(nome,valores)=> {
    const unicos=new Map();
    valores.filter(Boolean).forEach(valor=> {
      if(!unicos.has(normalizar(valor)))unicos.set(normalizar(valor),valor);
    });
    $('sugestoes-'+nome).replaceChildren(...Array.from(unicos.values()).map(valor=> {
      const option=document.createElement('option'); option.value=valor; return option;
    }));
  };
  preencher('marca',[...Object.keys(catalogo),...records.map(m=>m.marca)]);
  preencher('modelo',[...(catalogo[chave]||[]),...records.filter(m=>normalizar(m.marca)===marca).map(m=>m.modelo)]);
}
function limparNovoCadastro() {
  if(saving || editingId || somenteConsulta || !dialog.open || $('save-record').disabled)return;
  definitions[section].fields.forEach(([nome])=>{
    const input=$('field-'+nome);input.value='';input.setCustomValidity('');input.removeAttribute('aria-invalid');
  });
  buscaProprietario?.limpar();
  if(section==='produtos')preencherUnidades();
  atualizarSugestoesMoto();
  $('form-error').hidden=true;
  $('form-fields').querySelectorAll('.field-error').forEach(el=>el.textContent='');
  const primeiro=$('form-fields').querySelector('input:not([disabled]),select:not([disabled])');
  primeiro?.focus();
}
async function openForm(record=null, consultar=false) {
  if (saving) return;
  const atual=++aberturaCadastro;
  if(section==='motocicletas') {
    buscaProprietario?.elemento.remove();
    buscaProprietario=null;
  }
  editingId=record ? record.id : null;
  somenteConsulta=consultar;
  $('record-form').reset();
  $('form-error').hidden=true;
  document.querySelectorAll('.field-error').forEach(el=>el.textContent='');
  definitions[section].fields.forEach(([name])=> {
    const input=$('field-'+name);
    input.removeAttribute('aria-invalid');
    input.disabled=false;
    input.value='';
  });
  $('form-title').textContent=consultar?'Consultar '+definitions[section].singular:(record?'Editar ':'Novo cadastro de ')+definitions[section].singular;
  $('clear-record').hidden=!!record || consultar;
  $('save-record').hidden=consultar;
  $('save-record').disabled=!!record || section==='motocicletas';
  dialog.showModal();

  let completo=record;
  if(record) {
    try {
      completo=await api('/api/'+section+'/'+record.id);
    } catch(error) {
      if(!dialog.open || atual!==aberturaCadastro)return;
      $('form-error').textContent=error.mensagem;
      $('form-error').hidden=false;
      return;
    }
    if(!dialog.open || atual!==aberturaCadastro)return;
  }

  if(section==='produtos')preencherUnidades(completo?.unidadeMedida);
  definitions[section].fields.forEach(([name])=>$('field-'+name).value=completo?.[name] ?? '');
  atualizarSugestoesMoto();

  if(section==='motocicletas') {
    const seletor=$('field-clienteId');
    seletor.disabled=false;
    try {
      const clientes=await api('/api/clientes');
      if(!dialog.open || atual!==aberturaCadastro)return;
      const anterior=seletor.closest('.form-field');
      buscaProprietario?.elemento.remove();
      buscaProprietario=criarBusca({nome:'cliente-proprietario',rotulo:'Cliente proprietário',registros:()=>clientes,descricao:c=>c.nome,chaves:c=>[c.id,c.nome],obrigatorio:false});
      anterior?.remove();
      $('form-fields').prepend(buscaProprietario.elemento);
      seletor.hidden=true;
      seletor.required=false;
      $('form-fields').append(seletor);
      buscaProprietario.selecionarId(completo?.clienteId);
      buscaProprietario.desabilitar(consultar || !!completo?.clienteId);
    } catch(error) {
      if(!dialog.open || atual!==aberturaCadastro)return;
      $('form-error').textContent=error.mensagem;
      $('form-error').hidden=false;
      return;
    }
  }

  definitions[section].fields.forEach(([name])=>$('field-'+name).disabled=consultar || (section==='motocicletas' && name==='clienteId'));
  $('save-record').disabled=consultar;
  const primeiro=$('form-fields').querySelector('input:not([disabled]), select:not([disabled])');
  (primeiro||$('close-dialog')).focus();
}
function configurarFerramentasLista(def) {
  if(!secoesComFerramentas.includes(section))return;
  ordenacaoLista=def.defaultSort;
  $('search').placeholder=def.searchPlaceholder;
  $('search').setAttribute('aria-label',def.searchPlaceholder);
  const barra=$('list-filters');barra.hidden=false;
  const titulo=document.createElement('span');titulo.className='filter-title';titulo.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h18l-7 8v5l-4 2v-7z"></path></svg><strong>Filtros e ordenação</strong>';
  barra.append(titulo);
  if(def.stockFilter){
    const label=document.createElement('label');label.textContent='Estoque';
    const select=document.createElement('select');select.id='stock-filter';select.append(new Option('Todas as situações',''),new Option('Com saldo disponível','available'),new Option('Com produtos empenhados','reserved'),new Option('Sem saldo disponível','empty'));
    select.addEventListener('change',()=>{filtroEstoque=select.value;paginaAtual=1;renderRows();});label.append(select);barra.append(label);
  }
  const ordenarLabel=document.createElement('label');ordenarLabel.textContent='Ordenar por';
  const ordenar=document.createElement('select');ordenar.id='list-sort';def.sorts.forEach(([valor,rotulo])=>ordenar.append(new Option(rotulo,valor)));ordenar.value=ordenacaoLista;
  ordenar.addEventListener('change',()=>{ordenacaoLista=ordenar.value;paginaAtual=1;renderRows();});ordenarLabel.append(ordenar);barra.append(ordenarLabel);
  const limpar=document.createElement('button');limpar.type='button';limpar.className='button subtle';limpar.textContent='Limpar filtros';
  limpar.addEventListener('click',()=>{$('search').value='';filtroEstoque='';paginaAtual=1;if($('stock-filter'))$('stock-filter').value='';renderRows();});barra.append(limpar);
  $('pagination').hidden=false;
  $('page-size').addEventListener('change',()=>{tamanhoPagina=Number($('page-size').value);paginaAtual=1;renderRows();});
  $('page-prev').addEventListener('click',()=>{if(paginaAtual>1){paginaAtual--;renderRows();}});
  $('page-next').addEventListener('click',()=>{paginaAtual++;renderRows();});
}
function renderRows() {
  const def=definitions[section];
  const term=$('search').value.trim().toLocaleLowerCase('pt-BR');
  const normalizar=valor=>String(valor??'').toLocaleLowerCase('pt-BR');
  const filtered=records.filter(record => {
    const pesquisaGeral=!term || (def.searchKeys||def.columns.map(([key])=>key)).some(key => normalizar(record[key]).includes(term));
    const pesquisaColunas=def.columns.every(([key])=>!filtrosColuna[key] || normalizar(record[key]).includes(filtrosColuna[key]));
    const pesquisaEstoque=section!=='produtos'||!filtroEstoque||(filtroEstoque==='available'&&Number(record.saldo)>0)||(filtroEstoque==='reserved'&&Number(record.empenho)>0)||(filtroEstoque==='empty'&&Number(record.saldo)<=0);
    return pesquisaGeral && pesquisaColunas && pesquisaEstoque;
  });
  const ordenados=[...filtered];
  if(ordenacaoLista){const [chave,direcao]=ordenacaoLista.split(':');ordenados.sort((a,b)=>{const av=a[chave],bv=b[chave];const resultado=typeof av==='number'||typeof bv==='number'?Number(av??0)-Number(bv??0):String(av??'').localeCompare(String(bv??''),'pt-BR',{numeric:true,sensitivity:'base'});return direcao==='desc'?-resultado:resultado;});}
  const comPaginacao=secoesComFerramentas.includes(section)&&tamanhoPagina>0,totalPaginas=comPaginacao?Math.max(1,Math.ceil(ordenados.length/tamanhoPagina)):1;
  paginaAtual=Math.min(Math.max(1,paginaAtual),totalPaginas);
  const exibidos=comPaginacao?ordenados.slice((paginaAtual-1)*tamanhoPagina,paginaAtual*tamanhoPagina):ordenados;
  $('table-body').replaceChildren();
  exibidos.forEach(record=> {
    const tr=document.createElement('tr');
    def.columns.forEach(([key,label,format])=> {
      const td=document.createElement('td');
      const value=record[key];
      td.textContent=format==='money'?money.format(value):(['estoque','empenho','saldo'].includes(key)?new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value??0):(value??'—'));
      tr.append(td);
    }
    );
    const td=document.createElement('td');
    const actions=document.createElement('div');
    actions.className='row-actions';
    const consult=document.createElement('button');
    consult.type='button';
    consult.className='row-action';
    consult.textContent='⌕ Consultar';
    consult.setAttribute('aria-label','Consultar '+(record.nome||record.placa||record.chassi||record.codigo));
    consult.addEventListener('click',()=>openForm(record,true));
    const edit=document.createElement('button');
    edit.type='button';
    edit.className='row-action';
    edit.textContent='✎ Editar';
    edit.setAttribute('aria-label','Editar '+(record.nome||record.placa||record.chassi||record.codigo));
    edit.addEventListener('click',()=>openForm(record,false));
    actions.append(consult,edit);
    if(section==='produtos') {
      const estoque=document.createElement('button'); estoque.type='button'; estoque.className='row-action'; estoque.textContent='⌕ Estoque';
      estoque.setAttribute('aria-label','Consultar estoque do produto '+record.codigo);
      estoque.addEventListener('click',()=>consultarEstoqueProduto(record.id,estoque)); actions.prepend(estoque);
    }
    td.append(actions);
    tr.append(td);
    $('table-body').append(tr);
  }
  );
  $('empty-state').hidden=filtered.length>0 || loading || loadFailed;
  const isFiltered=term || filtroEstoque || Object.values(filtrosColuna).some(Boolean);
  $('empty-title').textContent=isFiltered?'Nenhum resultado encontrado':'Seu primeiro cadastro começa aqui';
  $('empty-description').textContent=isFiltered?'Tente outro termo ou ajuste o filtro.':'Adicione um registro para começar a organizar sua oficina.';
  $('empty-add').hidden=!!isFiltered;
  $('record-count').textContent=loadFailed?'Não foi possível carregar os registros':`${filtered.length} de ${records.length} registro${records.length===1?'':'s'}`;
  if(secoesComFerramentas.includes(section)){$('page-status').textContent=`Página ${paginaAtual} de ${totalPaginas}`;$('page-prev').disabled=paginaAtual<=1;$('page-next').disabled=paginaAtual>=totalPaginas||filtered.length===0;}
}
async function loadRecords() {
  loading=true;
  loadFailed=false;
  $('loading').hidden=false;
  $('empty-state').hidden=true;
  $('refresh').disabled=true;
  try {
    records=await api('/api/'+section);
    atualizarSugestoesMoto();
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
  if(saving||somenteConsulta||$('save-record').disabled)return;
  const form=event.target;
  $('form-error').hidden=true;
  document.querySelectorAll('.field-error').forEach(el=>el.textContent='');
  definitions[section].fields.forEach(([name])=>$('field-'+name).removeAttribute('aria-invalid'));
  if(section==='motocicletas') {
    if(!buscaProprietario?.validar())return;
    ['placa','chassi'].forEach(name=>$('field-'+name).value=$('field-'+name).value.trim());
    if(!$('field-placa').value && !$('field-chassi').value) {
      $('form-error').textContent='Informe a placa ou o chassi.';
      $('form-error').hidden=false;
      $('field-placa').setAttribute('aria-invalid','true');
      $('error-placa').textContent='Informe a placa ou preencha o chassi.';
      $('field-placa').focus();
      return;
    }
  }
  if(!form.reportValidity())return;
  const data= {
  }
  ;
  definitions[section].fields.forEach(([name,label,type])=> {
    const value=$('field-'+name).value.trim();
    data[name]=name==='clienteId'&&buscaProprietario?buscaProprietario.valor():name==='unidadeMedida'?value:['number','select'].includes(type)?(value===''?null:Number(value)):value;
  }
  );
  const button=$('save-record');
  saving=true;
  button.disabled=true;
  $('close-dialog').disabled=true;
  $('cancel-dialog').disabled=true;
  $('clear-record').disabled=true;
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
    $('clear-record').disabled=false;
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
else if(definitions[section]?.fields) {
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
  configurarFerramentasLista(def);
  if(secoesComFiltrosColuna.includes(section)) {
    const filtros=document.createElement('tr');
    filtros.className='column-filters';
    def.columns.forEach(([key,label])=> {
      const th=document.createElement('th');
      const input=document.createElement('input');
      input.type='search';
      input.placeholder='Filtrar';
      input.setAttribute('aria-label','Filtrar por '+label);
      input.addEventListener('input',()=> {
        filtrosColuna[key]=input.value.trim().toLocaleLowerCase('pt-BR');
        renderRows();
      });
      th.append(input);
      filtros.append(th);
    });
    const th=document.createElement('th');
    const limpar=document.createElement('button');
    limpar.type='button';
    limpar.className='row-action';
    limpar.textContent='Limpar';
    limpar.addEventListener('click',()=> {
      filtros.querySelectorAll('input').forEach(input=>input.value='');
      Object.keys(filtrosColuna).forEach(key=>delete filtrosColuna[key]);
      $('search').value='';
      renderRows();
    });
    th.append(limpar);
    filtros.append(th);
    $('table-head').append(filtros);
  }
  def.fields.forEach(addField);
  if(def.note)$('form-description').textContent+=' '+def.note;
  $('new-record').addEventListener('click',()=>openForm());
  $('empty-add').addEventListener('click',()=>openForm());
  $('clear-record').addEventListener('click',limparNovoCadastro);
  $('search').addEventListener('input',()=>{paginaAtual=1;renderRows();});
  $('refresh').addEventListener('click',loadRecords);
  $('record-form').addEventListener('submit',saveRecord);
  $('close-dialog').addEventListener('click',()=>dialog.close());
  $('cancel-dialog').addEventListener('click',()=>dialog.close());
  loadRecords();
}
