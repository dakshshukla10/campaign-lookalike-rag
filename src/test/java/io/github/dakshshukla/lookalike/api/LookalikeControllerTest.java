package io.github.dakshshukla.lookalike.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.rerank.NoopRecommendationRanker;
import io.github.dakshshukla.lookalike.rerank.PassthroughReranker;
import io.github.dakshshukla.lookalike.retrieval.EmptyCampaignRetriever;

@WebMvcTest(LookalikeController.class)
@Import({
    LookalikeService.class,
    EmptyCampaignRetriever.class,
    PassthroughReranker.class,
    NoopRecommendationRanker.class,
    GroundingValidator.class
})
class LookalikeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsStubResponseWithFinalShape() throws Exception {
        mvc.perform(post("/v1/lookalikes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "Back-to-school promo for parents", "category": "grocery",
                                 "channel": "display", "objective": "conversion", "budgetUsd": 50000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stub"))
                .andExpect(jsonPath("$.retrievedCount").value(0))
                .andExpect(jsonPath("$.recommendations").isEmpty())
                .andExpect(jsonPath("$.grounding.citationsChecked").value(0))
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
}
