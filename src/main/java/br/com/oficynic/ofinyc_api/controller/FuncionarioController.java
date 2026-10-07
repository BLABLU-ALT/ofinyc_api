package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Funcionario;
import br.com.oficynic.ofinyc_api.repository.FuncionarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/funcionarios")
public class FuncionarioController {
    private final FuncionarioRepository repository;
    public FuncionarioController(FuncionarioRepository repository) { this.repository = repository; }
    public record Cadastro(@NotBlank(message = "Informe o nome.") @Size(max = 120) String nome) { }
    @GetMapping
    public List<Funcionario> listar() { return repository.findAll(Sort.by("nome", "id")); }
    @GetMapping("/{id}")
    public Funcionario buscar(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Funcionario cadastrar(@Valid @RequestBody Cadastro dados) {
        var funcionario = new Funcionario(); funcionario.setNome(dados.nome().trim());
        return repository.saveAndFlush(funcionario);
    }
    @PutMapping("/{id}")
    public Funcionario atualizar(@PathVariable Long id, @Valid @RequestBody Cadastro dados) {
        var funcionario = buscar(id); funcionario.setNome(dados.nome().trim());
        return repository.saveAndFlush(funcionario);
    }
}
