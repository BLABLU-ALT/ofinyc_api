package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.OrdemServico;
import br.com.oficynic.ofinyc_api.model.ItemServicoOS;
import br.com.oficynic.ofinyc_api.model.SituacaoOrdemServico;
import br.com.oficynic.ofinyc_api.model.ItemProdutoOS;
import br.com.oficynic.ofinyc_api.service.OrdemServicoService;
import br.com.oficynic.ofinyc_api.service.PdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ordens-servico")
public class OrdemServicoController {
    private final OrdemServicoService service;
    private final PdfService pdfService;

    public OrdemServicoController(OrdemServicoService service, PdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    public record Abertura(
            @NotBlank(message = "Informe a orientação do cliente.") @Size(max = 255) String descricao,
            @NotNull(message = "Selecione o cliente.") @Positive Long clienteId,
            @NotNull(message = "Selecione a motocicleta.") @Positive Long motocicletaId,
            LocalDate dataAbertura,
            @NotNull(message = "Selecione o funcionário responsável.") @Positive Long funcionarioId) { }

    public record Edicao(
            @NotBlank(message = "Informe a orientação do cliente.") @Size(max = 255) String descricao,
            @Size(max = 1000) String parecerTecnico,
            @NotNull @Positive Long clienteId, @NotNull @Positive Long motocicletaId,
            LocalDate dataAbertura,
            @NotNull(message = "Selecione o funcionário responsável.") @Positive Long funcionarioId) { }

    public record InclusaoProduto(@NotNull @Positive Long produtoId,
            @NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantidade,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal valorUnitario) { }
    public record EdicaoProduto(@NotNull @Positive @Digits(integer = 12, fraction = 3) BigDecimal quantidade,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal valorUnitario) { }
    public record ProdutoConsulta(Long id, Long produtoId, String codigo, String descricao, BigDecimal quantidade,
            String unidadeMedida, BigDecimal valorUnitario, BigDecimal subtotal) { }

    public record InclusaoServico(@NotNull @Positive Long servicoId,
            @Positive @Digits(integer = 10, fraction = 0) @DecimalMax("2147483647") BigDecimal quantidade,
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal valorUnitario) { }

    public record EdicaoItem(@NotNull @Positive @Digits(integer = 10, fraction = 0) @DecimalMax("2147483647") BigDecimal quantidade,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal valorUnitario) { }

    public record AlteracaoSituacao(@NotNull SituacaoOrdemServico situacao) { }

    public record ItemConsulta(Long id, Long servicoId, String nome, Integer quantidade,
                               BigDecimal valorUnitario, BigDecimal subtotal) { }

    public record Consulta(Long id, String descricao, LocalDate dataAbertura,
            Long clienteId, String clienteNome, Long motocicletaId, String placa, String chassi,
            String marca, String modelo, SituacaoOrdemServico situacao, LocalDate dataFinalizacao,
            List<ItemConsulta> servicos, BigDecimal valorTotal, Long funcionarioId, String funcionarioNome,
            List<ProdutoConsulta> produtos, BigDecimal totalServicos, BigDecimal totalProdutos,
            Long ordemOrigemId, LocalDate dataCriacao, LocalDate dataAprovacao, LocalDate dataAberturaAnterior,
            String parecerTecnico) { }

    @GetMapping("/data-atual")
    public LocalDate dataAtual() { return service.dataAtual(); }

    // PDF de aprovação do cliente: disponível a quem trabalha na OS; só lê os dados.
    @GetMapping("/{id}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> pdfAprovacao(@PathVariable Long id) {
        byte[] pdf = pdfService.gerarAprovacaoOs(service.buscarPorId(id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"os-" + id + "-aprovacao.pdf\"").body(pdf);
    }

    @PostMapping("/{id}/aprovacao")
    public Consulta aprovar(@PathVariable Long id) { return consulta(service.aprovar(id)); }

    @PostMapping("/abertura")
    public ResponseEntity<Consulta> abrir(@Valid @RequestBody Abertura dados) {
        var ordem = service.abrir(dados.descricao(), dados.dataAbertura(), dados.clienteId(), dados.motocicletaId(), dados.funcionarioId());
        return ResponseEntity.created(URI.create("/api/ordens-servico/consulta/" + ordem.getId())).body(consulta(ordem));
    }

    @GetMapping("/consulta")
    @Transactional(readOnly = true)
    public List<Consulta> consultar() {
        List<Consulta> resultado = new ArrayList<>();
        for (OrdemServico ordem : service.listar()) resultado.add(consulta(ordem));
        return resultado;
    }

    @GetMapping("/consulta/{id}")
    @Transactional(readOnly = true)
    public Consulta consultarPorId(@PathVariable Long id) {
        return consulta(service.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public Consulta atualizar(@PathVariable Long id, @Valid @RequestBody Edicao dados, HttpServletRequest request) {
        return consulta(service.atualizar(id, dados.descricao(), dados.parecerTecnico(), dados.clienteId(),
                dados.motocicletaId(), dados.funcionarioId(), administrador(request)));
    }

    @PostMapping("/{id}/servicos")
    public ResponseEntity<Consulta> adicionarServico(@PathVariable Long id, @Valid @RequestBody InclusaoServico dados,
                                                     HttpServletRequest request) {
        var ordem = service.adicionarServico(id, dados.servicoId(),
                dados.quantidade() == null ? 1 : dados.quantidade().intValueExact(), dados.valorUnitario(), administrador(request));
        return ResponseEntity.status(201).body(consulta(ordem));
    }

    @PutMapping("/{id}/servicos/{itemId}")
    public Consulta atualizarItem(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody EdicaoItem dados,
                                  HttpServletRequest request) {
        return consulta(service.atualizarItem(id, itemId, dados.quantidade().intValueExact(), dados.valorUnitario(), administrador(request)));
    }

    @DeleteMapping("/{id}/servicos/{itemId}")
    public Consulta removerItem(@PathVariable Long id, @PathVariable Long itemId) {
        return consulta(service.removerItem(id, itemId));
    }

    @PutMapping("/{id}/situacao")
    public Consulta alterarSituacao(@PathVariable Long id, @Valid @RequestBody AlteracaoSituacao dados) {
        return consulta(service.alterarSituacao(id, dados.situacao()));
    }

    @PostMapping("/{id}/finalizacao")
    public Consulta finalizarOrdem(@PathVariable Long id, Principal usuario) {
        return consulta(service.finalizarOrdem(id, usuario.getName()));
    }

    @PostMapping("/{id}/cancelamento")
    public Consulta cancelarOrdem(@PathVariable Long id) {
        return consulta(service.cancelarOrdem(id));
    }

    @PostMapping("/{id}/retrabalho")
    public ResponseEntity<Consulta> criarRetrabalho(@PathVariable Long id) {
        var nova = service.criarRetrabalho(id);
        return ResponseEntity.created(URI.create("/api/ordens-servico/consulta/" + nova.getId()))
                .body(consulta(nova));
    }

    @PostMapping("/{id}/produtos")
    public ResponseEntity<Consulta> adicionarProduto(@PathVariable Long id, @Valid @RequestBody InclusaoProduto dados,
                                                     HttpServletRequest request) {
        return ResponseEntity.status(201).body(consulta(service.adicionarProduto(id, dados.produtoId(), dados.quantidade(),
                dados.valorUnitario(), administrador(request))));
    }
    @PutMapping("/{id}/produtos/{itemId}")
    public Consulta atualizarProduto(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody EdicaoProduto dados,
                                     HttpServletRequest request) {
        return consulta(service.atualizarProduto(id, itemId, dados.quantidade(), dados.valorUnitario(), administrador(request)));
    }
    @DeleteMapping("/{id}/produtos/{itemId}")
    public Consulta removerProduto(@PathVariable Long id, @PathVariable Long itemId) {
        return consulta(service.removerProduto(id, itemId));
    }

    private Consulta consulta(OrdemServico ordem) {
        var cliente = ordem.getCliente();
        var moto = ordem.getMotocicleta();
        var funcionario = ordem.getFuncionario();
        List<ProdutoConsulta> produtos = new ArrayList<>();
        for (ItemProdutoOS item : ordem.getProdutos()) produtos.add(new ProdutoConsulta(item.getId(), item.getProduto().getId(),
                item.getProduto().getCodigo(), item.getProduto().getDescricao(), item.getQuantidade(), item.getUnidadeMedida(), item.getValorUnitario(), item.calcularSubtotal()));
        List<ItemConsulta> itens = new ArrayList<>();
        for (ItemServicoOS item : ordem.getItens()) {
            itens.add(new ItemConsulta(item.getId(), item.getServico().getId(), item.getServico().getNome(),
                    item.getQuantidade(), item.getValorUnitario(), item.calcularSubtotal()));
        }
        return new Consulta(ordem.getId(), ordem.getDescricao(), ordem.getDataAbertura(),
                cliente == null ? null : cliente.getId(), cliente == null ? null : cliente.getNome(),
                moto == null ? null : moto.getId(), moto == null ? null : moto.getPlaca(), moto == null ? null : moto.getChassi(),
                moto == null ? null : moto.getMarca(), moto == null ? null : moto.getModelo(),
                ordem.getSituacao(), ordem.getDataFinalizacao(), itens, ordem.calcularValorTotal(),
                funcionario == null ? null : funcionario.getId(), funcionario == null ? null : funcionario.getNome(),
                produtos, ordem.calcularTotalServicos(), ordem.calcularTotalProdutos(),
                ordem.getOrdemOrigem() == null ? null : ordem.getOrdemOrigem().getId(),
                ordem.getDataCriacao(), ordem.getDataAprovacao(), ordem.getDataAberturaAnterior(),
                ordem.getParecerTecnico());
    }

    private boolean administrador(HttpServletRequest request) {
        return request.isUserInRole("ADMINISTRADOR");
    }

    // Rotas da aula preservadas; suas gravações passam pelas mesmas proteções do service.
    @GetMapping
    public List<OrdemServico> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public OrdemServico buscar(@PathVariable Long id) { return service.buscarPorId(id); }

    @PostMapping
    public ResponseEntity<OrdemServico> cadastrar(@Valid @RequestBody OrdemServico dados) {
        return ResponseEntity.status(201).body(service.cadastrarAntiga(dados));
    }

    @PutMapping("/{ordemId}/cliente/{clienteId}")
    public OrdemServico adicionarCliente(@PathVariable Long ordemId, @PathVariable Long clienteId) {
        return service.adicionarCliente(ordemId, clienteId);
    }
}
