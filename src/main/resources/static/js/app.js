'use strict';
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
    recurso:'usuarios', title:'Funcionários',singular:'funcionário',description:'Responsáveis pelos atendimentos e movimentações.',icon:'◉',hint:'Cadastre os responsáveis.',searchKeys:['nome'],searchPlaceholder:'Pesquisar por nome',defaultSort:'nome:asc',sorts:[['nome:asc','Nome (A–Z)'],['nome:desc','Nome (Z–A)']],columns:[['id','Código'],['nome','Nome'],['login','Login'],['perfil','Perfil','perfil'],['ativo','Situação','ativo']],fields:[['nome','Nome completo','text',true,120,'wide'],
    ['perfil','Perfil','select',true,null,'','Definido no cadastro; não muda na edição.',[['FUNCIONARIO','Funcionário'],['ADMINISTRADOR','Administrador']],'FUNCIONARIO'],
    ['ativo','Situação','select',true,null,'','Inativos não entram no sistema nem recebem novas OS.',[['true','Ativo'],['false','Inativo']],'true'],
    ['login','Login de acesso','text',false,60,'','Deixe em branco para quem não acessa o sistema.'],
    ['senha','Senha','password',false,72,'','Mínimo de 6 caracteres. Na edição, deixe em branco para manter.']]
  },
  servicos: {
    title:'Serviços',singular:'serviço',description:'Os serviços que a sua oficina oferece.',icon:'⚒',hint:'Mantenha os serviços e valores atualizados.',searchKeys:['id','nome','descricao'],searchPlaceholder:'Pesquisar por código, nome ou descrição',defaultSort:'nome:asc',sorts:[['nome:asc','Nome (A–Z)'],['nome:desc','Nome (Z–A)'],['preco:asc','Menor valor'],['preco:desc','Maior valor']],columns:[['id','Código'],['nome','Serviço'],['descricao','Descrição'],['preco','Preço','money']],fields:[
    ['nome','Nome do serviço','text',true,120,'wide'],['preco','Preço (R$)','number',true,null],['descricao','Descrição','text',false,255,'wide']]
  }
}
;
const $ = id => document.getElementById(id);
const desenhosIcone = {
  consultar:'<circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16" y2="16"/>',
  editar:'<path d="M4 20h4L19 9l-4-4L4 16z"/><line x1="13" y1="7" x2="17" y2="11"/>',
  excluir:'<polyline points="4 7 20 7"/><path d="M6 7l1 13h10l1-13"/><path d="M9 7V4h6v3"/>',
  salvar:'<path d="M5 3h11l3 3v15H5z"/><rect x="8" y="3" width="7" height="5"/><rect x="8" y="13" width="8" height="6"/>',
  imprimir:'<rect x="6" y="3" width="12" height="6"/><rect x="4" y="9" width="16" height="8"/><rect x="7" y="14" width="10" height="7"/>',
  pdf:'<path d="M6 2h9l5 5v15H6z"/><polyline points="15 2 15 7 20 7"/><line x1="9" y1="13" x2="17" y2="13"/><line x1="9" y1="17" x2="15" y2="17"/>',
  excel:'<rect x="4" y="3" width="16" height="18"/><line x1="4" y1="9" x2="20" y2="9"/><line x1="4" y1="15" x2="20" y2="15"/><line x1="10" y1="3" x2="10" y2="21"/>',
  limpar:'<path d="M15 4l5 5-9 9H6l-3-3z"/><line x1="11" y1="18" x2="21" y2="18"/><line x1="9" y1="10" x2="14" y2="15"/>',
  voltar:'<line x1="20" y1="12" x2="5" y2="12"/><polyline points="11 6 5 12 11 18"/>',
  adicionar:'<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
  atualizar:'<path d="M20 12a8 8 0 1 1-2.3-5.7"/><polyline points="20 4 20 9 15 9"/>',
  estoque:'<path d="M3 8l9-5 9 5v8l-9 5-9-5z"/><polyline points="3 8 12 13 21 8"/><line x1="12" y1="13" x2="12" y2="21"/>',
  aprovar:'<polyline points="5 12 10 17 19 7"/>',
  cancelar:'<circle cx="12" cy="12" r="9"/><line x1="8" y1="8" x2="16" y2="16"/><line x1="16" y1="8" x2="8" y2="16"/>',
  fechar:'<line x1="6" y1="6" x2="18" y2="18"/><line x1="18" y1="6" x2="6" y2="18"/>',
  retrabalho:'<polyline points="4 4 4 9 9 9"/><path d="M4 9a8 8 0 1 1 1.5 7"/>',
  ordens:'<rect x="5" y="4" width="14" height="18"/><rect x="9" y="2" width="6" height="4"/><line x1="8" y1="11" x2="16" y2="11"/><line x1="8" y1="15" x2="16" y2="15"/>'
};
function icone(nome) {
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
  svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');svg.classList.add('icone');
  svg.innerHTML=desenhosIcone[nome]||'';return svg;
}
function botaoIcone(nome,rotulo,acao,classe='row-action') {
  const botao=document.createElement('button');botao.type='button';botao.className=classe+' icon-button';
  botao.title=rotulo;botao.setAttribute('aria-label',rotulo);botao.append(icone(nome));
  if(acao)botao.addEventListener('click',acao);return botao;
}
function decorarBotao(elemento,nome,somenteIcone=false) {
  if(!elemento)return;
  const rotulo=elemento.textContent.trim();
  if(somenteIcone){elemento.title=rotulo;elemento.setAttribute('aria-label',rotulo);elemento.replaceChildren(icone(nome));elemento.classList.add('icon-button');}
  else elemento.replaceChildren(icone(nome),rotulo);
}
function rotular(elemento,nome,texto){elemento.replaceChildren(icone(nome),texto);}
let linhaSelecionada=null, ultimaAtualizacaoAcoes=null;
function criarAcaoLista(nomeIcone,rotulo,acao,exigeSelecao=true) {
  const botao=botaoIcone(nomeIcone,rotulo,acao,'button subtle');botao.dataset.exigeSelecao=String(exigeSelecao);
  $('list-actions').append(botao);return botao;
}
function selecionarLinha(id) {
  linhaSelecionada=id;
  document.querySelectorAll('#table-body tr').forEach(tr=>tr.setAttribute('aria-selected',String(tr.dataset.id===String(id))));
  if(ultimaAtualizacaoAcoes)atualizarAcoesLista(...ultimaAtualizacaoAcoes);
}
function linhaSelecionavel(tr,id,aoAbrir) {
  tr.dataset.id=id;tr.tabIndex=0;tr.classList.add('selectable');tr.setAttribute('aria-selected',String(String(id)===String(linhaSelecionada)));
  tr.addEventListener('click',()=>selecionarLinha(id));
  tr.addEventListener('dblclick',()=>{selecionarLinha(id);aoAbrir();});
  tr.addEventListener('keydown',evento=>{if(evento.key===' '){evento.preventDefault();selecionarLinha(id);}else if(evento.key==='Enter'){selecionarLinha(id);aoAbrir();}});
}
function atualizarAcoesLista(idsVisiveis,descricao) {
  ultimaAtualizacaoAcoes=[idsVisiveis,descricao];
  if(linhaSelecionada!==null&&!idsVisiveis.some(id=>String(id)===String(linhaSelecionada)))linhaSelecionada=null;
  const nome=linhaSelecionada===null?'':descricao(linhaSelecionada);
  $('list-hint').textContent=linhaSelecionada===null?'':'Selecionado: '+nome;
  $('list-actions').querySelectorAll('button').forEach(botao=>{if(botao.dataset.exigeSelecao==='true')botao.disabled=linhaSelecionada===null;});
}
// Perfil so ajusta a tela e as permissoes ficam no servidor
const administrador = document.querySelector('meta[name="perfil"]')?.content === 'ADMINISTRADOR';
const secoesSoAdministradorAltera = ['produtos','servicos','funcionarios'];
if(administrador) {
  definitions.produtos.columns.push(['precoCusto','Preço de custo','money']);
  definitions.produtos.fields.splice(2,0,['precoCusto','Preço de custo (R$)','number',false,null,'','Visível somente ao administrador.']);
}
const section = document.body.dataset.secao;
// Rota da API da secao no plural e Funcionarios usa /api/usuarios
const rotaApi = () => '/api/' + (definitions[section]?.recurso || section);
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
    $('metric-canceled').textContent=quantidade('CANCELADA');
    $('metric-rework').textContent=lista.filter(ordem=>ordem.ordemOrigemId).length;
    const valores=Object.keys(rotulos).map(situacao=>[situacao,quantidade(situacao)]),maior=Math.max(1,...valores.map(([,valor])=>valor));
    $('status-chart').replaceChildren(...valores.map(([situacao,valor])=>{
      const linha=document.createElement('div');linha.className='status-row';
      const topo=document.createElement('div'),nome=document.createElement('span'),numero=document.createElement('strong');nome.textContent=rotulos[situacao];numero.textContent=valor;topo.append(nome,numero);
      const trilho=document.createElement('div'),barra=document.createElement('span');trilho.className='status-track';barra.className='status-bar status-'+situacao.toLocaleLowerCase('pt-BR').replace('_','-');barra.style.width=(valor/maior*100)+'%';trilho.setAttribute('role','img');trilho.setAttribute('aria-label',rotulos[situacao]+': '+valor);trilho.append(barra);linha.append(topo,trilho);return linha;
    }));
  };
  $('dashboard-period').addEventListener('change',renderizar);
  api('/api/painel/resumo').then(resumo=>{$('metric-clients').textContent=resumo.clientes;$('metric-vehicles').textContent=resumo.motocicletas;})
    .catch(error=>notice(error.mensagem||'Não foi possível carregar os totais de cadastros.',true));
  api('/api/ordens-servico/consulta').then(dados=>{ordensDashboard=dados;$('dashboard-loading').hidden=true;renderizar();}).catch(error=>{
    $('dashboard-loading').textContent=error.mensagem||'Não foi possível carregar o painel.';$('dashboard-loading').classList.add('error');
    ['metric-total','metric-progress','metric-done','metric-budget','metric-canceled','metric-rework'].forEach(id=>$(id).textContent='—');
    $('status-chart').textContent='Não foi possível carregar as situações das OS.';notice(error.mensagem,true);
  });
}
function addField(field) {
  const [name,label,type,required,maxLength,width,help,opcoes]=field;
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
  if(opcoes)opcoes.forEach(([valor,rotulo])=>input.append(new Option(rotulo,valor)));
  if(type==='password')input.autocomplete='new-password';
  if(name==='chassi')input.pattern='[A-Za-z0-9]{17}';
  const err=document.createElement('span');
  err.className='field-error';
  err.id='error-'+name;
  input.setAttribute('aria-describedby',err.id);
  wrap.append(labelEl,input);
  if(section==='motocicletas' && ['marca','modelo'].includes(name)) {
    const list=document.createElement('datalist');
    list.id='sugestoes-'+name;
    input.setAttribute('list',list.id);
    wrap.append(list);
    if(name==='marca')input.addEventListener('input',atualizarSugestoesMoto);
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
  definitions[section].fields.forEach(([nome,,,,,,,,padrao])=>{
    const input=$('field-'+nome);input.value=padrao??'';input.setCustomValidity('');input.removeAttribute('aria-invalid');
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
      completo=await api(rotaApi()+'/'+record.id);
    } catch(error) {
      if(!dialog.open || atual!==aberturaCadastro)return;
      $('form-error').textContent=error.mensagem;
      $('form-error').hidden=false;
      return;
    }
    if(!dialog.open || atual!==aberturaCadastro)return;
  }

  if(section==='produtos')preencherUnidades(completo?.unidadeMedida);
  definitions[section].fields.forEach(([name,,,,,,,,padrao])=>$('field-'+name).value=completo?.[name] ?? (record ? '' : padrao ?? ''));
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

  definitions[section].fields.forEach(([name])=>$('field-'+name).disabled=consultar || (section==='motocicletas' && name==='clienteId') || (!!record && name==='perfil'));
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
    const textoColuna=(key,format)=>format==='ativo'?(record[key]?'ativo':'inativo'):format==='perfil'?(record[key]==='ADMINISTRADOR'?'administrador':'funcionário'):record[key];
    const pesquisaColunas=def.columns.every(([key,,format])=>!filtrosColuna[key] || normalizar(textoColuna(key,format)).includes(filtrosColuna[key]));
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
      td.textContent=format==='ativo'?(value?'Ativo':'Inativo'):format==='perfil'?(value==='ADMINISTRADOR'?'Administrador':'Funcionário'):format==='money'?(value==null?'—':money.format(value)):(['estoque','empenho','saldo'].includes(key)?new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value??0):(value??'—'));
      tr.append(td);
    }
    );
    linhaSelecionavel(tr,record.id,()=>openForm(record,true));
    $('table-body').append(tr);
  }
  );
  atualizarAcoesLista(exibidos.map(r=>r.id),id=>{const r=records.find(item=>String(item.id)===String(id));return r.nome||r.placa||r.chassi||r.codigo;});
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
    records=await api(rotaApi());
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
  definitions[section].fields.forEach(([name,label,type,,,,,opcoes])=> {
    const value=$('field-'+name).value.trim();
    if(opcoes){data[name]=name==='ativo'?value==='true':value;return;}
    data[name]=name==='clienteId'&&buscaProprietario?buscaProprietario.valor():name==='unidadeMedida'?value:['number','select'].includes(type)?(value===''?null:Number(value)):value;
  }
  );
  const button=$('save-record');
  saving=true;
  button.disabled=true;
  $('close-dialog').disabled=true;
  $('cancel-dialog').disabled=true;
  $('clear-record').disabled=true;
  rotular(button,'salvar','Salvando…');
  try {
    await api(rotaApi()+(editingId?'/'+editingId:''), {
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
    rotular(button,'salvar','Salvar cadastro');
  }
}
decorarBotao($('refresh'),'atualizar',true);decorarBotao($('cancel-dialog'),'fechar');decorarBotao($('clear-record'),'limpar');
rotular($('save-record'),'salvar','Salvar cadastro');$('new-record').querySelector('span:not(#new-label)')?.replaceWith(icone('adicionar'));
decorarBotao($('os-reload'),'atualizar',true);decorarBotao(document.querySelector('.os-toolbar a.button'),'voltar',true);
decorarBotao($('os-save-header'),'salvar');decorarBotao($('os-add-button'),'adicionar');decorarBotao($('stock-save'),'salvar');decorarBotao($('stock-reload'),'atualizar');
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
  $('new-record').hidden=!administrador && secoesSoAdministradorAltera.includes(section);
  $('new-label').textContent='Cadastrar '+def.singular;
  if($('new-record').hidden) $('empty-add').remove();
  const tr=document.createElement('tr');
  def.columns.forEach(([,label])=> {
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
    $('table-head').append(filtros);
  }
  const registroSelecionado=()=>records.find(r=>String(r.id)===String(linhaSelecionada));
  if(section==='produtos'){const estoque=criarAcaoLista('estoque','Consultar estoque do produto selecionado',()=>consultarEstoqueProduto(registroSelecionado().id,estoque));}
  if(section==='servicos'){const consulta=criarAcaoLista('ordens','Consultar OS abertas do serviço selecionado',()=>consultarOrdensServico(registroSelecionado().id,consulta));}
  criarAcaoLista('consultar','Consultar o registro selecionado',()=>openForm(registroSelecionado(),true));
  if(administrador || !secoesSoAdministradorAltera.includes(section)) criarAcaoLista('editar','Editar o registro selecionado',()=>openForm(registroSelecionado(),false));
  criarAcaoLista('limpar','Limpar filtros',()=>{
    document.querySelectorAll('.column-filters input').forEach(input=>input.value='');
    Object.keys(filtrosColuna).forEach(key=>delete filtrosColuna[key]);
    $('search').value='';paginaAtual=1;renderRows();
  },false);
  def.fields.forEach(addField);
  $('new-record').addEventListener('click',()=>openForm());
  $('empty-add')?.addEventListener('click',()=>openForm());
  $('clear-record').addEventListener('click',limparNovoCadastro);
  $('search').addEventListener('input',()=>{paginaAtual=1;renderRows();});
  $('refresh').addEventListener('click',loadRecords);
  $('record-form').addEventListener('submit',saveRecord);
  $('close-dialog').addEventListener('click',()=>dialog.close());
  $('cancel-dialog').addEventListener('click',()=>dialog.close());
  loadRecords();
}
