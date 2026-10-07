package br.com.oficynic.ofinyc_api.service;

import br.com.oficynic.ofinyc_api.model.*;
import br.com.oficynic.ofinyc_api.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final FuncionarioRepository funcionarios;
    private final EstoqueService estoque;

    public OrdemServicoService(OrdemServicoRepository repository, ClienteRepository clientes,
                              MotocicletaRepository motocicletas, ServicoRepository servicos,
                              ProdutoRepository produtos, FuncionarioRepository funcionarios, EstoqueService estoque) {
        this.repository = repository;
        this.clientes = clientes;
        this.motocicletas = motocicletas;
        this.servicos = servicos;
        this.produtos = produtos;
        this.funcionarios = funcionarios;
        this.estoque = estoque;
    }

    public OrdemServico abrir(String descricao, LocalDate data, Long clienteId, Long motocicletaId, Long funcionarioId) {
        var cliente = buscarCliente(clienteId);
        var moto = motocicletas.findById(motocicletaId).orElseThrow(() ->
                erro(HttpStatus.NOT_FOUND, "Motocicleta não encontrada."));
        conferirProprietario(cliente, moto);
        var ordem = new OrdemServico();
        ordem.setDescricao(descricao.trim());
        ordem.setDataAbertura(data == null ? LocalDate.now() : data);
        ordem.setCliente(cliente);
        ordem.setMotocicleta(moto);
        ordem.setFuncionario(buscarFuncionario(funcionarioId));
        return repository.saveAndFlush(ordem);
    }

    // Compatibilidade com o exemplo da aula: ainda permite preparar uma OS sem vínculos.
    public OrdemServico cadastrarAntiga(OrdemServico dados) {
        var ordem = new OrdemServico();
        ordem.setDescricao(dados.getDescricao().trim());
        ordem.setDataAbertura(dados.getDataAbertura());
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

    public OrdemServico atualizar(Long id, String descricao, LocalDate data, Long clienteId, Long motocicletaId, Long funcionarioId) {
        var ordem = buscarParaAlterar(id);
        var cliente = buscarCliente(clienteId);
        var moto = motocicletas.findById(motocicletaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Motocicleta não encontrada."));
        conferirProprietario(cliente, moto);
        ordem.setCliente(cliente);
        ordem.setMotocicleta(moto);
        ordem.setFuncionario(buscarFuncionario(funcionarioId));
        ordem.setDescricao(descricao.trim());
        ordem.setDataAbertura(data);
        repository.flush();
        return ordem;
    }

    public OrdemServico adicionarServico(Long id, Long servicoId, Integer quantidade, BigDecimal valor) {
        var ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);
        var servico = servicos.findById(servicoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Serviço não encontrado."));
        var item = new ItemServicoOS();
        item.setOrdemServico(ordem);
        item.setServico(servico);
        item.setQuantidade(quantidade);
        // Copia o preço praticado; a OS não usa o preço futuro do catálogo.
        item.setValorUnitario(valor == null ? servico.getPreco() : valor);
        ordem.getItens().add(item);
        repository.flush();
        return ordem;
    }

    public OrdemServico atualizarItem(Long id, Long itemId, Integer quantidade, BigDecimal valor) {
        var ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);
        var item = buscarItem(ordem, itemId);
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

    public OrdemServico adicionarProduto(Long id, Long produtoId, BigDecimal quantidade) {
        var ordem = buscarParaAlterar(id); conferirVinculos(ordem);
        var produto = bloquearProduto(produtoId);
        conferirDisponivel(produto, quantidade);
        var item = new ItemProdutoOS(); item.setOrdemServico(ordem); item.setProduto(produto);
        item.setQuantidade(quantidade); item.setValorUnitario(produto.getPrecoVenda()); item.setUnidadeMedida(produto.getUnidadeMedida());
        ordem.getProdutos().add(item);
        // Apenas reserva: a saída física será registrada na finalização.
        repository.flush(); return ordem;
    }

    public OrdemServico atualizarProduto(Long id, Long itemId, BigDecimal quantidade) {
        var ordem = buscarParaAlterar(id); conferirVinculos(ordem);
        var item = buscarProdutoDaOrdem(ordem, itemId);
        var produto = bloquearProduto(item.getProduto().getId());
        var aumento = quantidade.subtract(item.getQuantidade());
        if (aumento.signum() > 0) conferirDisponivel(produto, aumento);
        item.setQuantidade(quantidade); // Conserva preço e unidade praticados.
        repository.flush(); return ordem;
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
        // Repetir o status não altera nenhum dado, inclusive a data da finalização.
        if (ordem.getSituacao() == situacao) return ordem;
        conferirEditavel(ordem);
        conferirVinculos(ordem);
        if (situacao == SituacaoOrdemServico.FINALIZADA) {
            throw erro(HttpStatus.CONFLICT, "Use a operação Finalizar OS.");
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
        ordem.setSituacao(SituacaoOrdemServico.FINALIZADA);
        ordem.setDataFinalizacao(LocalDate.now());
        repository.flush();
        return ordem;
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
        if (ordem.getSituacao() == SituacaoOrdemServico.FINALIZADA) {
            throw erro(HttpStatus.CONFLICT, "A OS finalizada permanece somente para consulta.");
        }
    }

    private Cliente buscarCliente(Long id) {
        return clientes.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private Funcionario buscarFuncionario(Long id) {
        if (id == null) throw erro(HttpStatus.BAD_REQUEST, "Selecione o funcionário responsável.");
        return funcionarios.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
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
