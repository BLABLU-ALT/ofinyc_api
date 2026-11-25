package br.edu.ifsul.oficynic;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Login pelo formulario com usuarios da tabela perfil e bloqueio de inativos REQ-12 e REQ-14
@SpringBootTest
@AutoConfigureMockMvc
class LoginUsuarioIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Value("${APP_USER:admin}") String loginTecnico;
    final List<Long> usuarios = new ArrayList<>();

    @AfterEach void limpar() {
        for (long id : usuarios) jdbc.update("DELETE FROM usuarios WHERE id=?", id);
    }

    private void usuario(String tipo, String login, boolean ativo) {
        usuarios.add(jdbc.queryForObject("INSERT INTO usuarios(nome, tipo, login, senha, ativo) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, "Usuário " + login, tipo, login, encoder.encode("segredo1"), ativo));
    }

    @Test void funcionarioAtivoEntraComPerfilFuncionario() throws Exception {
        usuario("FUNCIONARIO", "login.funcionario", true);
        mvc.perform(formLogin("/login").user("login.funcionario").password("segredo1"))
                .andExpect(authenticated().withUsername("login.funcionario").withRoles("FUNCIONARIO"));
        mvc.perform(formLogin("/login").user("login.funcionario").password("senha-errada")).andExpect(unauthenticated());
    }

    @Test void administradorCadastradoEntraComPerfilAdministrador() throws Exception {
        usuario("ADMINISTRADOR", "login.administrador", true);
        mvc.perform(formLogin("/login").user("login.administrador").password("segredo1"))
                .andExpect(authenticated().withRoles("ADMINISTRADOR"));
    }

    @Test void usuarioInativoNaoEntra() throws Exception {
        usuario("FUNCIONARIO", "login.inativo", false);
        mvc.perform(formLogin("/login").user("login.inativo").password("segredo1")).andExpect(unauthenticated());
    }

    @Test void funcionarioLogadoNaoAbreAreaAdministrativaPelaUrl() throws Exception {
        usuario("FUNCIONARIO", "login.url", true);
        var sessao = (MockHttpSession) mvc.perform(formLogin("/login").user("login.url").password("segredo1"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isForbidden());
        mvc.perform(get("/funcionarios").session(sessao)).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios/ativos").session(sessao)).andExpect(status().isOk());
        mvc.perform(get("/ordens-servico").session(sessao)).andExpect(status().isOk());
    }

    @Test void loginDaContaTecnicaEhReservado() throws Exception {
        mvc.perform(post("/api/usuarios").with(user("adm").roles("ADMINISTRADOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Cópia\",\"login\":\"" + loginTecnico + "\",\"senha\":\"segredo1\"}"))
                .andExpect(status().isConflict());
    }
}
