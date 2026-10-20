package io.github.dakshshukla.lookalike.feedback;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.dakshshukla.lookalike.ingest.CampaignRepository;

@WebMvcTest(controllers = OutcomeController.class, properties = "lookalike.feedback.endpoint.enabled=false")
class OutcomeEndpointSwitchTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FeedbackStore store;

    @MockitoBean
    private CampaignRepository campaigns;

    @Test
    void theEndpointIsNotRegisteredWhenSwitchedOff() throws Exception {
        mvc.perform(post("/v1/outcomes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campaignId\":\"C-0001\",\"helpful\":true}"))
                .andExpect(status().is4xxClientError());

        verify(store, never()).record("C-0001", true);
    }
}
