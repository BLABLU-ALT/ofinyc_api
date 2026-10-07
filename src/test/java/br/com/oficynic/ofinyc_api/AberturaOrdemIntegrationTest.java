package br.com.oficynic.ofinyc_api;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
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
class AberturaOrdemIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long cliente() {
        return jdbc.queryForObject("INSERT INTO clientes(nome,cpf) VALUES ('Cliente OS','80000000001') RETURNING id", Long.class);
    }

    private long moto(long cliente) {
        return jdbc.queryForObject("INSERT INTO motocicletas(placa,chassi,marca,modelo,ano,cliente_id) VALUES ('OSD1A23','12345678901234568','Marca','Modelo',2024,?) RETURNING id", Long.class, cliente);
    }

    private long funcionario() {
        return jdbc.queryForObject("INSERT INTO funcionarios(nome) VALUES ('Responsável teste') RETURNING id", Long.class);
    }

    private String dados(long cliente, long moto) {
        return "{\"descricao\":\"  Revisão solicitada  \",\"clienteId\":" + cliente + ",\"motocicletaId\":" + moto + ",\"funcionarioId\":" + funcionario() + "}";
    }

    private long abrir(long cliente, long moto) throws Exception {
        String json = mvc.perform(post("/api/ordens-servico/abertura").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(dados(cliente, moto)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.clienteId").value(cliente))
                .andExpect(jsonPath("$.motocicletaId").value(moto))
                .andExpect(jsonPath("$.descricao").value("Revisão solicitada"))
                .andExpect(jsonPath("$.dataAbertura").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.clienteNome").value("Cliente OS"))
                .andExpect(jsonPath("$.placa").value("OSD1A23"))
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return ((Number)JsonPath.read(json,"$.id")).longValue();
    }

    @Test
    void abreEConsultaOrdemComVinculosNoPostgres() throws Exception {
        long cliente = cliente(), moto = moto(cliente), ordem = abrir(cliente,moto);
        assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM ordens_servico WHERE id=?", Long.class, ordem));
        assertEquals(moto, jdbc.queryForObject("SELECT motocicleta_id FROM ordens_servico WHERE id=?", Long.class, ordem));
        mvc.perform(get("/api/ordens-servico/consulta/" + ordem)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ordem)).andExpect(jsonPath("$.modelo").value("Modelo"))
                .andExpect(jsonPath("$.cliente").doesNotExist()).andExpect(jsonPath("$.ordensServico").doesNotExist());
        mvc.perform(get("/api/ordens-servico/consulta")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ordem));
        mvc.perform(get("/ordens-servico")).andExpect(status().isOk()).andExpect(view().name("cadastros"));
    }

    @Test
    void entradasInvalidasNaoCriamOrdem() throws Exception {
        long antes = jdbc.queryForObject("SELECT count(*) FROM ordens_servico", Long.class);
        for (String body : new String[]{"{}", "{\"descricao\":\"   \",\"clienteId\":1,\"motocicletaId\":1}",
                "{\"descricao\":\"Revisão\",\"clienteId\":0,\"motocicletaId\":-1}",
                "{\"descricao\":\"Revisão\",\"clienteId\":1}",
                "{\"descricao\":\"Revisão\",\"motocicletaId\":1}",
                "{\"descricao\":\"" + "x".repeat(256) + "\",\"clienteId\":1,\"motocicletaId\":1}"}) {
            mvc.perform(post("/api/ordens-servico/abertura").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(antes,jdbc.queryForObject("SELECT count(*) FROM ordens_servico",Long.class));
    }

    @Test
    void referenciaInexistenteNaoGravaParcialmente() throws Exception {
        long cliente = cliente(), moto = moto(cliente);
        long antes = jdbc.queryForObject("SELECT count(*) FROM ordens_servico",Long.class);
        for (String body : new String[]{dados(999999999,moto),dados(cliente,999999999)}) {
            mvc.perform(post("/api/ordens-servico/abertura").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        }
        assertEquals(antes,jdbc.queryForObject("SELECT count(*) FROM ordens_servico",Long.class));
        mvc.perform(get("/api/ordens-servico/consulta/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void consultaRegistrosAntigosSemVinculos() throws Exception {
        long id = jdbc.queryForObject("INSERT INTO ordens_servico(descricao,data_abertura) VALUES ('OS antiga',CURRENT_DATE) RETURNING id",Long.class);
        mvc.perform(get("/api/ordens-servico/consulta/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("OS antiga"))
                .andExpect(jsonPath("$.clienteId").isEmpty()).andExpect(jsonPath("$.motocicletaId").isEmpty());
    }

    @Test
    void exigeCsrf() throws Exception {
        mvc.perform(post("/api/ordens-servico/abertura").contentType(MediaType.APPLICATION_JSON)
                .content(dados(1,1))).andExpect(status().isForbidden());
    }

    @Test @WithAnonymousUser
    void exigeAutenticacao() throws Exception {
        mvc.perform(get("/api/ordens-servico/consulta")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ordens-servico/consulta/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ordens-servico/abertura").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(1,1))).andExpect(status().isUnauthorized());
    }

    @Test
    void postgresImpedeMotoInexistente() throws Exception {
        long cliente = cliente();
        long id = abrir(cliente,moto(cliente));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE ordens_servico SET motocicleta_id=999999999 WHERE id=?",id));
    }

    @Test
    void postgresProtegeMotoReferenciada() throws Exception {
        long cliente = cliente(), moto = moto(cliente);
        abrir(cliente,moto);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM motocicletas WHERE id=?",moto));
    }
}
