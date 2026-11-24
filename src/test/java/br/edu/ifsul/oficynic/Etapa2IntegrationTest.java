package br.edu.ifsul.oficynic;

import com.jayway.jsonpath.JsonPath;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMINISTRADOR")
class Etapa2IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long cadastrarCliente(String cpf, String nome) throws Exception {
        return cadastrarCliente(cpf, nome, "(53) 99999-4321");
    }

    private long cadastrarCliente(String cpf, String nome, String telefone) throws Exception {
        String resposta = mvc.perform(post("/api/clientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nome + "\",\"cpf\":\"" + cpf
                                + "\",\"telefone\":\"" + telefone + "\",\"email\":\"cliente@oficynic.com\",\"endereco\":\"Rua Teste\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private long cadastrarMotoSemProprietario(String placa) throws Exception {
        String resposta = mvc.perform(post("/api/motocicletas").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"" + placa + "\",\"marca\":\"Honda\",\"modelo\":\"CG 160\",\"ano\":2024}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").isEmpty())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private String moto(long clienteId, String placa) {
        return "{\"clienteId\":" + clienteId + ",\"placa\":\"" + placa
                + "\",\"marca\":\"Honda\",\"modelo\":\"CG 160\",\"ano\":2024}";
    }

    @Test
    void listagemMascaraDadosEDetalheMantemValoresCompletos() throws Exception {
        long id = cadastrarCliente("52998224725", "Cliente Privacidade");

        mvc.perform(get("/api/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].cpf").value("***.***.247-25"))
                .andExpect(jsonPath("$[0].telefone").value("(**) *****-4321"))
                .andExpect(jsonPath("$[0].email").value("cliente@oficynic.com"))
                .andExpect(jsonPath("$[0].endereco").doesNotExist());

        mvc.perform(get("/api/clientes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value("52998224725"))
                .andExpect(jsonPath("$.telefone").value("(53) 99999-4321"))
                .andExpect(jsonPath("$.email").value("cliente@oficynic.com"))
                .andExpect(jsonPath("$.endereco").value("Rua Teste"));

        assertEquals("52998224725", jdbc.queryForObject("SELECT cpf FROM clientes WHERE id=?", String.class, id));
        assertEquals("(53) 99999-4321", jdbc.queryForObject("SELECT telefone FROM clientes WHERE id=?", String.class, id));
    }

    @Test
    void edicaoRecebeDadosCompletosESemCorromperMascara() throws Exception {
        long id = cadastrarCliente("12345678909", "Cliente Editável");
        mvc.perform(put("/api/clientes/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cliente Editado\",\"cpf\":\"12345678909\",\"telefone\":\"11987654321\",\"email\":\"editado@oficynic.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Cliente Editado"));

        assertEquals("12345678909", jdbc.queryForObject("SELECT cpf FROM clientes WHERE id=?", String.class, id));
        assertFalse(jdbc.queryForObject("SELECT telefone FROM clientes WHERE id=?", String.class, id).contains("*"));
        mvc.perform(get("/api/clientes"))
                .andExpect(jsonPath("$[0].cpf").value("***.***.789-09"))
                .andExpect(jsonPath("$[0].telefone").value("(**) *****-4321"));
    }

    @Test
    void motoPodeNascerSemProprietarioESerVinculadaDepois() throws Exception {
        int motosAntes = jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class);
        long moto = cadastrarMotoSemProprietario("ETP2A01");
        long cliente = cadastrarCliente("11144477735", "Proprietário Posterior", "(53) 99999-4322");
        long outro = cadastrarCliente("52998224725", "Outro Proprietário");

        mvc.perform(put("/api/motocicletas/" + moto).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(moto(cliente, "ETP2A01")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(cliente));
        mvc.perform(put("/api/motocicletas/" + moto).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ETP2A01\",\"marca\":\"Honda\",\"modelo\":\"CG 160\",\"ano\":2025}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(cliente));
        mvc.perform(put("/api/motocicletas/" + moto).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(moto(outro, "ETP2A01")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/motocicletas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(moto(999999999, "ETP2A02")))
                .andExpect(status().isNotFound());

        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM motocicletas WHERE id=?", Long.class, moto));
        assertEquals(motosAntes + 1, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
    }

    @Test
    void osRejeitaMotoSemProprietarioEAceitaDepoisDoVinculo() throws Exception {
        int ordensAntes = jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Integer.class);
        long moto = cadastrarMotoSemProprietario("ETP2B01");
        long cliente = cadastrarCliente("52998224725", "Cliente da OS");
        long funcionario = jdbc.queryForObject("INSERT INTO usuarios(nome) VALUES ('Responsável Etapa 2') RETURNING id", Long.class);
        String os = "{\"descricao\":\"Revisão Etapa 2\",\"clienteId\":" + cliente
                + ",\"motocicletaId\":" + moto + ",\"funcionarioId\":" + funcionario + "}";

        mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(os))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/motocicletas/" + moto).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(moto(cliente, "ETP2B01")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(os))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").value(cliente));
        assertEquals(ordensAntes + 1, jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Integer.class));
    }

    @Test
    void postgresMantemChaveEstrangeiraMesmoComClienteOpcional() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO motocicletas(placa,marca,modelo,ano,cliente_id) VALUES ('ETP2C01','Honda','CG',2024,NULL)");
                var ponto = connection.setSavepoint();
                try {
                    assertThrows(SQLException.class, () -> statement.execute(
                            "UPDATE motocicletas SET cliente_id=999999999 WHERE placa='ETP2C01'"));
                } finally {
                    connection.rollback(ponto);
                }
            }
            return null;
        });
        assertNull(jdbc.queryForObject("SELECT cliente_id FROM motocicletas WHERE placa='ETP2C01'", Long.class));
    }

    @Test
    @WithAnonymousUser
    void dadosDeClientesContinuamProtegidosPorLogin() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes/1")).andExpect(status().isUnauthorized());
    }
}
