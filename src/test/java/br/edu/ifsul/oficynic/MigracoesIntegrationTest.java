package br.edu.ifsul.oficynic;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MigracoesIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    // Bancos antigos com OS sem cliente ou moto mantem os dados ate a V12
    // A V13 so entra depois que esses vinculos forem completados
    private Flyway ateV12(String schema) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("12").load();
    }

    private String obrigatoria(String schema, String coluna) {
        return jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=? "
                + "AND table_name='ordens_servico' AND column_name=?", String.class, schema, coluna);
    }

    @Test
    void atualizaV6ConvertendoStatusSemPerderHistorico() {
        String schema = "teste_v6_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load().migrate();
            long aberta = jdbc.queryForObject("INSERT INTO " + schema + ".ordens_servico(descricao,data_abertura,situacao) VALUES ('Aberta preservada','2026-09-01','ABERTA') RETURNING id", Long.class);
            long finalizada = jdbc.queryForObject("INSERT INTO " + schema + ".ordens_servico(descricao,data_abertura,situacao,data_finalizacao) VALUES ('Finalizada preservada','2026-08-01','FINALIZADA','2026-08-02') RETURNING id", Long.class);
            Flyway flyway = ateV12(schema);
            assertEquals(6, flyway.migrate().migrationsExecuted);
            assertEquals("ORCAMENTO", jdbc.queryForObject("SELECT situacao FROM " + schema + ".ordens_servico WHERE id=?", String.class, aberta));
            assertEquals("CONCLUIDA", jdbc.queryForObject("SELECT situacao FROM " + schema + ".ordens_servico WHERE id=?", String.class, finalizada));
            assertNull(jdbc.queryForObject("SELECT ordem_origem_id FROM " + schema + ".ordens_servico WHERE id=?", Long.class, aberta));
            assertEquals("Aberta preservada", jdbc.queryForObject("SELECT descricao FROM " + schema + ".ordens_servico WHERE id=?", String.class, aberta));
            assertEquals(java.sql.Date.valueOf("2026-08-02"), jdbc.queryForObject("SELECT data_finalizacao FROM " + schema + ".ordens_servico WHERE id=?", java.sql.Date.class, finalizada));
            for (String sql : new String[]{
                    "UPDATE " + schema + ".ordens_servico SET situacao='ABERTA' WHERE id=" + aberta,
                    "UPDATE " + schema + ".ordens_servico SET situacao='FINALIZADA' WHERE id=" + aberta,
                    "UPDATE " + schema + ".ordens_servico SET situacao='CONCLUIDA' WHERE id=" + aberta,
                    "UPDATE " + schema + ".ordens_servico SET data_finalizacao=CURRENT_DATE WHERE id=" + aberta,
                    "UPDATE " + schema + ".ordens_servico SET ordem_origem_id=" + aberta + " WHERE id=" + aberta,
                    "UPDATE " + schema + ".ordens_servico SET situacao='RETRABALHO' WHERE id=" + aberta,
                    "UPDATE " + schema + ".ordens_servico SET ordem_origem_id=999999999 WHERE id=" + aberta})
                assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(sql));
            flyway.validate();
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }

    @Test
    void atualizaV5SemInventarUnidadeSaldoOuResponsavel() {
        String schema = "teste_v5_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load().migrate();
            long produto = jdbc.queryForObject("INSERT INTO " + schema + ".produtos(codigo,descricao,preco_venda) VALUES ('ANTIGO','Produto preservado',12.34) RETURNING id", Long.class);
            long os = jdbc.queryForObject("INSERT INTO " + schema + ".ordens_servico(descricao,data_abertura) VALUES ('OS preservada','2026-09-01') RETURNING id", Long.class);
            var antesProduto = jdbc.queryForMap("SELECT * FROM " + schema + ".produtos WHERE id=?", produto);
            var antesOs = jdbc.queryForMap("SELECT * FROM " + schema + ".ordens_servico WHERE id=?", os);
            Flyway flyway = ateV12(schema);
            assertEquals(7, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            var depoisProduto = jdbc.queryForMap("SELECT * FROM " + schema + ".produtos WHERE id=?", produto);
            assertNull(depoisProduto.remove("unidade_medida"));
            assertEquals(0, ((java.math.BigDecimal)depoisProduto.remove("estoque")).signum());
            assertNull(depoisProduto.remove("preco_custo"));
            assertEquals(antesProduto, depoisProduto);
            var depoisOs = jdbc.queryForMap("SELECT * FROM " + schema + ".ordens_servico WHERE id=?", os);
            assertNull(depoisOs.remove("funcionario_id"));
            assertNull(depoisOs.remove("ordem_origem_id"));
            assertEquals("ORCAMENTO", depoisOs.remove("situacao"));
            assertEquals("ABERTA", antesOs.remove("situacao"));
            assertNull(depoisOs.remove("data_criacao"));
            assertNull(depoisOs.remove("data_aprovacao"));
            assertNull(depoisOs.remove("data_abertura_anterior"));
            assertNull(depoisOs.remove("parecer_tecnico"));
            assertEquals(antesOs, depoisOs);
            for (String tabela : new String[]{"usuarios", "itens_produto_os", "movimentacoes_estoque"})
                assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + schema + "." + tabela, Integer.class));
            flyway.validate();
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }

    @Test
    void atualizaV4SemInventarProprietarioOuAlterarDadosAntigos() {
        String schema = "teste_v4_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
            long moto = jdbc.queryForObject("INSERT INTO " + schema + ".motocicletas(placa,marca,modelo,ano) VALUES ('OLD1A23','Marca','Modelo',2024) RETURNING id",Long.class);
            long os = jdbc.queryForObject("INSERT INTO " + schema + ".ordens_servico(descricao,data_abertura,motocicleta_id) VALUES ('OS antiga','2026-09-01',?) RETURNING id",Long.class,moto);
            Flyway flyway = ateV12(schema);
            assertEquals(8, flyway.migrate().migrationsExecuted);
            assertEquals(0,flyway.migrate().migrationsExecuted);
            assertNull(jdbc.queryForObject("SELECT cliente_id FROM " + schema + ".motocicletas WHERE id=?",Long.class,moto));
            assertEquals("ORCAMENTO",jdbc.queryForObject("SELECT situacao FROM " + schema + ".ordens_servico WHERE id=?",String.class,os));
            assertNull(jdbc.queryForObject("SELECT data_finalizacao FROM " + schema + ".ordens_servico WHERE id=?",java.sql.Date.class,os));
            assertEquals(moto,jdbc.queryForObject("SELECT motocicleta_id FROM " + schema + ".ordens_servico WHERE id=?",Long.class,os));
            assertEquals("OS antiga",jdbc.queryForObject("SELECT descricao FROM " + schema + ".ordens_servico WHERE id=?",String.class,os));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM " + schema + ".itens_servico_os",Integer.class));
            flyway.validate();
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE"); }
    }

    @Test
    void migraBancoVazio() {
        String schema = "teste_vazio_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway flyway = Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).load();
            assertEquals(13, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".ordens_servico", Integer.class));
            assertEquals("NO", obrigatoria(schema, "cliente_id"));
            assertEquals("NO", obrigatoria(schema, "motocicleta_id"));
            assertEquals("YES", obrigatoria(schema, "funcionario_id"));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void atualizaV1PreservandoCadastro() {
        String schema = "teste_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("1").load().migrate();
            long id = jdbc.queryForObject("INSERT INTO " + schema
                    + ".clientes(nome, cpf) VALUES ('Cliente preservado', '90000000005') RETURNING id", Long.class);
            Flyway flyway = Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).load();
            assertEquals(12, flyway.migrate().migrationsExecuted);
            assertEquals("Cliente preservado", jdbc.queryForObject("SELECT nome FROM " + schema
                    + ".clientes WHERE id = ?", String.class, id));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".ordens_servico", Integer.class));
            flyway.validate();
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void atualizaV2PreservandoOrdemAntiga() {
        String schema = "teste_os_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("2").load().migrate();
            long cliente = jdbc.queryForObject("INSERT INTO " + schema
                    + ".clientes(nome, cpf) VALUES ('Cliente antigo', '90000000006') RETURNING id", Long.class);
            long ordem = jdbc.queryForObject("INSERT INTO " + schema
                    + ".ordens_servico(descricao, data_abertura, cliente_id) VALUES ('OS anterior', CURRENT_DATE, ?) RETURNING id",
                    Long.class, cliente);
            Flyway flyway = ateV12(schema);
            assertEquals(10, flyway.migrate().migrationsExecuted);
            assertEquals(cliente, jdbc.queryForObject("SELECT cliente_id FROM " + schema + ".ordens_servico WHERE id=?", Long.class, ordem));
            assertNull(jdbc.queryForObject("SELECT motocicleta_id FROM " + schema + ".ordens_servico WHERE id=?", Long.class, ordem));
            assertEquals("OS anterior", jdbc.queryForObject("SELECT descricao FROM " + schema + ".ordens_servico WHERE id=?", String.class, ordem));
            flyway.validate();
            var todas = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            var erro = assertThrows(org.flywaydb.core.api.FlywayException.class, todas::migrate);
            assertTrue(erro.getMessage().contains("sem cliente ou motocicleta"));
            assertEquals("YES", obrigatoria(schema, "motocicleta_id"));
            assertEquals("OS anterior", jdbc.queryForObject("SELECT descricao FROM " + schema + ".ordens_servico WHERE id=?", String.class, ordem));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void atualizaV3PreservandoMotoEVinculoDaOs() {
        String schema = "teste_moto_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("3").load().migrate();
            long moto = jdbc.queryForObject("INSERT INTO " + schema
                    + ".motocicletas(placa,chassi,marca,modelo,ano,cor) "
                    + "VALUES ('LEG1A23','12345678901234567','Marca antiga','Modelo antigo',2024,'Preta') RETURNING id", Long.class);
            var antes = jdbc.queryForMap("SELECT * FROM " + schema + ".motocicletas WHERE id=?", moto);
            long os = jdbc.queryForObject("INSERT INTO " + schema
                    + ".ordens_servico(descricao,data_abertura,motocicleta_id) "
                    + "VALUES ('OS preservada',CURRENT_DATE,?) RETURNING id", Long.class, moto);
            Flyway flyway = ateV12(schema);
            assertEquals(9, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            var depois = jdbc.queryForMap("SELECT * FROM " + schema + ".motocicletas WHERE id=?", moto);
            assertNull(depois.remove("cliente_id"));
            assertEquals(antes, depois);
            assertEquals(moto, jdbc.queryForObject("SELECT motocicleta_id FROM " + schema
                    + ".ordens_servico WHERE id=?", Long.class, os));
            jdbc.update("INSERT INTO " + schema + ".motocicletas(placa,marca,modelo,ano) VALUES ('NEW1A23','M','M',2024)");
            jdbc.update("INSERT INTO " + schema + ".motocicletas(chassi,marca,modelo,ano) VALUES ('98765432109876543','M','M',2024)");
            flyway.validate();
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void atualizaV8SemInventarDatasLegadas() {
        String schema="teste_v8_"+UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load().migrate();
            long id=jdbc.queryForObject("INSERT INTO "+schema+".ordens_servico(descricao,data_abertura,situacao) VALUES ('Preservada','2020-03-04','ORCAMENTO') RETURNING id",Long.class);
            var antes=jdbc.queryForMap("SELECT * FROM "+schema+".ordens_servico WHERE id=?",id);
            var flyway=ateV12(schema);
            assertEquals(4,flyway.migrate().migrationsExecuted);
            var depois=jdbc.queryForMap("SELECT * FROM "+schema+".ordens_servico WHERE id=?",id);
            assertNull(depois.remove("data_criacao"));assertNull(depois.remove("data_aprovacao"));assertNull(depois.remove("data_abertura_anterior"));assertNull(depois.remove("parecer_tecnico"));
            assertEquals(antes,depois);flyway.validate();
        } finally {jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
    }
}
