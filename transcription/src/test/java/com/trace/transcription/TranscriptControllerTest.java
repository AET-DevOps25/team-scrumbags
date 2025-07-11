// TranscriptControllerTest.java
package com.trace.transcription;

import com.trace.transcription.controller.TranscriptController;
import com.trace.transcription.model.TranscriptEntity;
import com.trace.transcription.dto.TranscriptSegment;
import com.trace.transcription.dto.LoadingResponse;
import com.trace.transcription.service.TranscriptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

@WebMvcTest(controllers = TranscriptController.class)
public class TranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranscriptService transcriptService;

    @MockitoBean
    private Executor executor;

    // Missing file part should be treated as bad request
    @Test
    public void testReceiveMedia_MissingFile() throws Exception {
        UUID projectId = UUID.randomUUID();

        mockMvc.perform(multipart("/projects/{projectId}/transcripts", projectId)
                        // no file attached
                        .param("speakerAmount", "1"))
                .andExpect(status().isBadRequest());
    }

    // Invalid speakerAmount should return 400 Bad Request
    @Test
    public void testReceiveMedia_InvalidSpeakerAmount() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "dummy".getBytes());

        mockMvc.perform(multipart("/projects/{projectId}/transcripts", projectId)
                        .file(file)
                        .param("speakerAmount", "0"))
                .andExpect(status().isBadRequest());
    }

    // Valid request returns 202 and LoadingResponse JSON
    @Test
    public void testReceiveMedia_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "dummy".getBytes());

        UUID transcriptId = UUID.randomUUID();
        when(transcriptService.createLoadingEntity(eq(projectId), any(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    TranscriptEntity e = new TranscriptEntity();
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

    // No transcripts => 204 No Content
    @Test
    public void testGetAllTranscripts_NoContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(transcriptService.getAllTranscripts(eq(projectId)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/projects/{projectId}/transcripts", projectId))
                .andExpect(status().isNoContent());
    }

    // Existing transcripts => 200 OK with JSON array
    @Test
    public void testGetAllTranscripts_WithData() throws Exception {
        UUID projectId = UUID.randomUUID();
        List<TranscriptSegment> segments = Collections.singletonList(
                new TranscriptSegment("0", "Hello world", "0", "5", "spk1", "Speaker1")
        );
        TranscriptEntity entity = new TranscriptEntity(null, segments, projectId, null, "wav", System.currentTimeMillis(), false);

        when(transcriptService.getAllTranscripts(eq(projectId)))
                .thenReturn(Collections.singletonList(entity));

        mockMvc.perform(get("/projects/{projectId}/transcripts", projectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].content", hasSize(1)))
                .andExpect(jsonPath("$[0].content[0].text").value("Hello world"))
                .andExpect(jsonPath("$[0].content[0].userName").value("spk1"));
    }
}
