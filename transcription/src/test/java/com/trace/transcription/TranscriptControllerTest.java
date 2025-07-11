package com.trace.transcription;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trace.transcription.model.TranscriptEntity;
import com.trace.transcription.dto.TranscriptSegment;
import com.trace.transcription.repository.TranscriptRepository;
import com.trace.transcription.service.TranscriptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.concurrent.Executor;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class TranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TranscriptService transcriptService;

    @Autowired
    private TranscriptRepository transcriptRepository;

    @MockitoBean
    private Executor executor;

    // Test POST /projects/{projectId}/receive with missing file parameter
    @Test
    public void testReceiveMedia_MissingFile() throws Exception {
        UUID projectId = UUID.randomUUID();

        // Create an *empty* file part named "file"
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "",
                "application/octet-stream",
                new byte[0]
        );

        mockMvc.perform(multipart("/projects/{projectId}/transcripts", projectId)
                        .file(emptyFile)
                        .param("speakerAmount", "1"))
                .andExpect(status().isBadRequest());
    }

    // Test POST /projects/{projectId}/receive with invalid speakerAmount (e.g., 0)
    @Test
    public void testReceiveMedia_InvalidSpeakerAmount() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "dummy".getBytes());

        // Invalid speakerAmount should return 400 Bad Request immediately
        mockMvc.perform(multipart("/projects/{projectId}/transcripts", projectId)
                        .file(file)
                        .param("speakerAmount", "0"))
                .andExpect(status().isBadRequest());
    }

    // Test POST /projects/{projectId}/transcripts with valid parameters
    @Test
    public void testReceiveMedia_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "dummy".getBytes());

        // Mock createLoadingEntity to return a TranscriptEntity with known ID
        UUID transcriptId = UUID.randomUUID();
        when(transcriptService.createLoadingEntity(eq(projectId), any(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    // return a minimal entity with ID for test
                    com.trace.transcription.model.TranscriptEntity e = new com.trace.transcription.model.TranscriptEntity();
                    e.setId(transcriptId);
                    return e;
                });

        MvcResult mvcResult = mockMvc.perform(multipart("/projects/{projectId}/transcripts", projectId)
                        .file(file)
                        .param("speakerAmount", "2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transcriptId").value(transcriptId.toString()))
                .andExpect(jsonPath("$.isLoading").value(true));
    }

    // Test GET /projects/{projectId}/transcripts when no transcripts exist
    @Test
    public void testGetAllTranscripts_NoContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(get("/projects/{projectId}/transcripts", projectId))
                .andExpect(status().isNoContent());
    }

    // Test GET /projects/{projectId}/transcripts with existing transcripts
    @Test
    public void testGetAllTranscripts_WithData() throws Exception {
        UUID projectId = UUID.randomUUID();

        // Build one segment
        List<TranscriptSegment> segments = Collections.singletonList(
                new TranscriptSegment("0", "Hello world", "0", "5", "spk1", "Speaker1")
        );

        // Create entity with known data
        TranscriptEntity entity = new TranscriptEntity(null, segments, projectId, null, "wav", System.currentTimeMillis(), false);

        when(transcriptService.getAllTranscripts(eq(projectId)))
                .thenReturn(Collections.singletonList(entity));

        mockMvc.perform(get("/projects/{projectId}/transcripts", projectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].content", hasSize(1)))
                .andExpect(jsonPath("$[0].content[0].text").value("Hello world"))
                .andExpect(jsonPath("$[0].content[0].userName").value("spk1"));
    }

}
