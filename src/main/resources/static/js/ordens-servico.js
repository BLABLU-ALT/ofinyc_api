'use strict';
(() => {
  let ordens=[],geracao=0,cliente,moto,funcionario,limparRapido=null,cadastroRapidoEnviando=false;
  const filtros={};
  const colunas=[['id','Nº OS'],['dataAbertura','Abertura'],['clienteNome','Cliente'],['placa','Placa / chassi'],['funcionarioNome','Responsável'],['situacao','Status'],['valorTotal','Total']];
  const estado=v=>({ORCAMENTO:'Orçamento',EM_ANDAMENTO:'Em andamento',CONCLUIDA:'Concluída',CANCELADA:'Cancelada',RETRABALHO:'Retrabalho'}[v]||v);
  const valorColuna=(o,k)=>k==='valorTotal'?money.format(o[k]):k==='situacao'?estado(o[k]):k==='placa'?(o.placa||o.chassi||'Não informado'):k==='dataAbertura'?(o[k]?.split('-').reverse().join('/')||'—'):(o[k]??'Não informado');
  $('breadcrumb').textContent=$('page-title').textContent='Ordens de serviço'; document.title='Ordens de serviço · OFICYNIC';
  $('listing').hidden=$('new-record').hidden=false; $('new-label').textContent='Abrir OS';
  $('search').placeholder='Número, cliente, responsável, placa, chassi ou descrição';
  const head=document.createElement('tr'); colunas.forEach(([,nome])=>{const th=document.createElement('th');th.scope='col';th.textContent=nome;head.append(th);});$('table-head').append(head);
  const linhaFiltros=document.createElement('tr');linhaFiltros.className='column-filters';
  colunas.forEach(([chave,nome])=>{const th=document.createElement('th');const input=chave==='situacao'?document.createElement('select'):document.createElement('input');
    if(chave==='situacao'){input.append(new Option('Todos',''));[['ORCAMENTO','Orçamento'],['EM_ANDAMENTO','Em andamento'],['CONCLUIDA','Concluída'],['CANCELADA','Cancelada'],['RETRABALHO','Retrabalho']].forEach(([valor,rotulo])=>input.append(new Option(rotulo,valor)));}
    else{input.type=chave==='dataAbertura'?'date':'search';input.placeholder='Filtrar';}
    input.setAttribute('aria-label','Filtrar coluna '+nome);input.addEventListener('input',()=>{filtros[chave]=input.value;renderizar();});th.append(input);linhaFiltros.append(th);});
  $('table-head').append(linhaFiltros);
  const abrirSelecionada=()=>{if(linhaSelecionada!==null)window.location.assign('/ordens-servico/'+linhaSelecionada);};
  criarAcaoLista('consultar','Consultar a OS selecionada',abrirSelecionada);
  criarAcaoLista('limpar','Limpar filtros',()=>{linhaFiltros.querySelectorAll('input,select').forEach(campo=>campo.value='');$('search').value='';Object.keys(filtros).forEach(chave=>delete filtros[chave]);renderizar();},false);
  function renderizar(){
    const termo=$('search').value.trim().toLocaleLowerCase('pt-BR');
    const normalizar=valor=>String(valor??'').toLocaleLowerCase('pt-BR');
    const lista=ordens.filter(o=>[...colunas,['chassi'],['descricao']].some(([k])=>normalizar(valorColuna(o,k)).includes(termo)))
      .filter(o=>Object.entries(filtros).every(([chave,filtro])=>{
        if(!filtro)return true;
        if(chave==='situacao')return o.situacao===filtro;
        if(chave==='dataAbertura')return o.dataAbertura===filtro;
        return normalizar(valorColuna(o,chave)).includes(normalizar(filtro));
      }));
    $('table-body').replaceChildren();
    lista.forEach(o=>{const tr=document.createElement('tr');colunas.forEach(([k])=>{const td=document.createElement('td');td.textContent=valorColuna(o,k);tr.append(td);});
      linhaSelecionavel(tr,o.id,()=>window.location.assign('/ordens-servico/'+o.id));$('table-body').append(tr);});
    atualizarAcoesLista(lista.map(o=>o.id),id=>'OS '+id);
    $('empty-state').hidden=lista.length>0; $('empty-title').textContent='Nenhuma OS encontrada';$('empty-description').textContent='Use Abrir OS para registrar um atendimento.';$('empty-add').hidden=true;$('record-count').textContent=lista.length+' de '+ordens.length+' ordens';
  }
  async function carregar(){
    $('loading').hidden=false;$('refresh').disabled=true;$('empty-state').hidden=true;
    try{ordens=await api('/api/ordens-servico/consulta');renderizar();}catch(e){notice(e.mensagem,true);$('empty-state').hidden=true;$('record-count').textContent='Não foi possível carregar as ordens';}
    finally{$('loading').hidden=true;$('refresh').disabled=false;}
  }
  function campo(nome,rotulo,tipo,valor){const div=document.createElement('div');div.className='form-field'+(tipo==='textarea'?' wide':'');const label=document.createElement('label');label.htmlFor='os-'+nome;label.textContent=rotulo+' *';const input=document.createElement(tipo==='textarea'?'textarea':'input');if(tipo!=='textarea')input.type=tipo;input.id='os-'+nome;input.required=true;input.value=valor; if(tipo==='textarea'){input.rows=4;input.maxLength=255;}div.append(label,input);$('form-fields').append(div);return input;}
  function painelRapido({prefixo,titulo,textoNovo,definicoes,url,preparar=dados=>dados,aoAbrir=()=>{},aoSalvar}){
    const geracaoFormulario=geracao;
    const area=document.createElement('section');area.className='quick-client-panel';area.hidden=true;
    const cabecalho=document.createElement('strong');cabecalho.textContent=titulo;
    const grade=document.createElement('div');grade.className='quick-client-grid';
    const entradas={};
    definicoes.forEach(([nome,rotulo,tipo,obrigatorio,tamanho,ajustar])=>{
      const envoltorio=document.createElement('div');envoltorio.className='form-field';
      const label=document.createElement('label');label.htmlFor=prefixo+nome;label.textContent=rotulo+(obrigatorio?' *':'');
      const input=document.createElement('input');input.id=prefixo+nome;input.type=tipo;input.required=obrigatorio;input.disabled=true;input.autocomplete='off';
      if(tamanho)input.maxLength=tamanho;if(ajustar)ajustar(input);
      envoltorio.append(label,input);grade.append(envoltorio);entradas[nome]=input;
    });
    const erro=document.createElement('p');erro.className='form-error';erro.hidden=true;
    const acoes=document.createElement('div');acoes.className='quick-actions';
    const salvar=document.createElement('button');salvar.type='button';salvar.className='button primary';rotular(salvar,'salvar','Salvar');
    const cancelar=document.createElement('button');cancelar.type='button';cancelar.className='button subtle';cancelar.textContent='Cancelar';
    acoes.append(salvar,cancelar);area.append(cabecalho,grade,erro,acoes);
    const novo=document.createElement('button');novo.type='button';novo.className='button subtle quick-client-open';rotular(novo,'adicionar',textoNovo);
    const limpar=()=>{Object.values(entradas).forEach(input=>{input.value='';input.removeAttribute('aria-invalid');});erro.hidden=true;};
    const exibir=visivel=>{area.hidden=!visivel;Object.values(entradas).forEach(input=>input.disabled=!visivel);};
    novo.addEventListener('click',()=>{exibir(true);erro.hidden=true;aoAbrir(entradas);(Object.values(entradas).find(input=>!input.value)||salvar).focus();});
    cancelar.addEventListener('click',()=>{limpar();exibir(false);});
    salvar.addEventListener('click',async()=>{
      if(cadastroRapidoEnviando)return;
      if(!Object.values(entradas).every(input=>input.reportValidity()))return;
      cadastroRapidoEnviando=true;erro.hidden=true;
      const controles=[salvar,cancelar,novo,$('save-record'),$('close-dialog'),$('cancel-dialog'),$('clear-record')];controles.forEach(controle=>controle.disabled=true);
      try{
        const dados=preparar(Object.fromEntries(Object.entries(entradas).map(([nome,input])=>[nome,input.value.trim()])));
        const cadastrado=await api(url,{method:'POST',body:JSON.stringify(dados)});
        if(geracao!==geracaoFormulario||!dialog.open)return;
        limpar();exibir(false);aoSalvar(cadastrado);
      }catch(e){if(geracao===geracaoFormulario&&dialog.open){erro.textContent=e.mensagem||'Não foi possível salvar.';erro.hidden=false;Object.entries(e.campos||{}).forEach(([nome])=>entradas[nome]?.setAttribute('aria-invalid','true'));}}
      finally{cadastroRapidoEnviando=false;if(geracao===geracaoFormulario&&dialog.open)controles.forEach(controle=>controle.disabled=false);}
    });
    area.addEventListener('keydown',evento=>{if(evento.key==='Enter'){evento.preventDefault();salvar.click();}});
    return {novo,area,limpar:()=>{limpar();exibir(false);}};
  }
  async function abrir(){
    if(saving||dialog.open)return;const atual=++geracao;$('record-form').reset();$('form-fields').replaceChildren();$('form-error').hidden=true;
    $('form-title').textContent='Abrir ordem de serviço';$('form-description').textContent='Carregando cadastros…';$('save-record').disabled=true;$('save-record').hidden=false;rotular($('save-record'),'salvar','Abrir OS');$('clear-record').hidden=false;limparRapido=null;dialog.showModal();
    try{
      const [clientes,motos,funcionarios,dataServidor]=await Promise.all([api('/api/clientes'),api('/api/motocicletas'),api('/api/usuarios/ativos'),api('/api/ordens-servico/data-atual')]);if(atual!==geracao||!dialog.open)return;
      if(!funcionarios.length){$('form-description').textContent='Não há funcionário ativo. Peça ao administrador para cadastrar ou reativar um responsável.';return;}
      cliente=criarBusca({nome:'os-cliente',rotulo:'Cliente',registros:()=>clientes,descricao:c=>c.nome,chaves:c=>[c.id,c.nome],aoSelecionar:()=>moto?.limpar()});
      moto=criarBusca({nome:'os-moto',rotulo:'Motocicleta',registros:()=>motos.filter(m=>String(m.clienteId)===String(cliente.valor())),descricao:m=>(m.placa||m.chassi)+' · '+m.marca+' '+m.modelo,chaves:m=>[m.id,m.placa,m.chassi,m.marca+' '+m.modelo]});
      funcionario=criarBusca({nome:'os-funcionario',rotulo:'Funcionário responsável',registros:()=>funcionarios,descricao:f=>f.nome,chaves:f=>[f.id,f.nome]});
      const novoCliente=painelRapido({prefixo:'quick-',titulo:'Novo cliente',textoNovo:'Novo cliente',url:'/api/clientes',
        definicoes:[['nome','Nome completo','text',true,120],['cpf','CPF','text',true,11,input=>{input.pattern='[0-9]{11}';input.inputMode='numeric';}],['telefone','Telefone','tel',false,20],['email','E-mail','email',false,160],['endereco','Endereço','text',false,255]],
        aoAbrir:entradas=>{if(!entradas.nome.value)entradas.nome.value=cliente.input.value.trim();},
        aoSalvar:cadastrado=>{clientes.push(cadastrado);cliente.selecionarId(cadastrado.id);moto.limpar();}});
      const novaMoto=painelRapido({prefixo:'quick-moto-',titulo:'Nova motocicleta do cliente',textoNovo:'Nova motocicleta',url:'/api/motocicletas',
        definicoes:[['placa','Placa','text',false,7,input=>{input.pattern='[A-Za-z0-9]{7}';}],['chassi','Chassi','text',false,17,input=>{input.pattern='[A-Za-z0-9]{17}';}],['marca','Marca','text',true,80],['modelo','Modelo','text',true,100],['ano','Ano','number',true,null,input=>{input.min='1900';input.max='2100';input.step='1';}],['cor','Cor','text',false,50]],
        aoAbrir:entradas=>{const digitado=moto.input.value.trim().replace(/[\s-]/g,'');if(/^[A-Za-z0-9]{7}$/.test(digitado))entradas.placa.value=digitado.toUpperCase();else if(/^[A-Za-z0-9]{17}$/.test(digitado))entradas.chassi.value=digitado.toUpperCase();},
        preparar:dados=>{if(!dados.placa&&!dados.chassi)throw {mensagem:'Informe a placa ou o chassi.'};return {...dados,placa:dados.placa||null,chassi:dados.chassi||null,ano:Number(dados.ano),clienteId:cliente.valor()};},
        aoSalvar:cadastrada=>{motos.push(cadastrada);moto.selecionarId(cadastrada.id);}});
      limparRapido=()=>{novoCliente.limpar();novaMoto.limpar();};
      const normalizar=valor=>String(valor??'').trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g,'');
      const atualizarCadastroCondicional=()=>{
        const termoCliente=normalizar(cliente.input.value),termoMoto=normalizar(moto.input.value);
        novoCliente.novo.hidden=!!cliente.valor() || (clientes.length>0 && (!termoCliente || clientes.some(c=>[c.id,c.nome].some(v=>normalizar(v).includes(termoCliente)))));
        if(novoCliente.novo.hidden&&!cadastroRapidoEnviando)novoCliente.limpar();
        const candidatas=motos.filter(m=>String(m.clienteId)===String(cliente.valor()));
        novaMoto.novo.hidden=!cliente.valor() || !!moto.valor() || (candidatas.length>0 && (!termoMoto || candidatas.some(m=>[m.id,m.placa,m.chassi,m.marca+' '+m.modelo].some(v=>normalizar(v).includes(termoMoto)))));
        if(novaMoto.novo.hidden&&!cadastroRapidoEnviando)novaMoto.limpar();
      };
      [cliente.input,moto.input].forEach(input=>['input','blur','change'].forEach(evento=>input.addEventListener(evento,atualizarCadastroCondicional)));
      atualizarCadastroCondicional();
      $('form-fields').append(cliente.elemento,novoCliente.novo,novoCliente.area,moto.elemento,novaMoto.novo,novaMoto.area,funcionario.elemento);
      campo('data','Data de abertura','date',dataServidor).readOnly=true;campo('descricao','Orientação do cliente','textarea','');
      $('form-description').textContent='';$('save-record').disabled=false;cliente.input.focus();
    }catch(e){if(atual===geracao&&dialog.open){$('form-error').textContent=e.mensagem;$('form-error').hidden=false;}}
  }
  $('record-form').addEventListener('submit',async e=>{
    e.preventDefault();if(saving||cadastroRapidoEnviando||$('save-record').disabled)return;
    if(!cliente.validar()||!moto.validar()||!funcionario.validar()||!e.target.reportValidity())return;
    saving=true;dialog.querySelectorAll('button').forEach(b=>b.disabled=true);$('form-error').hidden=true;
    let criada=false;
    try{const os=await api('/api/ordens-servico/abertura',{method:'POST',body:JSON.stringify({clienteId:cliente.valor(),motocicletaId:moto.valor(),funcionarioId:funcionario.valor(),dataAbertura:$('os-data').value,descricao:$('os-descricao').value.trim()})});criada=true;dialog.close();notice('OS '+os.id+' criada. Consulte-a na lista se a página não abrir.');carregar();window.location.assign('/ordens-servico/'+os.id);}
    catch(e){$('form-error').textContent=e.mensagem;$('form-error').hidden=false;}
    finally{if(!criada){saving=false;dialog.querySelectorAll('button').forEach(b=>b.disabled=false);}}
  });
  $('clear-record').addEventListener('click',()=>{
    if(saving || cadastroRapidoEnviando || $('save-record').disabled || !dialog.open)return;
    cliente?.limpar();moto?.limpar();funcionario?.limpar();limparRapido?.();
    const descricao=$('os-descricao');if(descricao){descricao.value='';descricao.removeAttribute('aria-invalid');descricao.setCustomValidity('');}
    $('form-error').hidden=true;cliente?.input.focus();
  });
  $('new-record').addEventListener('click',abrir);$('refresh').addEventListener('click',carregar);$('search').addEventListener('input',renderizar);
  $('close-dialog').addEventListener('click',()=>dialog.close());$('cancel-dialog').addEventListener('click',()=>dialog.close());dialog.addEventListener('cancel',evento=>{if(cadastroRapidoEnviando)evento.preventDefault();});dialog.addEventListener('close',()=>geracao++);carregar();if(new URLSearchParams(window.location.search).get('nova')==='1')abrir();
})();
