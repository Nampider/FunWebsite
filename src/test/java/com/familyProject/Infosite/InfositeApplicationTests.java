package com.familyProject.Infosite;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"chat.users[0].username=chris",
		"chat.users[0].password=test-password-chris",
		"chat.users[1].username=audrey",
		"chat.users[1].password=test-password-audrey"
})
class InfositeApplicationTests {

	@Test
	void contextLoads() {
	}

}
