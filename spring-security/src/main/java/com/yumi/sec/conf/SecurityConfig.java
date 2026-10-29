package com.yumi.sec.conf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class SecurityConfig {


    @Bean
    public Customizer<HttpSecurity> httpSecurityCustomizer() {
        return http -> http
                .webAuthn(a -> a.allowedOrigins("http://localhost:8080/")
                        .rpName("yumicom")
                        .rpId("localhost")
                )
                .oneTimeTokenLogin(ott ->
                        ott.tokenGenerationSuccessHandler((request, response, oneTimeToken) -> {
                            response.getWriter().println("you've got console mail!");
                            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                            IO.println("please go to http://localhost:8080/login/ott?token=" +
                                    oneTimeToken.getTokenValue());
                        }));
    }


    @Bean
    public InMemoryUserDetailsManager userDetailsManager() {
        var user1 = User.withUsername("yumi")
                .password("{noop}yumi123")
                .roles("USER")
                .build();

        var user2 = User.withUsername("admin")
                .password("{noop}admin123")
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user1, user2);
    }
}
