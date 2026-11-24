package br.edu.ifsul.oficynic;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles="ADMINISTRADOR")
class TelefoneClienteIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    String dados(String nome, String cpf, String telefone) {
        return "{\"nome\":\""+nome+"\",\"cpf\":\""+cpf+"\",\"telefone\":"
                +(telefone==null ? "null" : "\""+telefone+"\"")+"}";
    }
    long inserir(String nome, String cpf, String telefone) {
        return jdbc.queryForObject("INSERT INTO clientes(nome,cpf,telefone) VALUES (?,?,?) RETURNING id",
                Long.class,nome,cpf,telefone);
    }
    @Test void recusaNovoTelefoneComOutroFormato() throws Exception {
        inserir("Original","52998224725","(11) 98765-4321");
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Novo","12345678909","11 987654321")))
                .andExpect(status().isConflict()).andExpect(jsonPath("campos.telefone").value("Já existe um cliente cadastrado com este telefone."));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM clientes WHERE cpf='12345678909'",Integer.class));
    }
    @Test void recusaEdicaoSemGravarParcialmente() throws Exception {
        inserir("Outro","52998224725","(11) 98765-4321");
        long id=inserir("Original","12345678909","21988887777");
        mvc.perform(put("/api/clientes/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Alterado","12345678909","11987654321"))).andExpect(status().isConflict());
        assertEquals("Original",jdbc.queryForObject("SELECT nome FROM clientes WHERE id=?",String.class,id));
        assertEquals("21988887777",jdbc.queryForObject("SELECT telefone FROM clientes WHERE id=?",String.class,id));
    }
    @Test void permiteProprioTelefoneEVazios() throws Exception {
        long id=inserir("Original","12345678909","(11) 98765-4321");
        mvc.perform(put("/api/clientes/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Editado","12345678909","11987654321"))).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Sem telefone","52998224725",""))).andExpect(status().isCreated());
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Sem telefone também","11144477735",null))).andExpect(status().isCreated());
    }
    @Test void preservaLegadoMasExigeCorrigirAoEditar() throws Exception {
        long id=inserir("Legado","12345678909","11987654321");
        inserir("Outro legado","52998224725","(11) 98765-4321");
        mvc.perform(get("/api/clientes/"+id)).andExpect(status().isOk()).andExpect(jsonPath("telefone").value("11987654321"));
        mvc.perform(put("/api/clientes/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Editado","12345678909","11987654321"))).andExpect(status().isConflict());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM clientes WHERE regexp_replace(telefone,'[^0-9]','','g')='11987654321'",Integer.class));
        mvc.perform(put("/api/clientes/"+id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados("Legado corrigido","12345678909","11911112222"))).andExpect(status().isOk());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void duasCriacoesConcorrentesNaoDuplicam() throws Exception { concorrencia(false); }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void criacaoEEdicaoConcorrentesNaoDuplicam() throws Exception { concorrencia(true); }

    void concorrencia(boolean editar) throws Exception {
        String nome="Telefone teste "+UUID.randomUUID();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM clientes WHERE cpf IN ('12345678909','52998224725')",Integer.class));
        Long id=editar ? inserir(nome,"12345678909","11900000000") : null;
        var inicio=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var primeiro=executor.submit(()->{
                inicio.await();
                var request=id==null ? post("/api/clientes") : put("/api/clientes/"+id);
                return mvc.perform(request.with(user("teste").roles("ADMINISTRADOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(dados(nome,"12345678909","(11) 98765-4321"))).andReturn().getResponse().getStatus();
            });
            var segundo=executor.submit(()->{
                inicio.await();
                return mvc.perform(post("/api/clientes").with(user("teste").roles("ADMINISTRADOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(dados(nome,"52998224725","11987654321"))).andReturn().getResponse().getStatus();
            });
            inicio.countDown();
            var resultados=List.of(primeiro.get(20,TimeUnit.SECONDS),segundo.get(20,TimeUnit.SECONDS));
            assertEquals(1,resultados.stream().filter(status->status==409).count());
            assertEquals(1,resultados.stream().filter(status->status==200||status==201).count());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM clientes WHERE nome=? AND regexp_replace(telefone,'[^0-9]','','g')='11987654321'",Integer.class,nome));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(25,TimeUnit.SECONDS));
            jdbc.update("DELETE FROM clientes WHERE nome=?",nome);
        }
    }
}