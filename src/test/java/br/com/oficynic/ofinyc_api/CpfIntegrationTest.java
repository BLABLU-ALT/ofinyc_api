package br.com.oficynic.ofinyc_api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class CpfIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String dados(String cpf) {
        return "{\"nome\":\"Cliente teste CPF\",\"cpf\":\"" + cpf + "\"}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678909", "01234567890", "00000000191", "90000000175"})
    void aceitaCpfComDigitosCorretos(String cpf) throws Exception {
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(dados(cpf)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cpf").value(cpf));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM clientes WHERE cpf=?", Integer.class, cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678919", "12345678908", "00000000000", "11111111111", "22222222222",
            "33333333333", "44444444444", "55555555555", "66666666666", "77777777777", "88888888888",
            "99999999999", "123", "123456789090", "1234567890a", "123.456.789-09", ""})
    void rejeitaInvalidoSemGravar(String cpf) throws Exception {
        long antes = jdbc.queryForObject("SELECT count(*) FROM clientes", Long.class);
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(dados(cpf)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.cpf").isNotEmpty());
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM clientes", Long.class));
    }

    @Test
    void edicaoInvalidaPreservaCadastroEValidaPodeSerSalva() throws Exception {
        String resposta = mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("12345678909"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        mvc.perform(put("/api/clientes/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Nome alterado\",\"cpf\":\"12345678908\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.cpf").value("CPF inválido. Confira os números informados."));
        assertEquals("12345678909", jdbc.queryForObject("SELECT cpf FROM clientes WHERE id=?", String.class, id));
        assertEquals("Cliente teste CPF", jdbc.queryForObject("SELECT nome FROM clientes WHERE id=?", String.class, id));
        mvc.perform(put("/api/clientes/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("01234567890"))).andExpect(status().isOk());
        assertEquals("01234567890", jdbc.queryForObject("SELECT cpf FROM clientes WHERE id=?", String.class, id));
    }
}
