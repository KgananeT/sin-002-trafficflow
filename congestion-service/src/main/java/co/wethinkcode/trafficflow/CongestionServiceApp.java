package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CongestionServiceApp {

    // 0 = free-flowing, 8 = gridlock. Starts at 0 (normal conditions).
    private static final AtomicInteger level = new AtomicInteger(0);

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7022);

        app.get("/health", ctx -> ctx.result("OK"));

        // GET /congestion -> current Congestion Level
        app.get("/congestion", ctx -> ctx.json(Map.of("level", level.get())));

        // PUT /congestion -> set a new Congestion Level, body: {"level": 0-8}
        app.put("/congestion", ctx -> {
            LevelUpdate update = ctx.bodyAsClass(LevelUpdate.class);

            if (update.level == null || update.level < 0 || update.level > 8) {
                ctx.status(HttpStatus.BAD_REQUEST)
                        .json(Map.of("error", "level must be an integer between 0 and 8"));
                return;
            }

            level.set(update.level);
            ctx.json(Map.of("level", level.get()));
        });
    }

    static class LevelUpdate {
        public Integer level;

        public LevelUpdate() {
            // needed for Jackson deserialization
        }
    }
}