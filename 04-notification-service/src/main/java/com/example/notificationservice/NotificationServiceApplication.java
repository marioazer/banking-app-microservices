package com.example.notificationservice;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
@EnableRetry
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}

	/**
	 * Injected system clock so time-dependent beans (e.g. DailyBalanceSummaryJob) can be
	 * driven by a fixed/controlled Clock in tests instead of the real wall clock.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
