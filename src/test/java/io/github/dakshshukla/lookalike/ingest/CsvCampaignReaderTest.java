package io.github.dakshshukla.lookalike.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.dakshshukla.lookalike.domain.Campaign;

class CsvCampaignReaderTest {

    private static final String HEADER =
            "campaign_id,name,category,audience,channel,objective,budget_usd,duration_days,impressions,ctr,roas";

    @Test
    void readsRowsAndLowercasesTheFilterColumns() throws IOException {
        String csv = HEADER + "\nC-0001,Summer beauty push,Beauty,Young-Adults,Video,Awareness,194000,7,5311005,0.0277,5.42\n";

        List<Campaign> campaigns = CsvCampaignReader.read(new StringReader(csv));

        assertThat(campaigns).hasSize(1);
        Campaign c = campaigns.get(0);
        assertThat(c.id()).isEqualTo("C-0001");
        assertThat(c.category()).isEqualTo("beauty");
        assertThat(c.audience()).isEqualTo("young-adults");
        assertThat(c.budgetUsd()).isEqualTo(194000);
        assertThat(c.impressions()).isEqualTo(5_311_005L);
        assertThat(c.roas()).isEqualTo(5.42);
        assertThat(c.description()).isEmpty();
    }

    @Test
    void readsTheOptionalDescriptionColumn() throws IOException {
        String csv = HEADER + ",description\nC-0002,Name,pet,families,email,retention,1000,7,100,0.01,2.0,\"Treats, with a comma\"\n";

        assertThat(CsvCampaignReader.read(new StringReader(csv)).get(0).description()).isEqualTo("Treats, with a comma");
    }

    @Test
    void namesTheLineWhenANumberIsBroken() {
        String csv = HEADER + "\nC-0003,Name,pet,families,email,retention,lots,7,100,0.01,2.0\n";

        assertThatThrownBy(() -> CsvCampaignReader.read(new StringReader(csv)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 1");
    }

    @Test
    void namesTheColumnWhenAValueIsMissing() {
        String csv = HEADER + "\nC-0004,Name,,families,email,retention,10,7,100,0.01,2.0\n";

        assertThatThrownBy(() -> CsvCampaignReader.read(new StringReader(csv)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }
}
