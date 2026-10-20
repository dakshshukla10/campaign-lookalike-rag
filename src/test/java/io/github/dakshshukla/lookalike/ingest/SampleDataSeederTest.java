package io.github.dakshshukla.lookalike.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import io.github.dakshshukla.lookalike.domain.Campaign;

class SampleDataSeederTest {

    private final CampaignRepository repository = mock(CampaignRepository.class);
    private final CampaignIngestService ingest = mock(CampaignIngestService.class);

    @Test
    void loadsTheBundledCsvIntoAnEmptyTable() throws Exception {
        when(repository.count()).thenReturn(0);

        new SampleDataSeeder(repository, ingest, "data/sample_campaigns.csv").run(new DefaultApplicationArguments());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Campaign>> loaded = ArgumentCaptor.forClass(List.class);
        verify(ingest).ingest(loaded.capture());
        assertThat(loaded.getValue()).hasSize(120);
    }

    @Test
    void leavesAnExistingTableAlone() throws Exception {
        when(repository.count()).thenReturn(120);

        new SampleDataSeeder(repository, ingest, "data/sample_campaigns.csv").run(new DefaultApplicationArguments());

        verify(ingest, never()).ingest(anyList());
    }
}
