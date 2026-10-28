package br.com.oficynic.ofinyc_api.config;

import br.com.oficynic.ofinyc_api.model.Usuario;
import br.com.oficynic.ofinyc_api.repository.UsuarioRepository;
import jakarta.servlet.DispatcherType;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {
    // Gera senhas no formato {bcrypt}..., o mesmo usado na conta técnica.
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // A conta técnica (APP_USER/APP_PASSWORD) é o administrador inicial.
    // Os demais usuários vêm da tabela usuarios; inativos não conseguem entrar.
    @Bean
    UserDetailsService usuarios(UsuarioRepository repository, PasswordEncoder encoder,
                                @Value("${APP_USER:admin}") String loginTecnico,
                                @Value("${APP_PASSWORD}") String senhaTecnica) {
        String senhaTecnicaCodificada = encoder.encode(senhaTecnica);
        return login -> {
            if (login.equals(loginTecnico)) {
                return User.withUsername(loginTecnico).password(senhaTecnicaCodificada).roles("ADMINISTRADOR").build();
            }
            Usuario usuario = repository.findByLogin(login.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));
            return User.withUsername(usuario.getLogin()).password(usuario.getSenha())
                    .roles(usuario.getPerfil()).disabled(!usuario.isAtivo()).build();
        };
    }

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/css/**", "/js/**", "/login").permitAll()
                // Lista de responsáveis ativos usada por qualquer usuário ao abrir OS e movimentar estoque.
                .requestMatchers(HttpMethod.GET, "/api/funcionarios/ativos").authenticated()
                // Gestão de usuários e relatórios são exclusivos do administrador.
                .requestMatchers("/funcionarios", "/api/funcionarios/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/relatorios", "/api/relatorios/**").hasRole("ADMINISTRADOR")
                // Catálogo e preços de venda são definidos pelo administrador.
                .requestMatchers(HttpMethod.POST, "/api/produtos/**", "/api/servicos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/produtos/**", "/api/servicos/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
            .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
            .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                request -> request.getRequestURI().startsWith("/api/"))
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
                    request -> !request.getRequestURI().startsWith("/api/")));
        // O CSRF permanece ativo, inclusive nas chamadas feitas pelo JavaScript.
        return http.build();
    }
}
