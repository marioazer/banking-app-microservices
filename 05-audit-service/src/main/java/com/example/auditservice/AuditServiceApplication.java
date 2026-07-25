package com.example.auditservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditServiceApplication {

	// smallest of the six services by far, no controllers, no security config, this main class
	// is really the whole application entry point, ProfileAuditListener does all the real work
	public static void main(String[] args) {
		SpringApplication.run(AuditServiceApplication.class, args);
	}

}
