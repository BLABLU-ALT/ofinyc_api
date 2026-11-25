package br.edu.ifsul.oficynic.config;

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

import br.edu.ifsul.oficynic.model.Usuario;
import br.edu.ifsul.oficynic.repository.UsuarioRepository;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // Conta tecnica APP_USER vira o administrador inicial
    // Demais usuarios vem da tabela usuarios e inativos nao entram
    @Bean
    UserDetailsService usuarios(
            UsuarioRepository repository,
            PasswordEncoder encoder,
            @Value("${APP_USER:admin}") String loginTecnico,
            @Value("${APP_PASSWORD}") String senhaTecnica) {

        String senhaTecnicaCodificada = encoder.encode(senhaTecnica);

        return login -> {
            if (login.equals(loginTecnico)) {
                return User.withUsername(loginTecnico)
                        .password(senhaTecnicaCodificada)
                        .roles("ADMINISTRADOR")
                        .build();
            }

            Usuario usuario = repository.findByLogin(login.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));

            return User.withUsername(usuario.getLogin())
                    .password(usuario.getSenha())
                    .roles(usuario.getPerfil())
                    .disabled(!usuario.isAtivo())
                    .build();
        };
    }

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/css/**", "/js/**", "/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/usuarios/ativos").authenticated()
                .requestMatchers("/funcionarios", "/api/usuarios/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/relatorios", "/api/relatorios/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/produtos/**", "/api/servicos/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/produtos/**", "/api/servicos/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(erros -> erros
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                request -> !request.getRequestURI().startsWith("/api/")));

        return http.build();
    }
}
