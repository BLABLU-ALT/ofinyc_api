package br.com.oficynic.ofinyc_api.service;

import br.com.oficynic.ofinyc_api.model.*;
import br.com.oficynic.ofinyc_api.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrdemServicoService {
    private final OrdemServicoRepository repository;
    private final ClienteRepository clientes;
    private final MotocicletaRepository motocicletas;
    private final ServicoRepository servicos;
    private final ProdutoRepository produtos;
    private final UsuarioRepository funcionarios;
    private final EstoqueService estoque;

    public OrdemServicoService(OrdemServicoRepository repository, ClienteRepository clientes,
                              MotocicletaRepository motocicletas, ServicoRepository servicos,
                              ProdutoRepository produtos, UsuarioRepository funcionarios, EstoqueService estoque) {
        this.repository = repository;
        this.clientes = clientes;
        this.motocicletas = motocicletas;
        this.servicos = servicos;
        this.produtos = produtos;
        this.funcionarios = funcionarios;
        this.estoque = estoque;
    }

    public static LocalDate dataNoFuso(java.time.Instant instante) { return instante.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate(); }

    public LocalDate dataAtual() { return dataNoFuso(java.time.Instant.now()); }

    public OrdemServico aprovar(Long id) {
        var ordem = buscarComBloqueio(id);
        conferirEditavel(ordem);
        conferirVinculos(ordem);
        if (ordem.getSituacao() != SituacaoOrdemServico.ORCAMENTO
                && !(ordem.getSituacao() == SituacaoOrdemServico.EM_ANDAMENTO && ordem.getDataAprovacao() != null))
            throw erro(HttpStatus.CONFLICT, "A aprovação exige uma OS em orçamento.");
        if (ordem.getDataAprovacao() == null) {
            if (ordem.getDataCriacao() == null) ordem.setDataAberturaAnterior(ordem.getDataAbertura());
            var hoje = dataAtual();
            ordem.setDataAprovacao(hoje);
            ordem.setDataAbertura(hoje);
        }
        ordem.setSituacao(SituacaoOrdemServico.EM_ANDAMENTO);
        repository.flush();
        return ordem;
    }

    public OrdemServico abrir(String descricao, LocalDate data, Long clienteId, Long motocicletaId, Long funcionarioId) {
        var cliente = buscarCliente(clienteId);
        var moto = motocicletas.findById(motocicletaId).orElseThrow(() ->
                erro(HttpStatus.NOT_FOUND, "Motocicleta não encontrada."));
        conferirProprietario(cliente, moto);
        var ordem = new OrdemServico();
        ordem.setDescricao(descricao.trim());
        ordem.setDataCriacao(dataAtual());
        ordem.setDataAbertura(ordem.getDataCriacao());
        ordem.setCliente(cliente);
        ordem.setMotocicleta(moto);
        ordem.setFuncionario(buscarFuncionarioAtivo(funcionarioId));
        return repository.saveAndFlush(ordem);
    }

    // Compatibilidade com o exemplo da aula: ainda permite preparar uma OS sem vínculos.
    public OrdemServico cadastrarAntiga(OrdemServico dados) {
        var ordem = new OrdemServico();
        ordem.setDescricao(dados.getDescricao().trim());
        ordem.setDataCriacao(dataAtual());
        ordem.setDataAbertura(ordem.getDataCriacao());
        return repository.saveAndFlush(ordem);
    }

    @Transactional(readOnly = true)
    public List<OrdemServico> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public OrdemServico buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Ordem de serviço não encontrada."));
    }

    public OrdemServico adicionarCliente(Long id, Long clienteId) {
        var ordem = buscarParaAlterar(id);
        var cliente = buscarCliente(clienteId);
        if (ordem.getMotocicleta() != null) conferirProprietario(cliente, ordem.getMotocicleta());
        ordem.setCliente(cliente);
        repository.flush();
        return ordem;
    }

    public OrdemServico atualizar(Long id, String descricao, String parecerTecnico, Long clienteId, Long motocicletaId,
                                  Long funcionarioId, boolean administrador) {
        var ordem = buscarParaAlterar(id);
        var cliente = buscarCliente(clienteId);
        var moto = motocicletas.findById(motocicletaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Motocicleta não encontrada."));
        conferirProprietario(cliente, moto);
        // A orientação do cliente é registrada na abertura; depois, só o administrador corrige.
        String orientacao = descricao.trim();
        if (!administrador && !orientacao.equals(ordem.getDescricao())) {
            throw erro(HttpStatus.FORBIDDEN, "Somente o administrador pode alterar a orientação do cliente.");
        }
        ordem.setCliente(cliente);
        ordem.setMotocicleta(moto);
        boolean mesmoResponsavel = ordem.getFuncionario() != null && ordem.getFuncionario().getId().equals(funcionarioId);
        if (!mesmoResponsavel) ordem.setFuncionario(buscarFuncionarioAtivo(funcionarioId));
        ordem.setDescricao(orientacao);
        ordem.setParecerTecnico(parecerTecnico == null || parecerTecnico.isBlank() ? null : parecerTecnico.trim());
        // Datas são definidas pelo servidor e pelo evento de aprovação.
        repository.flush();
        return ordem;
    }

    public OrdemServico adicionarServico(Long id, Long servicoId, Integer quantidade, BigDecimal valor, boolean administrador) {
        var ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);
        var servico = servicos.findById(servicoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Serviço não encontrado."));
        var valorPraticado = valor == null ? servico.getPreco() : valor;
        conferirPrecoMinimo(valorPraticado, servico.getPreco(), administrador);
        var item = new ItemServicoOS();
        item.setOrdemServico(ordem);
        item.setServico(servico);
        item.setQuantidade(quantidade);
        // Copia o preço praticado; a OS não usa o preço futuro do catálogo.
        item.setValorUnitario(valorPraticado);
        ordem.getItens().add(item);
        repository.flush();
        return ordem;
    }

    public OrdemServico atualizarItem(Long id, Long itemId, Integer quantidade, BigDecimal valor, boolean administrador) {
        var ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);
        var item = buscarItem(ordem, itemId);
        if (valor.compareTo(item.getValorUnitario()) != 0) {
            conferirPrecoMinimo(valor, item.getServico().getPreco(), administrador);
        }
        item.setQuantidade(quantidade);
        item.setValorUnitario(valor);
        repository.flush();
        return ordem;
    }

    public OrdemServico removerItem(Long id, Long itemId) {
        var ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);
        ordem.getItens().remove(buscarItem(ordem, itemId));
        repository.flush();
        return ordem;
    }

    public OrdemServico adicionarProduto(Long id, Long produtoId, BigDecimal quantidade, BigDecimal valor, boolean administrador) {
        var ordem = buscarParaAlterar(id); conferirVinculos(ordem);
        var produto = bloquearProduto(produtoId);
        conferirDisponivel(produto, quantidade);
        // O preço de venda cadastrado é sugerido; o funcionário só pode aumentá-lo.
        var valorPraticado = valor == null ? produto.getPrecoVenda() : valor;
        conferirPrecoMinimo(valorPraticado, produto.getPrecoVenda(), administrador);
        var item = new ItemProdutoOS(); item.setOrdemServico(ordem); item.setProduto(produto);
        item.setQuantidade(quantidade); item.setValorUnitario(valorPraticado); item.setUnidadeMedida(produto.getUnidadeMedida());
        item.setCustoUnitario(produto.getPrecoCusto());
        ordem.getProdutos().add(item);
        // Apenas reserva: a saída física será registrada na finalização.
        repository.flush(); return ordem;
    }

    public OrdemServico atualizarProduto(Long id, Long itemId, BigDecimal quantidade, BigDecimal valor, boolean administrador) {
        var ordem = buscarParaAlterar(id); conferirVinculos(ordem);
        var item = buscarProdutoDaOrdem(ordem, itemId);
        var produto = bloquearProduto(item.getProduto().getId());
        var aumento = quantidade.subtract(item.getQuantidade());
        if (aumento.signum() > 0) conferirDisponivel(produto, aumento);
        if (valor != null && valor.compareTo(item.getValorUnitario()) != 0) {
            conferirPrecoMinimo(valor, produto.getPrecoVenda(), administrador);
            item.setValorUnitario(valor);
        }
        item.setQuantidade(quantidade); // Unidade e custo continuam os da inclusão.
        repository.flush(); return ordem;
    }

    // Regra de preço: o funcionário não pode cobrar menos que o preço definido pelo administrador.
    private void conferirPrecoMinimo(BigDecimal valor, BigDecimal precoCadastrado, boolean administrador) {
        if (!administrador && valor.compareTo(precoCadastrado) < 0) {
            String minimo = precoCadastrado.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
            throw erro(HttpStatus.FORBIDDEN, "O valor não pode ser menor que o preço cadastrado (R$ " + minimo + ").");
        }
    }

    public OrdemServico removerProduto(Long id, Long itemId) {
        var ordem = buscarParaAlterar(id); conferirVinculos(ordem);
        var item = buscarProdutoDaOrdem(ordem, itemId);
        bloquearProduto(item.getProduto().getId());
        ordem.getProdutos().remove(item);
        repository.flush(); return ordem;
    }

    private ItemProdutoOS buscarProdutoDaOrdem(OrdemServico ordem, Long itemId) {
        return ordem.getProdutos().stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Produto não encontrado nesta OS."));
    }

    private Produto bloquearProduto(Long id) {
        return estoque.bloquearProduto(id);
    }

    private void conferirDisponivel(Produto produto, BigDecimal quantidade) {
        if (produto.getUnidadeMedida() == null || produto.getUnidadeMedida().isBlank())
            throw erro(HttpStatus.CONFLICT, "Complete a unidade de medida do produto.");
        if (quantidade.compareTo(produto.getSaldo()) > 0)
            throw erro(HttpStatus.CONFLICT, "Saldo disponível insuficiente para empenhar este produto.");
    }

    public OrdemServico alterarSituacao(Long id, SituacaoOrdemServico situacao) {
        var ordem = buscarComBloqueio(id);
        if (ordem.getSituacao() == SituacaoOrdemServico.ORCAMENTO && situacao == SituacaoOrdemServico.EM_ANDAMENTO)
            return aprovar(id);
        // Repetir o status não altera nenhum dado, inclusive a data da finalização.
        if (ordem.getSituacao() == situacao) return ordem;
        conferirEditavel(ordem);
        conferirVinculos(ordem);
        boolean permitida = ordem.getSituacao() == SituacaoOrdemServico.ORCAMENTO
                && situacao == SituacaoOrdemServico.EM_ANDAMENTO
                || ordem.getSituacao() == SituacaoOrdemServico.EM_ANDAMENTO
                && situacao == SituacaoOrdemServico.ORCAMENTO
                || ordem.getSituacao() == SituacaoOrdemServico.RETRABALHO
                && situacao == SituacaoOrdemServico.EM_ANDAMENTO;
        if (!permitida) {
            throw erro(HttpStatus.CONFLICT, "Use a operação própria para concluir, cancelar ou criar um retrabalho.");
        }
        ordem.setSituacao(situacao);
        repository.flush();
        return ordem;
    }

    public OrdemServico finalizarOrdem(Long id, String usuario) {
        var ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);
        if (ordem.getSituacao() != SituacaoOrdemServico.EM_ANDAMENTO) {
            throw erro(HttpStatus.CONFLICT, "Coloque a OS em andamento antes de finalizar.");
        }
        if (ordem.getItens().isEmpty()) {
            throw erro(HttpStatus.CONFLICT, "Adicione pelo menos um serviço antes de finalizar.");
        }
        // Todas as OS adquirem seus produtos na mesma ordem, antes de efetuar qualquer baixa.
        var bloqueados = new java.util.TreeMap<Long, Produto>();
        ordem.getProdutos().stream().map(i -> i.getProduto().getId()).distinct().sorted().forEach(produtoId -> {
            var produto = bloquearProduto(produtoId);
            if (produto.getEstoque().compareTo(produto.getEmpenho()) < 0)
                throw erro(HttpStatus.CONFLICT, "Estoque incompatível com os empenhos do produto.");
            bloqueados.put(produtoId, produto);
        });
        for (ItemProdutoOS item : ordem.getProdutos()) {
            estoque.registrar(bloqueados.get(item.getProduto().getId()), ordem.getFuncionario(), ordem,
                    TipoMovimentacao.SAIDA, item.getQuantidade(), "Consumo de produto na finalização da OS " + id, usuario);
        }
        ordem.setSituacao(SituacaoOrdemServico.CONCLUIDA);
        ordem.setDataFinalizacao(dataAtual());
        repository.flush();
        return ordem;
    }

    public OrdemServico cancelarOrdem(Long id) {
        var ordem = buscarComBloqueio(id);
        if (ordem.getSituacao() == SituacaoOrdemServico.CANCELADA) return ordem;
        conferirEditavel(ordem);
        ordem.setSituacao(SituacaoOrdemServico.CANCELADA);
        ordem.setDataFinalizacao(null);
        repository.flush();
        return ordem;
    }

    public OrdemServico criarRetrabalho(Long id) {
        var origem = buscarComBloqueio(id);
        if (origem.getSituacao() != SituacaoOrdemServico.CONCLUIDA) {
            throw erro(HttpStatus.CONFLICT, "O retrabalho deve partir de uma OS concluída.");
        }
        var retrabalho = new OrdemServico();
        retrabalho.setDescricao(origem.getDescricao());
        retrabalho.setDataCriacao(dataAtual());
        retrabalho.setDataAbertura(retrabalho.getDataCriacao());
        retrabalho.setCliente(origem.getCliente());
        retrabalho.setMotocicleta(origem.getMotocicleta());
        // Funcionário inativo não recebe nova atribuição; o retrabalho pede outro responsável.
        if (origem.getFuncionario() != null && origem.getFuncionario().isAtivo()) retrabalho.setFuncionario(origem.getFuncionario());
        retrabalho.setSituacao(SituacaoOrdemServico.RETRABALHO);
        retrabalho.setOrdemOrigem(origem);
        return repository.saveAndFlush(retrabalho);
    }

    private OrdemServico buscarParaAlterar(Long id) {
        var ordem = buscarComBloqueio(id);
        conferirEditavel(ordem);
        return ordem;
    }

    private OrdemServico buscarComBloqueio(Long id) {
        // Todas as alterações da mesma OS aguardam esta transação terminar.
        return repository.buscarComBloqueio(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Ordem de serviço não encontrada."));
    }

    private void conferirEditavel(OrdemServico ordem) {
        if (ordem.getSituacao() == SituacaoOrdemServico.CONCLUIDA
                || ordem.getSituacao() == SituacaoOrdemServico.CANCELADA) {
            throw erro(HttpStatus.CONFLICT, "A OS concluída ou cancelada permanece somente para consulta.");
        }
    }

    private Cliente buscarCliente(Long id) {
        return clientes.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private Usuario buscarFuncionarioAtivo(Long id) {
        if (id == null) throw erro(HttpStatus.BAD_REQUEST, "Selecione o funcionário responsável.");
        var funcionario = funcionarios.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
        if (!funcionario.isAtivo()) throw erro(HttpStatus.CONFLICT, "Funcionário inativo não pode receber novas ordens de serviço.");
        return funcionario;
    }

    private void conferirVinculos(OrdemServico ordem) {
        if (ordem.getCliente() == null || ordem.getMotocicleta() == null) {
            throw erro(HttpStatus.CONFLICT, "Complete o cliente e a motocicleta desta OS antes de continuar.");
        }
        if (ordem.getFuncionario() == null) throw erro(HttpStatus.CONFLICT, "Complete o funcionário responsável desta OS antes de continuar.");
        conferirProprietario(ordem.getCliente(), ordem.getMotocicleta());
    }

    private void conferirProprietario(Cliente cliente, Motocicleta moto) {
        if (moto.getCliente() == null) {
            throw erro(HttpStatus.CONFLICT, "Informe o proprietário no cadastro da motocicleta.");
        }
        if (!moto.getCliente().getId().equals(cliente.getId())) {
            throw erro(HttpStatus.CONFLICT, "A motocicleta não pertence ao cliente selecionado.");
        }
    }

    private ItemServicoOS buscarItem(OrdemServico ordem, Long itemId) {
        for (ItemServicoOS item : ordem.getItens()) {
            if (item.getId().equals(itemId)) return item;
        }
        throw erro(HttpStatus.NOT_FOUND, "Serviço não encontrado nesta OS.");
    }

    private ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
