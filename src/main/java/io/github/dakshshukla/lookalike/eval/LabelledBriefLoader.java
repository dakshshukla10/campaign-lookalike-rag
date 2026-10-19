package io.github.dakshshukla.lookalike.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads {@code data/eval_briefs.json}. */
public final class LabelledBriefLoader {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LabelledBriefLoader() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Wrapper(List<LabelledBrief> briefs) {
    }

    public static List<LabelledBrief> load(Path file) throws IOException {
        Wrapper wrapper = JSON.readValue(file.toFile(), Wrapper.class);
        if (wrapper.briefs() == null || wrapper.briefs().isEmpty()) {
            throw new IOException("No briefs found in " + file);
        }
        for (LabelledBrief brief : wrapper.briefs()) {
            if (brief.expected().isEmpty()) {
                throw new IOException("Brief " + brief.id() + " has no expected campaign ids");
            }
        }
        return wrapper.briefs();
    }
}
