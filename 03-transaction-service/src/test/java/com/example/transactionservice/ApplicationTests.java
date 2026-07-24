package com.example.transactionservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApplicationTests {

	// default spring boot smoke test, confirms the transaction service context wires up cleanly
	// nothing to mock here, just a sanity check that all the beans construct without errors
	@Test
	void contextLoads() {
	}

}
