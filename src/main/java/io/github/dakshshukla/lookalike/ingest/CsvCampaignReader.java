package io.github.dakshshukla.lookalike.ingest;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import io.github.dakshshukla.lookalike.domain.Campaign;

/**
 * Reads the campaigns CSV described in {@code data/README.md}. The {@code description} column
 * is optional so older files still load.
 */
public final class CsvCampaignReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    private CsvCampaignReader() {
    }

    public static List<Campaign> read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public static List<Campaign> read(Reader reader) throws IOException {
        List<Campaign> campaigns = new ArrayList<>();
        try (CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord row : parser) {
                campaigns.add(toCampaign(row));
            }
        }
        return campaigns;
    }

    private static Campaign toCampaign(CSVRecord row) {
        try {
            return new Campaign(
                    required(row, "campaign_id"),
                    required(row, "name"),
                    required(row, "category").toLowerCase(),
                    required(row, "audience").toLowerCase(),
                    required(row, "channel").toLowerCase(),
                    required(row, "objective").toLowerCase(),
                    Integer.parseInt(required(row, "budget_usd")),
                    Integer.parseInt(required(row, "duration_days")),
                    Long.parseLong(required(row, "impressions")),
                    Double.parseDouble(required(row, "ctr")),
                    Double.parseDouble(required(row, "roas")),
                    row.isMapped("description") ? row.get("description") : "");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad number on CSV line " + row.getRecordNumber() + ": " + e.getMessage(), e);
        }
    }

    private static String required(CSVRecord row, String column) {
        if (!row.isMapped(column) || !row.isSet(column) || row.get(column).isBlank()) {
            throw new IllegalArgumentException("Missing " + column + " on CSV line " + row.getRecordNumber());
        }
        return row.get(column);
    }
}
