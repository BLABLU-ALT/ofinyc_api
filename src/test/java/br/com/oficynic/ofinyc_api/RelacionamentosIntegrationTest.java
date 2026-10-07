package br.com.oficynic.ofinyc_api;

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

// Sem transação no teste: cada requisição lê o que foi realmente salvo no banco.
@SpringBootTest
@AutoConfigureMockMvc
class RelacionamentosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> clientes = new ArrayList<>();
    private final List<Long> ordens = new ArrayList<>();

    @AfterEach
    void limparSomenteRegistrosDoTeste() {
        for (Long id : ordens) jdbc.update("DELETE FROM ordens_servico WHERE id = ?", id);
        for (Long id : clientes) jdbc.update("DELETE FROM clientes WHERE id = ?", id);
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

    private long ordem() throws Exception {
        String json = mvc.perform(post("/api/ordens-servico").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999999,\"descricao\":\"Revisão da moto\",\"dataAbertura\":\"2026-09-25\",\"cliente\":{\"id\":999999}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cliente").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        ordens.add(id);
        assertNotEquals(999999L, id);
        assertNull(jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id = ?", Long.class, id));
        return id;
    }

    private void vincular(long ordemId, long clienteId) throws Exception {
        mvc.perform(put("/api/ordens-servico/" + ordemId + "/cliente/" + clienteId).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ordemId))
                .andExpect(jsonPath("$.cliente").doesNotExist());
    }

    @Test @WithMockUser
    void variasOrdensAparecemNoClienteSemRecursao() throws Exception {
        long cliente = cliente("90000000175");
        long outro = cliente("90000000256");
        long primeira = ordem();
        long segunda = ordem();
        vincular(primeira, cliente);
        vincular(segunda, cliente);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ordens_servico WHERE cliente_id = ?", Integer.class, cliente));
        mvc.perform(get("/api/clientes/" + cliente)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ordensServico.length()").value(2))
                .andExpect(jsonPath("$.ordensServico[0].cliente").doesNotExist());
        mvc.perform(get("/api/clientes/" + outro)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ordensServico").isEmpty());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(get("/api/ordens-servico")).andExpect(status().isOk());
        mvc.perform(get("/api/ordens-servico/" + primeira)).andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Revisão da moto"));
        mvc.perform(put("/api/clientes/" + cliente).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cliente editado\",\"cpf\":\"90000000175\",\"ordensServico\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ordensServico.length()").value(2));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ordens_servico WHERE cliente_id = ?", Integer.class, cliente));
    }

    @Test @WithMockUser
    void vinculoInexistenteNaoAlteraOrdem() throws Exception {
        long cliente = cliente("90000000337");
        long ordem = ordem();
        vincular(ordem, cliente);
        mvc.perform(put("/api/ordens-servico/" + ordem + "/cliente/999999999").with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/ordens-servico/999999999/cliente/" + cliente).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ordens-servico/999999999")).andExpect(status().isNotFound());
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id = ?", Long.class, ordem));
    }

    @Test @WithMockUser
    void postgresImpedeVinculoInvalidoEExclusaoDeClienteReferenciado() throws Exception {
        long cliente = cliente("90000000418");
        long ordem = ordem();
        vincular(ordem, cliente);
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("UPDATE ordens_servico SET cliente_id = 999999999 WHERE id = ?", ordem));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("DELETE FROM clientes WHERE id = ?", cliente));
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id = ?", Long.class, ordem));
    }

    @Test @WithMockUser
    void validaDadosEExigeCsrf() throws Exception {
        long antes = jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class);
        mvc.perform(post("/api/ordens-servico").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ordens-servico").contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Revisão\",\"dataAbertura\":\"2026-09-25\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/ordens-servico/1/cliente/1")).andExpect(status().isForbidden());
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class));
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/api/ordens-servico")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ordens-servico/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ordens-servico").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/ordens-servico/1/cliente/1").with(csrf())).andExpect(status().isUnauthorized());
    }
}
