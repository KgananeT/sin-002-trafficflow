package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class RoutingServiceApp {

    private static final String INTERSECTION_SERVICE_URL = "http://localhost:7021";
    private static final String CONGESTION_SERVICE_URL = "http://localhost:7022";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7023);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/route/{id}", ctx -> {
            String id = ctx.pathParam("id").trim().toUpperCase();

            IntersectionRecord intersection;
            try {
                intersection = fetchIntersection(id);
            } catch (NotFoundException e) {
                ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", "no intersection found for id " + id));
                return;
            } catch (Exception e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(Map.of("error", "could not reach intersection-service: " + e.getMessage()));
                return;
            }

            int congestionLevel;
            try {
                congestionLevel = fetchCongestionLevel();
            } catch (Exception e) {
                ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(Map.of("error", "could not reach congestion-service: " + e.getMessage()));
                return;
            }

            int estimatedMinutes = computeEstimatedMinutes(congestionLevel);

            ctx.json(Map.of(
                    "intersectionId", intersection.id,
                    "district", intersection.district,
                    "congestionLevel", congestionLevel,
                    "estimatedMinutes", estimatedMinutes
            ));
        });
    }

    // Base 5 min, +2 min per congestion level (0 -> 5 min, 8 -> 21 min)
    private static int computeEstimatedMinutes(int congestionLevel) {
        return 5 + (congestionLevel * 2);
    }

    private static IntersectionRecord fetchIntersection(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(INTERSECTION_SERVICE_URL + "/intersections/" + id)).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) throw new NotFoundException(id);
        if (response.statusCode() != 200) throw new RuntimeException("status " + response.statusCode());
        return MAPPER.readValue(response.body(), IntersectionRecord.class);
    }

    private static int fetchCongestionLevel() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(CONGESTION_SERVICE_URL + "/congestion")).GET().build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new RuntimeException("status " + response.statusCode());
        Map<String, Integer> body = MAPPER.readValue(response.body(), Map.class);
        return body.get("level");
    }

    static class IntersectionRecord {
        public String id;
        public String district;
        public String signalType;
        public Boolean active;
        public String notes;
        public IntersectionRecord() {}
    }

    static class NotFoundException extends Exception {
        NotFoundException(String id) { super("not found: " + id); }
    }
}