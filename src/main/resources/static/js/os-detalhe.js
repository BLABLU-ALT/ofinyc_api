'use strict';
(() => {
  const id=document.body.dataset.ordemId;
  let ordem,clientes=[],motos=[],funcionarios=[],servicos=[],produtos=[],referencia,cliente,moto,funcionario,ocupado=false,geracao=0;
  const estados={ORCAMENTO:'Orçamento',EM_ANDAMENTO:'Em andamento',CONCLUIDA:'Concluída',CANCELADA:'Cancelada',RETRABALHO:'Retrabalho'};
  const somenteConsulta=()=>['CONCLUIDA','CANCELADA'].includes(ordem.situacao);
  const pdf=document.createElement('a');pdf.className='button';pdf.href='/api/ordens-servico/'+id+'/pdf';pdf.target='_blank';pdf.rel='noopener';
  pdf.title='Gerar PDF para aprovação do cliente';rotular(pdf,'pdf','PDF de aprovação');$('os-status').after(pdf);
  $('page-title').textContent='Ordem de serviço nº '+id;$('breadcrumb').textContent='OS '+id;document.title='OS '+id+' · OFICYNIC';
  function botao(texto,acao,nomeIcone){const b=document.createElement('button');b.type='button';b.className='button';if(nomeIcone)rotular(b,nomeIcone,texto);else b.textContent=texto;b.addEventListener('click',acao);return b;}
  function erro(e){notice(e.mensagem||'Confira os dados e tente novamente.',true);$('notice').scrollIntoView({block:'nearest'});}
  function campo(nome,label,tipo,valor,obrigatorio=true,tamanho=255){const div=document.createElement('div');div.className='form-field'+(tipo==='textarea'?' wide':'');const l=document.createElement('label');l.htmlFor=nome;l.textContent=label+(obrigatorio?' *':'');const input=document.createElement(tipo==='textarea'?'textarea':'input');input.id=nome;input.required=obrigatorio;input.value=valor??'';if(tipo!=='textarea')input.type=tipo;else{input.maxLength=tamanho;input.rows=3;}div.append(l,input);$('os-header-fields').append(div);return input;}
  async function catalogos(){[clientes,motos,funcionarios,servicos,produtos]=await Promise.all([api('/api/clientes'),api('/api/motocicletas'),api('/api/usuarios/ativos'),api('/api/servicos'),api('/api/produtos')]);}
  function cabecalho(){
    $('os-header-fields').replaceChildren();
    const finalizada=somenteConsulta();
    cliente=criarBusca({nome:'header-cliente',rotulo:'Cliente',registros:()=>clientes,descricao:c=>c.nome,chaves:c=>[c.id,c.nome],aoSelecionar:()=>moto?.limpar()});
    moto=criarBusca({nome:'header-moto',rotulo:'Motocicleta',registros:()=>motos.filter(m=>String(m.clienteId)===String(cliente.valor())),descricao:m=>(m.placa||m.chassi)+' · '+m.marca+' '+m.modelo,chaves:m=>[m.id,m.placa,m.chassi,m.marca+' '+m.modelo]});
    // Mantem o responsavel inativo nesta OS sem oferecer para outras
    if(ordem.funcionarioId&&!funcionarios.some(f=>String(f.id)===String(ordem.funcionarioId)))funcionarios.push({id:ordem.funcionarioId,nome:ordem.funcionarioNome+' (inativo)'});
    funcionario=criarBusca({nome:'header-funcionario',rotulo:'Funcionário responsável',registros:()=>funcionarios,descricao:f=>f.nome,chaves:f=>[f.id,f.nome]});
    cliente.selecionarId(ordem.clienteId);moto.selecionarId(ordem.motocicletaId);funcionario.selecionarId(ordem.funcionarioId);
    // Mostra o vinculo antigo mesmo sem proprietario na moto
    // Para editar continua exigindo uma moto valida do cliente
    if(ordem.motocicletaId&&!moto.valor()){
      const vinculada=motos.find(m=>String(m.id)===String(ordem.motocicletaId));
      moto.input.value=ordem.motocicletaId+' · '+(vinculada?.placa||vinculada?.chassi||ordem.placa||ordem.chassi||'Moto vinculada');
    }
    $('os-header-fields').append(cliente.elemento,moto.elemento,funcionario.elemento);
    campo('header-data','Data de abertura','date',ordem.dataAbertura).readOnly=true;
    const orientacao=campo('header-descricao','Orientação do cliente','textarea',ordem.descricao);
    if(!administrador){orientacao.readOnly=true;orientacao.title='Somente o administrador pode alterar a orientação do cliente.';}
    campo('header-parecer','Parecer técnico','textarea',ordem.parecerTecnico,false,1000).placeholder='Avaliação, diagnóstico ou observação técnica do atendimento.';
    [cliente,moto,funcionario].forEach(b=>b.desabilitar(finalizada));$('header-data').disabled=$('header-descricao').disabled=$('header-parecer').disabled=finalizada;
    $('os-save-header').hidden=finalizada;$('os-status').textContent=estados[ordem.situacao];
    $('os-workspace').classList.toggle('somente-consulta',finalizada);
    if(ordem.situacao==='CONCLUIDA')$('os-info').textContent='Concluída em '+ordem.dataFinalizacao.split('-').reverse().join('/')+'. Dados e itens somente para consulta.';
    else if(ordem.situacao==='CANCELADA')$('os-info').textContent='OS cancelada. Os dados foram preservados e os produtos deixaram de ficar empenhados.';
    else $('os-info').textContent='';
    if(ordem.dataCriacao)$('os-info').textContent+=' Criação: '+ordem.dataCriacao.split('-').reverse().join('/')+'.';
    if(ordem.dataAprovacao)$('os-info').textContent+=' Aprovação: '+ordem.dataAprovacao.split('-').reverse().join('/')+'.';
    if(ordem.dataAberturaAnterior)$('os-info').textContent+=' Abertura anteriormente registrada (legado): '+ordem.dataAberturaAnterior.split('-').reverse().join('/')+'.';
    if(ordem.ordemOrigemId)$('os-info').textContent+=' Retrabalho originado da OS '+ordem.ordemOrigemId+'.';
    if(!ordem.funcionarioId&&!finalizada)$('os-info').textContent+=' Complete o funcionário responsável desta OS antiga.';
    $('os-state-actions').replaceChildren();
    if(ordem.situacao==='ORCAMENTO')$('os-state-actions').append(botao('Registrar aprovação do cliente',()=>executar(`/api/ordens-servico/${id}/aprovacao`,{method:'POST'},'Aprovação registrada; atendimento iniciado.'),'aprovar'));
    if(ordem.situacao==='RETRABALHO')$('os-state-actions').append(botao('Iniciar retrabalho',()=>executar(`/api/ordens-servico/${id}/situacao`,{method:'PUT',body:JSON.stringify({situacao:'EM_ANDAMENTO'})},'Retrabalho iniciado.'),'retrabalho'));
    if(ordem.situacao==='EM_ANDAMENTO'){
      $('os-state-actions').append(botao('Voltar para orçamento',()=>executar(`/api/ordens-servico/${id}/situacao`,{method:'PUT',body:JSON.stringify({situacao:'ORCAMENTO'})},'Status atualizado.'),'voltar'));
      $('os-state-actions').append(botao('Concluir OS',()=>executar(`/api/ordens-servico/${id}/finalizacao`,{method:'POST'},'OS concluída; produtos consumidos e empenhos liberados.'),'aprovar'));
    }
    if(!finalizada)$('os-state-actions').append(botao('Cancelar OS',()=>executar(`/api/ordens-servico/${id}/cancelamento`,{method:'POST'},'OS cancelada; produtos liberados sem movimentação física.'),'cancelar'));
    if(ordem.situacao==='CONCLUIDA')$('os-state-actions').append(botao('Criar retrabalho',criarRetrabalho,'retrabalho'));
  }
  function totais(){
    $('os-totals').replaceChildren();[['Serviços',ordem.totalServicos],['Produtos',ordem.totalProdutos],['Total da OS',ordem.valorTotal]].forEach(([nome,valor])=>{const div=document.createElement('div'),l=document.createElement('span'),v=document.createElement('strong');l.textContent=nome;v.textContent=money.format(valor);div.append(l,v);$('os-totals').append(div);});
  }
  function numero(valor,preco=false,fracao=false){const i=document.createElement('input');i.type='number';i.min=preco?'0':fracao?'0.001':'1';i.max=preco?'999999999999.99':fracao?'999999999999.999':'2147483647';i.step=preco?'0.01':fracao?'0.001':'1';i.value=valor;i.required=true;return i;}
  function itens(){
    $('os-items-body').replaceChildren();
    const lista=[...ordem.servicos.map(i=>({...i,tipo:'servicos'})),...ordem.produtos.map(i=>({...i,tipo:'produtos'}))];
    $('os-items-empty').hidden=lista.length>0;
    lista.forEach((item,n)=>{
      const tr=document.createElement('tr');const celulas=[n+1,item.tipo==='servicos'?'Serviço':'Produto',item.codigo||item.servicoId,item.nome||item.descricao,null,item.unidadeMedida||'UN',null,money.format(item.subtotal),null];
      celulas.forEach(v=>{const td=document.createElement('td');if(v!==null)td.textContent=v;tr.append(td);});
      const qtd=numero(item.quantidade,false,item.tipo==='produtos'),preco=numero(item.valorUnitario,true);
      qtd.setAttribute('aria-label','Quantidade do item '+(n+1));preco.setAttribute('aria-label','Valor unitário do item '+(n+1));
      const editavel=!somenteConsulta();qtd.disabled=!editavel;preco.disabled=!editavel;
      tr.children[4].append(qtd);tr.children[6].append(preco);
      if(editavel){tr.children[8].append(botaoIcone('salvar','Salvar item '+(n+1),()=>{if(!qtd.reportValidity()||!preco.reportValidity())return;const dados={quantidade:qtd.value,valorUnitario:preco.value};executar(`/api/ordens-servico/${id}/${item.tipo}/${item.id}`,{method:'PUT',body:JSON.stringify(dados)},'Item atualizado.');}),botaoIcone('excluir','Remover item '+(n+1),()=>executar(`/api/ordens-servico/${id}/${item.tipo}/${item.id}`,{method:'DELETE'},'Item removido; eventual empenho liberado.')));
        if(item.tipo==='produtos'){const estoque=botaoIcone('estoque','Consultar estoque do item '+(n+1),()=>consultarEstoqueProduto(item.produtoId,estoque));tr.children[8].append(estoque);}
      }else tr.children[8].textContent='Somente consulta';
      $('os-items-body').append(tr);
    });
  }
  function configurarInclusao(){
    const tipo=$('os-add-type').value,produto=tipo==='produtos';$('os-add-reference').replaceChildren();
    referencia=criarBusca({nome:'add-reference',rotulo:produto?'Produto':'Serviço',registros:()=>produto?produtos:servicos,descricao:r=>r.descricao&&produto?r.descricao:r.nome,codigo:r=>produto?r.codigo:String(r.id),chaves:r=>produto?[r.id,r.codigo,r.descricao]:[r.id,r.nome,r.descricao],aoSelecionar:r=>{const sugerido=r?(produto?r.precoVenda:r.preco):0;$('os-add-price').value=sugerido;$('os-add-price').min=administrador?'0':String(sugerido);$('os-add-info').textContent=r&&produto?`${r.unidadeMedida||'Unidade não informada'} · Estoque físico: ${r.estoque}; empenho: ${r.empenho}; disponível: ${r.saldo}.`:'';}});
    $('os-add-reference').append(referencia.elemento);$('os-add-quantity').min=produto?'0.001':'1';$('os-add-quantity').step=produto?'0.001':'1';$('os-add-quantity').max=produto?'999999999999.999':'2147483647';$('os-add-quantity').value='1';$('os-add-price').value=0;$('os-add-price').min='0';$('os-add-price').title=administrador?'':'O valor pode ser aumentado, mas não reduzido abaixo do preço cadastrado.';$('os-add-info').textContent='';
  }
  function renderizar(){cabecalho();totais();itens();$('os-add-panel').hidden=somenteConsulta();configurarInclusao();$('os-workspace').hidden=false;}
  async function carregar(){if(ocupado)return;const atual=++geracao;$('os-reload').disabled=true;try{const [os]=await Promise.all([api('/api/ordens-servico/consulta/'+id),catalogos()]);if(atual!==geracao)return;ordem=os;renderizar();}catch(e){erro(e);}finally{$('os-reload').disabled=false;}}
  async function executar(url,options,mensagem){
    if(ocupado)return;ocupado=true;const atual=++geracao;$('os-workspace').querySelectorAll('button,input,select,textarea').forEach(e=>e.disabled=true);
    try{const os=await api(url,options);if(atual!==geracao)return;ordem=os;notice(mensagem);try{await catalogos();}catch(e){erro(e);}renderizar();}
    catch(e){erro(e);}
    finally{ocupado=false;$('os-workspace').querySelectorAll('button,input,select,textarea').forEach(e=>e.disabled=false);if(ordem&&somenteConsulta()){[cliente,moto,funcionario].forEach(b=>b.desabilitar(true));$('header-data').disabled=$('header-descricao').disabled=$('header-parecer').disabled=true;$('os-items-body').querySelectorAll('input').forEach(e=>e.disabled=true);}}
  }
  async function criarRetrabalho(){
    if(ocupado)return;ocupado=true;$('os-workspace').querySelectorAll('button').forEach(e=>e.disabled=true);
    try{const nova=await api(`/api/ordens-servico/${id}/retrabalho`,{method:'POST'});notice('Retrabalho criado na OS '+nova.id+'.');window.location.assign('/ordens-servico/'+nova.id);}
    catch(e){erro(e);ocupado=false;$('os-workspace').querySelectorAll('button').forEach(e=>e.disabled=false);if(ordem&&somenteConsulta())[cliente,moto,funcionario].forEach(b=>b.desabilitar(true));}
  }
  $('os-header-form').addEventListener('submit',e=>{e.preventDefault();if(ocupado||somenteConsulta())return;if(!cliente.validar()||!moto.validar()||!funcionario.validar()||!e.target.reportValidity())return;executar('/api/ordens-servico/'+id,{method:'PUT',body:JSON.stringify({clienteId:cliente.valor(),motocicletaId:moto.valor(),funcionarioId:funcionario.valor(),dataAbertura:$('header-data').value,descricao:$('header-descricao').value.trim(),parecerTecnico:$('header-parecer').value.trim()})},'Cabeçalho salvo.');});
  $('os-add-form').addEventListener('submit',e=>{e.preventDefault();if(ocupado||somenteConsulta())return;if(!referencia.validar()||!e.target.reportValidity())return;const tipo=$('os-add-type').value,dados={quantidade:$('os-add-quantity').value};dados.valorUnitario=$('os-add-price').value;if(tipo==='produtos')dados.produtoId=referencia.valor();else dados.servicoId=referencia.valor();executar(`/api/ordens-servico/${id}/${tipo}`,{method:'POST',body:JSON.stringify(dados)},tipo==='produtos'?'Produto empenhado na OS.':'Serviço adicionado.');});
  $('os-add-type').addEventListener('change',configurarInclusao);$('os-reload').addEventListener('click',carregar);carregar();
})();
