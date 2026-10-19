package io.github.dakshshukla.lookalike.feedback;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.dakshshukla.lookalike.ingest.CampaignRepository;

@WebMvcTest(OutcomeController.class)
class OutcomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FeedbackStore store;

    @MockitoBean
    private CampaignRepository campaigns;

    @Test
    void recordsAnOutcomeForAKnownCampaign() throws Exception {
        when(campaigns.exists("C-0001")).thenReturn(true);

        mvc.perform(post("/v1/outcomes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campaignId\":\"C-0001\",\"helpful\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignId").value("C-0001"))
                .andExpect(jsonPath("$.helpful").value(false));

        verify(store).record("C-0001", false);
    }

    @Test
    void unknownCampaignIsNotFound() throws Exception {
        when(campaigns.exists("C-9999")).thenReturn(false);

        mvc.perform(post("/v1/outcomes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campaignId\":\"C-9999\",\"helpful\":true}"))
                .andExpect(status().isNotFound());

        verify(store, never()).record("C-9999", true);
    }

    @Test
    void missingFieldsAreABadRequest() throws Exception {
        mvc.perform(post("/v1/outcomes").contentType(MediaType.APPLICATION_JSON).content("{\"campaignId\":\"C-0001\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/outcomes").contentType(MediaType.APPLICATION_JSON).content("{\"helpful\":true}"))
                .andExpect(status().isBadRequest());
    }
}
