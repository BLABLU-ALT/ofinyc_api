package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Servico;
import br.com.oficynic.ofinyc_api.repository.ServicoRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {
    private final ServicoRepository repository;

    public ServicoController(ServicoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Servico> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Servico buscar(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @PostMapping
    public ResponseEntity<Servico> cadastrar(@Valid @RequestBody Servico dados) {
        dados.setId(null);
        verificarDuplicidade(dados, null);
        Servico salvo = repository.saveAndFlush(dados);
        return ResponseEntity.created(URI.create("/api/servicos/" + salvo.getId())).body(salvo);
    }

    @PutMapping("/{id}")
    public Servico atualizar(@PathVariable Long id, @Valid @RequestBody Servico dados) {
        Servico existente = buscar(id);
        verificarDuplicidade(dados, id);
        // Copia somente os campos editáveis do formulário.
        existente.setNome(dados.getNome());
        existente.setDescricao(dados.getDescricao());
        existente.setPreco(dados.getPreco());
        return repository.saveAndFlush(existente);
    }


    private void verificarDuplicidade(Servico dados, Long id) {

    }
}
