package br.edu.ifsul.oficynic;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Testes dos apontamentos do orientador sobre orientacao parecer tecnico precos e permissoes
// Usuario padrao administrador e funcionario() simula o perfil FUNCIONARIO
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin-teste", roles = "ADMINISTRADOR")
class ApontamentosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final List<Long> ordens = new ArrayList<>(), produtos = new ArrayList<>(), servicos = new ArrayList<>();
    long cliente, moto, funcionario;

    @BeforeEach void preparar() throws Exception {
        funcionario = id(enviar(post("/api/usuarios"), "{\"nome\":\"Responsável apontamentos\"}", 201));
        cliente = id(enviar(post("/api/clientes"), "{\"nome\":\"Cliente apontamentos\",\"cpf\":\"52998224725\"}", 201));
        moto = id(enviar(post("/api/motocicletas"), "{\"clienteId\":" + cliente + ",\"placa\":\"APT1A23\",\"marca\":\"Honda\",\"modelo\":\"CG\",\"ano\":2022}", 201));
    }

    @AfterEach void limpar() {
        for (long os : ordens) {
            jdbc.update("DELETE FROM movimentacoes_estoque WHERE ordem_servico_id=?", os);
            jdbc.update("DELETE FROM itens_produto_os WHERE ordem_servico_id=?", os);
            jdbc.update("DELETE FROM itens_servico_os WHERE ordem_servico_id=?", os);
            jdbc.update("DELETE FROM ordens_servico WHERE id=?", os);
        }
        for (long p : produtos) { jdbc.update("DELETE FROM movimentacoes_estoque WHERE produto_id=?", p); jdbc.update("DELETE FROM produtos WHERE id=?", p); }
        for (long s : servicos) jdbc.update("DELETE FROM servicos WHERE id=?", s);
        jdbc.update("DELETE FROM motocicletas WHERE id=?", moto);
        jdbc.update("DELETE FROM clientes WHERE id=?", cliente);
        jdbc.update("DELETE FROM usuarios WHERE id=?", funcionario);
    }

    private String enviar(MockHttpServletRequestBuilder requisicao, String corpo, int esperado) throws Exception {
        return mvc.perform(requisicao.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }
    private MockHttpServletRequestBuilder funcionario(MockHttpServletRequestBuilder requisicao) {
        return requisicao.with(user("funcionario-teste").roles("FUNCIONARIO"));
    }
    private long id(String json) { return ((Number) JsonPath.read(json, "$.id")).longValue(); }
    private double numero(String json, String caminho) { return ((Number) JsonPath.read(json, caminho)).doubleValue(); }

    private long abrirOs(String orientacao) throws Exception {
        long os = id(enviar(post("/api/ordens-servico/abertura"), "{\"descricao\":\"" + orientacao + "\",\"clienteId\":" + cliente
                + ",\"motocicletaId\":" + moto + ",\"funcionarioId\":" + funcionario + "}", 201));
        ordens.add(os);
        return os;
    }
    private String cabecalho(String orientacao, String parecer) { return cabecalho(orientacao, parecer, funcionario); }
    private String cabecalho(String orientacao, String parecer, long responsavel) {
        return "{\"descricao\":\"" + orientacao + "\",\"parecerTecnico\":\"" + parecer + "\",\"clienteId\":" + cliente
                + ",\"motocicletaId\":" + moto + ",\"funcionarioId\":" + responsavel + "}";
    }
    private List<Long> ids(String json) {
        List<Number> lista = JsonPath.read(json, "$[*].id");
        return lista.stream().map(Number::longValue).toList();
    }
    private long produto(String precoVenda, String precoCusto) throws Exception {
        long p = id(enviar(post("/api/produtos"), "{\"codigo\":\"APT" + UUID.randomUUID().toString().substring(0, 8)
                + "\",\"descricao\":\"Pastilha\",\"precoVenda\":" + precoVenda + ",\"precoCusto\":" + precoCusto + ",\"unidadeMedida\":\"PC\"}", 201));
        produtos.add(p);
        // Entrada atualiza o custo entao a compra usa o proprio custo cadastrado
        // Produto sem custo recebe estoque direto no banco como saldo anterior
        if ("null".equals(precoCusto)) jdbc.update("UPDATE produtos SET estoque = 10 WHERE id = ?", p);
        else enviar(post("/api/movimentacoes-estoque"), "{\"produtoId\":" + p + ",\"funcionarioId\":" + funcionario
                + ",\"tipo\":\"ENTRADA\",\"quantidade\":10,\"valorUnitario\":" + precoCusto + ",\"motivo\":\"Compra de teste\"}", 201);
        return p;
    }
    private long servico(String preco) throws Exception {
        long s = id(enviar(post("/api/servicos"), "{\"nome\":\"Revisão apontamentos\",\"preco\":" + preco + "}", 201));
        servicos.add(s);
        return s;
    }

    private void concluir(long os, long servico) throws Exception {
        enviar(post("/api/ordens-servico/" + os + "/servicos"), "{\"servicoId\":" + servico + "}", 201);
        enviar(post("/api/ordens-servico/" + os + "/aprovacao"), "", 200);
        enviar(post("/api/ordens-servico/" + os + "/finalizacao"), "", 200);
    }
    private byte[] baixar(MockHttpServletRequestBuilder requisicao, String tipo) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(tipo))
                .andReturn().getResponse().getContentAsByteArray();
    }
    private String textoPdf(byte[] pdf) throws Exception {
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        var leitor = new com.lowagie.text.pdf.PdfReader(pdf);
        try {
            var extrator = new com.lowagie.text.pdf.parser.PdfTextExtractor(leitor);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) texto.append(extrator.getTextFromPage(pagina)).append('\n');
            return texto.toString();
        } finally { leitor.close(); }
    }
    // Mesma data do servidor na abertura no fuso America/Sao_Paulo
    private String hoje() throws Exception {
        return mvc.perform(get("/api/ordens-servico/data-atual")).andReturn().getResponse().getContentAsString().replace("\"", "");
    }

    // REQ-06/07/08
    @Test void funcionarioNaoAlteraOrientacaoMasRegistraParecer() throws Exception {
        long os = abrirOs("Barulho no motor");
        enviar(funcionario(put("/api/ordens-servico/" + os)), cabecalho("Texto trocado", ""), 403);
        assertEquals("Barulho no motor", jdbc.queryForObject("SELECT descricao FROM ordens_servico WHERE id=?", String.class, os));

        String json = enviar(funcionario(put("/api/ordens-servico/" + os)), cabecalho("Barulho no motor", "Corrente frouxa"), 200);
        assertEquals("Corrente frouxa", JsonPath.read(json, "$.parecerTecnico"));
        assertEquals("Barulho no motor", JsonPath.read(json, "$.descricao"));

        json = enviar(put("/api/ordens-servico/" + os), cabecalho("Barulho no motor ao acelerar", "Corrente frouxa"), 200);
        assertEquals("Barulho no motor ao acelerar", JsonPath.read(json, "$.descricao"));
        assertEquals("Corrente frouxa", jdbc.queryForObject("SELECT parecer_tecnico FROM ordens_servico WHERE id=?", String.class, os));
    }

    @Test void parecerNaoMudaDepoisDeConcluir() throws Exception {
        long os = abrirOs("Revisão geral"), s = servico("50");
        enviar(post("/api/ordens-servico/" + os + "/servicos"), "{\"servicoId\":" + s + "}", 201);
        enviar(post("/api/ordens-servico/" + os + "/aprovacao"), "", 200);
        enviar(post("/api/ordens-servico/" + os + "/finalizacao"), "", 200);
        enviar(funcionario(put("/api/ordens-servico/" + os)), cabecalho("Revisão geral", "Depois da conclusão"), 409);
        assertNull(jdbc.queryForObject("SELECT parecer_tecnico FROM ordens_servico WHERE id=?", String.class, os));
    }

    // REQ-09
    @Test void alterarPrecoNoCadastroNaoMudaOsAntiga() throws Exception {
        long os = abrirOs("Troca de pastilha"), p = produto("100", "60"), s = servico("80");
        enviar(post("/api/ordens-servico/" + os + "/produtos"), "{\"produtoId\":" + p + ",\"quantidade\":1}", 201);
        enviar(post("/api/ordens-servico/" + os + "/servicos"), "{\"servicoId\":" + s + "}", 201);

        enviar(put("/api/produtos/" + p), "{\"codigo\":\"APT-ALT-" + p + "\",\"descricao\":\"Pastilha\",\"precoVenda\":150,\"unidadeMedida\":\"PC\"}", 200);
        enviar(put("/api/servicos/" + s), "{\"nome\":\"Revisão apontamentos\",\"preco\":120}", 200);

        String json = mvc.perform(get("/api/ordens-servico/consulta/" + os)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(100.0, numero(json, "$.produtos[0].valorUnitario"));
        assertEquals(80.0, numero(json, "$.servicos[0].valorUnitario"));
        assertEquals(180.0, numero(json, "$.valorTotal"));
        assertEquals(0, new BigDecimal("60.00").compareTo(jdbc.queryForObject(
                "SELECT custo_unitario FROM itens_produto_os WHERE ordem_servico_id=?", BigDecimal.class, os)));
    }

    // REQ-11
    @Test void funcionarioSoPodeAumentarOPrecoCadastrado() throws Exception {
        long os = abrirOs("Freio fraco"), p = produto("120", "70"), s = servico("90");
        String json = enviar(funcionario(post("/api/ordens-servico/" + os + "/produtos")), "{\"produtoId\":" + p + ",\"quantidade\":1,\"valorUnitario\":140}", 201);
        assertEquals(140.0, numero(json, "$.produtos[0].valorUnitario"));
        long item = ((Number) JsonPath.read(json, "$.produtos[0].id")).longValue();

        enviar(funcionario(post("/api/ordens-servico/" + os + "/produtos")), "{\"produtoId\":" + p + ",\"quantidade\":1,\"valorUnitario\":100}", 403);
        enviar(funcionario(put("/api/ordens-servico/" + os + "/produtos/" + item)), "{\"quantidade\":1,\"valorUnitario\":110}", 403);
        enviar(funcionario(post("/api/ordens-servico/" + os + "/servicos")), "{\"servicoId\":" + s + ",\"valorUnitario\":80}", 403);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM itens_produto_os WHERE ordem_servico_id=?", Integer.class, os));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM itens_servico_os WHERE ordem_servico_id=?", Integer.class, os));

        // Sem valor informado usa o preco de venda cadastrado
        json = enviar(funcionario(post("/api/ordens-servico/" + os + "/servicos")), "{\"servicoId\":" + s + "}", 201);
        assertEquals(90.0, numero(json, "$.servicos[0].valorUnitario"));
        // Administrador pode dar valor menor
        json = enviar(post("/api/ordens-servico/" + os + "/produtos"), "{\"produtoId\":" + p + ",\"quantidade\":1,\"valorUnitario\":100}", 201);
        assertEquals(100.0, numero(json, "$.produtos[1].valorUnitario"));
    }

    // REQ-12/13
    @Test void inativoSaiDaSelecaoMasPermaneceNasOsAntigas() throws Exception {
        long os = abrirOs("Revisão de rotina");
        long afastado = id(enviar(post("/api/usuarios"), "{\"nome\":\"Mecânico afastado\",\"ativo\":false}", 201));
        try {
            String ativos = mvc.perform(funcionario(get("/api/usuarios/ativos"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(ids(ativos).contains(funcionario));
            assertFalse(ids(ativos).contains(afastado));
            enviar(post("/api/ordens-servico/abertura"), "{\"descricao\":\"Nova\",\"clienteId\":" + cliente + ",\"motocicletaId\":" + moto
                    + ",\"funcionarioId\":" + afastado + "}", 409);

            // Inativar o responsavel nao muda o vinculo da OS antiga
            enviar(put("/api/usuarios/" + funcionario), "{\"nome\":\"Responsável apontamentos\",\"ativo\":false}", 200);
            mvc.perform(get("/api/ordens-servico/consulta/" + os)).andExpect(jsonPath("$.funcionarioNome").value("Responsável apontamentos"));
            enviar(funcionario(put("/api/ordens-servico/" + os)), cabecalho("Revisão de rotina", "Mantém o responsável"), 200);
            enviar(put("/api/ordens-servico/" + os), cabecalho("Revisão de rotina", "", afastado), 409);
            assertEquals(funcionario, jdbc.queryForObject("SELECT funcionario_id FROM ordens_servico WHERE id=?", Long.class, os));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE id=?", Integer.class, funcionario));
        } finally {
            jdbc.update("DELETE FROM usuarios WHERE id=?", afastado);
        }
    }

    // REQ-14
    @Test void somenteAdministradorGerenciaFuncionarios() throws Exception {
        mvc.perform(funcionario(get("/api/usuarios"))).andExpect(status().isForbidden());
        mvc.perform(funcionario(get("/api/usuarios/" + funcionario))).andExpect(status().isForbidden());
        mvc.perform(funcionario(get("/funcionarios"))).andExpect(status().isForbidden());
        enviar(funcionario(post("/api/usuarios")), "{\"nome\":\"Intruso\"}", 403);
        enviar(funcionario(put("/api/usuarios/" + funcionario)), "{\"nome\":\"Alterado\",\"ativo\":false}", 403);
        assertTrue(jdbc.queryForObject("SELECT ativo FROM usuarios WHERE id=?", Boolean.class, funcionario));
        assertEquals("Responsável apontamentos", jdbc.queryForObject("SELECT nome FROM usuarios WHERE id=?", String.class, funcionario));

        String json = enviar(put("/api/usuarios/" + funcionario),
                "{\"nome\":\"Responsável apontamentos\",\"login\":\"Resp.Apontamentos\",\"senha\":\"segredo1\",\"ativo\":false}", 200);
        assertEquals(false, JsonPath.read(json, "$.ativo"));
        assertEquals("resp.apontamentos", JsonPath.read(json, "$.login"));
        assertEquals("FUNCIONARIO", JsonPath.read(json, "$.perfil"));
        assertFalse(json.contains("senha"), "A senha nunca sai na resposta.");
        assertTrue(jdbc.queryForObject("SELECT senha FROM usuarios WHERE id=?", String.class, funcionario).startsWith("{bcrypt}"));
        enviar(post("/api/usuarios"), "{\"nome\":\"Outro\",\"login\":\"resp.apontamentos\",\"senha\":\"segredo2\"}", 409);
        enviar(post("/api/usuarios"), "{\"nome\":\"Sem senha\",\"login\":\"sem.senha\"}", 400);
    }

    // REQ-17
    @Test void painelMostraTotaisReaisDeClientesEVeiculos() throws Exception {
        int clientes = jdbc.queryForObject("SELECT count(*) FROM clientes", Integer.class);
        int motos = jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class);
        mvc.perform(funcionario(get("/api/painel/resumo"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").value(clientes)).andExpect(jsonPath("$.motocicletas").value(motos));
    }

    // REQ-18
    @Test void pdfDeAprovacaoTrazOsDadosEDeixaAOsSemAprovar() throws Exception {
        long os = abrirOs("Freio fazendo barulho"), p = produto("120", "70"), s = servico("80");
        enviar(post("/api/ordens-servico/" + os + "/produtos"), "{\"produtoId\":" + p + ",\"quantidade\":1,\"valorUnitario\":130}", 201);
        enviar(post("/api/ordens-servico/" + os + "/servicos"), "{\"servicoId\":" + s + "}", 201);

        String texto = textoPdf(baixar(funcionario(get("/api/ordens-servico/" + os + "/pdf")), "application/pdf"));
        for (String esperado : List.of("Ordem de Serviço nº " + os, "AGUARDANDO APROVAÇÃO DO CLIENTE", "Cliente apontamentos",
                "APT1A23", "Honda CG", "Freio fazendo barulho", "Revisão apontamentos", "Pastilha", "130,00", "80,00", "210,00", "Assinatura do cliente"))
            assertTrue(texto.contains(esperado), "PDF sem: " + esperado);
        assertEquals("ORCAMENTO", jdbc.queryForObject("SELECT situacao FROM ordens_servico WHERE id=?", String.class, os));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ordens_servico WHERE id=? AND data_aprovacao IS NOT NULL", Integer.class, os));

        enviar(post("/api/ordens-servico/" + os + "/aprovacao"), "", 200);
        texto = textoPdf(baixar(get("/api/ordens-servico/" + os + "/pdf"), "application/pdf"));
        assertTrue(texto.contains("APROVADA PELO CLIENTE EM"));
    }

    // REQ-19/20/22
    @Test void relatorioDeOsCombinaFiltros() throws Exception {
        long aberta = abrirOs("Primeira"), concluida = abrirOs("Segunda"), s = servico("50");
        concluir(concluida, s);
        String base = "/api/relatorios/ordens-servico?clienteId=" + cliente;
        assertEquals(List.of(aberta, concluida), numeros(mvc.perform(get(base)).andReturn().getResponse().getContentAsString()));
        String json = mvc.perform(get(base + "&situacao=CONCLUIDA&inicio=" + hoje() + "&fim=" + hoje())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(concluida), numeros(json));
        assertEquals(50.0, numero(json, "$[0].valorTotal"));
        assertEquals("Cliente apontamentos", JsonPath.read(json, "$[0].cliente"));
        assertEquals("APT1A23 · Honda CG", JsonPath.read(json, "$[0].veiculo"));
        assertEquals(List.of(aberta), numeros(mvc.perform(get(base + "&numero=" + aberta)).andReturn().getResponse().getContentAsString()));
        assertEquals(List.of(), numeros(mvc.perform(get(base + "&inicio=" + LocalDate.parse(hoje()).plusDays(1))).andReturn().getResponse().getContentAsString()));
        mvc.perform(get(base + "&inicio=2026-10-10&fim=2026-10-01")).andExpect(status().isBadRequest());
    }
    private List<Long> numeros(String json) {
        List<Number> lista = JsonPath.read(json, "$[*].numero");
        return lista.stream().map(Number::longValue).toList();
    }

    // REQ-21
    @Test void exportacoesRespeitamOsMesmosFiltros() throws Exception {
        long aberta = abrirOs("Primeira"), concluida = abrirOs("Segunda"), s = servico("50");
        concluir(concluida, s);
        String filtro = "?clienteId=" + cliente + "&situacao=CONCLUIDA";
        byte[] planilha = baixar(get("/api/relatorios/ordens-servico/excel" + filtro), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertEquals("PK", new String(planilha, 0, 2, StandardCharsets.US_ASCII));
        try (var arquivo = new XSSFWorkbook(new ByteArrayInputStream(planilha))) {
            Sheet aba = arquivo.getSheetAt(0);
            assertEquals("Nº OS", aba.getRow(3).getCell(0).getStringCellValue());
            assertEquals(4, aba.getLastRowNum(), "Somente a OS concluída do cliente.");
            assertEquals(concluida, (long) aba.getRow(4).getCell(0).getNumericCellValue());
            assertEquals(50.0, aba.getRow(4).getCell(7).getNumericCellValue());
        }
        baixar(get("/api/relatorios/financeiro/excel" + filtro), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mvc.perform(get("/api/relatorios/ordens-servico/pdf" + filtro)).andExpect(status().isNotFound());
        mvc.perform(get("/api/relatorios/financeiro/pdf" + filtro)).andExpect(status().isNotFound());
        assertTrue(aberta < concluida);
    }

    // REQ-23
    @Test void financeiroUsaSomenteValoresRegistrados() throws Exception {
        long orcamento = abrirOs("Orçamento"), concluida = abrirOs("Concluída"), cancelada = abrirOs("Cancelada");
        long s80 = servico("80"), s50 = servico("50"), s30 = servico("30"), comCusto = produto("120", "70");
        enviar(post("/api/ordens-servico/" + orcamento + "/servicos"), "{\"servicoId\":" + s80 + "}", 201);
        enviar(post("/api/ordens-servico/" + concluida + "/produtos"), "{\"produtoId\":" + comCusto + ",\"quantidade\":1}", 201);
        concluir(concluida, s50);
        enviar(post("/api/ordens-servico/" + cancelada + "/servicos"), "{\"servicoId\":" + s30 + "}", 201);
        enviar(post("/api/ordens-servico/" + cancelada + "/cancelamento"), "", 200);

        String json = mvc.perform(get("/api/relatorios/financeiro?clienteId=" + cliente)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(3, (int) JsonPath.read(json, "$.resumo.quantidadeOs"));
        assertEquals(80.0, numero(json, "$.resumo.valorAguardandoAprovacao"));
        assertEquals(170.0, numero(json, "$.resumo.valorAprovado"));
        assertEquals(170.0, numero(json, "$.resumo.valorConcluido"));
        assertEquals(30.0, numero(json, "$.resumo.valorCancelado"));
        assertEquals(130.0, numero(json, "$.resumo.totalServicos"));
        assertEquals(120.0, numero(json, "$.resumo.totalProdutos"));
        assertEquals(70.0, numero(json, "$.resumo.custoProdutosConcluidos"));
        assertEquals(50.0, numero(json, "$.resumo.margemProdutosConcluidos"));
        assertTrue(((String) JsonPath.read(json, "$.resumo.observacao")).contains("não registra pagamentos"));

        // Produto sem custo nao gera custo nem margem
        long semCusto = produto("40", "null"), outra = abrirOs("Sem custo");
        enviar(post("/api/ordens-servico/" + outra + "/produtos"), "{\"produtoId\":" + semCusto + ",\"quantidade\":1}", 201);
        concluir(outra, s50);
        json = mvc.perform(get("/api/relatorios/financeiro?clienteId=" + cliente)).andReturn().getResponse().getContentAsString();
        assertNull(JsonPath.read(json, "$.resumo.custoProdutosConcluidos"));
        assertNull(JsonPath.read(json, "$.resumo.margemProdutosConcluidos"));
        assertEquals(1, (int) JsonPath.read(json, "$.resumo.itensConcluidosSemCusto"));
    }

    // REQ-24
    @Test void relatoriosSaoExclusivosDoAdministrador() throws Exception {
        for (String url : List.of("/relatorios", "/api/relatorios/ordens-servico",
                "/api/relatorios/ordens-servico/excel", "/api/relatorios/financeiro", "/api/relatorios/financeiro/excel")) {
            mvc.perform(funcionario(get(url))).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/relatorios/financeiro").with(anonymous())).andExpect(status().isUnauthorized());
    }

    // REQ-10
    @Test void precoDeCustoECatalogoSaoDoAdministrador() throws Exception {
        long p = produto("120", "70");
        mvc.perform(get("/api/produtos/" + p)).andExpect(status().isOk()).andExpect(jsonPath("$.precoCusto").value(70.0));
        mvc.perform(funcionario(get("/api/produtos/" + p))).andExpect(status().isOk())
                .andExpect(jsonPath("$.precoCusto").value(nullValue())).andExpect(jsonPath("$.precoVenda").value(120.0));
        mvc.perform(funcionario(get("/api/produtos"))).andExpect(status().isOk()).andExpect(jsonPath("$[*].precoCusto", everyItem(nullValue())));
        mvc.perform(funcionario(get("/api/produtos/" + p + "/estoque"))).andExpect(status().isOk()).andExpect(jsonPath("$.produto.precoCusto").value(nullValue()));

        enviar(funcionario(post("/api/produtos")), "{\"codigo\":\"NEGADO\",\"descricao\":\"X\",\"precoVenda\":1,\"unidadeMedida\":\"PC\"}", 403);
        enviar(funcionario(put("/api/produtos/" + p)), "{\"codigo\":\"NEGADO\",\"descricao\":\"X\",\"precoVenda\":1,\"unidadeMedida\":\"PC\"}", 403);
        enviar(funcionario(post("/api/servicos")), "{\"nome\":\"Negado\",\"preco\":1}", 403);
        assertEquals(0, new BigDecimal("120.00").compareTo(jdbc.queryForObject("SELECT preco_venda FROM produtos WHERE id=?", BigDecimal.class, p)));
    }
}
