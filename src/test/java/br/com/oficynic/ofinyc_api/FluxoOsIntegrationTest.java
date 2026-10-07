package br.com.oficynic.ofinyc_api;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import br.com.oficynic.ofinyc_api.service.OrdemServicoService;
import br.com.oficynic.ofinyc_api.repository.OrdemServicoRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Cada chamada confirma no PostgreSQL; a consulta posterior não depende da transação do teste.
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class FluxoOsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrdemServicoService service;
    @Autowired OrdemServicoRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    final List<Long> clientes=new ArrayList<>(), motos=new ArrayList<>(), ordens=new ArrayList<>(), servicos=new ArrayList<>(), funcionarios=new ArrayList<>();

    @AfterEach
    void limparFixtures() {
        for(long id:ordens) { jdbc.update("DELETE FROM itens_servico_os WHERE ordem_servico_id=?",id); jdbc.update("DELETE FROM ordens_servico WHERE id=?",id); }
        for(long id:motos)jdbc.update("DELETE FROM motocicletas WHERE id=?",id);
        for(long id:servicos)jdbc.update("DELETE FROM servicos WHERE id=?",id);
        for(long id:clientes)jdbc.update("DELETE FROM clientes WHERE id=?",id);
        for(long id:funcionarios)jdbc.update("DELETE FROM funcionarios WHERE id=?",id);
    }
    private String postJson(String rota,String body,int status) throws Exception {
        return mvc.perform(post(rota).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
    private long id(String json) { return ((Number)JsonPath.read(json,"$.id")).longValue(); }
    private long cliente() throws Exception {
        long id=id(postJson("/api/clientes","{\"nome\":\"Cliente protótipo\",\"cpf\":\"12345678909\",\"telefone\":\"000000000\"}",201)); clientes.add(id); return id;
    }
    private long moto(long cliente) throws Exception {
        long id=id(postJson("/api/motocicletas","{\"clienteId\":"+cliente+",\"placa\":\"TST1A23\",\"marca\":\"Teste\",\"modelo\":\"Modelo\",\"ano\":2024}",201)); motos.add(id); return id;
    }
    private long servico() throws Exception {
        long id=id(postJson("/api/servicos","{\"nome\":\"Revisão\",\"preco\":10.10}",201)); servicos.add(id); return id;
    }
    private long funcionario() {
        if(!funcionarios.isEmpty()) return funcionarios.get(0);
        long id=jdbc.queryForObject("INSERT INTO funcionarios(nome) VALUES ('Responsável protótipo') RETURNING id",Long.class); funcionarios.add(id); return id;
    }
    private String dados(long cliente,long moto) { return "{\"descricao\":\"Revisão\",\"dataAbertura\":\"2026-10-01\",\"clienteId\":"+cliente+",\"motocicletaId\":"+moto+",\"funcionarioId\":"+funcionario()+"}"; }
    private long ordem() throws Exception {
        long cliente=cliente(), moto=moto(cliente);
        long id=id(postJson("/api/ordens-servico/abertura",dados(cliente,moto),201)); ordens.add(id); return id;
    }
    private String incluir(long os,long servico,String valores,int status) throws Exception {
        return postJson("/api/ordens-servico/"+os+"/servicos","{\"servicoId\":"+servico+","+valores+"}",status);
    }
    private void situacao(long os,String valor,int status) throws Exception {
        mvc.perform(put("/api/ordens-servico/"+os+"/situacao").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"situacao\":\""+valor+"\"}")).andExpect(status().is(status));
    }

    @Test
    void demonstraFluxoCompletoComCommitHistoricoEdicaoERetorno() throws Exception {
        long os=ordem(), servico=servico();
        String resposta=incluir(os,servico,"\"quantidade\":3,\"valorUnitario\":12.35",201);
        long item=((Number)JsonPath.read(resposta,"$.servicos[0].id")).longValue();
        assertEquals(new BigDecimal("37.05"),new BigDecimal(JsonPath.read(resposta,"$.valorTotal").toString()));
        jdbc.update("UPDATE servicos SET preco=99.99 WHERE id=?",servico);
        mvc.perform(get("/api/ordens-servico/consulta/"+os)).andExpect(jsonPath("$.servicos[0].valorUnitario").value(12.35))
                .andExpect(jsonPath("$.dataAbertura").value("2026-10-01"));
        incluir(os,servico,"\"quantidade\":3,\"valorUnitario\":0.10",201);
        mvc.perform(get("/api/ordens-servico/consulta/"+os)).andExpect(jsonPath("$.valorTotal").value(37.35));
        mvc.perform(put("/api/ordens-servico/"+os+"/servicos/"+item).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantidade\":2,\"valorUnitario\":9.99}")).andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(20.28));
        long segundo=jdbc.queryForObject("SELECT max(id) FROM itens_servico_os WHERE ordem_servico_id=?",Long.class,os);
        mvc.perform(delete("/api/ordens-servico/"+os+"/servicos/"+segundo).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.valorTotal").value(19.98));
        mvc.perform(put("/api/ordens-servico/"+os).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(dados(clientes.get(0),motos.get(0)).replace("Revisão","Revisão corrigida")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descricao").value("Revisão corrigida"));
        situacao(os,"EM_ANDAMENTO",200); situacao(os,"EM_ANDAMENTO",200); situacao(os,"ABERTA",200); situacao(os,"EM_ANDAMENTO",200);
        postJson("/api/ordens-servico/"+os+"/finalizacao","",200);
        mvc.perform(get("/api/ordens-servico/consulta/"+os)).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("FINALIZADA"))
                .andExpect(jsonPath("$.valorTotal").value(19.98)).andExpect(jsonPath("$.dataFinalizacao").value(LocalDate.now().toString()));
        mvc.perform(get("/api/ordens-servico/consulta")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(os));
    }

    @Test
    void finalizacaoExigeAndamentoEServicoEBloqueiaTodasAsRotasDeAlteracao() throws Exception {
        long os=ordem(), servico=servico();
        postJson("/api/ordens-servico/"+os+"/finalizacao","",409);
        situacao(os,"EM_ANDAMENTO",200); postJson("/api/ordens-servico/"+os+"/finalizacao","",409);
        situacao(os,"FINALIZADA",409); situacao(os,"INVENTADA",400);
        String json=incluir(os,servico,"\"quantidade\":1",201);
        long item=((Number)JsonPath.read(json,"$.servicos[0].id")).longValue();
        assertEquals(new BigDecimal("10.10"),jdbc.queryForObject("SELECT valor_unitario FROM itens_servico_os WHERE id=?",BigDecimal.class,item));
        postJson("/api/ordens-servico/"+os+"/finalizacao","",200);
        var antes=jdbc.queryForMap("SELECT * FROM ordens_servico WHERE id=?",os);
        incluir(os,servico,"\"quantidade\":1,\"valorUnitario\":1",409);
        mvc.perform(put("/api/ordens-servico/"+os+"/servicos/"+item).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":2,\"valorUnitario\":2}")).andExpect(status().isConflict());
        mvc.perform(delete("/api/ordens-servico/"+os+"/servicos/"+item).with(csrf())).andExpect(status().isConflict());
        mvc.perform(put("/api/ordens-servico/"+os).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(dados(clientes.get(0),motos.get(0)))).andExpect(status().isConflict());
        mvc.perform(put("/api/ordens-servico/"+os+"/cliente/"+clientes.get(0)).with(csrf())).andExpect(status().isConflict());
        situacao(os,"FINALIZADA",200); situacao(os,"ABERTA",409); postJson("/api/ordens-servico/"+os+"/finalizacao","",409);
        assertEquals(antes,jdbc.queryForMap("SELECT * FROM ordens_servico WHERE id=?",os));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM itens_servico_os WHERE ordem_servico_id=?",Integer.class,os));
    }

    @Test
    void rejeitaItensInvalidosENaoGravaParcialmente() throws Exception {
        long os=ordem(), servico=servico();
        for(String valores:new String[]{"\"quantidade\":0","\"quantidade\":-1","\"quantidade\":1.5", "\"quantidade\":1,\"valorUnitario\":-1","\"quantidade\":1,\"valorUnitario\":1.001","\"quantidade\":1,\"valorUnitario\":1000000000000"}) incluir(os,servico,valores,400);
        incluir(os,999999999,"\"quantidade\":1",404);
        incluir(999999999,servico,"\"quantidade\":1",404);
        mvc.perform(put("/api/ordens-servico/"+os+"/servicos/999999999").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":1,\"valorUnitario\":1}")).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM itens_servico_os WHERE ordem_servico_id=?",Integer.class,os));
    }

    @Test
    void inclusaoUsaQuantidadeUmQuandoOmitida() throws Exception {
        long os=ordem(), servico=servico();
        incluir(os,servico,"\"valorUnitario\":1.23",201);
        mvc.perform(get("/api/ordens-servico/consulta/"+os)).andExpect(jsonPath("$.servicos[0].quantidade").value(1))
                .andExpect(jsonPath("$.valorTotal").value(1.23));
    }

    @Test
    void confereProprietarioSemTransferirEProtegeLegado() throws Exception {
        long os=ordem(), servico=servico();
        long outro=jdbc.queryForObject("INSERT INTO clientes(nome,cpf) VALUES ('Outro','90000000175') RETURNING id",Long.class); clientes.add(outro);
        postJson("/api/ordens-servico/abertura",dados(outro,motos.get(0)),409);
        mvc.perform(put("/api/ordens-servico/"+os+"/cliente/"+outro).with(csrf())).andExpect(status().isConflict());
        mvc.perform(put("/api/motocicletas/"+motos.get(0)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":"+outro+",\"placa\":\"TST1A23\",\"marca\":\"M\",\"modelo\":\"M\",\"ano\":2024}")).andExpect(status().isConflict());
        assertEquals(clientes.get(0),jdbc.queryForObject("SELECT cliente_id FROM motocicletas WHERE id=?",Long.class,motos.get(0)));
        long antiga=id(postJson("/api/ordens-servico","{\"descricao\":\"Legado\",\"dataAbertura\":\"2026-10-01\",\"situacao\":\"FINALIZADA\",\"dataFinalizacao\":\"2026-10-01\"}",201)); ordens.add(antiga);
        incluir(antiga,servico,"\"quantidade\":1",409);
        mvc.perform(get("/api/ordens-servico/consulta/"+antiga)).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("ABERTA"))
                .andExpect(jsonPath("$.clienteId").isEmpty()).andExpect(jsonPath("$.dataFinalizacao").isEmpty());
    }

    @Test
    void naoPermiteUsarItemDeOutraOrdem() throws Exception {
        long os=ordem(), servico=servico();
        long outra=id(postJson("/api/ordens-servico/abertura",dados(clientes.get(0),motos.get(0)),201)); ordens.add(outra);
        String json=incluir(os,servico,"\"quantidade\":1",201);
        long item=((Number)JsonPath.read(json,"$.servicos[0].id")).longValue();
        mvc.perform(delete("/api/ordens-servico/"+outra+"/servicos/"+item).with(csrf())).andExpect(status().isNotFound());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM itens_servico_os WHERE id=?",Integer.class,item));
    }

    @Test
    void novasMutacoesExigemLoginECsrf() throws Exception {
        long os=ordem(), servico=servico();
        for(String rota:new String[]{"/api/ordens-servico/"+os+"/servicos","/api/ordens-servico/"+os+"/finalizacao"}) {
            mvc.perform(post(rota).with(anonymous()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
            mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/ordens-servico/"+os+"/situacao").with(anonymous()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"situacao\":\"EM_ANDAMENTO\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/ordens-servico/"+os+"/servicos/1")).andExpect(status().isForbidden());
    }

    @Test
    void rollbackEConstraintsNoPostgres() throws Exception {
        long os=ordem(), servico=servico();
        var transacao=new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class,()->transacao.execute(status->{service.adicionarServico(os,servico,1,new BigDecimal("1.00")); throw new IllegalStateException("Falha simulada antes do commit");}));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM itens_servico_os WHERE ordem_servico_id=?",Integer.class,os));
        for(String sql:new String[]{"UPDATE ordens_servico SET situacao='INVALIDA' WHERE id="+os,
                "UPDATE ordens_servico SET situacao='FINALIZADA' WHERE id="+os,
                "UPDATE ordens_servico SET data_finalizacao=CURRENT_DATE WHERE id="+os,
                "UPDATE motocicletas SET cliente_id=999999999 WHERE id="+motos.get(0),
                "DELETE FROM clientes WHERE id="+clientes.get(0),
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES (999999999,"+servico+",1,1)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES (NULL,"+servico+",1,1)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES ("+os+",NULL,1,1)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES ("+os+","+servico+",1,NULL)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES ("+os+","+servico+",0,1)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES ("+os+","+servico+",1,-1)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES ("+os+",999999999,1,1)",
                "INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES ("+os+","+servico+",NULL,1)"}) assertThrows(DataIntegrityViolationException.class,()->jdbc.execute(sql));
        incluir(os,servico,"\"quantidade\":1",201);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM servicos WHERE id=?",servico));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("DELETE FROM ordens_servico WHERE id=?",os));
    }

    @Test
    void inclusaoAguardaFinalizacaoConcorrenteENaoAlteraOrdemFinal() throws Exception {
        long os=ordem(), servico=servico(); incluir(os,servico,"\"quantidade\":1",201); situacao(os,"EM_ANDAMENTO",200);
        CountDownLatch bloqueada=new CountDownLatch(1), liberar=new CountDownLatch(1), tentando=new CountDownLatch(1);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<?> finalizacao=executor.submit(()->new TransactionTemplate(transactionManager).execute(status->{
                repository.buscarComBloqueio(os).orElseThrow(); bloqueada.countDown();
                try { if(!liberar.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout teste"); }
                catch(InterruptedException erro) { Thread.currentThread().interrupt(); throw new IllegalStateException(erro); }
                service.finalizarOrdem(os,"user"); return null;
            }));
            assertTrue(bloqueada.await(10,TimeUnit.SECONDS));
            Future<?> inclusao=executor.submit(()-> { tentando.countDown(); service.adicionarServico(os,servico,1,new BigDecimal("2.00")); });
            assertTrue(tentando.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->inclusao.get(200,TimeUnit.MILLISECONDS));
            liberar.countDown(); finalizacao.get(10,TimeUnit.SECONDS);
            var falha=assertThrows(ExecutionException.class,()->inclusao.get(10,TimeUnit.SECONDS));
            var causa=assertInstanceOf(org.springframework.web.server.ResponseStatusException.class,falha.getCause());
            assertEquals(409,causa.getStatusCode().value());
            assertEquals("FINALIZADA",jdbc.queryForObject("SELECT situacao FROM ordens_servico WHERE id=?",String.class,os));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM itens_servico_os WHERE ordem_servico_id=?",Integer.class,os));
        } finally { liberar.countDown(); executor.shutdownNow(); }
    }
}
