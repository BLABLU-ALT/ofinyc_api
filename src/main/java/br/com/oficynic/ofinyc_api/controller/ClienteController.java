package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Cliente;
import br.com.oficynic.ofinyc_api.repository.ClienteRepository;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {
    private final ClienteRepository repository;

    public ClienteController(ClienteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Cliente> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Cliente buscar(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @PostMapping
    public ResponseEntity<Cliente> cadastrar(@Valid @RequestBody Cliente dados) {
        dados.setId(null);
        verificarDuplicidade(dados, null);
        Cliente salvo = repository.saveAndFlush(dados);
        return ResponseEntity.created(URI.create("/api/clientes/" + salvo.getId())).body(salvo);
    }

    @PutMapping("/{id}")
    public Cliente atualizar(@PathVariable Long id, @Valid @RequestBody Cliente dados) {
        Cliente existente = buscar(id);
        verificarDuplicidade(dados, id);
        // Copia somente os campos editáveis do formulário.
        existente.setNome(dados.getNome());
        existente.setCpf(dados.getCpf());
        existente.setTelefone(dados.getTelefone());
        existente.setEmail(dados.getEmail());
        existente.setEndereco(dados.getEndereco());
        return repository.saveAndFlush(existente);
    }

    private void verificarDuplicidade(Cliente dados, Long id) {
        if (id == null ? repository.existsByCpf(dados.getCpf())
                : repository.existsByCpfAndIdNot(dados.getCpf(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este cpf.");
        }
    }
}
