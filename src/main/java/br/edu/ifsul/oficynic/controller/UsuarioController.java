package br.edu.ifsul.oficynic.controller;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.Administrador;
import br.edu.ifsul.oficynic.model.Funcionario;
import br.edu.ifsul.oficynic.model.Usuario;
import br.edu.ifsul.oficynic.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String loginTecnico;

    public UsuarioController(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${APP_USER:admin}") String loginTecnico) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.loginTecnico = loginTecnico;
    }

    public record Cadastro(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 120)
            String nome,

            @Size(max = 60)
            @Pattern(regexp = "[A-Za-z0-9._-]*", message = "Use letras, números, ponto, hífen ou sublinhado.")
            String login,

            @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres.")
            String senha,

            @Pattern(regexp = "ADMINISTRADOR|FUNCIONARIO", message = "Selecione o perfil.")
            String perfil,

            Boolean ativo) {
    }

    public record Opcao(
            Long id,
            String nome) {
    }

    @GetMapping("/ativos")
    public List<Opcao> listarAtivos() {
        return repository.findByAtivoTrueOrderByNomeAsc()
                .stream()
                .map(usuario -> new Opcao(usuario.getId(), usuario.getNome()))
                .toList();
    }

    @GetMapping
    public List<Usuario> listar() {
        return repository.findAll(Sort.by("nome", "id"));
    }

    @GetMapping("/{id}")
    public Usuario buscar(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario cadastrar(
            @Valid @RequestBody Cadastro dados) {

        Usuario usuario = "ADMINISTRADOR".equals(dados.perfil())
                ? new Administrador()
                : new Funcionario();

        preencher(usuario, dados);

        return repository.saveAndFlush(usuario);
    }

    @PutMapping("/{id}")
    public Usuario atualizar(
            @PathVariable Long id,
            @Valid @RequestBody Cadastro dados) {

        Usuario usuario = buscar(id);

        preencher(usuario, dados);

        return repository.saveAndFlush(usuario);
    }

    private void preencher(Usuario usuario, Cadastro dados) {
        usuario.setNome(dados.nome().trim());
        usuario.setAtivo(dados.ativo() == null || dados.ativo());

        String login = dados.login() == null || dados.login().isBlank()
                ? null
                : dados.login().trim().toLowerCase(Locale.ROOT);

        // Sem login o funcionario so aparece como responsavel e nao entra no sistema
        if (login == null) {
            usuario.setLogin(null);
            usuario.setSenha(null);
            return;
        }

        if (login.equalsIgnoreCase(loginTecnico)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este login é reservado para a conta técnica do administrador.");
        }

        boolean loginRepetido = usuario.getId() == null
                ? repository.existsByLogin(login)
                : repository.existsByLoginAndIdNot(login, usuario.getId());

        if (loginRepetido) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este login.");
        }

        usuario.setLogin(login);

        if (dados.senha() != null && !dados.senha().isBlank()) {
            if (dados.senha().length() < 6) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A senha deve ter pelo menos 6 caracteres.");
            }

            usuario.setSenha(passwordEncoder.encode(dados.senha()));
        } else if (usuario.getSenha() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha para liberar o acesso.");
        }
    }
}
