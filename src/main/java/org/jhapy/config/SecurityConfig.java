package org.jhapy.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@EnableWebSecurity
public class SecurityConfig {

  private Environment env;

  public SecurityConfig(Environment env) {
    this.env = env;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService userDetailsService() throws Exception {
    // ensure the passwords are encoded properly
    UserBuilder users = User.builder().passwordEncoder(s -> passwordEncoder().encode(s));
    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
    manager.createUser(users.username(env.getProperty("spring.security.user.name")).password(
        env.getProperty("spring.security.user.password"))
        .roles(env.getProperty("spring.security.user.roles")).build());
    return manager;
  }

  @Configuration
  @Order(1)
  public static class MonitoringWebSecurityConfigurationAdapter extends
      WebSecurityConfigurerAdapter {

    protected void configure(HttpSecurity http) throws Exception {
      http.antMatcher("/actuator/**")
          .authorizeRequests(authorize ->
              authorize.requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                  .requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole("MONITORING")
                  .anyRequest().permitAll()
          ).httpBasic(Customizer.withDefaults());
      http.cors();
    }
  }

  @Configuration
  public static class ConfigWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http
              .authorizeRequests(authorize -> authorize
                      .anyRequest().authenticated()
              )
              .httpBasic(Customizer.withDefaults());
      http.cors();
    }
  }
}