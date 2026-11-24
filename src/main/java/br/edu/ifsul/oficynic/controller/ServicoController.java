package br.edu.ifsul.oficynic.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.Servico;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;
import br.edu.ifsul.oficynic.repository.OrdemServicoRepository;
import br.edu.ifsul.oficynic.repository.ServicoRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    private final ServicoRepository repository;
    private final OrdemServicoRepository ordemServicoRepository;

    public ServicoController(
            ServicoRepository repository,
            OrdemServicoRepository ordemServicoRepository) {

        this.repository = repository;
        this.ordemServicoRepository = ordemServicoRepository;
    }

    public record OrdemAberta(
            Long id,
            String cliente,
            SituacaoOrdemServico situacao) {
    }

    @GetMapping
    public List<Servico> listar() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @GetMapping("/{id}")
    public Servico buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    @GetMapping("/{id}/ordens-abertas")
    public List<OrdemAberta> listarOrdensAbertas(@PathVariable Long id) {
        buscar(id);

        var situacoesAbertas = List.of(
                SituacaoOrdemServico.ORCAMENTO,
                SituacaoOrdemServico.EM_ANDAMENTO,
                SituacaoOrdemServico.RETRABALHO);

        return ordemServicoRepository.buscarPorServico(id, situacoesAbertas)
                .stream()
                .map(ordem -> new OrdemAberta(
                        ordem.getId(),
                        ordem.getCliente() == null ? null : ordem.getCliente().getNome(),
                        ordem.getSituacao()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<Servico> cadastrar(
            @Valid @RequestBody Servico servico) {

        servico.setId(null);

        Servico salvo = repository.saveAndFlush(servico);

        return ResponseEntity
                .created(URI.create("/api/servicos/" + salvo.getId()))
                .body(salvo);
    }

    @PutMapping("/{id}")
    public Servico atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Servico dados) {

        Servico servico = buscar(id);

        servico.setNome(dados.getNome());
        servico.setDescricao(dados.getDescricao());
        servico.setPreco(dados.getPreco());

        return repository.saveAndFlush(servico);
    }
}
