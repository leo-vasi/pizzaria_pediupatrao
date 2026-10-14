package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //a autorização acontece em duas camadas, primeiro pelas URLs aqui embaixo e quando fizer sentido, por @PreAuthorize direto nas services

        // CSRF ligado porque o thymeleaf injeta o token nos forms e o layout expõe ele numa <meta> para as chamadas fetch()
        http.authorizeHttpRequests(auth -> auth
                        // Recursos públicos
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Área de usuário só ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole(ADMIN)
                        .requestMatchers("/admin", "/configuracoes").hasRole(ADMIN)

                        // Auditoria ADMIN e GERENTE podem consultar
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole(ADMIN, GERENTE)

                        // Cancelar e dar desconto em pedido é só do GERENTE (nem ADMIN entra aqui)
                        .requestMatchers("/pedidos/*/cancelar", "/pedidos/*/desconto",
                                "/api/pedidos/*/cancelar", "/api/pedidos/*/desconto").hasRole(GERENTE)
                        // Pedidos, aqui todo mundo vê, mas só GERENTE e ATENDENTE registram e movimentam
                        .requestMatchers(HttpMethod.GET, "/pedidos/**", "/api/pedidos/**").hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/pedidos/**", "/api/pedidos/**").hasAnyRole(GERENTE, ATENDENTE)

                        // Clientes, só GERENTE consulta, ATENDENTE cadastra e atualiza
                        .requestMatchers(HttpMethod.GET, "/clientes", "/api/clientes/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)
                        .requestMatchers("/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)

                        // Produtos (cardápio e preços), todo autenticado lê, só GERENTE mexe
                        .requestMatchers(HttpMethod.GET, "/produtos/**", "/api/produtos/**").authenticated()
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasRole(GERENTE)

                        .anyRequest().authenticated()
                )
                // Para chamadas REST, faz mais sentido devolver 401 do que redirecionar pro login
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {

            // Depois do login, cai direto na home
            response.sendRedirect("/");
        };
    }
}