package com.jtdev.authhooker;

import org.springframework.boot.SpringApplication;

public class TestAuthHookerApplication {

    public static void main(String[] args) {
        SpringApplication.from(AuthHookerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
