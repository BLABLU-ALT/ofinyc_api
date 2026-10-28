package br.com.oficynic.ofinyc_api;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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

// Apontamentos do orientador: orientação, parecer técnico, preços e permissões por perfil.
// O usuário padrão é administrador; as chamadas com funcionario() simulam o perfil FUNCIONARIO.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin-teste", roles = "ADMINISTRADOR")
class ApontamentosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    final List<Long> ordens = new ArrayList<>(), produtos = new ArrayList<>(), servicos = new ArrayList<>();
    long cliente, moto, funcionario;

    @BeforeEach void preparar() throws Exception {
        funcionario = id(enviar(post("/api/funcionarios"), "{\"nome\":\"Responsável apontamentos\"}", 201));
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
        enviar(post("/api/estoque/movimentos"), "{\"produtoId\":" + p + ",\"funcionarioId\":" + funcionario
                + ",\"tipo\":\"ENTRADA\",\"quantidade\":10,\"motivo\":\"Compra de teste\"}", 201);
        return p;
    }
    private long servico(String preco) throws Exception {
        long s = id(enviar(post("/api/servicos"), "{\"nome\":\"Revisão apontamentos\",\"preco\":" + preco + "}", 201));
        servicos.add(s);
        return s;
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

        // Sem valor informado, o preço sugerido é o de venda cadastrado.
        json = enviar(funcionario(post("/api/ordens-servico/" + os + "/servicos")), "{\"servicoId\":" + s + "}", 201);
        assertEquals(90.0, numero(json, "$.servicos[0].valorUnitario"));
        // O administrador pode conceder um valor menor.
        json = enviar(post("/api/ordens-servico/" + os + "/produtos"), "{\"produtoId\":" + p + ",\"quantidade\":1,\"valorUnitario\":100}", 201);
        assertEquals(100.0, numero(json, "$.produtos[1].valorUnitario"));
    }

    // REQ-12/13
    @Test void inativoSaiDaSelecaoMasPermaneceNasOsAntigas() throws Exception {
        long os = abrirOs("Revisão de rotina");
        long afastado = id(enviar(post("/api/funcionarios"), "{\"nome\":\"Mecânico afastado\",\"ativo\":false}", 201));
        try {
            String ativos = mvc.perform(funcionario(get("/api/funcionarios/ativos"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertTrue(ids(ativos).contains(funcionario));
            assertFalse(ids(ativos).contains(afastado));
            enviar(post("/api/ordens-servico/abertura"), "{\"descricao\":\"Nova\",\"clienteId\":" + cliente + ",\"motocicletaId\":" + moto
                    + ",\"funcionarioId\":" + afastado + "}", 409);

            // Inativar o responsável não apaga nem troca o vínculo da OS antiga.
            enviar(put("/api/funcionarios/" + funcionario), "{\"nome\":\"Responsável apontamentos\",\"ativo\":false}", 200);
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
        mvc.perform(funcionario(get("/api/funcionarios"))).andExpect(status().isForbidden());
        mvc.perform(funcionario(get("/api/funcionarios/" + funcionario))).andExpect(status().isForbidden());
        mvc.perform(funcionario(get("/funcionarios"))).andExpect(status().isForbidden());
        enviar(funcionario(post("/api/funcionarios")), "{\"nome\":\"Intruso\"}", 403);
        enviar(funcionario(put("/api/funcionarios/" + funcionario)), "{\"nome\":\"Alterado\",\"ativo\":false}", 403);
        assertTrue(jdbc.queryForObject("SELECT ativo FROM usuarios WHERE id=?", Boolean.class, funcionario));
        assertEquals("Responsável apontamentos", jdbc.queryForObject("SELECT nome FROM usuarios WHERE id=?", String.class, funcionario));

        String json = enviar(put("/api/funcionarios/" + funcionario),
                "{\"nome\":\"Responsável apontamentos\",\"login\":\"Resp.Apontamentos\",\"senha\":\"segredo1\",\"ativo\":false}", 200);
        assertEquals(false, JsonPath.read(json, "$.ativo"));
        assertEquals("resp.apontamentos", JsonPath.read(json, "$.login"));
        assertEquals("FUNCIONARIO", JsonPath.read(json, "$.perfil"));
        assertFalse(json.contains("senha"), "A senha nunca sai na resposta.");
        assertTrue(jdbc.queryForObject("SELECT senha FROM usuarios WHERE id=?", String.class, funcionario).startsWith("{bcrypt}"));
        enviar(post("/api/funcionarios"), "{\"nome\":\"Outro\",\"login\":\"resp.apontamentos\",\"senha\":\"segredo2\"}", 409);
        enviar(post("/api/funcionarios"), "{\"nome\":\"Sem senha\",\"login\":\"sem.senha\"}", 400);
    }

    // REQ-17
    @Test void painelMostraTotaisReaisDeClientesEVeiculos() throws Exception {
        int clientes = jdbc.queryForObject("SELECT count(*) FROM clientes", Integer.class);
        int motos = jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class);
        mvc.perform(funcionario(get("/api/painel/resumo"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientes").value(clientes)).andExpect(jsonPath("$.motocicletas").value(motos));
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
