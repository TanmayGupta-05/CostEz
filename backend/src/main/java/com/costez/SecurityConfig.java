package com.costez;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService users(JdbcTemplate db) {
    return email -> {
      var rows =
          db.queryForList(
              "SELECT * FROM users WHERE email=?", email.trim().toLowerCase(Locale.ROOT));
      if (rows.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
      var u = rows.getFirst();
      return User.withUsername((String) u.get("email"))
          .password((String) u.get("password_hash"))
          .roles((String) u.get("role"))
          .build();
    };
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    var csrf = new CookieCsrfTokenRepository();
    csrf.setCookieHttpOnly(false);
    var handler = new CsrfTokenRequestAttributeHandler();
    handler.setCsrfRequestAttributeName(null);
    return http.csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/auth/**",
                        "/api/pulse/**",
                        "/actuator/health",
                        "/",
                        "/index.html",
                        "/assets/**",
                        "/favicon.ico",
                        "/error")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/auth/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler(
                        (req, res, auth) -> {
                          res.setContentType("application/json");
                          res.getWriter().write("{\"ok\":true}");
                        })
                    .failureHandler(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter()
                              .write("{\"message\":\"Email or password is incorrect.\"}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
                    .deleteCookies("SESSION", "XSRF-TOKEN"))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                    .accessDeniedHandler(
                        (req, res, ex) -> res.sendError(HttpServletResponse.SC_FORBIDDEN)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'"
                                + " 'unsafe-inline'; font-src 'self'; img-src 'self' data:;"
                                + " connect-src 'self'; frame-ancestors 'none'; base-uri 'self';"
                                + " form-action 'self'")))
        .build();
  }
}
