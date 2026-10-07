'use strict';
(() => {
  let ordens=[],geracao=0,cliente,moto,funcionario;
  const colunas=[['id','Nº OS'],['dataAbertura','Abertura'],['clienteNome','Cliente'],['placa','Placa / chassi'],['funcionarioNome','Responsável'],['situacao','Status'],['valorTotal','Total']];
  const estado=v=>({ABERTA:'Aberta',EM_ANDAMENTO:'Em andamento',FINALIZADA:'Finalizada'}[v]||v);
  $('breadcrumb').textContent=$('page-title').textContent='Ordens de serviço'; document.title='Ordens de serviço · OFICYNIC';
  $('listing').hidden=$('new-record').hidden=false; $('new-label').textContent='Abrir OS';
  $('search').placeholder='Número, cliente, responsável, placa, chassi ou descrição';
  const head=document.createElement('tr'); [...colunas,['acao','Ação']].forEach(([,nome])=>{const th=document.createElement('th');th.scope='col';th.textContent=nome;head.append(th);});$('table-head').append(head);
  function renderizar(){
    const termo=$('search').value.trim().toLocaleLowerCase('pt-BR');
    const lista=ordens.filter(o=>[...colunas,['chassi'],['descricao']].some(([k])=>String(o[k]??'').toLocaleLowerCase('pt-BR').includes(termo)));
    $('table-body').replaceChildren();
    lista.forEach(o=>{const tr=document.createElement('tr');colunas.forEach(([k])=>{const td=document.createElement('td');td.textContent=k==='valorTotal'?money.format(o[k]):k==='situacao'?estado(o[k]):k==='placa'?(o.placa||o.chassi||'Não informado'):k==='dataAbertura'?(o[k]?.split('-').reverse().join('/')||'—'):(o[k]??'Não informado');tr.append(td);});
      const td=document.createElement('td');const link=document.createElement('a');link.className='button';link.href='/ordens-servico/'+o.id;link.textContent='Consultar OS '+o.id;td.append(link);tr.append(td);$('table-body').append(tr);});
    $('empty-state').hidden=lista.length>0; $('empty-title').textContent='Nenhuma OS encontrada';$('empty-description').textContent='Use Abrir OS para registrar um atendimento.';$('empty-add').hidden=true;$('record-count').textContent=lista.length+' de '+ordens.length+' ordens';
  }
  async function carregar(){
    $('loading').hidden=false;$('refresh').disabled=true;$('empty-state').hidden=true;
    try{ordens=await api('/api/ordens-servico/consulta');renderizar();}catch(e){notice(e.mensagem,true);$('empty-state').hidden=true;$('record-count').textContent='Não foi possível carregar as ordens';}
    finally{$('loading').hidden=true;$('refresh').disabled=false;}
  }
  function campo(nome,rotulo,tipo,valor){const div=document.createElement('div');div.className='form-field'+(tipo==='textarea'?' wide':'');const label=document.createElement('label');label.htmlFor='os-'+nome;label.textContent=rotulo+' *';const input=document.createElement(tipo==='textarea'?'textarea':'input');if(tipo!=='textarea')input.type=tipo;input.id='os-'+nome;input.required=true;input.value=valor; if(tipo==='textarea'){input.rows=4;input.maxLength=255;}div.append(label,input);$('form-fields').append(div);return input;}
  async function abrir(){
    if(saving||dialog.open)return;const atual=++geracao;$('record-form').reset();$('form-fields').replaceChildren();$('form-error').hidden=true;
    $('form-title').textContent='Abrir ordem de serviço';$('form-description').textContent='Carregando cadastros…';$('save-record').disabled=true;$('save-record').hidden=false;$('save-record').textContent='Abrir OS';dialog.showModal();
    try{
      const [clientes,motos,funcionarios]=await Promise.all([api('/api/clientes'),api('/api/motocicletas'),api('/api/funcionarios')]);if(atual!==geracao||!dialog.open)return;
      if(!clientes.length||!motos.length||!funcionarios.length){$('form-description').textContent='Cadastre cliente, moto com proprietário e funcionário responsável antes de abrir uma OS.';return;}
      cliente=criarBusca({nome:'os-cliente',rotulo:'Cliente',registros:()=>clientes,descricao:c=>c.nome,chaves:c=>[c.id,c.nome],aoSelecionar:()=>moto?.limpar()});
      moto=criarBusca({nome:'os-moto',rotulo:'Motocicleta',registros:()=>motos.filter(m=>String(m.clienteId)===String(cliente.valor())),descricao:m=>(m.placa||m.chassi)+' · '+m.marca+' '+m.modelo,chaves:m=>[m.id,m.placa,m.chassi,m.marca+' '+m.modelo]});
      funcionario=criarBusca({nome:'os-funcionario',rotulo:'Funcionário responsável',registros:()=>funcionarios,descricao:f=>f.nome,chaves:f=>[f.id,f.nome]});
      $('form-fields').append(cliente.elemento,moto.elemento,funcionario.elemento);
      const d=new Date();campo('data','Data de abertura','date',`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`);campo('descricao','Descrição / observação','textarea','');
      $('form-description').textContent='Digite código ou nome e confirme a referência. A lupa abre a pesquisa.';$('save-record').disabled=false;cliente.input.focus();
    }catch(e){if(atual===geracao&&dialog.open){$('form-error').textContent=e.mensagem;$('form-error').hidden=false;}}
  }
  $('record-form').addEventListener('submit',async e=>{
    e.preventDefault();if(saving||$('save-record').disabled)return;
    if(!cliente.validar()||!moto.validar()||!funcionario.validar()||!e.target.reportValidity())return;
    saving=true;dialog.querySelectorAll('button').forEach(b=>b.disabled=true);$('form-error').hidden=true;
    let criada=false;
    try{const os=await api('/api/ordens-servico/abertura',{method:'POST',body:JSON.stringify({clienteId:cliente.valor(),motocicletaId:moto.valor(),funcionarioId:funcionario.valor(),dataAbertura:$('os-data').value,descricao:$('os-descricao').value.trim()})});criada=true;dialog.close();notice('OS '+os.id+' criada. Consulte-a na lista se a página não abrir.');carregar();window.location.assign('/ordens-servico/'+os.id);}
    catch(e){$('form-error').textContent=e.mensagem;$('form-error').hidden=false;}
    finally{if(!criada){saving=false;dialog.querySelectorAll('button').forEach(b=>b.disabled=false);}}
  });
  $('new-record').addEventListener('click',abrir);$('refresh').addEventListener('click',carregar);$('search').addEventListener('input',renderizar);
  $('close-dialog').addEventListener('click',()=>dialog.close());$('cancel-dialog').addEventListener('click',()=>dialog.close());dialog.addEventListener('close',()=>geracao++);carregar();
})();
