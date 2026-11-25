'use strict';
(() => {
  const emComposicao = new WeakSet();
  const aviso = 'O texto em maiúsculas ultrapassa o limite do campo. Reduza o texto para continuar.';
  function converter(campo) {
    if (!(campo instanceof HTMLInputElement || campo instanceof HTMLTextAreaElement)) return;
    if (campo.disabled || campo.readOnly || campo.dataset.maiusculas === 'false') return;
    if (campo instanceof HTMLInputElement && !['text', 'search'].includes(campo.type)) return;
    if ([campo.name, campo.id].some(nome => /^(field-)?(login|username|email|senha|password)$/i.test(nome))) return;
    const original = campo.value;
    const maiusculas = texto => texto.toLocaleUpperCase('pt-BR');
    const valor = maiusculas(original);

    // Nao apagar conteudo quando a maiuscula vira duas letras como ß em SS
    if (campo.maxLength >= 0 && valor.length > campo.maxLength) campo.setCustomValidity(aviso);
    else if (campo.validationMessage === aviso) campo.setCustomValidity('');
    if (valor === original) return;
    const inicio = campo.selectionStart, fim = campo.selectionEnd, direcao = campo.selectionDirection;
    campo.value = valor;
    if (inicio !== null && fim !== null) campo.setSelectionRange(
      Math.min(maiusculas(original.slice(0, inicio)).length, valor.length),
      Math.min(maiusculas(original.slice(0, fim)).length, valor.length), direcao);
  }
  document.addEventListener('reset', evento => {
    for (const campo of evento.target.elements || []) {
      if (campo.validationMessage === aviso) campo.setCustomValidity('');
      emComposicao.delete(campo);
    }
  }, true);
  document.addEventListener('compositionstart', evento => emComposicao.add(evento.target), true);
  document.addEventListener('compositionend', evento => {
    emComposicao.delete(evento.target);
    converter(evento.target);
  }, true);
  document.addEventListener('input', evento => {
    if (!evento.isComposing && !emComposicao.has(evento.target)) converter(evento.target);
  }, true);
  document.addEventListener('change', evento => {
    // A lupa escreve o rotulo do registro e depende dele para manter o vinculo
    if (!evento.target.closest('.lookup-field') && !emComposicao.has(evento.target)) converter(evento.target);
  }, true);
})();