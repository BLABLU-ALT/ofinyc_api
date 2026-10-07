'use strict';
// Referências pesquisáveis: o texto digitado nunca é tratado como um ID confirmado.
function criarBusca({nome, rotulo, registros, descricao, chaves, codigo = r => String(r.id), aoSelecionar = () => {}}) {
  const campo = document.createElement('div'); campo.className = 'form-field';
  const label = document.createElement('label'); label.htmlFor = nome; label.textContent = rotulo + ' *';
  const caixa = document.createElement('div'); caixa.className = 'lookup-field';
  const texto = document.createElement('input'); texto.id = nome; texto.type = 'text'; texto.required = true;
  texto.autocomplete = 'off'; texto.placeholder = 'Código ou descrição';
  const lupa = document.createElement('button'); lupa.type = 'button'; lupa.className = 'lookup-button';
  lupa.textContent = '⌕'; lupa.setAttribute('aria-label', 'Pesquisar ' + rotulo.toLocaleLowerCase('pt-BR'));
  const ajuda = document.createElement('small'); ajuda.className = 'field-help'; ajuda.textContent = 'Digite o código ou nome, ou use a lupa para selecionar.';
  const normalizar = v => String(v ?? '').trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  let escolhido = null;
  const rotuloRegistro = r => codigo(r) + ' · ' + descricao(r);
  function selecionar(registro) {
    escolhido = registro || null; texto.value = registro ? rotuloRegistro(registro) : '';
    texto.setCustomValidity(''); texto.removeAttribute('aria-invalid'); aoSelecionar(escolhido);
  }
  function resolver() {
    if (escolhido && registros().some(r => r.id === escolhido.id) && texto.value === rotuloRegistro(escolhido)) return escolhido;
    escolhido = null;
    const termo = normalizar(texto.value);
    const encontrados = registros().filter(r => (chaves(r)).some(v => normalizar(v) === termo));
    if (termo && encontrados.length === 1) { selecionar(encontrados[0]); return escolhido; }
    return null;
  }
  texto.addEventListener('input', () => { escolhido = null; texto.setCustomValidity(''); aoSelecionar(null); });
  texto.addEventListener('blur', resolver);
  texto.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); if (!resolver()) abrir(); } });
  function abrir() {
    if (lupa.disabled || texto.disabled || texto.readOnly) return;
    const janela = document.createElement('dialog'); janela.className = 'lookup-dialog';
    const tituloId = nome + '-busca-titulo'; janela.setAttribute('aria-labelledby', tituloId);
    const topo = document.createElement('div'); topo.className = 'dialog-header';
    const titulo = document.createElement('h2'); titulo.id = tituloId; titulo.textContent = 'Pesquisar ' + rotulo;
    const fechar = document.createElement('button'); fechar.type = 'button'; fechar.className = 'close-button';
    fechar.textContent = '×'; fechar.setAttribute('aria-label','Fechar pesquisa'); fechar.addEventListener('click', () => janela.close()); topo.append(titulo, fechar);
    const corpo = document.createElement('div'); corpo.className = 'dialog-body';
    const pesquisaLabel = document.createElement('label'); pesquisaLabel.textContent = 'Código ou descrição';
    const pesquisa = document.createElement('input'); pesquisa.type = 'search'; pesquisa.value = escolhido ? '' : texto.value;
    pesquisaLabel.append(pesquisa);
    const scroll = document.createElement('div'); scroll.className = 'table-scroll';
    const tabela = document.createElement('table'); const head = document.createElement('thead'); const row = document.createElement('tr');
    ['Código', 'Descrição', 'Ação'].forEach(v => { const th = document.createElement('th'); th.scope = 'col'; th.textContent = v; row.append(th); }); head.append(row);
    const body = document.createElement('tbody'); tabela.append(head, body); scroll.append(tabela);
    const estado = document.createElement('p'); estado.setAttribute('role','status'); estado.setAttribute('aria-live','polite');
    function renderizar() {
      body.replaceChildren(); const termo = normalizar(pesquisa.value);
      const encontrados = registros().filter(r => chaves(r).some(v => normalizar(v).includes(termo)));
      encontrados.forEach(r => {
        const tr = document.createElement('tr'); const cod = document.createElement('td'); cod.textContent = codigo(r);
        const desc = document.createElement('td'); desc.textContent = descricao(r);
        const acao = document.createElement('td'); const button = document.createElement('button'); button.type = 'button'; button.className = 'button';
        button.textContent = 'Selecionar'; button.setAttribute('aria-label', 'Selecionar ' + rotuloRegistro(r));
        button.addEventListener('click', () => { selecionar(r); janela.close(); }); acao.append(button); tr.append(cod, desc, acao); body.append(tr);
      });
      estado.textContent = encontrados.length ? encontrados.length + ' registro(s)' : 'Nenhum registro encontrado.';
    }
    pesquisa.addEventListener('input', renderizar); corpo.append(pesquisaLabel, scroll, estado); janela.append(topo, corpo);
    document.body.append(janela);
    janela.addEventListener('close', () => { janela.remove(); if (texto.isConnected && !texto.disabled) texto.focus(); }, {once:true});
    renderizar(); janela.showModal(); pesquisa.focus();
  }
  lupa.addEventListener('click', abrir); caixa.append(texto, lupa); campo.append(label, caixa, ajuda);
  return {
    elemento: campo, input: texto,
    selecionarId(id) { selecionar(registros().find(r => String(r.id) === String(id)) || null); },
    limpar() { selecionar(null); },
    validar() { if (resolver()) return true; texto.setCustomValidity('Selecione um cadastro válido pela pesquisa.'); texto.setAttribute('aria-invalid','true'); texto.reportValidity(); return false; },
    valor() { return resolver()?.id ?? null; },
    desabilitar(valor) { texto.disabled = valor; lupa.disabled = valor; }
  };
}
