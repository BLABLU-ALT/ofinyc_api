'use strict';
(() => {
  const id=document.body.dataset.ordemId;
  let ordem,clientes=[],motos=[],funcionarios=[],servicos=[],produtos=[],referencia,cliente,moto,funcionario,ocupado=false,geracao=0;
  const estados={ABERTA:'Aberta',EM_ANDAMENTO:'Em andamento',FINALIZADA:'Finalizada'};
  $('page-title').textContent='Ordem de serviço nº '+id;$('breadcrumb').textContent='OS '+id;document.title='OS '+id+' · OFICYNIC';
  function botao(texto,acao){const b=document.createElement('button');b.type='button';b.className='button';b.textContent=texto;b.addEventListener('click',acao);return b;}
  function erro(e){notice(e.mensagem||'Confira os dados e tente novamente.',true);$('notice').scrollIntoView({block:'nearest'});}
  function campo(nome,label,tipo,valor){const div=document.createElement('div');div.className='form-field'+(tipo==='textarea'?' wide':'');const l=document.createElement('label');l.htmlFor=nome;l.textContent=label+' *';const input=document.createElement(tipo==='textarea'?'textarea':'input');input.id=nome;input.required=true;input.value=valor??'';if(tipo!=='textarea')input.type=tipo;else{input.maxLength=255;input.rows=3;}div.append(l,input);$('os-header-fields').append(div);return input;}
  async function catalogos(){[clientes,motos,funcionarios,servicos,produtos]=await Promise.all([api('/api/clientes'),api('/api/motocicletas'),api('/api/funcionarios'),api('/api/servicos'),api('/api/produtos')]);}
  function cabecalho(){
    $('os-header-fields').replaceChildren();
    const finalizada=ordem.situacao==='FINALIZADA';
    cliente=criarBusca({nome:'header-cliente',rotulo:'Cliente',registros:()=>clientes,descricao:c=>c.nome,chaves:c=>[c.id,c.nome],aoSelecionar:()=>moto?.limpar()});
    moto=criarBusca({nome:'header-moto',rotulo:'Motocicleta',registros:()=>motos.filter(m=>String(m.clienteId)===String(cliente.valor())),descricao:m=>(m.placa||m.chassi)+' · '+m.marca+' '+m.modelo,chaves:m=>[m.id,m.placa,m.chassi,m.marca+' '+m.modelo]});
    funcionario=criarBusca({nome:'header-funcionario',rotulo:'Funcionário responsável',registros:()=>funcionarios,descricao:f=>f.nome,chaves:f=>[f.id,f.nome]});
    cliente.selecionarId(ordem.clienteId);moto.selecionarId(ordem.motocicletaId);funcionario.selecionarId(ordem.funcionarioId);
    // Exibe o vínculo antigo mesmo quando a moto ainda não tem proprietário cadastrado.
    // A confirmação para editar continua exigindo uma moto elegível do cliente.
    if(ordem.motocicletaId&&!moto.valor()){
      const vinculada=motos.find(m=>String(m.id)===String(ordem.motocicletaId));
      moto.input.value=ordem.motocicletaId+' · '+(vinculada?.placa||vinculada?.chassi||ordem.placa||ordem.chassi||'Moto vinculada');
    }
    $('os-header-fields').append(cliente.elemento,moto.elemento,funcionario.elemento);
    campo('header-data','Data de abertura','date',ordem.dataAbertura);campo('header-descricao','Descrição / observação','textarea',ordem.descricao);
    [cliente,moto,funcionario].forEach(b=>b.desabilitar(finalizada));$('header-data').disabled=$('header-descricao').disabled=finalizada;
    $('os-save-header').hidden=finalizada;$('os-status').textContent=estados[ordem.situacao];
    $('os-info').textContent=finalizada?'Finalizada em '+ordem.dataFinalizacao.split('-').reverse().join('/')+'. Dados e itens somente para consulta.':'Produtos ficam empenhados até a finalização. Salve alterações do cabeçalho antes de incluir itens.';
    if(!ordem.funcionarioId&&!finalizada)$('os-info').textContent+=' Complete o funcionário responsável desta OS antiga.';
    $('os-state-actions').replaceChildren();
    if(!finalizada){$('os-state-actions').append(botao(ordem.situacao==='ABERTA'?'Iniciar atendimento':'Voltar para aberta',()=>executar(`/api/ordens-servico/${id}/situacao`,{method:'PUT',body:JSON.stringify({situacao:ordem.situacao==='ABERTA'?'EM_ANDAMENTO':'ABERTA'})},'Status atualizado.')));
      if(ordem.situacao==='EM_ANDAMENTO')$('os-state-actions').append(botao('Finalizar OS',()=>executar(`/api/ordens-servico/${id}/finalizacao`,{method:'POST'},'OS finalizada; produtos consumidos e empenhos liberados.')));}
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
      const editavel=ordem.situacao!=='FINALIZADA';qtd.disabled=!editavel;preco.readOnly=item.tipo==='produtos';preco.disabled=!editavel;
      tr.children[4].append(qtd);tr.children[6].append(preco);
      if(editavel){tr.children[8].append(botao('Salvar',()=>{if(!qtd.reportValidity()||!preco.reportValidity())return;const dados={quantidade:qtd.value};if(item.tipo==='servicos')dados.valorUnitario=preco.value;executar(`/api/ordens-servico/${id}/${item.tipo}/${item.id}`,{method:'PUT',body:JSON.stringify(dados)},'Item atualizado.');}),botao('Remover',()=>executar(`/api/ordens-servico/${id}/${item.tipo}/${item.id}`,{method:'DELETE'},'Item removido; eventual empenho liberado.')));
        if(item.tipo==='produtos')tr.children[8].append(botao('Estoque',()=>consultarEstoqueProduto(item.produtoId,tr.children[8].querySelector('button:last-child'))));
      }else tr.children[8].textContent='Somente consulta';
      $('os-items-body').append(tr);
    });
  }
  function configurarInclusao(){
    const tipo=$('os-add-type').value,produto=tipo==='produtos';$('os-add-reference').replaceChildren();
    referencia=criarBusca({nome:'add-reference',rotulo:produto?'Produto':'Serviço',registros:()=>produto?produtos:servicos,descricao:r=>r.descricao&&produto?r.descricao:r.nome,codigo:r=>produto?r.codigo:String(r.id),chaves:r=>produto?[r.id,r.codigo,r.descricao]:[r.id,r.nome,r.descricao],aoSelecionar:r=>{$('os-add-price').value=r?(produto?r.precoVenda:r.preco):0;$('os-add-info').textContent=r&&produto?`${r.unidadeMedida||'Unidade não informada'} · Estoque físico: ${r.estoque}; empenho: ${r.empenho}; disponível: ${r.saldo}. Inclusão reserva, sem saída física.`:'';}});
    $('os-add-reference').append(referencia.elemento);$('os-add-quantity').min=produto?'0.001':'1';$('os-add-quantity').step=produto?'0.001':'1';$('os-add-quantity').max=produto?'999999999999.999':'2147483647';$('os-add-quantity').value='1';$('os-add-price').value=0;$('os-add-price').readOnly=produto;$('os-add-info').textContent='';
  }
  function renderizar(){cabecalho();totais();itens();$('os-add-panel').hidden=ordem.situacao==='FINALIZADA';configurarInclusao();$('os-workspace').hidden=false;}
  async function carregar(){if(ocupado)return;const atual=++geracao;$('os-reload').disabled=true;try{const [os]=await Promise.all([api('/api/ordens-servico/consulta/'+id),catalogos()]);if(atual!==geracao)return;ordem=os;renderizar();}catch(e){erro(e);}finally{$('os-reload').disabled=false;}}
  async function executar(url,options,mensagem){
    if(ocupado)return;ocupado=true;const atual=++geracao;$('os-workspace').querySelectorAll('button,input,select,textarea').forEach(e=>e.disabled=true);
    try{const os=await api(url,options);if(atual!==geracao)return;ordem=os;notice(mensagem);try{await catalogos();}catch(e){erro(e);}renderizar();}
    catch(e){erro(e);}
    finally{ocupado=false;$('os-workspace').querySelectorAll('button,input,select,textarea').forEach(e=>e.disabled=false);if(ordem?.situacao==='FINALIZADA'){[cliente,moto,funcionario].forEach(b=>b.desabilitar(true));$('header-data').disabled=$('header-descricao').disabled=true;$('os-items-body').querySelectorAll('input').forEach(e=>e.disabled=true);}}
  }
  $('os-header-form').addEventListener('submit',e=>{e.preventDefault();if(ocupado||ordem.situacao==='FINALIZADA')return;if(!cliente.validar()||!moto.validar()||!funcionario.validar()||!e.target.reportValidity())return;executar('/api/ordens-servico/'+id,{method:'PUT',body:JSON.stringify({clienteId:cliente.valor(),motocicletaId:moto.valor(),funcionarioId:funcionario.valor(),dataAbertura:$('header-data').value,descricao:$('header-descricao').value.trim()})},'Cabeçalho salvo.');});
  $('os-add-form').addEventListener('submit',e=>{e.preventDefault();if(ocupado||ordem.situacao==='FINALIZADA')return;if(!referencia.validar()||!e.target.reportValidity())return;const tipo=$('os-add-type').value,dados={quantidade:$('os-add-quantity').value};if(tipo==='produtos')dados.produtoId=referencia.valor();else{dados.servicoId=referencia.valor();dados.valorUnitario=$('os-add-price').value;}executar(`/api/ordens-servico/${id}/${tipo}`,{method:'POST',body:JSON.stringify(dados)},tipo==='produtos'?'Produto empenhado na OS.':'Serviço adicionado.');});
  $('os-add-type').addEventListener('change',configurarInclusao);$('os-reload').addEventListener('click',carregar);carregar();
})();
