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

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {
    private final ProdutoRepository repository;

    public ProdutoController(ProdutoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Produto> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Produto buscar(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrar(@Valid @RequestBody Produto dados) {
        dados.setId(null);
        verificarDuplicidade(dados, null);
        Produto salvo = repository.saveAndFlush(dados);
        return ResponseEntity.created(URI.create("/api/produtos/" + salvo.getId())).body(salvo);
    }

    @PutMapping("/{id}")
    public Produto atualizar(@PathVariable Long id, @Valid @RequestBody Produto dados) {
        Produto existente = buscar(id);
        verificarDuplicidade(dados, id);
        existente.setCodigo(dados.getCodigo());
        existente.setDescricao(dados.getDescricao());
        existente.setPrecoVenda(dados.getPrecoVenda());
        return repository.saveAndFlush(existente);
    }


    private void verificarDuplicidade(Produto dados, Long id) {
        if (id == null ? repository.existsByCodigo(dados.getCodigo())
                : repository.existsByCodigoAndIdNot(dados.getCodigo(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este codigo.");
        }
    }
}
