package br.edu.ifsul.oficynic.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
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

import br.edu.ifsul.oficynic.exception.TelefoneDuplicadoException;
import br.edu.ifsul.oficynic.model.Cliente;
import br.edu.ifsul.oficynic.repository.ClienteRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public ClienteController(
            ClienteRepository repository,
            JdbcTemplate jdbcTemplate) {

        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ResumoCliente(
            Long id,
            String nome,
            String cpf,
            String telefone,
            String email) {
    }

    @GetMapping
    public List<ResumoCliente> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream()
                .map(cliente -> new ResumoCliente(
                        cliente.getId(),
                        cliente.getNome(),
                        ocultarCpf(cliente.getCpf()),
                        ocultarTelefone(cliente.getTelefone()),
                        cliente.getEmail()))
                .toList();
    }

    @GetMapping("/{id}")
    public Cliente buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @PostMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Cliente> cadastrar(
            @Valid @RequestBody Cliente cliente) {

        bloquearGravacao();

        cliente.setId(null);
        verificarDuplicidade(cliente, null);

        Cliente salvo = repository.saveAndFlush(cliente);

        return ResponseEntity
                .created(URI.create("/api/clientes/" + salvo.getId()))
                .body(salvo);
    }

    @PutMapping("/{id}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Cliente atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Cliente dados) {

        bloquearGravacao();

        Cliente cliente = buscar(id);
        verificarDuplicidade(dados, id);

        cliente.setNome(dados.getNome());
        cliente.setCpf(dados.getCpf());
        cliente.setTelefone(dados.getTelefone());
        cliente.setEmail(dados.getEmail());
        cliente.setEndereco(dados.getEndereco());

        return repository.saveAndFlush(cliente);
    }

    // Trava a tabela ate o fim da transacao para evitar cadastro duplicado
    private void bloquearGravacao() {
        jdbcTemplate.execute("LOCK TABLE clientes IN SHARE ROW EXCLUSIVE MODE");
    }

    private void verificarDuplicidade(Cliente dados, Long id) {
        String telefone = dados.getTelefone() == null ? "" : dados.getTelefone().replaceAll("[^0-9]", "");

        if (!telefone.isEmpty() && repository.existeOutroTelefone(telefone, id)) {
            throw new TelefoneDuplicadoException();
        }

        boolean cpfRepetido = id == null
                ? repository.existsByCpf(dados.getCpf())
                : repository.existsByCpfAndIdNot(dados.getCpf(), id);

        if (cpfRepetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este cpf.");
        }
    }

    private String ocultarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return "Não informado";
        }

        return "***.***." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private String ocultarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return "Não informado";
        }

        String numeros = telefone.replaceAll("[^0-9]", "");

        if (numeros.length() <= 4) {
            return "****";
        }

        return "(**) *****-" + numeros.substring(numeros.length() - 4);
    }
}
