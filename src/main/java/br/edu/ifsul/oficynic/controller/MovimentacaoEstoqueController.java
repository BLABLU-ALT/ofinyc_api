package br.edu.ifsul.oficynic.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifsul.oficynic.model.MovimentacaoEstoque;
import br.edu.ifsul.oficynic.model.TipoMovimentacao;
import br.edu.ifsul.oficynic.service.EstoqueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/movimentacoes-estoque")
public class MovimentacaoEstoqueController {

    private final EstoqueService service;

    public MovimentacaoEstoqueController(EstoqueService service) {
        this.service = service;
    }

    public record Registro(
            @NotNull @Positive
            Long produtoId,

            @NotNull @Positive
            Long funcionarioId,

            @NotNull
            TipoMovimentacao tipo,

            @NotNull @Positive @Digits(integer = 12, fraction = 3)
            BigDecimal quantidade,

            @DecimalMin(value = "0.00", message = "O valor unitário não pode ser negativo.")
            @Digits(integer = 12, fraction = 2)
            BigDecimal valorUnitario,

            @NotBlank(message = "Informe o motivo.")
            @Size(max = 255)
            String motivo) {
    }

    public record Consulta(
            Long id,
            Long produtoId,
            String codigo,
            String descricao,
            TipoMovimentacao tipo,
            BigDecimal quantidade,
            String unidadeMedida,
            Instant data,
            Long funcionarioId,
            String funcionarioNome,
            String usuarioAtuante,
            String motivo,
            Long ordemServicoId,
            BigDecimal valorUnitario) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Consulta> listar(HttpServletRequest request) {
        return service.listar()
                .stream()
                .map(movimento -> consulta(movimento, request))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Consulta registrar(
            @Valid @RequestBody Registro dados,
            Principal usuario,
            HttpServletRequest request) {

        MovimentacaoEstoque movimento = service.movimentar(
                dados.produtoId(),
                dados.funcionarioId(),
                dados.tipo(),
                dados.quantidade(),
                dados.valorUnitario(),
                dados.motivo(),
                usuario.getName());

        return consulta(movimento, request);
    }

    // Valor de compra so aparece para o administrador
    private Consulta consulta(MovimentacaoEstoque movimento, HttpServletRequest request) {
        return new Consulta(
                movimento.getId(),
                movimento.getProduto().getId(),
                movimento.getProduto().getCodigo(),
                movimento.getProduto().getDescricao(),
                movimento.getTipo(),
                movimento.getQuantidade(),
                movimento.getUnidadeMedida(),
                movimento.getData(),
                movimento.getFuncionario().getId(),
                movimento.getFuncionario().getNome(),
                movimento.getUsuarioAtuante(),
                movimento.getMotivo(),
                movimento.getOrdemServico() == null ? null : movimento.getOrdemServico().getId(),
                request.isUserInRole("ADMINISTRADOR") ? movimento.getValorUnitario() : null);
    }
}
