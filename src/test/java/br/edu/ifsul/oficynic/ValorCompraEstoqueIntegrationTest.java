package br.edu.ifsul.oficynic;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Valor de compra obrigatorio na entrada manual vira preco de custo e nao existe na saida
// So o administrador ve o valor de compra
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username="admin.teste",roles="ADMINISTRADOR")
class ValorCompraEstoqueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    long produto, funcionario;

    @BeforeEach void preparar() {
        produto=jdbc.queryForObject("INSERT INTO produtos(codigo,descricao,preco_venda,preco_custo,unidade_medida) VALUES (?,'Óleo',30,20,'LT') RETURNING id",
                Long.class,"VC"+UUID.randomUUID().toString().substring(0,8));
        funcionario=jdbc.queryForObject("INSERT INTO usuarios(nome,tipo,ativo) VALUES ('Responsável','FUNCIONARIO',true) RETURNING id",Long.class);
    }
    ResultActions movimentar(String tipo, String valor) throws Exception {
        return mvc.perform(post("/api/movimentacoes-estoque").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\":"+produto+",\"funcionarioId\":"+funcionario+",\"tipo\":\""+tipo+"\",\"quantidade\":2,"
                        +(valor==null?"":"\"valorUnitario\":"+valor+",")+"\"motivo\":\"Teste\"}"));
    }
    BigDecimal estoque() { return jdbc.queryForObject("SELECT estoque FROM produtos WHERE id=?",BigDecimal.class,produto); }

    @Test void entradaSemValorERecusadaEnaoMovimenta() throws Exception {
        movimentar("ENTRADA",null).andExpect(status().isBadRequest());
        assertEquals(0,estoque().signum());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=?",Integer.class,produto));
    }
    BigDecimal custo() { return jdbc.queryForObject("SELECT preco_custo FROM produtos WHERE id=?",BigDecimal.class,produto); }
    @Test void entradaGravaValorEAtualizaPrecoDeCustoSemMudarItensDeOs() throws Exception {
        movimentar("ENTRADA","12.50").andExpect(status().isCreated()).andExpect(jsonPath("$.valorUnitario").value(12.5));
        assertEquals(0,new BigDecimal("12.50").compareTo(jdbc.queryForObject("SELECT valor_unitario FROM movimentacoes_estoque WHERE produto_id=?",BigDecimal.class,produto)));
        assertEquals(0,new BigDecimal("12.50").compareTo(custo()));
        assertEquals(0,new BigDecimal("2").compareTo(estoque()));
        // Item da OS guarda o custo do momento e a compra seguinte muda so o cadastro
        long cliente=jdbc.queryForObject("INSERT INTO clientes(nome,cpf) VALUES ('Cliente custo',?) RETURNING id",Long.class,String.valueOf(System.nanoTime()).substring(0,11));
        long moto=jdbc.queryForObject("INSERT INTO motocicletas(placa,marca,modelo,ano,cliente_id) VALUES (?,'Marca','Modelo',2024,?) RETURNING id",Long.class,"V"+UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase(),cliente);
        long os=jdbc.queryForObject("INSERT INTO ordens_servico(descricao,data_abertura,data_criacao,cliente_id,motocicleta_id,funcionario_id) VALUES ('Custo',CURRENT_DATE,CURRENT_DATE,?,?,?) RETURNING id",Long.class,cliente,moto,funcionario);
        mvc.perform(post("/api/ordens-servico/"+os+"/produtos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"produtoId\":"+produto+",\"quantidade\":1}")).andExpect(status().isCreated());
        movimentar("ENTRADA","15.00").andExpect(status().isCreated());
        assertEquals(0,new BigDecimal("15.00").compareTo(custo()));
        assertEquals(0,new BigDecimal("12.50").compareTo(jdbc.queryForObject("SELECT custo_unitario FROM itens_produto_os WHERE ordem_servico_id=?",BigDecimal.class,os)));
    }
    @Test void saidaNaoGuardaValorMesmoSeEnviado() throws Exception {
        movimentar("ENTRADA","10").andExpect(status().isCreated());
        movimentar("SAIDA","99").andExpect(status().isCreated()).andExpect(jsonPath("$.valorUnitario").value(nullValue()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM movimentacoes_estoque WHERE produto_id=? AND tipo='SAIDA' AND valor_unitario IS NULL",Integer.class,produto));
        assertEquals(0,new BigDecimal("10").compareTo(custo()));
    }
    @Test void valorInvalidoERecusado() throws Exception {
        movimentar("ENTRADA","-1").andExpect(status().isBadRequest());
        movimentar("ENTRADA","1.234").andExpect(status().isBadRequest());
        assertEquals(0,estoque().signum());
    }
    @Test void funcionarioInformaMasNaoVeOValor() throws Exception {
        mvc.perform(post("/api/movimentacoes-estoque").with(csrf()).with(user("func.teste").roles("FUNCIONARIO")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"produtoId\":"+produto+",\"funcionarioId\":"+funcionario+",\"tipo\":\"ENTRADA\",\"quantidade\":1,\"valorUnitario\":7.25,\"motivo\":\"Compra\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.valorUnitario").value(nullValue()));
        assertEquals(0,new BigDecimal("7.25").compareTo(jdbc.queryForObject("SELECT valor_unitario FROM movimentacoes_estoque WHERE produto_id=?",BigDecimal.class,produto)));
        mvc.perform(get("/api/movimentacoes-estoque").with(user("func.teste").roles("FUNCIONARIO"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.produtoId=="+produto+")].valorUnitario",contains(nullValue())));
        mvc.perform(get("/api/movimentacoes-estoque")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.produtoId=="+produto+")].valorUnitario",contains(7.25)));
    }
    @Test void bancoRecusaValorEmSaida() throws Exception {
        movimentar("ENTRADA","10").andExpect(status().isCreated());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE movimentacoes_estoque SET tipo='SAIDA' WHERE produto_id=?",produto));
    }
}
