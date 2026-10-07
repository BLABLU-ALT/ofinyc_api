'use strict';
async function consultarEstoqueProduto(id, origem) {
  const janela=document.createElement('dialog'); janela.setAttribute('aria-labelledby','produto-stock-title');
  const topo=document.createElement('div'); topo.className='dialog-header';
  const titulo=document.createElement('h2'); titulo.id='produto-stock-title'; titulo.textContent='Estoque do produto';
  const fechar=document.createElement('button'); fechar.type='button'; fechar.className='close-button'; fechar.textContent='×'; fechar.setAttribute('aria-label','Fechar consulta de estoque');
  fechar.addEventListener('click',()=>janela.close()); topo.append(titulo,fechar);
  const corpo=document.createElement('div'); corpo.className='dialog-body';
  const mensagem=document.createElement('p'); mensagem.setAttribute('role','status'); mensagem.textContent='Carregando estoque…';
  const dados=document.createElement('div'); const atualizar=document.createElement('button'); atualizar.type='button'; atualizar.className='button'; atualizar.textContent='Atualizar estoque';
  corpo.append(mensagem,dados,atualizar); janela.append(topo,corpo); document.body.append(janela);
  let geracao=0;
  async function carregar() {
    const atual=++geracao; atualizar.disabled=true; mensagem.className=''; mensagem.textContent='Carregando estoque…';
    try {
      const consulta=await api('/api/produtos/'+id+'/estoque'); if(!janela.open||atual!==geracao)return;
      const p=consulta.produto; titulo.textContent=p.codigo+' · '+p.descricao; dados.replaceChildren();
      const unidade=document.createElement('p'); unidade.textContent='Unidade: '+(p.unidadeMedida||'Não informada'); dados.append(unidade);
      const totais=document.createElement('div'); totais.className='os-totals';
      [['Estoque físico',p.estoque],['Empenho',p.empenho],['Saldo disponível',p.saldo]].forEach(([nome,valor])=>{
        const box=document.createElement('div'); const label=document.createElement('span'); label.textContent=nome;
        const numero=document.createElement('strong'); numero.textContent=new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(valor); box.append(label,numero); totais.append(box);
      }); dados.append(totais);
      const explicacao=document.createElement('p'); explicacao.textContent='Disponível = estoque físico − empenho. Produtos de OS não finalizadas ficam reservados; a saída física acontece na finalização.'; dados.append(explicacao);
      const subtitulo=document.createElement('h3'); subtitulo.textContent='Empenhos em OS'; dados.append(subtitulo);
      consulta.reservas.forEach(r=> {
        const linha=document.createElement('p'); const link=document.createElement('a'); link.className='stock-os-link'; link.href='/ordens-servico/'+r.ordemServicoId; link.textContent='OS '+r.ordemServicoId;
        linha.append(link,document.createTextNode(` · ${r.clienteNome||'Cliente não informado'} · ${new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(r.quantidade)} ${r.unidadeMedida}`)); dados.append(linha);
      });
      mensagem.textContent=consulta.reservas.length?'Dados atualizados.':'Nenhum empenho neste produto.';
    } catch(error) { if(janela.open&&atual===geracao){mensagem.textContent=error.mensagem; mensagem.className='notice error';} }
    finally { if(janela.open&&atual===geracao)atualizar.disabled=false; }
  }
  atualizar.addEventListener('click',carregar);
  janela.addEventListener('close',()=>{geracao++;janela.remove();if(origem?.isConnected)origem.focus();},{once:true});
  janela.showModal(); fechar.focus(); await carregar();
}
