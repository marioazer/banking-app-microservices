package com.example.accountservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AccountServiceApplication {

	// nothing account specific happens here at all, the real logic is all in the controller,
	// service, and repository classes, this class only exists to give spring boot somewhere to start
	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

}
