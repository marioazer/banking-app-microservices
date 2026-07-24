package com.example.auditservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuditServiceApplicationTests {

	// default spring boot smoke test, confirms the audit service context wires up cleanly
	// the log output for this test is actually referenced by AuditServiceTestSuite's class
	// javadoc as proof the kafka listener subscribed to the profile-events topic correctly
	@Test
	void contextLoads() {
	}

}
