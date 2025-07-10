package com.trace.transcription;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = Replace.ANY)
public class TranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranscriptService transcriptService;

    @Autowired
    private TranscriptRepository transcriptRepository;

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

        MvcResult mvc = mockMvc.perform(multipart("/projects/{projectId}/receive", projectId)
                        .file(emptyFile)
                        .param("speakerAmount", "1"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest());
    }

    // Test POST /projects/{projectId}/receive with invalid speakerAmount (e.g., 0)
    @Test
    public void testReceiveMedia_InvalidSpeakerAmount() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "audio.wav", "audio/wav", "dummy".getBytes());

        MvcResult mvc = mockMvc.perform(multipart("/projects/{projectId}/receive", projectId)
                        .file(file)
                        .param("speakerAmount", "0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvc))
                .andExpect(status().isBadRequest());
    }

    // Test POST /projects/{projectId}/receive success path (mocking TranscriptService)
    @Test
    public void testReceiveMedia_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        // Mock the transcriptService.transcriptAsync to return a sample JSON
        String fakeJson = "[{\"metadata\":{\"projectId\":\"" + projectId + "\",\"timestamp\":123456},\"content\":[]}]";
        when(transcriptService.transcriptAsync(eq(projectId), any(), eq(2), anyLong()))
                .thenReturn(fakeJson);

        MockMultipartFile file = new MockMultipartFile(
                "file", "audio.wav", "audio/wav", "dummy".getBytes());
        MvcResult mvcResult = mockMvc.perform(multipart("/projects/{projectId}/receive", projectId)
                        .file(file)
                        .param("speakerAmount", "2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Dispatch the async result
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(fakeJson)));
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
        List<TranscriptSegment> segments =
                Collections.singletonList(
                        new TranscriptSegment(
                                "0", "Hello world", "0", "5", "spk1", "Speaker1"
                        )
                );

        // Pass `null` as the id so Hibernate will generate it
        TranscriptEntity entity = new TranscriptEntity(null, segments, projectId, System.currentTimeMillis());

        // Persist and flush immediately so the INSERT occurs in this transaction
        TranscriptEntity saved = transcriptRepository.save(entity);

        System.out.println("Saved transcript ID: " + saved.getId());
        System.out.println("Saved transcript content: " + saved.getContent());
        System.out.println("Saved transcript projectId: " + saved.getProjectId());
        System.out.println("Saved transcript timestamp: " + saved.getTimestamp());

        mockMvc.perform(get("/projects/{projectId}/transcripts", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].content[0].text").value("Hello world"))
                .andExpect(jsonPath("$[0].content[0].speakerId").value("spk1"));
    }

}
