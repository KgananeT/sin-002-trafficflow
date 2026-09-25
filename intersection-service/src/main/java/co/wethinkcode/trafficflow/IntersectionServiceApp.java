package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IntersectionServiceApp {

    private static final String INGESTION_URL = "http://localhost:7020/intersections";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // In-memory store of intersections, keyed by id, populated from ingestion-service at startup.
    private static final Map<String, IntersectionRecord> intersectionsById = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        loadIntersectionsFromIngestion();

        Javalin app = Javalin.create().start(7021);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /intersections -> all cleaned intersection records
        app.get("/intersections", ctx -> ctx.json(List.copyOf(intersectionsById.values())));

        // GET /intersections/{id} -> a single intersection, 404 if unknown
        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id").trim().toUpperCase();
            IntersectionRecord intersection = intersectionsById.get(id);
            if (intersection == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "no intersection found for id " + id));
            } else {
                ctx.json(intersection);
            }
        });
    }

    /**
     * Fetches the cleaned intersection list from ingestion-service on startup.
     * Retries a few times with a short delay, since ingestion-service may not
     * be up yet when intersection-service starts.
     */
    private static void loadIntersectionsFromIngestion() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(INGESTION_URL)).GET().build();

        int attempts = 5;
        for (int i = 1; i <= attempts; i++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    List<IntersectionRecord> intersections = MAPPER.readValue(response.body(), new com.fasterxml.jackson.core.type.TypeReference<List<IntersectionRecord>>() {});
                    for (IntersectionRecord intersection : intersections) {
                        intersectionsById.put(intersection.id, intersection);
                    }
                    System.out.println("Loaded " + intersectionsById.size() + " intersections from ingestion-service");
                    return;
                }
                System.err.println("ingestion-service returned status " + response.statusCode());
            } catch (Exception e) {
                System.err.println("Attempt " + i + "/" + attempts + ": could not reach ingestion-service (" + e.getMessage() + ")");
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        System.err.println("WARNING: could not load intersections from ingestion-service after " + attempts + " attempts. Starting with an empty list.");
    }

    /** Mirrors the shape produced by ingestion-service's /intersections endpoint. */
    static class IntersectionRecord {
        public String id;
        public String district;
        public String signalType;
        public Boolean active;
        public String notes;

        public IntersectionRecord() {
            // needed for Jackson deserialization
        }
    }
}