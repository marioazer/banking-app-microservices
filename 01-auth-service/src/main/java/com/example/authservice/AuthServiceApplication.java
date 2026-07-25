package com.example.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication bundles three annotations into one, @Configuration, @EnableAutoConfiguration,
// and @ComponentScan, this single line is what turns on all of spring boot's autoconfiguration magic
@SpringBootApplication
public class AuthServiceApplication {

	// this is the actual java entry point, SpringApplication.run boots up the whole embedded
	// tomcat server and the entire application context before this method even returns
	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
