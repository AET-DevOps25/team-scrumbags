package com.trace.comms_connector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.trace.comms_connector.connection.ConnectionRepo;
import com.trace.comms_connector.discord.DiscordRestClient;
import com.trace.comms_connector.user.UserRepo;

@SpringBootTest
class CommsConnectorApplicationTests {

	@MockitoBean
	private UserRepo userRepoMock;

	@MockitoBean
	private ConnectionRepo connectionRepoMock;

	@MockitoBean
	private DiscordRestClient discordClientMock;

	@MockitoBean
	private CommsRestClient commsClientMock;

	@Test
	void contextLoads() {
	}

}
