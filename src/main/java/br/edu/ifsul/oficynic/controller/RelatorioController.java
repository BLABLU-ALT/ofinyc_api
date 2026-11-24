package br.edu.ifsul.oficynic.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.edu.ifsul.oficynic.service.ExcelService;
import br.edu.ifsul.oficynic.service.RelatorioService;
import br.edu.ifsul.oficynic.service.RelatorioService.Filtro;
import br.edu.ifsul.oficynic.service.RelatorioService.Financeiro;
import br.edu.ifsul.oficynic.service.RelatorioService.LinhaOs;
import br.edu.ifsul.oficynic.service.RelatorioService.ResumoFinanceiro;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private static final List<String> COLUNAS_OS = List.of(
            "Nº OS", "Cliente", "Veículo", "Abertura", "Status",
            "Serviços", "Produtos", "Valor total");

    private static final List<String> COLUNAS_FINANCEIRO = List.of(
            "Nº OS", "Cliente", "Status", "Abertura", "Aprovação",
            "Serviços", "Produtos", "Valor total", "Custo dos produtos", "Margem dos produtos");

    private final RelatorioService service;
    private final ExcelService excelService;

    public RelatorioController(
            RelatorioService service,
            ExcelService excelService) {

        this.service = service;
        this.excelService = excelService;
    }

    @GetMapping("/ordens-servico")
    public List<LinhaOs> ordensServico(Filtro filtro) {
        return service.ordensServico(filtro);
    }

    @GetMapping("/ordens-servico/excel")
    public ResponseEntity<byte[]> ordensServicoExcel(Filtro filtro) {
        byte[] planilha = excelService.gerar(
                "Relatório de ordens de serviço",
                service.descreverFiltros(filtro),
                COLUNAS_OS,
                linhasOs(filtro),
                null,
                null);

        return arquivo(planilha, "relatorio-ordens-servico.xlsx");
    }

    @GetMapping("/financeiro")
    public Financeiro financeiro(Filtro filtro) {
        return service.financeiro(filtro);
    }

    @GetMapping("/financeiro/excel")
    public ResponseEntity<byte[]> financeiroExcel(Filtro filtro) {
        Financeiro dados = service.financeiro(filtro);

        byte[] planilha = excelService.gerar(
                "Relatório de controle financeiro",
                service.descreverFiltros(filtro),
                COLUNAS_FINANCEIRO,
                linhasFinanceiras(dados),
                resumo(dados.resumo()),
                dados.resumo().observacao());

        return arquivo(planilha, "relatorio-financeiro.xlsx");
    }

    private List<List<Object>> linhasOs(Filtro filtro) {
        List<List<Object>> linhas = new ArrayList<>();

        for (LinhaOs linha : service.ordensServico(filtro)) {
            linhas.add(Arrays.asList(
                    linha.numero(),
                    linha.cliente(),
                    linha.veiculo(),
                    linha.dataAbertura(),
                    RelatorioService.rotulo(linha.situacao()),
                    linha.totalServicos(),
                    linha.totalProdutos(),
                    linha.valorTotal()));
        }

        return linhas;
    }

    private List<List<Object>> linhasFinanceiras(Financeiro dados) {
        List<List<Object>> linhas = new ArrayList<>();

        for (var linha : dados.linhas()) {
            linhas.add(Arrays.asList(
                    linha.numero(),
                    linha.cliente(),
                    RelatorioService.rotulo(linha.situacao()),
                    linha.dataAbertura(),
                    linha.dataAprovacao(),
                    linha.totalServicos(),
                    linha.totalProdutos(),
                    linha.valorTotal(),
                    linha.custoProdutos(),
                    linha.margemProdutos()));
        }

        return linhas;
    }

    private List<List<Object>> resumo(ResumoFinanceiro resumo) {
        String semCusto = "Indisponível: " + resumo.itensConcluidosSemCusto() + " item(ns) sem custo registrado";

        Object custo = resumo.custoProdutosConcluidos() == null ? semCusto : resumo.custoProdutosConcluidos();
        Object margem = resumo.margemProdutosConcluidos() == null ? semCusto : resumo.margemProdutosConcluidos();

        return List.of(
                List.of("Ordens de serviço no período", resumo.quantidadeOs()),
                List.of("Aguardando aprovação (orçamentos)", resumo.valorAguardandoAprovacao()),
                List.of("Aprovado pelo cliente", resumo.valorAprovado()),
                List.of("Concluído (serviço executado)", resumo.valorConcluido()),
                List.of("Cancelado (fora dos totais)", resumo.valorCancelado()),
                List.of("Total de serviços", resumo.totalServicos()),
                List.of("Total de produtos", resumo.totalProdutos()),
                List.of("Custo dos produtos concluídos", custo),
                List.of("Margem dos produtos concluídos", margem));
    }

    private ResponseEntity<byte[]> arquivo(byte[] conteudo, String nome) {
        return ResponseEntity.ok()
                .contentType(EXCEL)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .body(conteudo);
    }
}
