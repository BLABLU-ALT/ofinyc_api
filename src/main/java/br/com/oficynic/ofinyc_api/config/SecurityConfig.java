package br.com.oficynic.ofinyc_api.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {
    // Acesso técnico para estudar os cadastros. Não substitui o futuro cadastro de funcionários.
    @Bean
    UserDetailsService acessoLocal(@Value("${APP_USER:admin}") String usuario,
                                  @Value("${APP_PASSWORD}") String senha) {
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(User.withUsername(usuario)
                .password("{bcrypt}" + encoder.encode(senha)).roles("USER").build());
    }

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/css/**", "/js/**", "/login").permitAll()
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
