'use strict';
(() => {
  const situacoes={ORCAMENTO:'Orçamento',EM_ANDAMENTO:'Em andamento',CONCLUIDA:'Concluída',CANCELADA:'Cancelada',RETRABALHO:'Retrabalho'};
  const data=valor=>valor?valor.split('-').reverse().join('/'):'—';
  const dinheiro=valor=>valor==null?'—':money.format(valor);
  const relatorios={
    ordens:{titulo:'Relatório de ordens de serviço',api:'/api/relatorios/ordens-servico',
      colunas:[['Nº OS',l=>l.numero],['Cliente',l=>l.cliente],['Veículo',l=>l.veiculo],['Abertura',l=>data(l.dataAbertura)],['Status',l=>situacoes[l.situacao]],
        ['Serviços',l=>dinheiro(l.totalServicos),true],['Produtos',l=>dinheiro(l.totalProdutos),true],['Valor total',l=>dinheiro(l.valorTotal),true]]},
    financeiro:{titulo:'Relatório de controle financeiro',api:'/api/relatorios/financeiro',
      colunas:[['Nº OS',l=>l.numero],['Cliente',l=>l.cliente],['Status',l=>situacoes[l.situacao]],['Abertura',l=>data(l.dataAbertura)],['Aprovação',l=>data(l.dataAprovacao)],
        ['Serviços',l=>dinheiro(l.totalServicos),true],['Produtos',l=>dinheiro(l.totalProdutos),true],['Valor total',l=>dinheiro(l.valorTotal),true],
        ['Custo dos produtos',l=>l.custoProdutos==null?'Sem custo registrado':dinheiro(l.custoProdutos),true],['Margem dos produtos',l=>l.margemProdutos==null?'—':dinheiro(l.margemProdutos),true]]}
  };
  let tipo=new URLSearchParams(location.search).get('tipo')==='financeiro'?'financeiro':'ordens';
  $('breadcrumb').textContent=$('page-title').textContent='Relatórios';document.title='Relatórios · OFICYNIC';
  let clientes=[];
  const cliente=criarBusca({nome:'report-cliente',rotulo:'Cliente',registros:()=>clientes,descricao:c=>c.nome,chaves:c=>[c.id,c.nome],obrigatorio:false});
  cliente.input.placeholder='Todos os clientes';$('report-cliente-campo').replaceWith(cliente.elemento);
  decorarBotao($('report-search'),'consultar');decorarBotao($('report-clear'),'limpar');decorarBotao($('report-pdf'),'pdf');decorarBotao($('report-excel'),'excel');

  function filtros(){
    const parametros=new URLSearchParams();
    if(cliente.valor())parametros.set('clienteId',cliente.valor());
    [['numero','report-numero'],['situacao','report-situacao'],['inicio','report-inicio'],['fim','report-fim']]
      .forEach(([nome,campo])=>{const valor=$(campo).value.trim();if(valor)parametros.set(nome,valor);});
    return parametros;
  }
  function validar(){
    if($('report-inicio').value&&$('report-fim').value&&$('report-inicio').value>$('report-fim').value){notice('A data inicial não pode ser posterior à data final.',true);return false;}
    return cliente.validar()&&$('report-filters').reportValidity();
  }
  function resumo(r){
    const cartoes=[['Ordens de serviço',r.quantidadeOs],['Aguardando aprovação',dinheiro(r.valorAguardandoAprovacao)],['Aprovado pelo cliente',dinheiro(r.valorAprovado)],['Concluído (executado)',dinheiro(r.valorConcluido)],
      ['Total de serviços',dinheiro(r.totalServicos)],['Total de produtos',dinheiro(r.totalProdutos)],
      ['Custo dos produtos concluídos',r.custoProdutosConcluidos==null?'Indisponível ('+r.itensConcluidosSemCusto+' item(ns) sem custo)':dinheiro(r.custoProdutosConcluidos)],
      ['Margem dos produtos concluídos',r.margemProdutosConcluidos==null?'Indisponível':dinheiro(r.margemProdutosConcluidos)]];
    $('report-summary').replaceChildren(...cartoes.map(([nome,valor])=>{const div=document.createElement('div'),s=document.createElement('span'),v=document.createElement('strong');s.textContent=nome;v.textContent=valor;div.append(s,v);return div;}));
    $('report-summary').hidden=false;$('report-note').textContent=r.observacao+' Cancelado (fora dos totais): '+dinheiro(r.valorCancelado)+'.';
  }
  async function pesquisar(){
    if(!validar())return;
    const def=relatorios[tipo];$('report-search').disabled=true;$('report-info').textContent='Consultando…';
    try{
      const dados=await api(def.api+'?'+filtros());
      const linhas=tipo==='financeiro'?dados.linhas:dados;
      $('report-title').textContent=def.titulo;
      const cabecalho=document.createElement('tr');def.colunas.forEach(([nome])=>{const th=document.createElement('th');th.scope='col';th.textContent=nome;cabecalho.append(th);});$('report-head').replaceChildren(cabecalho);
      $('report-body').replaceChildren(...linhas.map(linha=>{const tr=document.createElement('tr');def.colunas.forEach(([,valor,numero])=>{const td=document.createElement('td');td.textContent=valor(linha)??'—';if(numero)td.className='numero';tr.append(td);});return tr;}));
      $('report-empty').hidden=linhas.length>0;$('report-info').textContent=linhas.length+' registro(s) encontrado(s).';
      if(tipo==='financeiro')resumo(dados.resumo);else{$('report-summary').hidden=true;$('report-note').textContent='Período pela data de abertura da OS.';}
    }catch(e){notice(e.mensagem||'Não foi possível gerar o relatório.',true);$('report-info').textContent='';}
    finally{$('report-search').disabled=false;}
  }
  function exportar(formato){if(!validar())return;window.open(relatorios[tipo].api+'/'+formato+'?'+filtros(),'_blank','noopener');}
  function selecionar(novo){tipo=novo;document.querySelectorAll('.report-tabs .button').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tipo===tipo)));pesquisar();}

  document.querySelectorAll('.report-tabs .button').forEach(b=>b.addEventListener('click',()=>selecionar(b.dataset.tipo)));
  $('report-filters').addEventListener('submit',e=>{e.preventDefault();pesquisar();});
  $('report-clear').addEventListener('click',()=>{$('report-filters').reset();cliente.limpar();pesquisar();});
  // Botao Exportar PDF fica desativado porque nesta versao so exporta Excel
  $('report-excel').addEventListener('click',()=>exportar('excel'));
  api('/api/clientes').then(lista=>{clientes=lista.sort((a,b)=>a.nome.localeCompare(b.nome,'pt-BR'));})
    .catch(e=>notice(e.mensagem||'Não foi possível carregar os clientes.',true));
  $('reports-workspace').hidden=false;selecionar(tipo);
})();
