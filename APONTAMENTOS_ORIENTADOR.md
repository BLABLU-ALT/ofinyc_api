# Apontamentos do orientador — OFICYNIC

Tarefa: TAR-2026-10-21-001. Fonte: pedido completo do usuário (21/10/2026, retomado em 27–28/10/2026).

Legenda: `[ ]` pendente · `[x]` concluído e validado. Nenhum item é marcado sem implementação e verificação.

## Decisões do usuário

- REQ-01: "IF" significa exibir o cadastro de cliente/moto **somente quando necessário** na abertura da OS; não é um campo novo.
- Aprovação do cliente inicia o atendimento (ORCAMENTO → EM_ANDAMENTO) e é registrada uma única vez.
- 28/10/2026: usuários em **herança de tabela única** — `funcionarios` passa a `usuarios` com `tipo` (ADMINISTRADOR/FUNCIONARIO); Java: `Usuario` abstrata com `Administrador` e `Funcionario`.
- 28/10/2026: a regra de preço mínimo do REQ-11 vale para **produtos e serviços**.
- A conta técnica atual (APP_USER/APP_PASSWORD) é o administrador.

## Etapa 1 — Cadastro de clientes e formulários

- [x] REQ-01 — IF no cadastro do cliente ao abrir OS
  - Implementado: botão "Novo cliente" e "Cadastrar motocicleta" só aparecem quando a busca não encontra registro (`ordens-servico.js`).
  - Validação (28/10, navegador, banco de testes): oculto sem busca; visível ao digitar cliente inexistente.
- [x] REQ-02 — Botão limpar campos
  - Implementado: "Limpar" no cadastro novo e na abertura de OS; não aparece em edição/consulta e não apaga dados salvos (`app.js`, `ordens-servico.js`); restaura os padrões do formulário de funcionário.
  - Validação (28/10, navegador): limpou cliente e orientação; manteve a data do servidor.
- [x] REQ-03 — Alinhamento dos botões da OS
  - Implementado: ações agrupadas e alinhadas com mesma altura (`app.css`); ícones nos botões (REQ-15).
  - Validação (28/10, navegador): 1366 px em linha; 390 px em grade sem rolagem horizontal; ações dos itens na mesma linha.

## Etapa 2 — Datas e aprovação

- [x] REQ-04 — Data automática na criação da OS
  - Arquivos: `OrdemServicoService`, `OrdemServicoController`, `V9__datas_aprovacao_os.sql`.
  - Implementado: data do servidor (America/Sao_Paulo); campo somente leitura; edição não altera datas.
  - Validação: `FluxoOsIntegrationTest` (fuso, edição sem alterar data) — suíte 135/135 em 28/10.
- [x] REQ-05 — Atualização após aprovação do cliente
  - Implementado: `POST /api/ordens-servico/{id}/aprovacao` registra `dataAprovacao` uma vez e atualiza `dataAbertura`; `dataCriacao` preserva a criação; OS legadas guardam `dataAberturaAnterior`.
  - Validação: `FluxoOsIntegrationTest` e `MigracoesIntegrationTest` (aprovação repetida, legado, upgrade V8→V9).

## Etapa 3 — Orientação do cliente e parecer técnico

- [x] REQ-06 — Renomear para "Orientação do cliente"
  - Arquivos: `ordens-servico.js`, `os-detalhe.js`, `OrdemServicoController` (mensagens). PDF: aplicado no REQ-18.
- [x] REQ-07 — Campo Parecer técnico
  - Arquivos: `V10__parecer_tecnico_e_precos.sql` (`ordens_servico.parecer_tecnico`), `OrdemServico`, `OrdemServicoService.atualizar`, `os-detalhe.js`.
  - Implementado: campo independente (até 1000 caracteres); não sobrescreve a orientação; bloqueado em OS concluída/cancelada.
  - Validação: `ApontamentosIntegrationTest.funcionarioNaoAlteraOrientacaoMasRegistraParecer` e `parecerNaoMudaDepoisDeConcluir`.
- [x] REQ-08 — Permissões de edição
  - Implementado no backend: alteração da orientação por não administrador retorna 403 e não grava; parecer editável por ambos enquanto a OS estiver aberta. Na tela, a orientação fica somente leitura para o funcionário.
  - Validação: teste acima (funcionário 403 + banco inalterado; administrador altera).

## Etapa 4 — Produtos, preços e histórico financeiro

- [x] REQ-09 — Impacto de alteração de preços
  - Diagnóstico: itens de produto e serviço já guardavam `valor_unitario` próprio; os totais usam esse valor.
  - Validação: `alterarPrecoNoCadastroNaoMudaOsAntiga` (produto 100→150 e serviço 80→120 no cadastro; OS mantém 100 + 80 = 180). PDFs/relatórios usam os mesmos valores do item (REQ-18/22).
- [x] REQ-10 — Preço de custo e preço de venda
  - Arquivos: `V10` (`produtos.preco_custo`, `itens_produto_os.custo_unitario`), `Produto`, `ItemProdutoOS`, `ProdutoController` (record `ProdutoConsulta`), `SecurityConfig`, `app.js`.
  - Implementado: cadastro/edição de produtos e serviços só pelo administrador; preço de custo só aparece ao administrador (API devolve nulo ao funcionário); custo copiado no item da OS.
  - Validação: `precoDeCustoECatalogoSaoDoAdministrador` (403 do funcionário em POST/PUT; custo nulo em lista, detalhe e estoque).
- [x] REQ-11 — Regras de preço do funcionário
  - Implementado: preço de venda sugerido; funcionário pode aumentar e recebe 403 ao reduzir (inclusão e edição; produtos e serviços); administrador pode conceder valor menor; valor praticado salvo no item.
  - Validação: `funcionarioSoPodeAumentarOPrecoCadastrado` (140 aceito, 100/110/80 bloqueados sem gravar, administrador 100 aceito).

## Etapa 5 — Controle de funcionários

- [x] REQ-12 — Funcionário ativo/inativo
  - Arquivos: `V11__usuarios_tabela_unica.sql` (`funcionarios` → `usuarios` + `tipo`, `login`, `senha`, `ativo`), `Usuario` (abstrata), `Administrador`, `Funcionario`, `UsuarioRepository` (substitui `FuncionarioRepository`), `OrdemServico`, `MovimentacaoEstoque`, `FuncionarioController`, `app.js`.
  - Implementado: situação Ativo/Inativo no cadastro; ninguém é excluído; dados existentes viram FUNCIONARIO ativo sem login.
  - Validação: `ApontamentosIntegrationTest.somenteAdministradorGerenciaFuncionarios`, `LoginUsuarioIntegrationTest.usuarioInativoNaoEntra`, migrações V1→V11.
- [x] REQ-13 — Ocultar inativos na abertura da OS
  - Implementado: `GET /api/funcionarios/ativos` alimenta abertura de OS, cabeçalho e estoque; backend rejeita (409) inativo em nova OS, troca de responsável, movimentação manual; retrabalho não herda responsável inativo; OS antiga mantém e exibe o responsável inativo.
  - Validação: `inativoSaiDaSelecaoMasPermaneceNasOsAntigas`.
- [x] REQ-14 — Controle exclusivo do administrador
  - Implementado: `/funcionarios` e `/api/funcionarios/**` só ADMINISTRADOR (`SecurityConfig`); menu oculto ao funcionário; login pelo banco com BCrypt; conta técnica = administrador; perfil fixado no cadastro; senha nunca devolvida.
  - Validação: 403 do funcionário em GET/POST/PUT e na página; login real por formulário (`LoginUsuarioIntegrationTest`: funcionário, administrador, inativo, URL direta, login reservado).

## Etapa 6 — Interface, ícones e navegação

- [x] REQ-15 — Botões somente com ícones
  - Arquivos: `app.js` (`icone`, `botaoIcone`, `decorarBotao`), `ordens-servico.js`, `os-detalhe.js`, `app.css`.
  - Implementado: ícones SVG próprios (lupa, lápis, lixeira, disquete, impressora, documento, planilha, borracha, seta, +, atualizar, caixa); ações de tabela, voltar e atualizar só com ícone (40×40 px) com `title` e `aria-label`; ações de formulário com ícone + texto.
  - Validação (28/10, navegador): lista, OS, itens e diálogos conferidos em 1366 e 390 px.
- [ ] REQ-16 — Organização da navegação e aba Relatórios
  - Implementado: menu em grupos — Visão geral / OPERAÇÃO (Estoque, Ordens de serviço) / CADASTROS (Clientes, Motocicletas, Produtos, Serviços) / ADMINISTRAÇÃO só administrador (Funcionários); todos com ícone e `aria-current`. Validado no navegador com os dois perfis.
  - Pendente: link "Relatórios" (entra junto com a central, REQ-19).
- [x] REQ-17 — Visão Geral com totais de clientes e veículos
  - Arquivos: `PainelController` (`GET /api/painel/resumo`, `count()` no banco), `cadastros.html`, `app.js`, `app.css`.
  - Implementado: 8 cards em grade 4×2 (2×4 e 1 coluna em telas menores): clientes, veículos, total de OS, orçamento, em andamento, concluídas, canceladas, retrabalhos; painel de relatórios só para o administrador.
  - Validação: `painelMostraTotaisReaisDeClientesEVeiculos`; navegador mostrou 1/1 após cadastrar 1 cliente e 1 moto no banco de testes.

## Etapa 7 — PDF da OS

- [ ] REQ-18 — Documento de aprovação

## Etapa 8 — Central de relatórios

- [ ] REQ-19 — Aba Relatórios
- [ ] REQ-20 — Filtros interativos
- [ ] REQ-21 — Exportação PDF e Excel
- [ ] REQ-22 — Relatórios de OS
- [ ] REQ-23 — Relatório de controle financeiro
- [ ] REQ-24 — Segurança dos relatórios

## Testes executados

| Data | Comando | Resultado |
|---|---|---|
| 27/10 | `iniciar-local.ps1 -Teste` (linha de base) | 130 testes, 0 falhas |
| 28/10 | `iniciar-local.ps1 -Teste` (Etapas 3–4) | 135 testes, 0 falhas |
| 28/10 | `iniciar-local.ps1 -Teste` (Etapa 5) | 142 testes, 0 falhas |
| 28/10 | `iniciar-local.ps1 -Teste` (Etapa 6) | 143 testes, 0 falhas |
| 28/10 | Aplicação na porta 8086 com o banco de testes; navegador interno | Etapas 1–6 conferidas com administrador e funcionário reais |

Ajustes em testes existentes: passaram a rodar como `ADMINISTRADOR` (catálogo agora é administrativo); contagens de migração +1 a cada nova migração (V10, V11); o teste antigo que proibia login no cadastro de funcionário foi adaptado à decisão de 28/10 (login exige senha, perfil fixo); o teste de reserva deixou de enviar preço injetado, porque o REQ-11 passou a aceitar valor informado.

## Última atividade e próximo passo

- Última atividade: Etapa 6 concluída (REQ-16 aguarda o link Relatórios); iniciado o `PdfService` (Etapa 7).
- Próximo passo: Etapa 7 — PDF de aprovação da OS (REQ-18); depois Etapa 8 (REQ-19 a 24).
