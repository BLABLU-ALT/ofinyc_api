package br.com.oficynic.ofinyc_api;

import br.com.oficynic.ofinyc_api.model.*;
import br.com.oficynic.ofinyc_api.repository.OrdemServicoRepository;
import br.com.oficynic.ofinyc_api.service.*;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Chamadas confirmadas no banco: as consultas seguintes verificam dados após commit.
@SpringBootTest @AutoConfigureMockMvc @WithMockUser(username = "operador-teste")
class EstoqueOsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired OrdemServicoService osService;
    @Autowired EstoqueService estoqueService;
    @Autowired OrdemServicoRepository ordensRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManager entityManager;
    final List<Long> produtos = new ArrayList<>(), ordens = new ArrayList<>();
    long cliente, moto, funcionario, servico;

    @BeforeEach void preparar() throws Exception {
        try (var conexao = dataSource.getConnection()) {
            assertTrue(conexao.getMetaData().getURL().contains(":55434/"), "Usar somente o PostgreSQL separado de testes.");
        }
        funcionario = id(postJson("/api/funcionarios", "{\"nome\":\"Responsável estoque\"}", 201));
        cliente = id(postJson("/api/clientes", "{\"nome\":\"Cliente estoque\",\"cpf\":\"12345678909\"}", 201));
        moto = id(postJson("/api/motocicletas", "{\"clienteId\":"+cliente+",\"placa\":\"EMP1A23\",\"marca\":\"Teste\",\"modelo\":\"Moto\",\"ano\":2024}", 201));
        servico = id(postJson("/api/servicos", "{\"nome\":\"Revisão\",\"preco\":10}", 201));
    }
    @AfterEach void limpar() {
        for (long os : ordens) {
            jdbc.update("DELETE FROM movimentacoes_estoque WHERE ordem_servico_id=?", os);
            jdbc.update("DELETE FROM itens_produto_os WHERE ordem_servico_id=?", os);
            jdbc.update("DELETE FROM itens_servico_os WHERE ordem_servico_id=?", os);
            jdbc.update("DELETE FROM ordens_servico WHERE id=?", os);
        }
        for (long produto : produtos) { jdbc.update("DELETE FROM movimentacoes_estoque WHERE produto_id=?", produto); jdbc.update("DELETE FROM produtos WHERE id=?", produto); }
        jdbc.update("DELETE FROM motocicletas WHERE id=?", moto); jdbc.update("DELETE FROM clientes WHERE id=?", cliente);
        jdbc.update("DELETE FROM servicos WHERE id=?", servico); jdbc.update("DELETE FROM funcionarios WHERE id=?", funcionario);
    }
    private String postJson(String url, String corpo, int esperado) throws Exception {
        return mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }
    private String putJson(String url, String corpo, int esperado) throws Exception {
        return mvc.perform(put(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }
    private long id(String json) { return ((Number)JsonPath.read(json, "$.id")).longValue(); }
    private String dadosOs(long responsavel) { return "{\"descricao\":\"Atendimento\",\"clienteId\":"+cliente+",\"motocicletaId\":"+moto+",\"funcionarioId\":"+responsavel+",\"dataAbertura\":\"2026-10-05\"}"; }
    private long ordem() throws Exception { long os = id(postJson("/api/ordens-servico/abertura", dadosOs(funcionario), 201)); ordens.add(os); return os; }
    private long produto(String preco, String unidade) throws Exception {
        long p = id(postJson("/api/produtos", "{\"codigo\":\"EMP"+UUID.randomUUID().toString().substring(0,8)+"\",\"descricao\":\"Óleo teste\",\"precoVenda\":"+preco+",\"unidadeMedida\":\""+unidade+"\"}", 201)); produtos.add(p); return p;
    }
    private String movimento(long p, String tipo, String q, int esperado) throws Exception {
        return postJson("/api/estoque/movimentos", "{\"produtoId\":"+p+",\"funcionarioId\":"+funcionario+",\"tipo\":\""+tipo+"\",\"quantidade\":"+q+",\"motivo\":\"Conferência de teste\",\"usuarioAtuante\":\"injetado\"}", esperado);
    }
    private String reservar(long os, long p, String q, int esperado) throws Exception {
        return postJson("/api/ordens-servico/"+os+"/produtos", "{\"produtoId\":"+p+",\"quantidade\":"+q+",\"valorUnitario\":999}", esperado);
    }
    private long item(String json) { return ((Number)JsonPath.read(json, "$.produtos[0].id")).longValue(); }
    private void iniciar(long os) throws Exception {
        postJson("/api/ordens-servico/"+os+"/servicos", "{\"servicoId\":"+servico+"}", 201);
        putJson("/api/ordens-servico/"+os+"/situacao", "{\"situacao\":\"EM_ANDAMENTO\"}", 200);
    }
    private BigDecimal numero(String sql, long id) { return jdbc.queryForObject(sql, BigDecimal.class, id); }
    private void saldos(long p, String fisico, String empenho) throws Exception {
        var e = numero("SELECT COALESCE(SUM(i.quantidade),0) FROM itens_produto_os i JOIN ordens_servico o ON o.id=i.ordem_servico_id WHERE i.produto_id=? AND o.situacao <> 'FINALIZADA'", p);
        var f = numero("SELECT estoque FROM produtos WHERE id=?", p);
        assertEquals(0, f.compareTo(new BigDecimal(fisico))); assertEquals(0, e.compareTo(new BigDecimal(empenho)));
        assertTrue(f.subtract(e).signum() >= 0);
        mvc.perform(get("/api/produtos/"+p)).andExpect(status().isOk()).andExpect(jsonPath("$.estoque").value(f.doubleValue()))
                .andExpect(jsonPath("$.empenho").value(e.doubleValue())).andExpect(jsonPath("$.saldo").value(f.subtract(e).doubleValue()));
    }

    @Test void reservaCorrigeRemoveSemBaixaEConservaPreco() throws Exception {
        long p=produto("0.01"," l "), os=ordem(); movimento(p,"ENTRADA","5",201);
        String json=reservar(os,p,"0.5",201); long item=item(json);
        assertEquals(0.01, ((Number)JsonPath.read(json,"$.produtos[0].valorUnitario")).doubleValue());
        assertEquals(0.01, ((Number)JsonPath.read(json,"$.totalProdutos")).doubleValue()); // 0,005 → 0,01 por linha.
        saldos(p,"5","0.5");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=?",Integer.class,p));
        jdbc.update("UPDATE produtos SET preco_venda=20 WHERE id=?",p);
        json=putJson("/api/ordens-servico/"+os+"/produtos/"+item,"{\"quantidade\":2,\"valorUnitario\":99,\"unidadeMedida\":\"KG\"}",200);
        assertEquals("L",JsonPath.read(json,"$.produtos[0].unidadeMedida"));
        assertEquals(0.02,((Number)JsonPath.read(json,"$.produtos[0].subtotal")).doubleValue()); saldos(p,"5","2");
        putJson("/api/ordens-servico/"+os+"/produtos/"+item,"{\"quantidade\":5}",200); saldos(p,"5","5");
        putJson("/api/ordens-servico/"+os+"/produtos/"+item,"{\"quantidade\":4}",200); saldos(p,"5","4");
        mvc.perform(delete("/api/ordens-servico/"+os+"/produtos/"+item).with(csrf())).andExpect(status().isOk()); saldos(p,"5","0");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=?",Integer.class,p));
    }

    @Test void variasOsDisputamDisponivelESaidaManualNaoUsaEmpenho() throws Exception {
        long p=produto("10","UN"), a=ordem(), b=ordem(); movimento(p,"ENTRADA","5",201);
        reservar(a,p,"3",201); reservar(b,p,"3",409); reservar(b,p,"2",201); saldos(p,"5","5");
        movimento(p,"SAIDA","0.001",409);
        mvc.perform(get("/api/produtos/"+p+"/estoque")).andExpect(status().isOk()).andExpect(jsonPath("$.reservas.length()").value(2));
        iniciar(a); postJson("/api/ordens-servico/"+a+"/finalizacao","",200); saldos(p,"2","2");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE ordem_servico_id=?",Integer.class,a));
        postJson("/api/ordens-servico/"+a+"/finalizacao","",409); saldos(p,"2","2");
        assertEquals("operador-teste",jdbc.queryForObject("SELECT usuario_atuante FROM movimentacoes_estoque WHERE ordem_servico_id=?",String.class,a));
        assertEquals(funcionario,jdbc.queryForObject("SELECT funcionario_id FROM movimentacoes_estoque WHERE ordem_servico_id=?",Long.class,a));
    }

    @Test void unidadeSomenteComFisicoZeradoPreservaHistoricoFinal() throws Exception {
        long p=produto("3","L"), os=ordem(); movimento(p,"ENTRADA","1",201); reservar(os,p,"1",201);
        String editar="{\"codigo\":\"ALTERADO\",\"descricao\":\"Óleo\",\"unidadeMedida\":\"ML\",\"precoVenda\":9}";
        putJson("/api/produtos/"+p,editar,409); iniciar(os); postJson("/api/ordens-servico/"+os+"/finalizacao","",200);
        putJson("/api/produtos/"+p,editar,200); saldos(p,"0","0");
        mvc.perform(get("/api/ordens-servico/consulta/"+os)).andExpect(jsonPath("$.produtos[0].unidadeMedida").value("L"))
                .andExpect(jsonPath("$.produtos[0].valorUnitario").value(3));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=? AND unidade_medida='L'",Integer.class,p));
    }

    @Test void camposDeSaldoProtegidosEIdentidadeDeMovimentoDoServidor() throws Exception {
        long p=id(postJson("/api/produtos","{\"codigo\":\"PROTEGIDO\",\"descricao\":\"Teste\",\"precoVenda\":1,\"unidadeMedida\":\"UN\",\"estoque\":100,\"empenho\":10,\"saldo\":90}",201)); produtos.add(p); saldos(p,"0","0");
        movimento(p,"ENTRADA","2.125",201);
        putJson("/api/produtos/"+p,"{\"codigo\":\"PROTEGIDO\",\"descricao\":\"Teste editado\",\"precoVenda\":1,\"unidadeMedida\":\"UN\",\"estoque\":999,\"empenho\":999,\"saldo\":999}",200); saldos(p,"2.125","0");
        assertEquals("operador-teste",jdbc.queryForObject("SELECT usuario_atuante FROM movimentacoes_estoque WHERE produto_id=?",String.class,p));
        movimento(p,"SAIDA","0.125",201); saldos(p,"2","0");
    }

    @Test void invalidosOverflowEReferenciasNaoDeixamGravacoes() throws Exception {
        long p=produto("1","UN"), os=ordem();
        for(String q:List.of("0","-1","0.0001","1000000000000","null")) reservar(os,p,q,400);
        reservar(os,p,"1",409); reservar(os,999999999,"1",404);
        postJson("/api/estoque/movimentos","{\"produtoId\":"+p+",\"funcionarioId\":"+funcionario+",\"tipo\":\"ENTRADA\",\"quantidade\":1,\"motivo\":\" \"}",400);
        postJson("/api/estoque/movimentos","{\"produtoId\":"+p+",\"funcionarioId\":999999999,\"tipo\":\"ENTRADA\",\"quantidade\":1,\"motivo\":\"Teste\"}",404);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=?",Integer.class,p));
        movimento(p,"ENTRADA","999999999999.999",201); movimento(p,"ENTRADA","0.001",409); saldos(p,"999999999999.999","0");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=?",Integer.class,p));
    }

    @Test void produtoDeOutraOsEFinalizadaNaoPodemSerAlterados() throws Exception {
        long p=produto("2","UN"), os=ordem(), outra=ordem(); movimento(p,"ENTRADA","5",201);long i=item(reservar(os,p,"1",201));
        putJson("/api/ordens-servico/"+outra+"/produtos/"+i,"{\"quantidade\":2}",404);
        mvc.perform(delete("/api/ordens-servico/"+outra+"/produtos/"+i).with(csrf())).andExpect(status().isNotFound());
        putJson("/api/ordens-servico/"+os+"/situacao","{\"situacao\":\"EM_ANDAMENTO\"}",200);postJson("/api/ordens-servico/"+os+"/finalizacao","",409); // Produto sem serviço não muda decisão anterior.
        iniciar(os);postJson("/api/ordens-servico/"+os+"/finalizacao","",200);
        putJson("/api/ordens-servico/"+os+"/produtos/"+i,"{\"quantidade\":2}",409);reservar(os,p,"1",409);
        mvc.perform(delete("/api/ordens-servico/"+os+"/produtos/"+i).with(csrf())).andExpect(status().isConflict());saldos(p,"4","0");
    }

    @Test void responsavelLegadoPrecisaSerCompletadoSemInferencia() throws Exception {
        long p=produto("1","UN"), os=ordem();movimento(p,"ENTRADA","1",201);jdbc.update("UPDATE ordens_servico SET funcionario_id=NULL WHERE id=?",os);
        mvc.perform(get("/api/ordens-servico/consulta/"+os)).andExpect(jsonPath("$.funcionarioId").isEmpty());reservar(os,p,"1",409);
        putJson("/api/ordens-servico/"+os,dadosOs(funcionario),200);reservar(os,p,"1",201);saldos(p,"1","1");
        long antigo=jdbc.queryForObject("INSERT INTO produtos(codigo,descricao,preco_venda) VALUES ('LEGADO','Sem unidade',1) RETURNING id",Long.class);produtos.add(antigo);
        mvc.perform(get("/api/produtos/"+antigo)).andExpect(jsonPath("$.unidadeMedida").isEmpty());movimento(antigo,"ENTRADA","1",409);
    }

    @Test void contratosProtegidosECadastroFuncionarioNaoCriaLogin() throws Exception {
        long p=produto("1","UN"), os=ordem();
        for(String url:List.of("/api/funcionarios","/api/estoque/movimentos","/api/produtos/"+p+"/estoque"))
            mvc.perform(get(url).with(anonymous())).andExpect(status().isUnauthorized());
        for(String url:List.of("/funcionarios","/estoque","/ordens-servico/"+os)) {
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(view().name("cadastros"));
            mvc.perform(get(url).with(anonymous())).andExpect(status().is3xxRedirection());
        }
        mvc.perform(post("/api/estoque/movimentos").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/ordens-servico/"+os+"/produtos").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());
        putJson("/api/funcionarios/"+funcionario,"{\"nome\":\"Nome atualizado\",\"login\":\"injetado\",\"perfil\":\"ADMINISTRADOR\"}",200);
        mvc.perform(get("/api/funcionarios/"+funcionario)).andExpect(jsonPath("$.login").doesNotExist()).andExpect(jsonPath("$.perfil").doesNotExist());
        postJson("/api/funcionarios","{\"nome\":\" \"}",400);
    }

    @Test void falhaNoUltimoMovimentoReverteTodaFinalizacao() throws Exception {
        long a=produto("1","UN"),b=produto("2","L"),os=ordem();movimento(a,"ENTRADA","4",201);movimento(b,"ENTRADA","4",201);reservar(os,a,"2",201);reservar(os,b,"2",201);iniciar(os);
        String nome="falha_"+UUID.randomUUID().toString().replace("-","");
        try {
            jdbc.execute("CREATE FUNCTION "+nome+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.ordem_servico_id="+os+" AND NEW.produto_id="+b+" THEN RAISE EXCEPTION 'falha planejada'; END IF; RETURN NEW; END $$");
            jdbc.execute("CREATE TRIGGER "+nome+" BEFORE INSERT ON movimentacoes_estoque FOR EACH ROW EXECUTE FUNCTION "+nome+"()");
            assertThrows(RuntimeException.class,()->osService.finalizarOrdem(os,"operador-teste"));
        } finally {jdbc.execute("DROP TRIGGER IF EXISTS "+nome+" ON movimentacoes_estoque");jdbc.execute("DROP FUNCTION IF EXISTS "+nome+"()");}
        saldos(a,"4","2");saldos(b,"4","2");assertEquals("EM_ANDAMENTO",jdbc.queryForObject("SELECT situacao FROM ordens_servico WHERE id=?",String.class,os));
        assertNull(jdbc.queryForObject("SELECT data_finalizacao FROM ordens_servico WHERE id=?",java.time.LocalDate.class,os));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE ordem_servico_id=?",Integer.class,os));
        postJson("/api/ordens-servico/"+os+"/finalizacao","",200);saldos(a,"2","0");saldos(b,"2","0");
    }

    private List<Boolean> concorrer(Callable<Boolean> a, Callable<Boolean> b) throws Exception {
        var executor=Executors.newFixedThreadPool(2);var largada=new CountDownLatch(1);
        try {var primeiro=executor.submit(()->{largada.await();return a.call();});var segundo=executor.submit(()->{largada.await();return b.call();});largada.countDown();return List.of(primeiro.get(20,TimeUnit.SECONDS),segundo.get(20,TimeUnit.SECONDS));}
        finally {executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
    private boolean tentarReserva(long os,long p) {try {osService.adicionarProduto(os,p,new BigDecimal("3"));return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}}

    @Test void reservasConcorrentesNaoUltrapassamDisponivel() throws Exception {
        long p=produto("1","UN"),a=ordem(),b=ordem();movimento(p,"ENTRADA","5",201);
        var resultados=concorrer(()->tentarReserva(a,p),()->tentarReserva(b,p));assertEquals(1,resultados.stream().filter(Boolean::booleanValue).count());saldos(p,"5","3");
    }
    @Test void variasLinhasDoMesmoProdutoSomamReservasEBaixas() throws Exception {
        long p=produto("2","L"),os=ordem();movimento(p,"ENTRADA","5",201);
        reservar(os,p,"1.125",201);reservar(os,p,"2.875",201);saldos(p,"5","4");
        iniciar(os);postJson("/api/ordens-servico/"+os+"/finalizacao","",200);saldos(p,"1","0");
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE ordem_servico_id=?",Integer.class,os));
        assertEquals(0,numero("SELECT SUM(quantidade) FROM movimentacoes_estoque WHERE ordem_servico_id=?",os).compareTo(new BigDecimal("4")));
    }
    @Test void produtoPreCarregadoEAtualizadoAposEsperaDeLock() throws Exception {
        long p=produto("1","UN");movimento(p,"ENTRADA","5",201);
        var executor=Executors.newFixedThreadPool(2);var bloqueou=new CountDownLatch(1);var carregou=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        try {
            var saida=executor.submit(()->new TransactionTemplate(transactionManager).execute(status->{
                estoqueService.movimentar(p,funcionario,TipoMovimentacao.SAIDA,new BigDecimal("3"),"Saída concorrente","operador-teste");
                bloqueou.countDown();try{assertTrue(liberar.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}return true;
            }));
            assertTrue(bloqueou.await(10,TimeUnit.SECONDS));
            var espera=executor.submit(()->{
                try {new TransactionTemplate(transactionManager).execute(status->{
                    assertEquals(0,entityManager.find(Produto.class,p).getEstoque().compareTo(new BigDecimal("5")));
                    carregou.countDown();estoqueService.movimentar(p,funcionario,TipoMovimentacao.SAIDA,new BigDecimal("3"),"Não pode consumir saldo antigo","operador-teste");return null;
                });return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}
            });
            assertTrue(carregou.await(10,TimeUnit.SECONDS));liberar.countDown();
            assertTrue(saida.get(20,TimeUnit.SECONDS));assertFalse(espera.get(20,TimeUnit.SECONDS));saldos(p,"2","0");
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=?",Integer.class,p));
        } finally {liberar.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void reservaConcorrenteComSaidaManualNaoProduzSaldoNegativo() throws Exception {
        long p=produto("1","UN"),os=ordem();movimento(p,"ENTRADA","5",201);
        var resultados=concorrer(()->tentarReserva(os,p),()->{try {estoqueService.movimentar(p,funcionario,TipoMovimentacao.SAIDA,new BigDecimal("3"),"Saída teste","operador-teste");return true;}catch(ResponseStatusException e){assertEquals(409,e.getStatusCode().value());return false;}});
        assertEquals(1,resultados.stream().filter(Boolean::booleanValue).count());
        if(resultados.get(0))saldos(p,"5","3");else saldos(p,"2","0");
    }
    @Test void finalizacoesComProdutosEmOrdemInversaNaoDuplicamBaixas() throws Exception {
        long p=produto("1","UN"),q=produto("2","L"),a=ordem(),b=ordem();movimento(p,"ENTRADA","10",201);movimento(q,"ENTRADA","10",201);
        reservar(a,p,"2",201);reservar(a,q,"2",201);reservar(b,q,"2",201);reservar(b,p,"2",201);iniciar(a);iniciar(b);
        var resultados=concorrer(()->{osService.finalizarOrdem(a,"operador-teste");return true;},()->{osService.finalizarOrdem(b,"operador-teste");return true;});
        assertEquals(List.of(true,true),resultados);saldos(p,"6","0");saldos(q,"6","0");
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE ordem_servico_id=?",Integer.class,a));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE ordem_servico_id=?",Integer.class,b));
    }

    @Test void inclusaoQueAguardaFinalizacaoNaoCriaReservaPosterior() throws Exception {
        long p=produto("1","UN"),os=ordem();movimento(p,"ENTRADA","5",201);reservar(os,p,"1",201);iniciar(os);
        var executor=Executors.newFixedThreadPool(2);var bloqueou=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        try {
            var finalizacao=executor.submit(()->new TransactionTemplate(transactionManager).execute(status->{ordensRepository.buscarComBloqueio(os).orElseThrow();bloqueou.countDown();try{assertTrue(liberar.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}osService.finalizarOrdem(os,"operador-teste");return true;}));
            assertTrue(bloqueou.await(10,TimeUnit.SECONDS));var inclusao=executor.submit(()->tentarReserva(os,p));liberar.countDown();
            assertTrue(finalizacao.get(20,TimeUnit.SECONDS));assertFalse(inclusao.get(20,TimeUnit.SECONDS));saldos(p,"4","0");
        } finally {liberar.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }

    @Test void constraintsSqlProtegemSinaisUnidadesVinculosEMotivos() throws Exception {
        long p=produto("1","UN"),os=ordem();var tx=new TransactionTemplate(transactionManager);
        for(String sql:List.of("UPDATE produtos SET estoque=-1 WHERE id="+p,
                "INSERT INTO itens_produto_os(ordem_servico_id,produto_id,quantidade,valor_unitario,unidade_medida) VALUES ("+os+","+p+",0,1,'UN')",
                "INSERT INTO itens_produto_os(ordem_servico_id,produto_id,quantidade,valor_unitario,unidade_medida) VALUES ("+os+","+p+",1,-1,'UN')",
                "INSERT INTO itens_produto_os(ordem_servico_id,produto_id,quantidade,valor_unitario,unidade_medida) VALUES ("+os+",999999999,1,1,'UN')",
                "INSERT INTO itens_produto_os(ordem_servico_id,produto_id,quantidade,valor_unitario,unidade_medida) VALUES ("+os+","+p+",1,1,' ')",
                "INSERT INTO movimentacoes_estoque(produto_id,funcionario_id,tipo,quantidade,unidade_medida,data,motivo,usuario_atuante) VALUES ("+p+","+funcionario+",'ENTRADA',1,'UN',CURRENT_TIMESTAMP,' ','teste')",
                "DELETE FROM funcionarios WHERE id="+funcionario))
            assertThrows(RuntimeException.class,()->tx.execute(status->{jdbc.execute(sql);return null;}));
        saldos(p,"0","0");
    }
}
