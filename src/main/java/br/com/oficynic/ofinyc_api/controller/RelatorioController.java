package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.service.ExcelService;
import br.com.oficynic.ofinyc_api.service.PdfService;
import br.com.oficynic.ofinyc_api.service.RelatorioService;
import br.com.oficynic.ofinyc_api.service.RelatorioService.Filtro;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Central de relatórios: exclusiva do administrador (ver SecurityConfig).
// A tela, o PDF e o Excel usam o mesmo Filtro, então sempre trazem os mesmos registros.
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {
    private static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final List<String> COLUNAS_OS = List.of("Nº OS", "Cliente", "Veículo", "Abertura", "Status",
            "Serviços", "Produtos", "Valor total");
    private static final List<String> COLUNAS_FINANCEIRO = List.of("Nº OS", "Cliente", "Status", "Abertura", "Aprovação",
            "Serviços", "Produtos", "Valor total", "Custo dos produtos", "Margem dos produtos");
    // Larguras proporcionais das colunas no PDF (texto mais largo, números mais estreitos).
    private static final float[] LARGURAS_OS = {0.8f, 3, 3.2f, 1.3f, 1.4f, 1.4f, 1.4f, 1.5f};
    private static final float[] LARGURAS_FINANCEIRO = {0.8f, 2.8f, 1.4f, 1.3f, 1.3f, 1.4f, 1.4f, 1.5f, 1.5f, 1.5f};

    private final RelatorioService service;
    private final PdfService pdf;
    private final ExcelService excel;

    public RelatorioController(RelatorioService service, PdfService pdf, ExcelService excel) {
        this.service = service;
        this.pdf = pdf;
        this.excel = excel;
    }

    @GetMapping("/ordens-servico")
    public List<RelatorioService.LinhaOs> ordensServico(Filtro filtro) {
        return service.ordensServico(filtro);
    }

    @GetMapping("/ordens-servico/pdf")
    public ResponseEntity<byte[]> ordensServicoPdf(Filtro filtro) {
        byte[] arquivo = pdf.gerarTabela("Relatório de ordens de serviço", service.descreverFiltros(filtro), COLUNAS_OS,
                LARGURAS_OS, linhasOs(filtro), null, null);
        return arquivo(arquivo, MediaType.APPLICATION_PDF, "relatorio-ordens-servico.pdf");
    }

    @GetMapping("/ordens-servico/excel")
    public ResponseEntity<byte[]> ordensServicoExcel(Filtro filtro) {
        byte[] arquivo = excel.gerar("Relatório de ordens de serviço", service.descreverFiltros(filtro), COLUNAS_OS,
                linhasOs(filtro), null, null);
        return arquivo(arquivo, EXCEL, "relatorio-ordens-servico.xlsx");
    }

    @GetMapping("/financeiro")
    public RelatorioService.Financeiro financeiro(Filtro filtro) {
        return service.financeiro(filtro);
    }

    @GetMapping("/financeiro/pdf")
    public ResponseEntity<byte[]> financeiroPdf(Filtro filtro) {
        var dados = service.financeiro(filtro);
        byte[] arquivo = pdf.gerarTabela("Relatório de controle financeiro", service.descreverFiltros(filtro), COLUNAS_FINANCEIRO,
                LARGURAS_FINANCEIRO, linhasFinanceiras(dados), resumo(dados.resumo()), dados.resumo().observacao());
        return arquivo(arquivo, MediaType.APPLICATION_PDF, "relatorio-financeiro.pdf");
    }

    @GetMapping("/financeiro/excel")
    public ResponseEntity<byte[]> financeiroExcel(Filtro filtro) {
        var dados = service.financeiro(filtro);
        byte[] arquivo = excel.gerar("Relatório de controle financeiro", service.descreverFiltros(filtro), COLUNAS_FINANCEIRO,
                linhasFinanceiras(dados), resumo(dados.resumo()), dados.resumo().observacao());
        return arquivo(arquivo, EXCEL, "relatorio-financeiro.xlsx");
    }

    private List<List<Object>> linhasOs(Filtro filtro) {
        List<List<Object>> linhas = new ArrayList<>();
        for (var linha : service.ordensServico(filtro)) {
            linhas.add(java.util.Arrays.asList(linha.numero(), linha.cliente(), linha.veiculo(), linha.dataAbertura(),
                    RelatorioService.rotulo(linha.situacao()), linha.totalServicos(), linha.totalProdutos(), linha.valorTotal()));
        }
        return linhas;
    }

    private List<List<Object>> linhasFinanceiras(RelatorioService.Financeiro dados) {
        List<List<Object>> linhas = new ArrayList<>();
        for (var linha : dados.linhas()) {
            linhas.add(java.util.Arrays.asList(linha.numero(), linha.cliente(), RelatorioService.rotulo(linha.situacao()),
                    linha.dataAbertura(), linha.dataAprovacao(), linha.totalServicos(), linha.totalProdutos(), linha.valorTotal(),
                    linha.custoProdutos(), linha.margemProdutos()));
        }
        return linhas;
    }

    private List<List<Object>> resumo(RelatorioService.ResumoFinanceiro r) {
        String semCusto = r.itensConcluidosSemCusto() + " item(ns) sem custo registrado";
        return List.of(
                List.of("Ordens de serviço no período", r.quantidadeOs()),
                List.of("Aguardando aprovação (orçamentos)", r.valorAguardandoAprovacao()),
                List.of("Aprovado pelo cliente", r.valorAprovado()),
                List.of("Concluído (serviço executado)", r.valorConcluido()),
                List.of("Cancelado (fora dos totais)", r.valorCancelado()),
                List.of("Total de serviços", r.totalServicos()),
                List.of("Total de produtos", r.totalProdutos()),
                List.of("Custo dos produtos concluídos", r.custoProdutosConcluidos() == null ? "Indisponível: " + semCusto : r.custoProdutosConcluidos()),
                List.of("Margem dos produtos concluídos", r.margemProdutosConcluidos() == null ? "Indisponível: " + semCusto : r.margemProdutosConcluidos()));
    }

    private ResponseEntity<byte[]> arquivo(byte[] conteudo, MediaType tipo, String nome) {
        return ResponseEntity.ok().contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"").body(conteudo);
    }
}
