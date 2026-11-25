package br.edu.ifsul.oficynic;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Sem transacao no teste cada requisicao le o que foi salvo no banco
@SpringBootTest
@AutoConfigureMockMvc
class RelacionamentosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();
    private final List<Long> motos = new ArrayList<>();
    private final List<Long> ordens = new ArrayList<>();
    private final List<Long> funcionarios = new ArrayList<>();

    @AfterEach
    void limparSomenteRegistrosDoTeste() {
        for (Long id : ordens) jdbc.update("DELETE FROM ordens_servico WHERE id = ?", id);
        for (Long id : motos) jdbc.update("DELETE FROM motocicletas WHERE id = ?", id);
        for (Long id : clientes) jdbc.update("DELETE FROM clientes WHERE id = ?", id);
        for (Long id : funcionarios) jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
    }

    private long cliente(String cpf) throws Exception {
        String json = mvc.perform(post("/api/clientes").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente da aula\",\"cpf\":\"" + cpf + "\",\"ordensServico\":[{\"id\":999999}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ordensServico").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        clientes.add(id);
        return id;
    }

    private long moto(long cliente, String placa) {
        long id = jdbc.queryForObject("INSERT INTO motocicletas(placa,marca,modelo,ano,cliente_id) "
                + "VALUES (?,'Marca','Modelo',2024,?) RETURNING id", Long.class, placa, cliente);
        motos.add(id);
        return id;
    }

    private long funcionario() {
        if (!funcionarios.isEmpty()) return funcionarios.get(0);
        long id = jdbc.queryForObject("INSERT INTO usuarios(nome) VALUES ('Responsável da aula') RETURNING id", Long.class);
        funcionarios.add(id);
        return id;
    }

    private String dados(long cliente, long moto) {
        return "{\"descricao\":\"Revisão da moto\",\"clienteId\":" + cliente + ",\"motocicletaId\":" + moto
                + ",\"funcionarioId\":" + funcionario() + "}";
    }

    // OS aberta pela rota da tela ja ligada ao cliente e a moto dele
    private long ordem(long cliente, long moto) throws Exception {
        String json = mvc.perform(post("/api/ordens-servico/abertura").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(dados(cliente, moto)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cliente").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        ordens.add(id);
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id = ?", Long.class, id));
        return id;
    }

    @Test @WithMockUser(roles = "ADMINISTRADOR")
    void variasOrdensAparecemNoClienteSemRecursao() throws Exception {
        long cliente = cliente("90000000175");
        long outro = cliente("90000000256");
        long moto = moto(cliente, "REL1A23");
        long primeira = ordem(cliente, moto);
        ordem(cliente, moto);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ordens_servico WHERE cliente_id = ?", Integer.class, cliente));
        mvc.perform(get("/api/clientes/" + cliente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ordensServico.length()").value(2))
                .andExpect(jsonPath("$.ordensServico[0].cliente").doesNotExist());
        mvc.perform(get("/api/clientes/" + outro)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ordensServico").isEmpty());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(get("/api/ordens-servico/consulta")).andExpect(status().isOk());
        mvc.perform(get("/api/ordens-servico/consulta/" + primeira)).andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Revisão da moto"));
        mvc.perform(put("/api/clientes/" + cliente).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente editado\",\"cpf\":\"90000000175\",\"ordensServico\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ordensServico.length()").value(2));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ordens_servico WHERE cliente_id = ?", Integer.class, cliente));
    }

    @Test @WithMockUser(roles = "ADMINISTRADOR")
    void vinculoInexistenteNaoAlteraOrdem() throws Exception {
        long cliente = cliente("90000000337");
        long moto = moto(cliente, "REL2A23");
        long ordem = ordem(cliente, moto);
        long antes = jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class);
        mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(999999999L, moto))).andExpect(status().isNotFound());
        mvc.perform(put("/api/ordens-servico/" + ordem).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(999999999L, moto))).andExpect(status().isNotFound());
        mvc.perform(put("/api/ordens-servico/999999999").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(cliente, moto))).andExpect(status().isNotFound());
        mvc.perform(get("/api/ordens-servico/consulta/999999999")).andExpect(status().isNotFound());
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class));
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id = ?", Long.class, ordem));
    }

    @Test @WithMockUser(roles = "ADMINISTRADOR")
    void postgresImpedeVinculoInvalidoEExclusaoDeClienteReferenciado() throws Exception {
        long cliente = cliente("90000000418");
        long moto = moto(cliente, "REL3A23");
        long ordem = ordem(cliente, moto);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE ordens_servico SET cliente_id = 999999999 WHERE id = ?", ordem));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE ordens_servico SET cliente_id = NULL WHERE id = ?", ordem));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE ordens_servico SET motocicleta_id = NULL WHERE id = ?", ordem));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("DELETE FROM clientes WHERE id = ?", cliente));
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id = ?", Long.class, ordem));
    }

    @Test @WithMockUser(roles = "ADMINISTRADOR")
    void validaDadosEExigeCsrf() throws Exception {
        long antes = jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class);
        mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ordens-servico/abertura").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Revisão\",\"clienteId\":1,\"motocicletaId\":1,\"funcionarioId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/ordens-servico/1")).andExpect(status().isForbidden());
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class));
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/api/ordens-servico/consulta")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ordens-servico/consulta/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/ordens-servico/1").with(csrf())).andExpect(status().isUnauthorized());
    }
}
