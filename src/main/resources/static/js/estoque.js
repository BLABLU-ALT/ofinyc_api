'use strict';
(() => {
  let produtos=[],funcionarios=[],produto,funcionario,ocupado=false;
  $('page-title').textContent=$('breadcrumb').textContent='Estoque';document.title='Estoque · OFICYNIC';
  function campo(nome,rotulo,tipo,obrigatorio=true){const wrap=document.createElement('div');wrap.className='form-field';const l=document.createElement('label');l.htmlFor=nome;l.textContent=rotulo+(obrigatorio?' *':'');const e=document.createElement(tipo==='select'?'select':'input');e.id=nome;if(tipo!=='select')e.type=tipo;e.required=obrigatorio;wrap.append(l,e);$('stock-fields').append(wrap);return e;}
  function atualizarUnidade(p){
    const codigo=p?.unidadeMedida,descricao=unidadesMedida.find(([valor])=>valor===codigo)?.[1];
    $('stock-unit').value=p?(codigo?(descricao?codigo+' — '+descricao:codigo):'Não informada'):'';
    $('stock-unit-help').textContent=p&&!codigo?'Complete a unidade no cadastro do produto antes de movimentar.':'Unidade obtida do cadastro do produto.';
    document.querySelector('label[for="stock-quantity"]').textContent='Quantidade'+(codigo?' ('+codigo+')':'')+' *';
  }
  async function carregar(){
    if(ocupado)return;$('stock-save').disabled=$('stock-reload').disabled=true;
    try{const dados=await Promise.all([api('/api/produtos'),api('/api/funcionarios'),api('/api/estoque/movimentos')]);produtos=dados[0];funcionarios=dados[1];
      if(!produto){produto=criarBusca({nome:'stock-produto',rotulo:'Produto',registros:()=>produtos,codigo:p=>p.codigo,descricao:p=>p.descricao,chaves:p=>[p.id,p.codigo,p.descricao],aoSelecionar:atualizarUnidade});funcionario=criarBusca({nome:'stock-funcionario',rotulo:'Funcionário responsável',registros:()=>funcionarios,descricao:f=>f.nome,chaves:f=>[f.id,f.nome]});$('stock-fields').append(produto.elemento,funcionario.elemento);
        const unidade=campo('stock-unit','Unidade de medida','text',false);unidade.readOnly=true;unidade.placeholder='Selecione um produto';const ajuda=document.createElement('small');ajuda.id='stock-unit-help';ajuda.className='field-help';ajuda.setAttribute('aria-live','polite');unidade.setAttribute('aria-describedby',ajuda.id);unidade.parentElement.append(ajuda);
        const tipo=campo('stock-type','Tipo','select');tipo.append(new Option('Entrada','ENTRADA'),new Option('Saída','SAIDA'));const q=campo('stock-quantity','Quantidade','number');q.min='0.001';q.max='999999999999.999';q.step='0.001';q.value='1';const m=campo('stock-reason','Motivo / observação','text');m.maxLength=255;}
      const produtoId=produto.valor();atualizarUnidade(produtos.find(p=>p.id===produtoId));
      $('stock-body').replaceChildren();dados[2].forEach(m=>{const tr=document.createElement('tr');[m.id,new Date(m.data).toLocaleString('pt-BR'),m.codigo+' · '+m.descricao,m.tipo,m.quantidade,m.unidadeMedida,m.funcionarioNome,m.usuarioAtuante,m.motivo,m.ordemServicoId??'Manual'].forEach(v=>{const td=document.createElement('td');td.textContent=v;tr.append(td);});$('stock-body').append(tr);});$('stock-empty').hidden=dados[2].length>0;$('stock-workspace').hidden=false;
    }catch(e){notice(e.mensagem,true);}finally{$('stock-save').disabled=$('stock-reload').disabled=false;}
  }
  $('stock-form').addEventListener('submit',async e=>{e.preventDefault();if(ocupado||!produto?.validar()||!funcionario?.validar()||!e.target.reportValidity())return;ocupado=true;$('stock-save').disabled=true;try{await api('/api/estoque/movimentos',{method:'POST',body:JSON.stringify({produtoId:produto.valor(),funcionarioId:funcionario.valor(),tipo:$('stock-type').value,quantidade:$('stock-quantity').value,motivo:$('stock-reason').value.trim()})});notice('Movimentação registrada.');$('stock-reason').value='';ocupado=false;await carregar();}catch(e){notice(e.mensagem,true);}finally{ocupado=false;$('stock-save').disabled=false;}});
  $('stock-reload').addEventListener('click',carregar);carregar();
})();
