package br.com.oficynic.ofinyc_api.controller;

import br.com.oficynic.ofinyc_api.model.Administrador;
import br.com.oficynic.ofinyc_api.model.Funcionario;
import br.com.oficynic.ofinyc_api.model.Usuario;
import br.com.oficynic.ofinyc_api.repository.UsuarioRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

// Gestão de funcionários e administradores (somente administrador; ver SecurityConfig).
@RestController
@RequestMapping("/api/funcionarios")
public class FuncionarioController {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String loginTecnico;

    public FuncionarioController(UsuarioRepository repository, PasswordEncoder passwordEncoder,
                                 @Value("${APP_USER:admin}") String loginTecnico) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.loginTecnico = loginTecnico;
    }

    public record Cadastro(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @Size(max = 60) @Pattern(regexp = "[A-Za-z0-9._-]*", message = "Use letras, números, ponto, hífen ou sublinhado.") String login,
            @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres.") String senha,
            @Pattern(regexp = "ADMINISTRADOR|FUNCIONARIO", message = "Selecione o perfil.") String perfil,
            Boolean ativo) { }

    // Opção resumida para escolher o responsável: somente usuários ativos.
    public record Opcao(Long id, String nome) { }

    @GetMapping("/ativos")
    public List<Opcao> listarAtivos() {
        return repository.findByAtivoTrueOrderByNomeAsc().stream().map(u -> new Opcao(u.getId(), u.getNome())).toList();
    }

    @GetMapping
    public List<Usuario> listar() { return repository.findAll(Sort.by("nome", "id")); }

    @GetMapping("/{id}")
    public Usuario buscar(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario cadastrar(@Valid @RequestBody Cadastro dados) {
        // O perfil define a subclasse e é fixado no cadastro.
        Usuario usuario = "ADMINISTRADOR".equals(dados.perfil()) ? new Administrador() : new Funcionario();
        preencher(usuario, dados);
        return repository.saveAndFlush(usuario);
    }

    @PutMapping("/{id}")
    public Usuario atualizar(@PathVariable Long id, @Valid @RequestBody Cadastro dados) {
        Usuario usuario = buscar(id);
        preencher(usuario, dados);
        return repository.saveAndFlush(usuario);
    }

    private void preencher(Usuario usuario, Cadastro dados) {
        usuario.setNome(dados.nome().trim());
        usuario.setAtivo(dados.ativo() == null || dados.ativo());
        String login = dados.login() == null || dados.login().isBlank() ? null : dados.login().trim().toLowerCase(Locale.ROOT);
        if (login == null) {
            // Sem login, o funcionário é apenas responsável pelas OS e não acessa o sistema.
            usuario.setLogin(null);
            usuario.setSenha(null);
            return;
        }
        if (login.equalsIgnoreCase(loginTecnico)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este login é reservado para a conta técnica do administrador.");
        }
        boolean duplicado = usuario.getId() == null ? repository.existsByLogin(login) : repository.existsByLoginAndIdNot(login, usuario.getId());
        if (duplicado) throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este login.");
        usuario.setLogin(login);
        if (dados.senha() != null && !dados.senha().isBlank()) {
            if (dados.senha().length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter pelo menos 6 caracteres.");
            usuario.setSenha(passwordEncoder.encode(dados.senha()));
        } else if (usuario.getSenha() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a senha para liberar o acesso.");
        }
    }
}
