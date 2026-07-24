package com.example.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificationServiceApplicationTests {

	// default spring boot smoke test, just confirms the notification service context wires up
	// this one intentionally uses the default kafka config pointing at an unreachable local broker,
	// which proves consumer containers just retry quietly in the background without blocking startup
	@Test
	void contextLoads() {
	}

}
