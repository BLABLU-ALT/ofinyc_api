package br.edu.ifsul.oficynic.controller;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.Produto;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;
import br.edu.ifsul.oficynic.repository.ItemProdutoOSRepository;
import br.edu.ifsul.oficynic.repository.ProdutoRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoRepository repository;
    private final ItemProdutoOSRepository itemProdutoOSRepository;

    public ProdutoController(
            ProdutoRepository repository,
            ItemProdutoOSRepository itemProdutoOSRepository) {

        this.repository = repository;
        this.itemProdutoOSRepository = itemProdutoOSRepository;
    }

    public record ProdutoConsulta(
            Long id,
            String codigo,
            String descricao,
            BigDecimal precoVenda,
            BigDecimal precoCusto,
            String unidadeMedida,
            BigDecimal estoque,
            BigDecimal empenho,
            BigDecimal saldo) {
    }

    public record Reserva(
            Long ordemServicoId,
            String clienteNome,
            SituacaoOrdemServico situacao,
            BigDecimal quantidade,
            String unidadeMedida) {
    }

    public record ConsultaEstoque(
            ProdutoConsulta produto,
            List<Reserva> reservas) {
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProdutoConsulta> listar(HttpServletRequest request) {
        List<Produto> produtos = repository.findAll(Sort.by(Sort.Direction.DESC, "id"));

        produtos.forEach(produto -> produto.setEmpenho(repository.quantidadeEmpenhada(produto.getId())));

        return produtos.stream()
                .map(produto -> consulta(produto, request))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProdutoConsulta buscar(
            @PathVariable Long id,
            HttpServletRequest request) {

        Produto produto = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        produto.setEmpenho(repository.quantidadeEmpenhada(id));

        return consulta(produto, request);
    }

    @GetMapping("/{id}/estoque")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConsultaEstoque consultarEstoque(
            @PathVariable Long id,
            HttpServletRequest request) {

        ProdutoConsulta produto = buscar(id, request);

        var situacoesAbertas = List.of(
                SituacaoOrdemServico.ORCAMENTO,
                SituacaoOrdemServico.EM_ANDAMENTO,
                SituacaoOrdemServico.RETRABALHO);

        List<Reserva> reservas = itemProdutoOSRepository
                .findByProdutoIdAndOrdemServicoSituacaoInOrderById(id, situacoesAbertas)
                .stream()
                .map(item -> new Reserva(
                        item.getOrdemServico().getId(),
                        item.getOrdemServico().getCliente() == null
                                ? null
                                : item.getOrdemServico().getCliente().getNome(),
                        item.getOrdemServico().getSituacao(),
                        item.getQuantidade(),
                        item.getUnidadeMedida()))
                .toList();

        return new ConsultaEstoque(produto, reservas);
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrar(
            @Valid @RequestBody Produto produto) {

        produto.setId(null);
        produto.setEstoque(new BigDecimal("0.000"));
        produto.setEmpenho(new BigDecimal("0.000"));
        verificarDuplicidade(produto, null);

        Produto salvo = repository.saveAndFlush(produto);

        return ResponseEntity
                .created(URI.create("/api/produtos/" + salvo.getId()))
                .body(salvo);
    }

    @PutMapping("/{id}")
    @Transactional
    public Produto atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Produto dados) {

        Produto produto = repository.buscarComBloqueio(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        produto.setEmpenho(repository.quantidadeEmpenhada(id));

        boolean trocouUnidade = !Objects.equals(produto.getUnidadeMedida(), dados.getUnidadeMedida());
        boolean temQuantidade = produto.getEstoque().signum() != 0 || produto.getEmpenho().signum() != 0;

        if (trocouUnidade && temQuantidade) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A unidade só pode ser alterada com estoque físico e empenho zerados.");
        }

        verificarDuplicidade(dados, id);

        produto.setCodigo(dados.getCodigo());
        produto.setDescricao(dados.getDescricao());
        produto.setPrecoVenda(dados.getPrecoVenda());
        produto.setPrecoCusto(dados.getPrecoCusto());
        produto.setUnidadeMedida(dados.getUnidadeMedida());

        repository.flush();

        return produto;
    }

    // Preco de custo so aparece para o administrador
    private ProdutoConsulta consulta(Produto produto, HttpServletRequest request) {
        BigDecimal custo = request.isUserInRole("ADMINISTRADOR") ? produto.getPrecoCusto() : null;

        return new ProdutoConsulta(
                produto.getId(),
                produto.getCodigo(),
                produto.getDescricao(),
                produto.getPrecoVenda(),
                custo,
                produto.getUnidadeMedida(),
                produto.getEstoque(),
                produto.getEmpenho(),
                produto.getSaldo());
    }

    private void verificarDuplicidade(Produto dados, Long id) {
        boolean codigoRepetido = id == null
                ? repository.existsByCodigo(dados.getCodigo())
                : repository.existsByCodigoAndIdNot(dados.getCodigo(), id);

        if (codigoRepetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este codigo.");
        }
    }
}
