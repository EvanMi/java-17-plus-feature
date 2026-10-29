package com.yumi.sec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;

@EnableMultiFactorAuthentication(authorities = {
        FactorGrantedAuthority.PASSWORD_AUTHORITY,
        FactorGrantedAuthority.OTT_AUTHORITY
})
@SpringBootApplication
public class YumiSecApplication {
    void main(String[] args) {
        SpringApplication.run(YumiSecApplication.class, args);
    }
}
