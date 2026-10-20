package io.github.dakshshukla.lookalike.feedback;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.dakshshukla.lookalike.ingest.CampaignRepository;

/** Not registered when {@code lookalike.feedback.endpoint.enabled=false}, as in the demo profile. */
@RestController
@ConditionalOnProperty(name = "lookalike.feedback.endpoint.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/v1")
public class OutcomeController {

    private final FeedbackStore store;
    private final CampaignRepository campaigns;

    public OutcomeController(FeedbackStore store, CampaignRepository campaigns) {
        this.store = store;
        this.campaigns = campaigns;
    }

    @PostMapping("/outcomes")
    @ResponseStatus(HttpStatus.CREATED)
    public OutcomeRequest record(@Valid @RequestBody OutcomeRequest request) {
        if (!campaigns.exists(request.campaignId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown campaign " + request.campaignId());
        }
        store.record(request.campaignId(), request.helpful());
        return request;
    }
}
