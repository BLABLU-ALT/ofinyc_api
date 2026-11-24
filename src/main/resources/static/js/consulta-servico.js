'use strict';
async function consultarOrdensServico(id, origem) {
  const janela=document.createElement('dialog');janela.setAttribute('aria-labelledby','servico-os-titulo');
  const topo=document.createElement('div');topo.className='dialog-header';
  const titulo=document.createElement('h2');titulo.id='servico-os-titulo';titulo.textContent='OS abertas com este serviço';
  const fechar=document.createElement('button');fechar.type='button';fechar.className='close-button';fechar.textContent='×';fechar.setAttribute('aria-label','Fechar consulta de OS');fechar.onclick=()=>janela.close();
  topo.append(titulo,fechar);
  const corpo=document.createElement('div');corpo.className='dialog-body';
  const mensagem=document.createElement('p');mensagem.setAttribute('role','status');mensagem.textContent='Carregando OS…';
  const lista=document.createElement('ul');corpo.append(mensagem,lista);janela.append(topo,corpo);document.body.append(janela);
  janela.addEventListener('close',()=>{janela.remove();if(origem?.isConnected)origem.focus();},{once:true});
  janela.showModal();fechar.focus();
  try {
    const ordens=await api('/api/servicos/'+id+'/ordens-abertas');if(!janela.open||!janela.isConnected)return;
    const situacoes={ORCAMENTO:'Orçamento',EM_ANDAMENTO:'Em andamento',RETRABALHO:'Retrabalho'};
    ordens.forEach(os=>{const linha=document.createElement('li'),link=document.createElement('a');link.href='/ordens-servico/'+os.id;link.textContent='OS '+os.id;linha.append(link,document.createTextNode(' · '+(os.cliente||'Cliente não informado')+' · '+(situacoes[os.situacao]||os.situacao)));lista.append(linha);});
    mensagem.textContent=ordens.length?ordens.length+' OS aberta(s).':'Nenhuma OS aberta contém este serviço.';
  } catch(erro) {if(janela.open&&janela.isConnected){mensagem.className='notice error';mensagem.textContent=erro.mensagem||'Não foi possível consultar as OS.';}}
}