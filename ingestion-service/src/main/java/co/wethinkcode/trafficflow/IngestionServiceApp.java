package co.wethinkcode.trafficflow;

import com.opencsv.CSVReader;
import io.javalin.Javalin;

import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IngestionServiceApp {

    private static final List<String> PLACEHOLDERS = List.of("", "n/a", "tbd", "unknown", "-", "nan");

    public static void main(String[] args) throws IOException, com.opencsv.exceptions.CsvException {
        List<IntersectionRecord> intersections = loadAndCleanIntersections("src/main/resources/intersections-legacy.csv");

        Javalin app = Javalin.create().start(7020);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /intersections -> cleaned intersection records, for intersection-service to consume
        app.get("/intersections", ctx -> ctx.json(intersections));
    }

    /**
     * Reads intersections-legacy.csv, cleans each row, and merges duplicate
     * intersections (same id, different casing/values) into a single record.
     */
    static List<IntersectionRecord> loadAndCleanIntersections(String csvPath) throws IOException, com.opencsv.exceptions.CsvException {
        // Keyed by normalized id so duplicates collapse into one entry.
        Map<String, IntersectionRecord> byId = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(csvPath))) {
            List<String[]> rows = reader.readAll();

            // First row is the header (intersection_id, District, signal_type, active_flag) - skip it.
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                IntersectionRecord cleaned = cleanRow(row);

                IntersectionRecord existing = byId.get(cleaned.id);
                if (existing == null) {
                    byId.put(cleaned.id, cleaned);
                } else {
                    byId.put(cleaned.id, mergeDuplicate(existing, cleaned));
                }
            }
        }

        return byId.values().stream().toList();
    }

    /** Cleans a single raw CSV row into an IntersectionRecord. */
    static IntersectionRecord cleanRow(String[] row) {
        String rawId = row[0];
        String rawDistrict = row[1];
        String rawSignalType = row[2];
        String rawActiveFlag = row[3];

        String id = rawId.trim().toUpperCase();

        String districtTrimmed = rawDistrict.trim();
        String district;
        String districtNote = null;
        if (isPlaceholder(districtTrimmed)) {
            district = null;
            districtNote = "district was missing/placeholder ('" + districtTrimmed + "')";
        } else {
            district = titleCase(districtTrimmed);
        }

        String signalTrimmed = rawSignalType.trim();
        String signalType;
        String signalNote = null;
        if (isPlaceholder(signalTrimmed)) {
            signalType = null;
            signalNote = "signalType was missing/placeholder ('" + signalTrimmed + "')";
        } else {
            signalType = signalTrimmed.toLowerCase().replaceAll("\\s+", "-");
        }

        String activeTrimmed = rawActiveFlag.trim();
        Boolean active = parseBoolean(activeTrimmed);
        String activeNote = null;
        if (active == null) {
            activeNote = "active flag was unrecognized ('" + activeTrimmed + "')";
        }

        String note = combineNotes(combineNotes(districtNote, signalNote), activeNote);

        return new IntersectionRecord(id, district, signalType, active, note);
    }

    /** Maps common boolean representations to true/false; returns null if unrecognized (including placeholders). */
    private static Boolean parseBoolean(String value) {
        String v = value.toLowerCase();
        if (v.equals("y") || v.equals("yes") || v.equals("true") || v.equals("1")) {
            return true;
        }
        if (v.equals("n") || v.equals("no") || v.equals("false") || v.equals("0")) {
            return false;
        }
        return null; // includes placeholders like "unknown", "n/a", blank, etc.
    }

    private static boolean isPlaceholder(String value) {
        return PLACEHOLDERS.contains(value.toLowerCase());
    }

    /**
     * Merges two records that share an id: prefer whichever value is already
     * known (non-null) on each field, and record that a merge happened.
     */
    private static IntersectionRecord mergeDuplicate(IntersectionRecord first, IntersectionRecord second) {
        String district = first.district != null ? first.district : second.district;
        String signalType = first.signalType != null ? first.signalType : second.signalType;
        Boolean active = first.active != null ? first.active : second.active;

        String mergeNote = "merged duplicate record for " + first.id;
        String note = combineNotes(mergeNote, combineNotes(first.notes, second.notes));

        return new IntersectionRecord(first.id, district, signalType, active, note);
    }

    private static String combineNotes(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + "; " + b;
    }

    private static String titleCase(String value) {
        String[] words = value.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Cleaned intersection record - shape matches the worked example in the README. */
    static class IntersectionRecord {
        public String id;
        public String district;
        public String signalType;
        public Boolean active;
        public String notes;

        public IntersectionRecord(String id, String district, String signalType, Boolean active, String notes) {
            this.id = id;
            this.district = district;
            this.signalType = signalType;
            this.active = active;
            this.notes = notes;
        }
    }
}