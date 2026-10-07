package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Produto;
import br.com.oficynic.ofinyc_api.repository.ProdutoRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Objects;
import br.com.oficynic.ofinyc_api.repository.ItemProdutoOSRepository;
import br.com.oficynic.ofinyc_api.model.SituacaoOrdemServico;
import org.springframework.transaction.annotation.Isolation;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    private final ProdutoRepository repository;
    private final ItemProdutoOSRepository itens;

    public ProdutoController(ProdutoRepository repository, ItemProdutoOSRepository itens) {
        this.repository = repository;
        this.itens = itens;
    }

    @GetMapping
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Produto> listar() {
        var produtos = repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
        produtos.forEach(p -> p.setEmpenho(repository.quantidadeEmpenhada(p.getId())));
        return produtos;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Produto buscar(@PathVariable Long id) {
        var produto = repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        produto.setEmpenho(repository.quantidadeEmpenhada(id));
        return produto;
    }

    public record Reserva(Long ordemServicoId, String clienteNome, SituacaoOrdemServico situacao,
                          BigDecimal quantidade, String unidadeMedida) { }
    public record ConsultaEstoque(Produto produto, List<Reserva> reservas) { }
    @GetMapping("/{id}/estoque")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConsultaEstoque consultarEstoque(@PathVariable Long id) {
        var produto = buscar(id);
        var reservas = itens.findByProdutoIdAndOrdemServicoSituacaoNotOrderById(id, SituacaoOrdemServico.FINALIZADA).stream().map(item ->
                new Reserva(item.getOrdemServico().getId(), item.getOrdemServico().getCliente() == null ? null : item.getOrdemServico().getCliente().getNome(),
                        item.getOrdemServico().getSituacao(), item.getQuantidade(), item.getUnidadeMedida())).toList();
        return new ConsultaEstoque(produto, reservas);
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrar(@Valid @RequestBody Produto dados) {
        dados.setId(null);
        dados.setEstoque(new BigDecimal("0.000"));
        dados.setEmpenho(new BigDecimal("0.000"));
        verificarDuplicidade(dados, null);
        Produto salvo = repository.saveAndFlush(dados);
        return ResponseEntity.created(URI.create("/api/produtos/" + salvo.getId())).body(salvo);
    }

    @PutMapping("/{id}")
    @Transactional
    public Produto atualizar(@PathVariable Long id, @Valid @RequestBody Produto dados) {
        Produto existente = repository.buscarComBloqueio(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        existente.setEmpenho(repository.quantidadeEmpenhada(id));
        if (!Objects.equals(existente.getUnidadeMedida(), dados.getUnidadeMedida())
                && (existente.getEstoque().signum() != 0 || existente.getEmpenho().signum() != 0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A unidade só pode ser alterada com estoque físico e empenho zerados.");
        }
        verificarDuplicidade(dados, id);
        existente.setCodigo(dados.getCodigo());
        existente.setDescricao(dados.getDescricao());
        existente.setPrecoVenda(dados.getPrecoVenda());
        existente.setUnidadeMedida(dados.getUnidadeMedida());
        repository.flush();
        return existente;
    }


    private void verificarDuplicidade(Produto dados, Long id) {
        if (id == null ? repository.existsByCodigo(dados.getCodigo())
                : repository.existsByCodigoAndIdNot(dados.getCodigo(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este codigo.");
        }
    }
}
