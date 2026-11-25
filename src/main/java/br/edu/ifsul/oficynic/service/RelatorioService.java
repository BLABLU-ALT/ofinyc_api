package br.edu.ifsul.oficynic.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.Cliente;
import br.edu.ifsul.oficynic.model.ItemProdutoOS;
import br.edu.ifsul.oficynic.model.Motocicleta;
import br.edu.ifsul.oficynic.model.OrdemServico;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;
import br.edu.ifsul.oficynic.repository.ClienteRepository;
import br.edu.ifsul.oficynic.repository.OrdemServicoRepository;

@Service
@Transactional(readOnly = true)
public class RelatorioService {

    public static final String OBSERVACAO_FINANCEIRA = "Valores calculados a partir dos itens registrados nas OS. "
            + "O sistema não registra pagamentos: valores aprovados ou concluídos não representam receita recebida. "
            + "Custos e margens consideram apenas produtos com preço de custo registrado no momento da inclusão.";

    private final OrdemServicoRepository ordemServicoRepository;
    private final ClienteRepository clienteRepository;

    public RelatorioService(
            OrdemServicoRepository ordemServicoRepository,
            ClienteRepository clienteRepository) {

        this.ordemServicoRepository = ordemServicoRepository;
        this.clienteRepository = clienteRepository;
    }

    public record Filtro(
            Long clienteId,
            Long numero,
            SituacaoOrdemServico situacao,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
    }

    public record LinhaOs(
            Long numero,
            String cliente,
            String veiculo,
            LocalDate dataAbertura,
            SituacaoOrdemServico situacao,
            BigDecimal totalServicos,
            BigDecimal totalProdutos,
            BigDecimal valorTotal) {
    }

    public record LinhaFinanceira(
            Long numero,
            String cliente,
            SituacaoOrdemServico situacao,
            LocalDate dataAbertura,
            LocalDate dataAprovacao,
            BigDecimal totalServicos,
            BigDecimal totalProdutos,
            BigDecimal valorTotal,
            BigDecimal custoProdutos,
            BigDecimal margemProdutos) {
    }

    public record ResumoFinanceiro(
            int quantidadeOs,
            BigDecimal valorAguardandoAprovacao,
            BigDecimal valorAprovado,
            BigDecimal valorConcluido,
            BigDecimal valorCancelado,
            BigDecimal totalServicos,
            BigDecimal totalProdutos,
            BigDecimal custoProdutosConcluidos,
            BigDecimal margemProdutosConcluidos,
            int itensConcluidosSemCusto,
            String observacao) {
    }

    public record Financeiro(
            List<LinhaFinanceira> linhas,
            ResumoFinanceiro resumo) {
    }

    // Periodo pela data de abertura e todos os filtros opcionais
    public List<OrdemServico> filtrar(Filtro filtro) {
        if (filtro.inicio() != null && filtro.fim() != null && filtro.inicio().isAfter(filtro.fim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A data inicial não pode ser posterior à data final.");
        }

        List<OrdemServico> resultado = new ArrayList<>();

        for (OrdemServico os : ordemServicoRepository.findAll(Sort.by("id"))) {
            if (filtro.numero() != null && !filtro.numero().equals(os.getId())) {
                continue;
            }

            if (filtro.clienteId() != null
                    && (os.getCliente() == null || !filtro.clienteId().equals(os.getCliente().getId()))) {
                continue;
            }

            if (filtro.situacao() != null && filtro.situacao() != os.getSituacao()) {
                continue;
            }

            if (filtro.inicio() != null && os.getDataAbertura().isBefore(filtro.inicio())) {
                continue;
            }

            if (filtro.fim() != null && os.getDataAbertura().isAfter(filtro.fim())) {
                continue;
            }

            resultado.add(os);
        }

        return resultado;
    }

    public List<LinhaOs> ordensServico(Filtro filtro) {
        List<LinhaOs> linhas = new ArrayList<>();

        for (OrdemServico os : filtrar(filtro)) {
            linhas.add(new LinhaOs(
                    os.getId(),
                    nomeCliente(os),
                    veiculo(os.getMotocicleta()),
                    os.getDataAbertura(),
                    os.getSituacao(),
                    os.calcularTotalServicos(),
                    os.calcularTotalProdutos(),
                    os.calcularValorTotal()));
        }

        return linhas;
    }

    public Financeiro financeiro(Filtro filtro) {
        List<LinhaFinanceira> linhas = new ArrayList<>();

        BigDecimal aguardando = BigDecimal.ZERO;
        BigDecimal aprovado = BigDecimal.ZERO;
        BigDecimal concluido = BigDecimal.ZERO;
        BigDecimal cancelado = BigDecimal.ZERO;
        BigDecimal servicos = BigDecimal.ZERO;
        BigDecimal produtos = BigDecimal.ZERO;
        BigDecimal custoConcluidas = BigDecimal.ZERO;
        BigDecimal produtosConcluidas = BigDecimal.ZERO;
        int semCusto = 0;

        List<OrdemServico> selecionadas = filtrar(filtro);

        for (OrdemServico os : selecionadas) {
            BigDecimal custo = custoProdutos(os);
            BigDecimal margem = custo == null ? null : os.calcularTotalProdutos().subtract(custo);

            linhas.add(new LinhaFinanceira(
                    os.getId(),
                    nomeCliente(os),
                    os.getSituacao(),
                    os.getDataAbertura(),
                    os.getDataAprovacao(),
                    os.calcularTotalServicos(),
                    os.calcularTotalProdutos(),
                    os.calcularValorTotal(),
                    custo,
                    margem));

            // OS cancelada aparece na lista mas fica fora dos totais
            if (os.getSituacao() == SituacaoOrdemServico.CANCELADA) {
                cancelado = cancelado.add(os.calcularValorTotal());
                continue;
            }

            servicos = servicos.add(os.calcularTotalServicos());
            produtos = produtos.add(os.calcularTotalProdutos());

            if (os.getSituacao() == SituacaoOrdemServico.ORCAMENTO) {
                aguardando = aguardando.add(os.calcularValorTotal());
            }

            if (os.getDataAprovacao() != null) {
                aprovado = aprovado.add(os.calcularValorTotal());
            }

            if (os.getSituacao() == SituacaoOrdemServico.CONCLUIDA) {
                concluido = concluido.add(os.calcularValorTotal());
                produtosConcluidas = produtosConcluidas.add(os.calcularTotalProdutos());

                for (ItemProdutoOS item : os.getProdutos()) {
                    if (item.getCustoUnitario() == null) {
                        semCusto++;
                    } else {
                        custoConcluidas = custoConcluidas.add(item.getCustoUnitario().multiply(item.getQuantidade()));
                    }
                }
            }
        }

        // Custo e margem so quando todos os produtos concluidos tem custo
        BigDecimal custoFinal = semCusto == 0 ? custoConcluidas.setScale(2, RoundingMode.HALF_UP) : null;
        BigDecimal margemFinal = custoFinal == null ? null : produtosConcluidas.subtract(custoFinal);

        ResumoFinanceiro resumo = new ResumoFinanceiro(
                selecionadas.size(),
                aguardando,
                aprovado,
                concluido,
                cancelado,
                servicos,
                produtos,
                custoFinal,
                margemFinal,
                semCusto,
                OBSERVACAO_FINANCEIRA);

        return new Financeiro(linhas, resumo);
    }

    public String descreverFiltros(Filtro filtro) {
        List<String> partes = new ArrayList<>();

        if (filtro.clienteId() != null) {
            String cliente = clienteRepository.findById(filtro.clienteId())
                    .map(Cliente::getNome)
                    .orElse("nº " + filtro.clienteId());

            partes.add("cliente " + cliente);
        }

        if (filtro.numero() != null) {
            partes.add("OS nº " + filtro.numero());
        }

        if (filtro.situacao() != null) {
            partes.add("status " + rotulo(filtro.situacao()));
        }

        if (filtro.inicio() != null || filtro.fim() != null) {
            String inicio = filtro.inicio() == null ? "início" : PdfService.data(filtro.inicio());
            String fim = filtro.fim() == null ? "hoje" : PdfService.data(filtro.fim());

            partes.add("abertura de " + inicio + " a " + fim);
        }

        return partes.isEmpty()
                ? "Filtros: nenhum (todas as OS)"
                : "Filtros: " + String.join("; ", partes);
    }

    public static String rotulo(SituacaoOrdemServico situacao) {
        if (situacao == null) {
            return "—";
        }

        return switch (situacao) {
            case ORCAMENTO -> "Orçamento";
            case EM_ANDAMENTO -> "Em andamento";
            case CONCLUIDA -> "Concluída";
            case CANCELADA -> "Cancelada";
            case RETRABALHO -> "Retrabalho";
        };
    }

    // Nulo se algum produto da OS nao tem custo
    private BigDecimal custoProdutos(OrdemServico os) {
        BigDecimal total = BigDecimal.ZERO;

        for (ItemProdutoOS item : os.getProdutos()) {
            if (item.getCustoUnitario() == null) {
                return null;
            }

            total = total.add(item.getCustoUnitario().multiply(item.getQuantidade()));
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String nomeCliente(OrdemServico os) {
        return os.getCliente() == null ? "Não informado" : os.getCliente().getNome();
    }

    private String veiculo(Motocicleta moto) {
        if (moto == null) {
            return "Não informado";
        }

        String identificacao = moto.getPlaca() != null ? moto.getPlaca() : moto.getChassi();

        return identificacao + " · " + moto.getMarca() + " " + moto.getModelo();
    }
}
