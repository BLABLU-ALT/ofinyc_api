package br.com.oficynic.ofinyc_api;

import com.jayway.jsonpath.JsonPath;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
@WithMockUser
class MotocicletaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long cliente() {
        var ids = jdbc.queryForList("SELECT id FROM clientes WHERE cpf='74000000000'", Long.class);
        if (!ids.isEmpty()) return ids.get(0);
        return jdbc.queryForObject("INSERT INTO clientes(nome,cpf) VALUES ('Proprietário teste','74000000000') RETURNING id", Long.class);
    }

    private String dados(String identificacao) {
        return "{\"clienteId\":" + cliente() + "," + identificacao + "\"marca\":\"Marca livre\",\"modelo\":\"Modelo livre\",\"ano\":2024}";
    }

    private long criar(String identificacao) throws Exception {
        String json = mvc.perform(post("/api/motocicletas").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(dados(identificacao)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    static Stream<Arguments> identificacoes() {
        return Stream.of(
                Arguments.of("\"placa\":\" abc1d23 \",", "ABC1D23", null),
                Arguments.of("\"chassi\":\" abcdef12345678901 \",", null, "ABCDEF12345678901"),
                Arguments.of("\"placa\":\"ABC1D23\",\"chassi\":\"ABCDEF12345678901\",", "ABC1D23", "ABCDEF12345678901"),
                Arguments.of("\"placa\":\"ABC1D23\",\"chassi\":null,", "ABC1D23", null),
                Arguments.of("\"placa\":null,\"chassi\":\"ABCDEF12345678901\",", null, "ABCDEF12345678901"),
                Arguments.of("\"placa\":\"ABC1D23\",\"chassi\":\"   \",", "ABC1D23", null),
                Arguments.of("\"placa\":\"\",\"chassi\":\"ABCDEF12345678901\",", null, "ABCDEF12345678901"));
    }

    @ParameterizedTest @MethodSource("identificacoes")
    void cadastraConsultaEEditaCadaIdentificacao(String entrada, String placa, String chassi) throws Exception {
        long id = criar(entrada);
        mvc.perform(get("/api/motocicletas/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.placa").value(placa)).andExpect(jsonPath("$.chassi").value(chassi));
        mvc.perform(put("/api/motocicletas/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(entrada).replace("Modelo livre", "Modelo editado")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        assertEquals(placa, jdbc.queryForObject("SELECT placa FROM motocicletas WHERE id=?", String.class, id));
        assertEquals(chassi, jdbc.queryForObject("SELECT chassi FROM motocicletas WHERE id=?", String.class, id));
        assertEquals("Modelo editado", jdbc.queryForObject("SELECT modelo FROM motocicletas WHERE id=?", String.class, id));
    }

    static Stream<String> ausentes() {
        return Stream.of("", "\"placa\":null,\"chassi\":null,", "\"placa\":\"\",\"chassi\":\"   \",",
                "\"placa\":\" \",", "\"chassi\":\"\\t\",", "\"placa\":\"\\n\",\"chassi\":\"\\t\",");
    }

    @ParameterizedTest @MethodSource("ausentes")
    void ausenciaRejeitadaSemCriarNemAlterar(String entrada) throws Exception {
        mvc.perform(post("/api/motocicletas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(entrada))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Informe a placa ou o chassi."));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
        long id = criar("\"placa\":\"ABC1D23\",");
        var antes = jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id);
        mvc.perform(put("/api/motocicletas/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(entrada))).andExpect(status().isBadRequest());
        assertEquals(antes, jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id));
    }

    static Stream<Arguments> invalidas() {
        return Stream.of(
                Arguments.of("\"placa\":\"ABC-123\",", "placa"),
                Arguments.of("\"chassi\":\"123\",", "chassi"),
                Arguments.of("\"placa\":\"12345678\",\"chassi\":\"ABCDEF12345678901\",", "placa"),
                Arguments.of("\"placa\":\"ABC1D23\",\"chassi\":\"1234567890123456!\",", "chassi"));
    }

    @ParameterizedTest @MethodSource("invalidas")
    void formatoInvalidoRejeitadoMesmoComOutroIdentificador(String entrada, String campo) throws Exception {
        mvc.perform(post("/api/motocicletas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(entrada))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos." + campo).exists());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
        long id = criar("\"placa\":\"ABC1D23\",");
        var antes = jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id);
        mvc.perform(put("/api/motocicletas/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(entrada))).andExpect(status().isBadRequest());
        assertEquals(antes, jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id));
    }

    static Stream<Arguments> duplicadas() {
        return Stream.of(Arguments.of("placa", "ABC1D23", "XYZ9A87"),
                Arguments.of("chassi", "ABCDEF12345678901", "ZYXWVU12345678901"));
    }

    @ParameterizedTest @MethodSource("duplicadas")
    void duplicidadeNormalizadaRejeitadaNoCadastroENaEdicao(String campo, String primeiro, String segundo) throws Exception {
        criar("\"" + campo + "\":\"" + primeiro + "\",");
        String duplicado = "\"" + campo + "\":\" " + primeiro.toLowerCase(java.util.Locale.ROOT) + " \",";
        mvc.perform(post("/api/motocicletas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(duplicado))).andExpect(status().isConflict());
        long id = criar("\"" + campo + "\":\"" + segundo + "\",");
        var antes = jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id);
        mvc.perform(put("/api/motocicletas/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(duplicado))).andExpect(status().isConflict());
        assertEquals(antes, jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
    }

    @Test
    void trocaIdentificacaoMantendoIdEVinculoDaOs() throws Exception {
        long id = criar("\"placa\":\"ABC1D23\",");
        long os = jdbc.queryForObject("INSERT INTO ordens_servico(descricao,data_abertura,motocicleta_id) "
                + "VALUES ('OS teste',CURRENT_DATE,?) RETURNING id", Long.class, id);
        for (String entrada : new String[]{"\"chassi\":\"ABCDEF12345678901\",",
                "\"placa\":\"ABC1D23\",\"chassi\":\"ABCDEF12345678901\",", "\"placa\":\"ABC1D23\","}) {
            mvc.perform(put("/api/motocicletas/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(dados(entrada))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
            assertEquals(id, jdbc.queryForObject("SELECT motocicleta_id FROM ordens_servico WHERE id=?", Long.class, os));
        }
        assertNull(jdbc.queryForObject("SELECT chassi FROM motocicletas WHERE id=?", String.class, id));
    }

    @Test
    void abreEConsultaOsDeMotoSemPlaca() throws Exception {
        long moto = criar("\"chassi\":\"ABCDEF12345678901\",");
        long cliente = cliente();
        long funcionario = jdbc.queryForObject("INSERT INTO funcionarios(nome) VALUES ('Responsável teste') RETURNING id", Long.class);
        String json = mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Revisão\",\"clienteId\":" + cliente + ",\"motocicletaId\":" + moto + ",\"funcionarioId\":" + funcionario + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.placa").isEmpty())
                .andExpect(jsonPath("$.chassi").value("ABCDEF12345678901"))
                .andReturn().getResponse().getContentAsString();
        long os = ((Number) JsonPath.read(json, "$.id")).longValue();
        mvc.perform(get("/api/ordens-servico/consulta/" + os)).andExpect(status().isOk())
                .andExpect(jsonPath("$.chassi").value("ABCDEF12345678901"));
        mvc.perform(get("/api/ordens-servico/consulta")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chassi").value("ABCDEF12345678901"));
    }

    @Test
    void postgresPermiteAusenciaParcialEProtegeIdentificacao() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO motocicletas(placa,chassi,marca,modelo,ano) VALUES "
                        + "('ABC1D23',NULL,'M','M',2024),('XYZ9A87',NULL,'M','M',2024),"
                        + "(NULL,'ABCDEF12345678901','M','M',2024),(NULL,'ZYXWVU12345678901','M','M',2024),"
                        + "('BTH1A23','BOTHAB12345678901','M','M',2024)");
                for (String sql : new String[]{
                        "INSERT INTO motocicletas(marca,modelo,ano) VALUES ('M','M',2024)",
                        "INSERT INTO motocicletas(placa,marca,modelo,ano) VALUES ('','M','M',2024)",
                        "INSERT INTO motocicletas(chassi,marca,modelo,ano) VALUES ('   ','M','M',2024)",
                        "INSERT INTO motocicletas(placa,chassi,marca,modelo,ano) VALUES ('NEW1A23','','M','M',2024)",
                        "INSERT INTO motocicletas(placa,marca,modelo,ano) VALUES ('ABC1D23','M','M',2024)",
                        "INSERT INTO motocicletas(chassi,marca,modelo,ano) VALUES ('ABCDEF12345678901','M','M',2024)",
                        "UPDATE motocicletas SET placa='ABC1D23' WHERE placa='XYZ9A87'",
                        "UPDATE motocicletas SET chassi='ABCDEF12345678901' WHERE chassi='ZYXWVU12345678901'",
                        "UPDATE motocicletas SET placa=NULL WHERE placa='ABC1D23'"}) {
                    var ponto = connection.setSavepoint();
                    try { assertThrows(SQLException.class, () -> statement.execute(sql)); }
                    finally { connection.rollback(ponto); }
                }
            }
            return null;
        });
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
    }

    @Test @WithAnonymousUser
    void exigeLoginParaLerCriarEEditar() throws Exception {
        mvc.perform(get("/api/motocicletas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/motocicletas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("\"placa\":\"ABC1D23\","))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/motocicletas/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("\"chassi\":\"ABCDEF12345678901\","))).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
    }

    @Test
    void exigeCsrfParaCriarEEditar() throws Exception {
        long id = criar("\"placa\":\"ABC1D23\",");
        var antes = jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id);
        mvc.perform(post("/api/motocicletas").contentType(MediaType.APPLICATION_JSON)
                .content(dados("\"chassi\":\"ABCDEF12345678901\","))).andExpect(status().isForbidden());
        mvc.perform(put("/api/motocicletas/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(dados("\"chassi\":\"ABCDEF12345678901\","))).andExpect(status().isForbidden());
        assertEquals(antes, jdbc.queryForMap("SELECT * FROM motocicletas WHERE id=?", id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM motocicletas", Integer.class));
    }
}
