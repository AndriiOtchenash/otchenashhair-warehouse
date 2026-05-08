package com.hairmony.warehouse;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires live PostgreSQL — run manually with -Dspring.profiles.active=dev")
class WarehouseApplicationTests {

	@Test
	void contextLoads() {
	}

}
