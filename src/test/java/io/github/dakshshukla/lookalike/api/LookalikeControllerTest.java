package io.github.dakshshukla.lookalike.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;

@WebMvcTest(LookalikeController.class)
class LookalikeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LookalikeService service;

    @Test
    void returnsTheServiceResponseInTheFinalShape() throws Exception {
        when(service.lookalikes(any())).thenReturn(new LookalikeResponse(
                "ok",
                2,
                List.of(new Recommendation(1, "Same category and channel", List.of(new Citation("C-0001", "same channel")))),
                new LookalikeResponse.Grounding(1, 1, List.of()),
                List.of()));

        mvc.perform(post("/v1/lookalikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Back-to-school promo for parents", "category": "grocery",
                                 "channel": "display", "objective": "conversion", "budgetUsd": 50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.retrievedCount").value(2))
                .andExpect(jsonPath("$.recommendations[0].citations[0].campaignId").value("C-0001"))
                .andExpect(jsonPath("$.recommendations[0].source").value("MODEL"))
                .andExpect(jsonPath("$.grounding.citationsChecked").value(1))
                .andExpect(jsonPath("$.grounding.rejected").isEmpty())
                .andExpect(jsonPath("$.notes").isArray());
    }

    @Test
    void rejectsBlankDescription() throws Exception {
        mvc.perform(post("/v1/lookalikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFilterValuesWithUnexpectedCharacters() throws Exception {
        mvc.perform(post("/v1/lookalikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"x\", \"category\": \"beauty\\\" || 1==1\"}"))
                .andExpect(status().isBadRequest());
    }
}
