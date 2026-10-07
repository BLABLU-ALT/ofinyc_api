package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Motocicleta;
import br.com.oficynic.ofinyc_api.repository.MotocicletaRepository;
import br.com.oficynic.ofinyc_api.repository.ClienteRepository;
import br.com.oficynic.ofinyc_api.model.Cliente;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/motocicletas")
public class MotocicletaController {
    private final MotocicletaRepository repository;
    private final ClienteRepository clienteRepository;

    public MotocicletaController(MotocicletaRepository repository, ClienteRepository clienteRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    @GetMapping
    public List<Motocicleta> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Motocicleta buscar(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Motocicleta> cadastrar(@Valid @RequestBody Motocicleta dados) {
        dados.setId(null);
        verificarDuplicidade(dados, null);
        dados.setCliente(buscarCliente(dados.getClienteId()));
        Motocicleta salvo = repository.saveAndFlush(dados);
        return ResponseEntity.created(URI.create("/api/motocicletas/" + salvo.getId())).body(salvo);
    }

    @PutMapping("/{id}")
    @Transactional
    public Motocicleta atualizar(@PathVariable Long id, @Valid @RequestBody Motocicleta dados) {
        Motocicleta existente = repository.buscarComBloqueio(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        verificarDuplicidade(dados, id);
        Cliente cliente = buscarCliente(dados.getClienteId());
        if (existente.getCliente() != null && !existente.getCliente().getId().equals(cliente.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A troca de proprietário não faz parte deste protótipo.");
        }
        existente.setCliente(cliente);
        // Copia somente os campos editáveis do formulário.
        existente.setPlaca(dados.getPlaca());
        existente.setChassi(dados.getChassi());
        existente.setMarca(dados.getMarca());
        existente.setModelo(dados.getModelo());
        existente.setAno(dados.getAno());
        existente.setCor(dados.getCor());
        return repository.saveAndFlush(existente);
    }

    private Cliente buscarCliente(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o cliente proprietário.");
        }
        return clienteRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private void verificarDuplicidade(Motocicleta dados, Long id) {
        if (dados.getPlaca() == null && dados.getChassi() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a placa ou o chassi.");
        }
        if (dados.getPlaca() != null && (id == null ? repository.existsByPlaca(dados.getPlaca())
                : repository.existsByPlacaAndIdNot(dados.getPlaca(), id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com esta placa.");
        }
        if (dados.getChassi() != null && (id == null ? repository.existsByChassi(dados.getChassi())
                : repository.existsByChassiAndIdNot(dados.getChassi(), id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este chassi.");
        }
    }
}
