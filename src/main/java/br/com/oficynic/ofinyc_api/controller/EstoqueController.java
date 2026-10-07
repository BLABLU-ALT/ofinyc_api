package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.*;
import br.com.oficynic.ofinyc_api.service.EstoqueService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/estoque/movimentos")
public class EstoqueController {
    private final EstoqueService service;
    public EstoqueController(EstoqueService service) { this.service = service; }
    public record Registro(@NotNull @Positive Long produtoId, @NotNull @Positive Long funcionarioId,
            @NotNull TipoMovimentacao tipo, @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantidade,
            @NotBlank(message = "Informe o motivo.") @Size(max = 255) String motivo) { }
    public record Consulta(Long id, Long produtoId, String codigo, String descricao, TipoMovimentacao tipo,
            BigDecimal quantidade, String unidadeMedida, Instant data, Long funcionarioId, String funcionarioNome,
            String usuarioAtuante, String motivo, Long ordemServicoId) { }
    @GetMapping @Transactional(readOnly = true)
    public List<Consulta> listar() { return service.listar().stream().map(this::consulta).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Consulta registrar(@Valid @RequestBody Registro dados, Principal usuario) {
        return consulta(service.movimentar(dados.produtoId(), dados.funcionarioId(), dados.tipo(), dados.quantidade(), dados.motivo(), usuario.getName()));
    }
    private Consulta consulta(MovimentacaoEstoque movimento) {
        return new Consulta(movimento.getId(), movimento.getProduto().getId(), movimento.getProduto().getCodigo(),
                movimento.getProduto().getDescricao(), movimento.getTipo(), movimento.getQuantidade(), movimento.getUnidadeMedida(),
                movimento.getData(), movimento.getFuncionario().getId(), movimento.getFuncionario().getNome(), movimento.getUsuarioAtuante(),
                movimento.getMotivo(), movimento.getOrdemServico() == null ? null : movimento.getOrdemServico().getId());
    }
}
