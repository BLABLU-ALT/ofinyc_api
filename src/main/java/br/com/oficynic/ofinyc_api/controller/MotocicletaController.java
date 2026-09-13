package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Motocicleta;
import br.com.oficynic.ofinyc_api.repository.MotocicletaRepository;
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

    public MotocicletaController(MotocicletaRepository repository) {
        this.repository = repository;
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
    public ResponseEntity<Motocicleta> cadastrar(@Valid @RequestBody Motocicleta dados) {
        dados.setId(null);
        verificarDuplicidade(dados, null);
        Motocicleta salvo = repository.saveAndFlush(dados);
        return ResponseEntity.created(URI.create("/api/motocicletas/" + salvo.getId())).body(salvo);
    }

    @PutMapping("/{id}")
    public Motocicleta atualizar(@PathVariable Long id, @Valid @RequestBody Motocicleta dados) {
        Motocicleta existente = buscar(id);
        verificarDuplicidade(dados, id);
        // Copia somente os campos editáveis do formulário.
        existente.setPlaca(dados.getPlaca());
        existente.setChassi(dados.getChassi());
        existente.setMarca(dados.getMarca());
        existente.setModelo(dados.getModelo());
        existente.setAno(dados.getAno());
        existente.setCor(dados.getCor());
        return repository.saveAndFlush(existente);
    }

    private void verificarDuplicidade(Motocicleta dados, Long id) {
        if (id == null ? repository.existsByPlaca(dados.getPlaca())
                : repository.existsByPlacaAndIdNot(dados.getPlaca(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este placa.");
        }
        if (id == null ? repository.existsByChassi(dados.getChassi())
                : repository.existsByChassiAndIdNot(dados.getChassi(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um cadastro com este chassi.");
        }
    }
}
