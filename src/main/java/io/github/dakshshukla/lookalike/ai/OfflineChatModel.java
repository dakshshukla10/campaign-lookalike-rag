package io.github.dakshshukla.lookalike.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dakshshukla.lookalike.rerank.RankingPayload;
import io.github.dakshshukla.lookalike.rerank.RankingPrompt;
import io.github.dakshshukla.lookalike.text.TextAnalyzer;

/**
 * Rule-based stand-in for the ranking model. It reads the same prompt a real model gets, keeps
 * the candidates in the order they were given (the reranker already scored them), and writes
 * the answer as the JSON the structured output converter expects. Every number and id in the
 * answer is copied from a candidate, so it never cites something that was not offered.
 */
public class OfflineChatModel implements ChatModel {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = lastUserText(prompt);
        try {
            String answer = JSON.writeValueAsString(answer(text));
            return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Offline model could not read the ranking prompt", e);
        }
    }

    private static String lastUserText(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() == MessageType.USER) {
                return messages.get(i).getText();
            }
        }
        throw new IllegalArgumentException("Prompt has no user message");
    }

    private static RankingPayload answer(String text) throws JsonProcessingException {
        int limit = Integer.parseInt(lineAfter(text, RankingPrompt.LIMIT_MARKER).strip());
        Map<String, Object> brief = JSON.readValue(
                lineAfter(text, RankingPrompt.BRIEF_MARKER), new TypeReference<Map<String, Object>>() { });
        List<Map<String, Object>> candidates = JSON.readValue(
                lineAfter(text, RankingPrompt.CANDIDATES_MARKER), new TypeReference<List<Map<String, Object>>>() { });

        Set<String> briefTerms = new LinkedHashSet<>(TextAnalyzer.tokens(String.valueOf(brief.get("description"))));
        List<RankingPayload.Item> items = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            if (items.size() == limit) {
                break;
            }
            items.add(item(items.size() + 1, brief, briefTerms, candidate));
        }
        return new RankingPayload(items);
    }

    private static RankingPayload.Item item(
            int rank, Map<String, Object> brief, Set<String> briefTerms, Map<String, Object> c) {
        String id = String.valueOf(c.get("id"));
        Set<String> shared = new LinkedHashSet<>(TextAnalyzer.tokens(c.get("name") + " " + c.get("description")));
        shared.retainAll(briefTerms);

        List<String> matches = new ArrayList<>();
        for (String field : List.of("category", "audience", "channel", "objective")) {
            Object wanted = brief.get(field);
            if (wanted != null && wanted.toString().equalsIgnoreCase(String.valueOf(c.get(field)))) {
                matches.add(field + " " + c.get(field));
            }
        }

        String summary = String.format(Locale.ROOT,
                "%s was a %s %s campaign on %s for %s. It ran %s days on a $%s budget and reached ROAS %s with CTR %.2f%%.",
                c.get("name"), c.get("category"), c.get("objective"), c.get("channel"), c.get("audience"),
                c.get("durationDays"), c.get("budgetUsd"), c.get("roas"), ((Number) c.get("ctr")).doubleValue() * 100);

        List<String> reasonParts = new ArrayList<>();
        if (!shared.isEmpty()) {
            reasonParts.add("shares the terms " + String.join(", ", shared.stream().limit(5).toList()));
        }
        if (!matches.isEmpty()) {
            reasonParts.add("same " + String.join(", ", matches));
        }
        String reason = reasonParts.isEmpty()
                ? "closest remaining candidate by retrieval score " + c.get("score")
                : String.join("; ", reasonParts);

        return new RankingPayload.Item(rank, summary, List.of(new RankingPayload.CitationItem(id, reason)));
    }

    private static String lineAfter(String text, String marker) {
        int start = text.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Prompt is missing " + marker);
        }
        start += marker.length();
        int end = text.indexOf('\n', start);
        return (end < 0 ? text.substring(start) : text.substring(start, end)).strip();
    }
}
