package br.edu.ifsul.oficynic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class ConsultaServicoIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
 long servico(){return jdbc.queryForObject("INSERT INTO servicos(nome,preco) VALUES ('Serviço consulta',10) RETURNING id",Long.class);}
 @Test @WithMockUser(roles="FUNCIONARIO")
 void consultaAbertasSemDuplicarNemIncluirTerminais() throws Exception {
  long s=servico(),outro=servico();
  long cliente=jdbc.queryForObject("INSERT INTO clientes(nome,cpf) VALUES ('Cliente consulta','12345678909') RETURNING id",Long.class);
  long moto=jdbc.queryForObject("INSERT INTO motocicletas(placa,marca,modelo,ano,cliente_id) VALUES ('CSV1A23','Marca','Modelo',2024,?) RETURNING id",Long.class,cliente);
  long origem=jdbc.queryForObject("INSERT INTO ordens_servico(descricao,data_abertura,situacao,data_finalizacao,cliente_id,motocicleta_id) VALUES ('Origem',CURRENT_DATE,'CONCLUIDA',CURRENT_DATE,?,?) RETURNING id",Long.class,cliente,moto);
  for(String estado:new String[]{"ORCAMENTO","EM_ANDAMENTO","RETRABALHO","CONCLUIDA","CANCELADA"}){
   long os=jdbc.queryForObject("INSERT INTO ordens_servico(descricao,data_abertura,situacao,data_finalizacao,ordem_origem_id,cliente_id,motocicleta_id) VALUES ('Consulta',CURRENT_DATE,?,CASE WHEN ?='CONCLUIDA' THEN CURRENT_DATE ELSE NULL END,?,?,?) RETURNING id",Long.class,estado,estado,origem,cliente,moto);
   for(int n=0;n<2;n++)jdbc.update("INSERT INTO itens_servico_os(ordem_servico_id,servico_id,quantidade,valor_unitario) VALUES (?,?,1,10)",os,s);
  }
  mvc.perform(get("/api/servicos/"+s+"/ordens-abertas")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(3))).andExpect(jsonPath("$[*].situacao",containsInAnyOrder("ORCAMENTO","EM_ANDAMENTO","RETRABALHO"))).andExpect(jsonPath("$[0].cliente").value("Cliente consulta"));
  mvc.perform(get("/api/servicos/"+outro+"/ordens-abertas")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
 }
 @Test @WithMockUser(roles="ADMINISTRADOR") void inexistenteEVazio() throws Exception {
  mvc.perform(get("/api/servicos/"+servico()+"/ordens-abertas")).andExpect(status().isOk()).andExpect(jsonPath("$",hasSize(0)));
  mvc.perform(get("/api/servicos/999999999/ordens-abertas")).andExpect(status().isNotFound());
 }
 @Test void anonimoNaoConsulta() throws Exception {mvc.perform(get("/api/servicos/1/ordens-abertas")).andExpect(status().isUnauthorized());}
}