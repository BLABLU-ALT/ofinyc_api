# Apontamentos do orientador — OFICYNIC

Tarefa: TAR-2026-10-21-001. Fonte: pedido completo recebido em 21/10/2026.

Status: inspeção realizada; execução por etapas em andamento. Nenhum requisito marcado sem validação.

- [ ] REQ-01 — Cadastro condicional de cliente e motocicleta (IF)
- [ ] REQ-02 — Botão limpar campos
- [ ] REQ-03 — Alinhamento dos botões
- [ ] REQ-04 — Data automática na criação da OS
- [ ] REQ-05 — Atualização após aprovação do cliente
- [ ] REQ-06 — Renomear campo
- [ ] REQ-07 — Criar campo Parecer Técnico
- [ ] REQ-08 — Permissões de edição
- [ ] REQ-09 — Validar impacto de alterações de preços
- [ ] REQ-10 — Preço de custo e preço de venda
- [ ] REQ-11 — Regras de preços dos funcionários
- [ ] REQ-12 — Funcionário ativo/inativo
- [ ] REQ-13 — Ocultar funcionários inativos na abertura da OS
- [ ] REQ-14 — Controle exclusivo do administrador
- [ ] REQ-15 — Botões somente com ícones
- [ ] REQ-16 — Organização da navegação
- [ ] REQ-17 — Visão Geral
- [ ] REQ-18 — Documento de aprovação
- [ ] REQ-19 — Criar aba Relatórios
- [ ] REQ-20 — Filtros interativos
- [ ] REQ-21 — Exportação
- [ ] REQ-22 — Relatórios de OS
- [ ] REQ-23 — Relatório de controle financeiro
- [ ] REQ-24 — Segurança dos relatórios

## Diagnóstico inicial

- REQ-01: resolvido pelo usuário: IF significa mostrar cadastro apenas quando necessário; não é um novo campo. Implementado e coberto por DOM.
- Cadastro rápido de cliente na OS já existe; preservar seleção e dados salvos ao limpar rascunho.
- REQ-09: itens já armazenam valorUnitario próprio; falta repetir cenário de preço histórico nesta entrega.
- Datas hoje vêm do navegador e podem ser alteradas. Não há evento de aprovação nem datas originais separadas.
- Existe apenas conta técnica ROLE_USER. Perfis administrativos e operacionais precisam ser implementados e testados no servidor nas etapas seguintes.
- Não há PDF/XLSX reais nem central administrativa de relatórios.

## Última atividade e próximo passo

### TAR-2026-10-21-001 — Apontamentos do orientador: andamento

- Data: iniciado em 2026-10-21; registro retomado em 2026-10-27.
- Pedido: atender os 24 apontamentos, por etapas, preservando dados e simplicidade Java/Spring.
- Objetivo desta entrega parcial: REQ01–05, cadastro condicional, limpeza segura, alinhamento, datas automáticas e aprovação.
- Situação: EM_ANDAMENTO; não equivale à conclusão dos 24 requisitos.
- RF/RNF/RN: fluxo de OS RF06–09/RF11, integridade e histórico RN05–07 e RNF07; impactos acadêmicos sujeitos a atualização futura.
- Decisões explícitas: IF significa exibir cadastro somente quando necessário; aprovação inicia atendimento; conta atual será administrador e funcionários terão login próprio nas etapas posteriores.
- Implementado: limpeza apenas de rascunho novo; ações condicionais cliente/moto; alinhamento CSS; data do servidor em America/Sao_Paulo; aprovação única iniciando atendimento; preservação da criação e da abertura anterior legada.
- Arquivos afetados: templates/cadastros.html; static/js/{app,busca,ordens-servico,os-detalhe}.js; static/css/app.css; OrdemServico, OrdemServicoService, OrdemServicoController; V9__datas_aprovacao_os.sql; FluxoOsIntegrationTest; MigracoesIntegrationTest; harnesses locais.
- Testes e resultados: relatórios Surefire de 21/10 conferidos em 27/10: 130 testes, zero falhas/erros; execução isolada PostgreSQL 18.1/55434. Incluem concorrência, rollback, upgrade V8, legado, CSRF e fuso. Harnesses DOM previamente PASS; conferência visual em andamento. Não confundir testes isolados com implantação no banco de estudo.
- Pareceres: PostgreSQL POS APROVADO nesta retomada; negócio/TCC e backend/frontend solicitados novamente. Revisões anteriores foram interrompidas por limite de uso.
- Impacto no TCC: documentar cadastro condicional, limpeza de rascunho e distinção entre criação, aprovação e abertura operacional; legado sem criação conhecida permanece nulo. Originais acadêmicos não editados. Perfis, parecer técnico, PDF e relatórios ainda não devem ser descritos como implementados.
- Pendências: validação visual REQ03; pareceres finais restantes; etapas seguintes REQ06–24. Sem commit/push automático.
Próximo passo exato: finalizar inspeção visual e pareceres REQ01–05; depois implementar REQ06–08 (orientação, parecer e permissões), com perfis autorizados, seguindo a ordem do pedido.
