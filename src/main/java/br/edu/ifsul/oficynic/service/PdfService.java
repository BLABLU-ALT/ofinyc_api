package br.edu.ifsul.oficynic.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import br.edu.ifsul.oficynic.model.Cliente;
import br.edu.ifsul.oficynic.model.ItemProdutoOS;
import br.edu.ifsul.oficynic.model.ItemServicoOS;
import br.edu.ifsul.oficynic.model.Motocicleta;
import br.edu.ifsul.oficynic.model.OrdemServico;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;

@Service
public class PdfService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
    private static final Font SECAO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font NEGRITO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font DESTAQUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Color FUNDO = new Color(228, 234, 240);

    // So gera o documento e a aprovacao fica em outra rota
    public byte[] gerarAprovacaoOs(OrdemServico os) {
        var saida = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4, 36, 36, 32, 32);
        PdfWriter.getInstance(documento, saida);
        documento.open();

        documento.add(new Paragraph("OFICYNIC — Ordem de Serviço nº " + os.getId(), TITULO));
        documento.add(new Paragraph("Documento para aprovação do orçamento pelo cliente", TEXTO));
        documento.add(espaco());
        String situacao;

        if (os.getSituacao() == SituacaoOrdemServico.CANCELADA) {
            situacao = "OS CANCELADA — documento somente para consulta";
        } else if (os.getDataAprovacao() != null) {
            situacao = "APROVADA PELO CLIENTE EM " + data(os.getDataAprovacao());
        } else {
            situacao = "AGUARDANDO APROVAÇÃO DO CLIENTE";
        }

        documento.add(new Paragraph(situacao, DESTAQUE));
        documento.add(espaco());

        var dados = tabela(4);
        adicionarPar(dados, "Situação", RelatorioService.rotulo(os.getSituacao()));
        adicionarPar(dados, "Data de criação", data(os.getDataCriacao()));
        adicionarPar(dados, "Data de abertura", data(os.getDataAbertura()));
        adicionarPar(dados, "Data de aprovação", os.getDataAprovacao() == null ? "Aguardando" : data(os.getDataAprovacao()));
        adicionarPar(dados, "Responsável", os.getFuncionario() == null ? null : os.getFuncionario().getNome());
        adicionarPar(dados, "Retrabalho da OS", os.getOrdemOrigem() == null ? "—" : "nº " + os.getOrdemOrigem().getId());
        documento.add(dados);

        Cliente cliente = os.getCliente();
        documento.add(secao("Cliente"));
        var tabelaCliente = tabela(4);
        adicionarPar(tabelaCliente, "Nome", cliente == null ? null : cliente.getNome());
        adicionarPar(tabelaCliente, "CPF", cliente == null ? null : cpf(cliente.getCpf()));
        adicionarPar(tabelaCliente, "Telefone", cliente == null ? null : cliente.getTelefone());
        adicionarPar(tabelaCliente, "E-mail", cliente == null ? null : cliente.getEmail());
        documento.add(tabelaCliente);

        Motocicleta moto = os.getMotocicleta();
        documento.add(secao("Veículo"));
        var tabelaMoto = tabela(4);
        adicionarPar(tabelaMoto, "Marca / modelo", moto == null ? null : moto.getMarca() + " " + moto.getModelo());
        adicionarPar(tabelaMoto, "Ano / cor", moto == null ? null : anoECor(moto));
        adicionarPar(tabelaMoto, "Placa", moto == null ? null : moto.getPlaca());
        adicionarPar(tabelaMoto, "Chassi", moto == null ? null : moto.getChassi());
        documento.add(tabelaMoto);

        documento.add(secao("Orientação do cliente"));
        documento.add(new Paragraph(os.getDescricao(), TEXTO));
        if (os.getParecerTecnico() != null) {
            documento.add(secao("Parecer técnico"));
            documento.add(new Paragraph(os.getParecerTecnico(), TEXTO));
        }

        documento.add(secao("Serviços"));
        var servicos = tabela(new float[]{6, 1.2f, 2, 2});
        cabecalho(servicos, "Descrição", "Qtd.", "Valor unitário", "Subtotal");
        for (ItemServicoOS item : os.getItens()) {
            texto(servicos, item.getServico().getNome());
            numero(servicos, String.valueOf(item.getQuantidade()));
            numero(servicos, dinheiro(item.getValorUnitario()));
            numero(servicos, dinheiro(item.calcularSubtotal()));
        }
        if (os.getItens().isEmpty()) {
            vazio(servicos, 4, "Nenhum serviço registrado.");
        }

        documento.add(servicos);

        documento.add(secao("Produtos e materiais"));
        var produtos = tabela(new float[]{1.8f, 4.2f, 1.2f, 1, 2, 2});
        cabecalho(produtos, "Código", "Descrição", "Qtd.", "Un.", "Valor unitário", "Subtotal");
        for (ItemProdutoOS item : os.getProdutos()) {
            texto(produtos, item.getProduto().getCodigo());
            texto(produtos, item.getProduto().getDescricao());
            numero(produtos, item.getQuantidade().stripTrailingZeros().toPlainString().replace('.', ','));
            texto(produtos, item.getUnidadeMedida());
            numero(produtos, dinheiro(item.getValorUnitario()));
            numero(produtos, dinheiro(item.calcularSubtotal()));
        }
        if (os.getProdutos().isEmpty()) {
            vazio(produtos, 6, "Nenhum produto registrado.");
        }

        documento.add(produtos);

        documento.add(espaco());
        var totais = tabela(new float[]{6, 4});
        totais.setWidthPercentage(55);
        totais.setHorizontalAlignment(Element.ALIGN_RIGHT);
        adicionarTotal(totais, "Total de serviços", os.calcularTotalServicos(), false);
        adicionarTotal(totais, "Total de produtos", os.calcularTotalProdutos(), false);
        adicionarTotal(totais, "Valor total previsto", os.calcularValorTotal(), true);
        documento.add(totais);

        documento.add(secao("Aprovação do cliente"));
        documento.add(new Paragraph(
                "Declaro que conferi os serviços, produtos e valores acima e autorizo a execução do atendimento.",
                TEXTO));
        documento.add(espaco());
        documento.add(espaco());
        var assinatura = tabela(new float[]{6, 1, 3});
        assinatura.addCell(semBorda("______________________________________________", TEXTO));
        assinatura.addCell(semBorda("", TEXTO));
        assinatura.addCell(semBorda(os.getDataAprovacao() == null ? "____/____/________" : data(os.getDataAprovacao()), TEXTO));
        assinatura.addCell(semBorda("Assinatura do cliente" + (cliente == null ? "" : " — " + cliente.getNome()), TEXTO));
        assinatura.addCell(semBorda("", TEXTO));
        assinatura.addCell(semBorda("Data da aprovação", TEXTO));
        documento.add(assinatura);

        String geradoEm = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).format(DATA_HORA);

        documento.add(espaco());
        documento.add(new Paragraph(
                "Gerado em " + geradoEm + ". A geração deste documento não registra a aprovação no sistema.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8)));
        documento.close();

        return saida.toByteArray();
    }

    static String formatar(Object valor) {
        if (valor == null) {
            return "—";
        }

        if (valor instanceof BigDecimal dinheiro) {
            return dinheiro(dinheiro);
        }

        if (valor instanceof LocalDate dia) {
            return data(dia);
        }

        return String.valueOf(valor);
    }

    static String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }

        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }

    static String data(LocalDate dia) {
        return dia == null ? "—" : dia.format(DATA);
    }

    private static String cpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return cpf;
        }

        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    private static String anoECor(Motocicleta moto) {
        if (moto.getCor() == null || moto.getCor().isBlank()) {
            return String.valueOf(moto.getAno());
        }

        return moto.getAno() + " / " + moto.getCor();
    }

    private PdfPTable tabela(int colunas) {
        var tabela = new PdfPTable(colunas);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(4);
        return tabela;
    }

    private PdfPTable tabela(float[] larguras) {
        var tabela = new PdfPTable(larguras);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(4);
        return tabela;
    }

    private Paragraph secao(String titulo) {
        var paragrafo = new Paragraph(titulo, SECAO);
        paragrafo.setSpacingBefore(10);
        return paragrafo;
    }

    private Paragraph espaco() {
        return new Paragraph(" ", TEXTO);
    }

    private void cabecalho(PdfPTable tabela, String... titulos) {
        for (String titulo : titulos) {
            var celula = new PdfPCell(new Phrase(titulo, NEGRITO));
            celula.setBackgroundColor(FUNDO);
            celula.setPadding(4);
            tabela.addCell(celula);
        }
    }

    private void adicionarPar(PdfPTable tabela, String rotulo, String valor) {
        var celulaRotulo = new PdfPCell(new Phrase(rotulo, NEGRITO));
        celulaRotulo.setBackgroundColor(FUNDO);
        celulaRotulo.setPadding(4);
        tabela.addCell(celulaRotulo);
        texto(tabela, valor == null || valor.isBlank() ? "Não informado" : valor);
    }

    private void adicionarTotal(PdfPTable tabela, String rotulo, BigDecimal valor, boolean destaque) {
        var celulaRotulo = new PdfPCell(new Phrase(rotulo, destaque ? DESTAQUE : NEGRITO));
        celulaRotulo.setPadding(4);
        tabela.addCell(celulaRotulo);
        var celulaValor = new PdfPCell(new Phrase(dinheiro(valor), destaque ? DESTAQUE : TEXTO));
        celulaValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celulaValor.setPadding(4);
        tabela.addCell(celulaValor);
    }

    private void texto(PdfPTable tabela, String valor) {
        var celula = new PdfPCell(new Phrase(valor == null ? "—" : valor, TEXTO));
        celula.setPadding(4);
        tabela.addCell(celula);
    }

    private void numero(PdfPTable tabela, String valor) {
        var celula = new PdfPCell(new Phrase(valor, TEXTO));
        celula.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celula.setPadding(4);
        tabela.addCell(celula);
    }

    private void vazio(PdfPTable tabela, int colunas, String mensagem) {
        var celula = new PdfPCell(new Phrase(mensagem, TEXTO));
        celula.setColspan(colunas);
        celula.setPadding(6);
        tabela.addCell(celula);
    }

    private PdfPCell semBorda(String texto, Font fonte) {
        var celula = new PdfPCell(new Phrase(texto, fonte));
        celula.setBorder(Rectangle.NO_BORDER);
        return celula;
    }
}
