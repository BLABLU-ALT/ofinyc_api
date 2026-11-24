package br.edu.ifsul.oficynic.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.Cliente;
import br.edu.ifsul.oficynic.model.Motocicleta;
import br.edu.ifsul.oficynic.repository.ClienteRepository;
import br.edu.ifsul.oficynic.repository.MotocicletaRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/motocicletas")
public class MotocicletaController {

    private final MotocicletaRepository repository;
    private final ClienteRepository clienteRepository;

    public MotocicletaController(
            MotocicletaRepository repository,
            ClienteRepository clienteRepository) {

        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    @GetMapping
    public List<Motocicleta> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Motocicleta buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Motocicleta> cadastrar(
            @Valid @RequestBody Motocicleta motocicleta) {

        motocicleta.setId(null);
        verificarDuplicidade(motocicleta, null);
        motocicleta.setCliente(buscarCliente(motocicleta.getClienteId()));

        Motocicleta salva = repository.saveAndFlush(motocicleta);

        return ResponseEntity
                .created(URI.create("/api/motocicletas/" + salva.getId()))
                .body(salva);
    }

    @PutMapping("/{id}")
    @Transactional
    public Motocicleta atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Motocicleta dados) {

        Motocicleta motocicleta = repository.buscarComBloqueio(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        verificarDuplicidade(dados, id);

        Cliente cliente = buscarCliente(dados.getClienteId());

        if (motocicleta.getCliente() != null) {
            if (cliente != null && !motocicleta.getCliente().getId().equals(cliente.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A troca de proprietário não faz parte deste protótipo.");
            }

            cliente = motocicleta.getCliente();
        }

        motocicleta.setCliente(cliente);
        motocicleta.setPlaca(dados.getPlaca());
        motocicleta.setChassi(dados.getChassi());
        motocicleta.setMarca(dados.getMarca());
        motocicleta.setModelo(dados.getModelo());
        motocicleta.setAno(dados.getAno());
        motocicleta.setCor(dados.getCor());

        return repository.saveAndFlush(motocicleta);
    }

    private Cliente buscarCliente(Long id) {
        if (id == null) {
            return null;
        }

        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado."));
    }

    private void verificarDuplicidade(Motocicleta dados, Long id) {
        if (dados.getPlaca() == null && dados.getChassi() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a placa ou o chassi.");
        }

        boolean placaRepetida = dados.getPlaca() != null && (id == null
                ? repository.existsByPlaca(dados.getPlaca())
                : repository.existsByPlacaAndIdNot(dados.getPlaca(), id));

        if (placaRepetida) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com esta placa.");
        }

        boolean chassiRepetido = dados.getChassi() != null && (id == null
                ? repository.existsByChassi(dados.getChassi())
                : repository.existsByChassiAndIdNot(dados.getChassi(), id));

        if (chassiRepetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este chassi.");
        }
    }
}
