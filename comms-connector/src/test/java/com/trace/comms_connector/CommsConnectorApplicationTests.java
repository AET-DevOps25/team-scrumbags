package com.trace.comms_connector;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.comms_connector.connection.ConnectionEntity;
import com.trace.comms_connector.connection.ConnectionRepo;
import com.trace.comms_connector.discord.DiscordRestClient;
import com.trace.comms_connector.user.UserEntity;
import com.trace.comms_connector.user.UserRepo;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
class CommsConnectorApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ConnectionRepo connectionRepo;

	@Autowired
	private UserRepo userRepo;

	@MockitoBean
	private DiscordRestClient discordClientMock;

	@MockitoBean
	private TraceRestClient commsClientMock;

	// Mock it so the thread is not run for no reason
	@MockitoBean
	private CommsThreadRunner commsThreadRunnerMock;

	@BeforeEach
	public void clearRepos() {
		userRepo.deleteAll();
		connectionRepo.deleteAll();
	}

	// Test getting platform users when there are no connections added
	@Test
	public void test_getPlatformUsers_empty() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;

		mockMvc.perform(
			get("/projects/{projectId}/comms/{platform}/users", projectId, platform)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string("[]")
		);
	}

	// Test getting platform users with added connections
	@Test
	public void test_getPlatformUsers() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformUserId1 = "user1";
		String platformUserId2 = "user2";

		UserEntity userEntity1 = new UserEntity(projectId, platformUserId1, platform, null);
		UserEntity userEntity2 = new UserEntity(projectId, platformUserId2, platform, null);
		List<UserEntity> userList = new ArrayList<>();
		userList.add(userEntity1);
		userList.add(userEntity2);

		String userJsonResponse = new ObjectMapper().writeValueAsString(userList);

		userRepo.save(userEntity1);
		userRepo.save(userEntity2);

		mockMvc.perform(
			get("/projects/{projectId}/comms/{platform}/users", projectId, platform)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string(userJsonResponse)
		);
	}

	/* // Test getting all users when there are no connections added
	@Test
	public void test_getAllUsers_empty() throws Exception {
		UUID projectId = UUID.randomUUID();

		mockMvc.perform(
			get("/projects/{projectId}/comms/users", projectId)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string("[]")
		);
	}

	// Test getting all users with added connections
	@Test
	public void test_getAllUsers() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformUserId1 = "user1";
		String platformUserId2 = "user2";

		UserEntity userEntity1 = new UserEntity(projectId, platformUserId1, platform, null);
		UserEntity userEntity2 = new UserEntity(projectId, platformUserId2, platform, null);
		List<UserEntity> userList = new ArrayList<>();
		userList.add(userEntity1);
		userList.add(userEntity2);

		String userJsonResponse = new ObjectMapper().writeValueAsString(userList);

		userRepo.save(userEntity1);
		userRepo.save(userEntity2);

		mockMvc.perform(
			get("/projects/{projectId}/comms/users", projectId)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string(userJsonResponse)
		);
	} */

	// Test adding a connection
	@Test
	public void test_addCommsIntegration() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String serverId = "1";
		String platformChannelId1 = "2";
		String platformChannelId2 = "3";
		String platformUserId1 = "user1";
		String platformUserId2 = "user2";

		ConnectionEntity connectionEntity1 = new ConnectionEntity(projectId, platformChannelId1, platform, "0");
		ConnectionEntity connectionEntity2 = new ConnectionEntity(projectId, platformChannelId2, platform, "0");
		List<ConnectionEntity> connectionList = new ArrayList<>();
		connectionList.add(connectionEntity1);
		connectionList.add(connectionEntity2);

		UserEntity userEntity1 = new UserEntity(projectId, platformUserId1, platform, null);
		UserEntity userEntity2 = new UserEntity(projectId, platformUserId2, platform, null);
		List<UserEntity> userList = new ArrayList<>();
		userList.add(userEntity1);
		userList.add(userEntity2);

		String channelJsonResponse = new ObjectMapper().writeValueAsString(connectionList);

		when(discordClientMock.getGuildChannelIds(serverId)).thenReturn(Arrays.asList(platformChannelId1, platformChannelId2));
		when(discordClientMock.getGuildMemberNames(serverId)).thenReturn(Arrays.asList(platformUserId1, platformUserId2));
		
		mockMvc.perform(
			post("/projects/{projectId}/comms/{platform}", projectId, platform)
				.param("serverId", "1")
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string(channelJsonResponse)
		);

		assertEquals(
			connectionRepo.findAll(),
			connectionList
		);

		assertEquals(
			userRepo.findAll(),
			userList
		);
	}

	// Test adding a connection without specifying server ID, should return bad request
	@Test
	public void test_addCommsIntegration_noServerId() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;

		mockMvc.perform(
			post("/projects/{projectId}/comms/{platform}", projectId, platform)
		).andExpect(
			status().isBadRequest()
		);
	}
	
	// Test saving comms user, should update the existing entry and not create a duplicate one
	@Test
	public void test_addPlatformUser() throws Exception {
		UUID projectId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformUserId = "user";

		UserEntity userEntityBefore = new UserEntity(projectId, platformUserId, platform, null);
		UserEntity userEntityAfter = new UserEntity(projectId, platformUserId, platform, userId);

		String userJsonResponse = new ObjectMapper().writeValueAsString(userEntityAfter);

		userRepo.save(userEntityBefore);

		assertEquals(
			userRepo.findAll(),
			Arrays.asList(userEntityBefore)
		);

		mockMvc.perform(
			post("/projects/{projectId}/comms/{platform}/users", projectId, platform)
				.param("userId", userId.toString())
				.param("platformUserId", platformUserId)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string(userJsonResponse)
		);

		assertEquals(
			userRepo.findAll(),
			Arrays.asList(userEntityAfter)
		);
	}

	// Test add user endpoint with no userId or platformUserId, should return bad request
	@Test
	public void test_addPlatformUser_missingIds() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;

		mockMvc.perform(
			post("/projects/{projectId}/comms/{platform}/users", projectId, platform)
		).andExpectAll(
			status().isBadRequest()
		);
	}

	// Test delete platform connections
	@Test
	public void test_deletePlatformCommIntegrations() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformChannelId1 = "1";
		String platformChannelId2 = "2";

		ConnectionEntity connectionEntity1 = new ConnectionEntity(projectId, platformChannelId1, platform, "0");
		ConnectionEntity connectionEntity2 = new ConnectionEntity(projectId, platformChannelId2, platform, "0");

		connectionRepo.save(connectionEntity1);
		connectionRepo.save(connectionEntity2);

		mockMvc.perform(
			delete("/projects/{projectId}/comms/{platform}", projectId, platform)
		).andExpectAll(
			status().is2xxSuccessful()
		);

		assertTrue(connectionRepo.findAll().isEmpty());
	}

	// Test delete all connections
	@Test
	public void test_deleteAllCommIntegrations() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformChannelId1 = "1";
		String platformChannelId2 = "2";

		ConnectionEntity connectionEntity1 = new ConnectionEntity(projectId, platformChannelId1, platform, "0");
		ConnectionEntity connectionEntity2 = new ConnectionEntity(projectId, platformChannelId2, platform, "0");

		connectionRepo.save(connectionEntity1);
		connectionRepo.save(connectionEntity2);

		mockMvc.perform(
			delete("/projects/{projectId}/comms", projectId)
		).andExpectAll(
			status().is2xxSuccessful()
		);

		assertTrue(connectionRepo.findAll().isEmpty());
	}

	// Test get platform connections with none added
	@Test
	public void test_getPlatformConnections_empty() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;

		mockMvc.perform(
			get("/projects/{projectId}/comms/{platform}", projectId, platform)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string("[]")
		);
	}

	// Test get platform connections
	@Test
	public void test_getPlatformConnections() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformChannelId1 = "1";
		String platformChannelId2 = "2";
		
		ConnectionEntity connectionEntity1 = new ConnectionEntity(projectId, platformChannelId1, platform, "0");
		ConnectionEntity connectionEntity2 = new ConnectionEntity(projectId, platformChannelId2, platform, "0");
		List<ConnectionEntity> connectionList = new ArrayList<>();
		connectionList.add(connectionEntity1);
		connectionList.add(connectionEntity2);

		String channelJsonResponse = new ObjectMapper().writeValueAsString(connectionList);

		connectionRepo.save(connectionEntity1);
		connectionRepo.save(connectionEntity2);

		mockMvc.perform(
			get("/projects/{projectId}/comms/{platform}", projectId, platform)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string(channelJsonResponse)
		);
	}

	// Test get all connections with none added
	@Test
	public void test_getAllConnections_empty() throws Exception {
		UUID projectId = UUID.randomUUID();

		mockMvc.perform(
			get("/projects/{projectId}/comms", projectId)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string("[]")
		);
	}

	// Test get all connections
	@Test
	public void test_getAllConnections() throws Exception {
		UUID projectId = UUID.randomUUID();
		Platform platform = Platform.DISCORD;
		String platformChannelId1 = "1";
		String platformChannelId2 = "2";
		
		ConnectionEntity connectionEntity1 = new ConnectionEntity(projectId, platformChannelId1, platform, "0");
		ConnectionEntity connectionEntity2 = new ConnectionEntity(projectId, platformChannelId2, platform, "0");
		List<ConnectionEntity> connectionList = new ArrayList<>();
		connectionList.add(connectionEntity1);
		connectionList.add(connectionEntity2);

		String channelJsonResponse = new ObjectMapper().writeValueAsString(connectionList);

		connectionRepo.save(connectionEntity1);
		connectionRepo.save(connectionEntity2);

		mockMvc.perform(
			get("/projects/{projectId}/comms", projectId)
		).andExpectAll(
			status().is2xxSuccessful(),
			content().string(channelJsonResponse)
		);
	}

	// Test start thread while not running
	@Test
	public void test_startCommsThread_notRunning() throws Exception {
		CommsThread commsThreadMock = mock(CommsThread.class);

		try (MockedStatic<CommsThread> staticMock = Mockito.mockStatic(CommsThread.class)) {
			staticMock.when(CommsThread::getInstance).thenReturn(commsThreadMock);

			mockMvc.perform(
				post("/comms/thread")
			).andExpectAll(
				status().is2xxSuccessful()
			);
		}
	}

	// Test start thread while running
	@Test
	public void test_startCommsThread_running() throws Exception {
		CommsThread commsThreadMock = mock(CommsThread.class);

		try (MockedStatic<CommsThread> staticMock = Mockito.mockStatic(CommsThread.class)) {
			staticMock.when(CommsThread::getInstance).thenReturn(commsThreadMock);
			doThrow(new RuntimeException()).when(commsThreadMock).startThread();

			mockMvc.perform(
				post("/comms/thread")
			).andExpectAll(
				status().is4xxClientError()
			);
		}
	}

	// Test stop thread while running
	@Test
	public void test_stopCommsThread_running() throws Exception {
		CommsThread commsThreadMock = mock(CommsThread.class);

		try (MockedStatic<CommsThread> staticMock = Mockito.mockStatic(CommsThread.class)) {
			staticMock.when(CommsThread::getInstance).thenReturn(commsThreadMock);

			mockMvc.perform(
				delete("/comms/thread")
			).andExpectAll(
				status().is2xxSuccessful()
			);
		}
	}

	// Test stop thread while not running
	@Test
	public void test_stopCommsThread_notRunning() throws Exception {
		CommsThread commsThreadMock = mock(CommsThread.class);

		try (MockedStatic<CommsThread> staticMock = Mockito.mockStatic(CommsThread.class)) {
			staticMock.when(CommsThread::getInstance).thenReturn(commsThreadMock);
			doThrow(new RuntimeException()).when(commsThreadMock).stopThread();
			
			mockMvc.perform(
				delete("/comms/thread")
			).andExpectAll(
				status().is4xxClientError()
			);
		}
	}
}