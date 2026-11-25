package br.edu.ifsul.oficynic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.Cliente;
import br.edu.ifsul.oficynic.model.ItemProdutoOS;
import br.edu.ifsul.oficynic.model.ItemServicoOS;
import br.edu.ifsul.oficynic.model.Motocicleta;
import br.edu.ifsul.oficynic.model.OrdemServico;
import br.edu.ifsul.oficynic.model.Produto;
import br.edu.ifsul.oficynic.model.Servico;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;
import br.edu.ifsul.oficynic.model.TipoMovimentacao;
import br.edu.ifsul.oficynic.model.Usuario;
import br.edu.ifsul.oficynic.repository.ClienteRepository;
import br.edu.ifsul.oficynic.repository.MotocicletaRepository;
import br.edu.ifsul.oficynic.repository.OrdemServicoRepository;
import br.edu.ifsul.oficynic.repository.ServicoRepository;
import br.edu.ifsul.oficynic.repository.UsuarioRepository;

@Service
@Transactional
public class OrdemServicoService {

    private final OrdemServicoRepository repository;
    private final ClienteRepository clienteRepository;
    private final MotocicletaRepository motocicletaRepository;
    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstoqueService estoqueService;

    public OrdemServicoService(
            OrdemServicoRepository repository,
            ClienteRepository clienteRepository,
            MotocicletaRepository motocicletaRepository,
            ServicoRepository servicoRepository,
            UsuarioRepository usuarioRepository,
            EstoqueService estoqueService) {

        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.motocicletaRepository = motocicletaRepository;
        this.servicoRepository = servicoRepository;
        this.usuarioRepository = usuarioRepository;
        this.estoqueService = estoqueService;
    }

    public static LocalDate dataNoFuso(Instant instante) {
        return instante.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
    }

    public LocalDate dataAtual() {
        return dataNoFuso(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<OrdemServico> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public OrdemServico buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Ordem de serviço não encontrada."));
    }

    public OrdemServico abrir(
            String descricao,
            LocalDate data,
            Long clienteId,
            Long motocicletaId,
            Long funcionarioId) {

        Cliente cliente = buscarCliente(clienteId);
        Motocicleta moto = buscarMotocicleta(motocicletaId);
        conferirProprietario(cliente, moto);

        OrdemServico ordem = new OrdemServico();
        ordem.setDescricao(descricao.trim());
        ordem.setDataCriacao(dataAtual());
        ordem.setDataAbertura(ordem.getDataCriacao());
        ordem.setCliente(cliente);
        ordem.setMotocicleta(moto);
        ordem.setFuncionario(buscarFuncionarioAtivo(funcionarioId));

        return repository.saveAndFlush(ordem);
    }

    public OrdemServico atualizar(
            Long id,
            String descricao,
            String parecerTecnico,
            Long clienteId,
            Long motocicletaId,
            Long funcionarioId,
            boolean administrador) {

        OrdemServico ordem = buscarParaAlterar(id);
        Cliente cliente = buscarCliente(clienteId);
        Motocicleta moto = buscarMotocicleta(motocicletaId);
        conferirProprietario(cliente, moto);

        String orientacao = descricao.trim();

        if (!administrador && !orientacao.equals(ordem.getDescricao())) {
            throw erro(HttpStatus.FORBIDDEN, "Somente o administrador pode alterar a orientação do cliente.");
        }

        ordem.setCliente(cliente);
        ordem.setMotocicleta(moto);

        boolean mesmoResponsavel = ordem.getFuncionario() != null
                && ordem.getFuncionario().getId().equals(funcionarioId);

        if (!mesmoResponsavel) {
            ordem.setFuncionario(buscarFuncionarioAtivo(funcionarioId));
        }

        ordem.setDescricao(orientacao);
        ordem.setParecerTecnico(parecerTecnico == null || parecerTecnico.isBlank() ? null : parecerTecnico.trim());

        repository.flush();

        return ordem;
    }

    public OrdemServico aprovar(Long id) {
        OrdemServico ordem = buscarComBloqueio(id);
        conferirEditavel(ordem);
        conferirVinculos(ordem);

        boolean emOrcamento = ordem.getSituacao() == SituacaoOrdemServico.ORCAMENTO;
        boolean jaAprovada = ordem.getSituacao() == SituacaoOrdemServico.EM_ANDAMENTO
                && ordem.getDataAprovacao() != null;

        if (!emOrcamento && !jaAprovada) {
            throw erro(HttpStatus.CONFLICT, "A aprovação exige uma OS em orçamento.");
        }

        if (ordem.getDataAprovacao() == null) {
            if (ordem.getDataCriacao() == null) {
                ordem.setDataAberturaAnterior(ordem.getDataAbertura());
            }

            LocalDate hoje = dataAtual();
            ordem.setDataAprovacao(hoje);
            ordem.setDataAbertura(hoje);
        }

        ordem.setSituacao(SituacaoOrdemServico.EM_ANDAMENTO);
        repository.flush();

        return ordem;
    }

    public OrdemServico alterarSituacao(Long id, SituacaoOrdemServico situacao) {
        OrdemServico ordem = buscarComBloqueio(id);

        if (ordem.getSituacao() == SituacaoOrdemServico.ORCAMENTO
                && situacao == SituacaoOrdemServico.EM_ANDAMENTO) {
            return aprovar(id);
        }

        if (ordem.getSituacao() == situacao) {
            return ordem;
        }

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
        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        if (ordem.getSituacao() != SituacaoOrdemServico.EM_ANDAMENTO) {
            throw erro(HttpStatus.CONFLICT, "Coloque a OS em andamento antes de finalizar.");
        }

        if (ordem.getItens().isEmpty()) {
            throw erro(HttpStatus.CONFLICT, "Adicione pelo menos um serviço antes de finalizar.");
        }

        // Bloqueia os produtos em ordem crescente de id antes da baixa
        // Assim duas finalizacoes ao mesmo tempo nao travam uma a outra
        Map<Long, Produto> bloqueados = new TreeMap<>();

        ordem.getProdutos().stream()
                .map(item -> item.getProduto().getId())
                .distinct()
                .sorted()
                .forEach(produtoId -> {
                    Produto produto = estoqueService.bloquearProduto(produtoId);

                    if (produto.getEstoque().compareTo(produto.getEmpenho()) < 0) {
                        throw erro(HttpStatus.CONFLICT, "Estoque incompatível com os empenhos do produto.");
                    }

                    bloqueados.put(produtoId, produto);
                });

        for (ItemProdutoOS item : ordem.getProdutos()) {
            estoqueService.registrar(
                    bloqueados.get(item.getProduto().getId()),
                    ordem.getFuncionario(),
                    ordem,
                    TipoMovimentacao.SAIDA,
                    item.getQuantidade(),
                    "Consumo de produto na finalização da OS " + id,
                    usuario);
        }

        ordem.setSituacao(SituacaoOrdemServico.CONCLUIDA);
        ordem.setDataFinalizacao(dataAtual());
        repository.flush();

        return ordem;
    }

    public OrdemServico cancelarOrdem(Long id) {
        OrdemServico ordem = buscarComBloqueio(id);

        if (ordem.getSituacao() == SituacaoOrdemServico.CANCELADA) {
            return ordem;
        }

        conferirEditavel(ordem);

        ordem.setSituacao(SituacaoOrdemServico.CANCELADA);
        ordem.setDataFinalizacao(null);
        repository.flush();

        return ordem;
    }

    public OrdemServico criarRetrabalho(Long id) {
        OrdemServico origem = buscarComBloqueio(id);

        if (origem.getSituacao() != SituacaoOrdemServico.CONCLUIDA) {
            throw erro(HttpStatus.CONFLICT, "O retrabalho deve partir de uma OS concluída.");
        }

        OrdemServico retrabalho = new OrdemServico();
        retrabalho.setDescricao(origem.getDescricao());
        retrabalho.setDataCriacao(dataAtual());
        retrabalho.setDataAbertura(retrabalho.getDataCriacao());
        retrabalho.setCliente(origem.getCliente());
        retrabalho.setMotocicleta(origem.getMotocicleta());
        retrabalho.setSituacao(SituacaoOrdemServico.RETRABALHO);
        retrabalho.setOrdemOrigem(origem);

        // Funcionario inativo nao recebe OS nova e o retrabalho fica sem responsavel
        if (origem.getFuncionario() != null && origem.getFuncionario().isAtivo()) {
            retrabalho.setFuncionario(origem.getFuncionario());
        }

        return repository.saveAndFlush(retrabalho);
    }

    public OrdemServico adicionarServico(
            Long id,
            Long servicoId,
            Integer quantidade,
            BigDecimal valor,
            boolean administrador) {

        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        Servico servico = servicoRepository.findById(servicoId)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Serviço não encontrado."));

        BigDecimal valorPraticado = valor == null ? servico.getPreco() : valor;
        conferirPrecoMinimo(valorPraticado, servico.getPreco(), administrador);

        ItemServicoOS item = new ItemServicoOS();
        item.setOrdemServico(ordem);
        item.setServico(servico);
        item.setQuantidade(quantidade);
        item.setValorUnitario(valorPraticado);

        ordem.getItens().add(item);
        repository.flush();

        return ordem;
    }

    public OrdemServico atualizarItem(
            Long id,
            Long itemId,
            Integer quantidade,
            BigDecimal valor,
            boolean administrador) {

        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        ItemServicoOS item = buscarItem(ordem, itemId);

        if (valor.compareTo(item.getValorUnitario()) != 0) {
            conferirPrecoMinimo(valor, item.getServico().getPreco(), administrador);
        }

        item.setQuantidade(quantidade);
        item.setValorUnitario(valor);
        repository.flush();

        return ordem;
    }

    public OrdemServico removerItem(Long id, Long itemId) {
        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        ordem.getItens().remove(buscarItem(ordem, itemId));
        repository.flush();

        return ordem;
    }

    // Produto entra na OS como reserva e a baixa acontece na finalizacao
    public OrdemServico adicionarProduto(
            Long id,
            Long produtoId,
            BigDecimal quantidade,
            BigDecimal valor,
            boolean administrador) {

        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        Produto produto = estoqueService.bloquearProduto(produtoId);
        conferirDisponivel(produto, quantidade);

        BigDecimal valorPraticado = valor == null ? produto.getPrecoVenda() : valor;
        conferirPrecoMinimo(valorPraticado, produto.getPrecoVenda(), administrador);

        ItemProdutoOS item = new ItemProdutoOS();
        item.setOrdemServico(ordem);
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(valorPraticado);
        item.setUnidadeMedida(produto.getUnidadeMedida());
        item.setCustoUnitario(produto.getPrecoCusto());

        ordem.getProdutos().add(item);
        repository.flush();

        return ordem;
    }

    public OrdemServico atualizarProduto(
            Long id,
            Long itemId,
            BigDecimal quantidade,
            BigDecimal valor,
            boolean administrador) {

        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        ItemProdutoOS item = buscarProdutoDaOrdem(ordem, itemId);
        Produto produto = estoqueService.bloquearProduto(item.getProduto().getId());

        BigDecimal aumento = quantidade.subtract(item.getQuantidade());

        if (aumento.signum() > 0) {
            conferirDisponivel(produto, aumento);
        }

        if (valor != null && valor.compareTo(item.getValorUnitario()) != 0) {
            conferirPrecoMinimo(valor, produto.getPrecoVenda(), administrador);
            item.setValorUnitario(valor);
        }

        item.setQuantidade(quantidade);
        repository.flush();

        return ordem;
    }

    public OrdemServico removerProduto(Long id, Long itemId) {
        OrdemServico ordem = buscarParaAlterar(id);
        conferirVinculos(ordem);

        ItemProdutoOS item = buscarProdutoDaOrdem(ordem, itemId);
        estoqueService.bloquearProduto(item.getProduto().getId());

        ordem.getProdutos().remove(item);
        repository.flush();

        return ordem;
    }

    private OrdemServico buscarParaAlterar(Long id) {
        OrdemServico ordem = buscarComBloqueio(id);
        conferirEditavel(ordem);

        return ordem;
    }

    private OrdemServico buscarComBloqueio(Long id) {
        return repository.buscarComBloqueio(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Ordem de serviço não encontrada."));
    }

    private Cliente buscarCliente(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private Motocicleta buscarMotocicleta(Long id) {
        return motocicletaRepository.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Motocicleta não encontrada."));
    }

    private Usuario buscarFuncionarioAtivo(Long id) {
        if (id == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Selecione o funcionário responsável.");
        }

        Usuario funcionario = usuarioRepository.findById(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        if (!funcionario.isAtivo()) {
            throw erro(HttpStatus.CONFLICT, "Funcionário inativo não pode receber novas ordens de serviço.");
        }

        return funcionario;
    }

    private ItemServicoOS buscarItem(OrdemServico ordem, Long itemId) {
        for (ItemServicoOS item : ordem.getItens()) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }

        throw erro(HttpStatus.NOT_FOUND, "Serviço não encontrado nesta OS.");
    }

    private ItemProdutoOS buscarProdutoDaOrdem(OrdemServico ordem, Long itemId) {
        return ordem.getProdutos().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Produto não encontrado nesta OS."));
    }

    private void conferirEditavel(OrdemServico ordem) {
        if (ordem.getSituacao() == SituacaoOrdemServico.CONCLUIDA
                || ordem.getSituacao() == SituacaoOrdemServico.CANCELADA) {
            throw erro(HttpStatus.CONFLICT, "A OS concluída ou cancelada permanece somente para consulta.");
        }
    }

    // Cliente e moto obrigatorios no banco e responsavel pode faltar so no retrabalho
    private void conferirVinculos(OrdemServico ordem) {
        if (ordem.getFuncionario() == null) {
            throw erro(HttpStatus.CONFLICT, "Complete o funcionário responsável desta OS antes de continuar.");
        }

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

    private void conferirDisponivel(Produto produto, BigDecimal quantidade) {
        if (produto.getUnidadeMedida() == null || produto.getUnidadeMedida().isBlank()) {
            throw erro(HttpStatus.CONFLICT, "Complete a unidade de medida do produto.");
        }

        if (quantidade.compareTo(produto.getSaldo()) > 0) {
            throw erro(HttpStatus.CONFLICT, "Saldo disponível insuficiente para empenhar este produto.");
        }
    }

    // Funcionario nao cobra menos que o preco cadastrado
    private void conferirPrecoMinimo(BigDecimal valor, BigDecimal precoCadastrado, boolean administrador) {
        if (!administrador && valor.compareTo(precoCadastrado) < 0) {
            String minimo = precoCadastrado.setScale(2, RoundingMode.HALF_UP)
                    .toPlainString()
                    .replace('.', ',');

            throw erro(HttpStatus.FORBIDDEN, "O valor não pode ser menor que o preço cadastrado (R$ " + minimo + ").");
        }
    }

    private ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
