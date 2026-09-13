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
class CadastrosIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    static Stream<Arguments> cadastros() {
        return Stream.of(
            Arguments.of("clientes", "nome", "Cliente de teste", "Cliente editado",
                "{\"nome\":\"Cliente de teste\",\"cpf\":\"12345678901\",\"email\":\"teste@example.com\"}"),
            Arguments.of("motocicletas", "modelo", "Modelo teste", "Modelo editado",
                "{\"placa\":\"ABC1D23\",\"chassi\":\"12345678901234567\",\"marca\":\"Marca teste\",\"modelo\":\"Modelo teste\",\"ano\":2024}"),
            Arguments.of("produtos", "descricao", "Produto teste", "Produto editado",
                "{\"codigo\":\"P001\",\"descricao\":\"Produto teste\",\"precoVenda\":29.90}"),
            Arguments.of("servicos", "nome", "Serviço teste", "Serviço editado",
                "{\"nome\":\"Serviço teste\",\"descricao\":\"Descrição teste\",\"preco\":50.00}")
        );
    }

    @ParameterizedTest @MethodSource("cadastros") @WithMockUser
    void cadastraConsultaEditaNoPostgres(String tabela, String campo, String antigo, String novo, String body) throws Exception {
        String salvo = mvc.perform(post("/api/"+tabela).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replaceFirst("\\{", "{\"id\":999999,")))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number)JsonPath.read(salvo, "$.id")).longValue();
        assertNotEquals(999999, id);
        mvc.perform(get("/api/"+tabela+"/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$."+campo).value(antigo));
        mvc.perform(put("/api/"+tabela+"/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace(antigo, novo).replaceFirst("\\{", "{\"id\":999999,")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$."+campo).value(novo));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM "+tabela, Integer.class));
        assertEquals(novo, jdbc.queryForObject("SELECT "+campo+" FROM "+tabela+" WHERE id = ?", String.class, id));
        mvc.perform(get("/api/"+tabela)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get("/api/"+tabela+"/9999999")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/"+tabela+"/"+id).with(csrf())).andExpect(status().isMethodNotAllowed());
    }

    static Stream<Arguments> duplicidades() {
        return Stream.of(
            Arguments.of("clientes","cpf","12345678901","12345678902", "{\"nome\":\"Teste\",\"cpf\":\"12345678901\"}"),
            Arguments.of("motocicletas","placa","ABC1D23","XYZ9A87", "{\"placa\":\"ABC1D23\",\"chassi\":\"12345678901234567\",\"marca\":\"Marca\",\"modelo\":\"Modelo\",\"ano\":2024}"),
            Arguments.of("motocicletas","chassi","12345678901234567","98765432109876543", "{\"placa\":\"ABC1D23\",\"chassi\":\"12345678901234567\",\"marca\":\"Marca\",\"modelo\":\"Modelo\",\"ano\":2024}"),
            Arguments.of("produtos","codigo","P001","P002", "{\"codigo\":\"P001\",\"descricao\":\"Produto\",\"precoVenda\":10.00}")
        );
    }

    @ParameterizedTest @MethodSource("duplicidades") @WithMockUser
    void impedeDuplicidadeCriacaoEEdicao(String tabela,String campo,String primeiro,String segundo,String body) throws Exception {
        mvc.perform(post("/api/"+tabela).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/"+tabela).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        String outro=body.replace(primeiro,segundo);
        if(tabela.equals("motocicletas")) outro=outro.replace(campo.equals("placa")?"12345678901234567":"ABC1D23",campo.equals("placa")?"98765432109876543":"XYZ9A87");
        String resposta=mvc.perform(post("/api/"+tabela).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(outro))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=((Number)JsonPath.read(resposta,"$.id")).longValue();
        mvc.perform(put("/api/"+tabela+"/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(outro.replace(segundo,primeiro))).andExpect(status().isConflict());
        assertEquals(segundo,jdbc.queryForObject("SELECT "+campo+" FROM "+tabela+" WHERE id = ?",String.class,id));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM "+tabela,Integer.class));
    }

    @ParameterizedTest @MethodSource("cadastros") @WithMockUser
    void rejeitaVazioENaoGrava(String tabela,String a,String b,String c,String body) throws Exception {
        mvc.perform(post("/api/"+tabela).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos").isMap());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM "+tabela,Integer.class));
    }

    @ParameterizedTest @MethodSource("cadastros")
    void exigeLoginNaApi(String tabela,String a,String b,String c,String body) throws Exception {
        mvc.perform(get("/api/"+tabela)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @MethodSource("cadastros") @WithMockUser
    void exigeCsrf(String tabela,String a,String b,String c,String body) throws Exception {
        mvc.perform(post("/api/"+tabela).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM "+tabela,Integer.class));
    }

    @Test @WithMockUser
    void rejeitaPrecoNegativoEPrecisaoExcessiva() throws Exception {
        for(String preco:new String[]{"-1","1.234","1000000000000"}) {
            mvc.perform(post("/api/produtos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"P1\",\"descricao\":\"Teste\",\"precoVenda\":"+preco+"}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM produtos",Integer.class));
    }

    @Test
    void bancoTemUnicidadesObrigatoriosEChecks() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            String[][] casos={
                {"INSERT INTO clientes(nome,cpf) VALUES('Teste','12345678901')","INSERT INTO clientes(nome,cpf) VALUES('Outro','12345678901')"},
                {"INSERT INTO motocicletas(placa,chassi,marca,modelo,ano) VALUES('ABC1D23','12345678901234567','M','M',2024)","INSERT INTO motocicletas(placa,chassi,marca,modelo,ano) VALUES('ABC1D23','98765432109876543','M','M',2024)"},
                {"INSERT INTO motocicletas(placa,chassi,marca,modelo,ano) VALUES('ABC1D23','12345678901234567','M','M',2024)","INSERT INTO motocicletas(placa,chassi,marca,modelo,ano) VALUES('XYZ9A87','12345678901234567','M','M',2024)"},
                {"INSERT INTO produtos(codigo,descricao,preco_venda) VALUES('P1','Teste',1)","INSERT INTO produtos(codigo,descricao,preco_venda) VALUES('P1','Outro',2)"},
                {null,"INSERT INTO servicos(nome,preco) VALUES(NULL,1)"},
                {null,"INSERT INTO servicos(nome,preco) VALUES('Teste',-1)"},
                {null,"INSERT INTO produtos(codigo,descricao,preco_venda) VALUES('P1','Teste',-1)"}
            };
            for(String[] caso:casos) {
                var ponto=connection.setSavepoint();
                try(var statement=connection.createStatement()) {
                    if(caso[0]!=null) statement.execute(caso[0]);
                    assertThrows(SQLException.class,()->statement.execute(caso[1]));
                } finally { connection.rollback(ponto); }
            }
            return null;
        });
    }

    @Test @WithMockUser
    void paginasRenderizamSemErro() throws Exception {
        for(String pagina:new String[]{"/","/clientes","/motocicletas","/produtos","/servicos"})
            mvc.perform(get(pagina)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void loginPublicoERestoProtegido() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/clientes")).andExpect(status().is3xxRedirection());
    }
}
