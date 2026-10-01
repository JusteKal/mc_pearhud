package be.justekal.pearhud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Client HTTP pour le plugin "API Server" de Pear Desktop. */
public final class PearApi {
    public enum Status { CONNECTING, WAITING_AUTH, DENIED, OFFLINE, NO_SONG, OK }

    public record Song(String title, String artist, boolean paused,
                       double duration, double elapsed, long fetchedAtMs) {
        public double currentElapsed() {
            double e = elapsed;
            if (!paused) e += (System.currentTimeMillis() - fetchedAtMs) / 1000.0;
            return duration > 0 ? Math.min(e, duration) : e;
        }
    }

    /** Pochette deja decodee et re-encodee en PNG 64x64. */
    public record Cover(String url, byte[] png) {}

    private final PearConfig cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3)).build();
    private String lastError = "";

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pearhud-poller");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService coverExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pearhud-cover");
        t.setDaemon(true);
        return t;
    });

    private volatile Status status = Status.CONNECTING;
    private volatile Song song = null;
    private volatile Cover cover = null;
    private volatile String wantedCoverUrl = "";
    private long nextAuthAt = 0;

    public PearApi(PearConfig cfg) { this.cfg = cfg; }

    public Status status() { return status; }
    public Song song() { return song; }
    public Cover cover() { return cover; }

    public void start() {
        exec.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private String base() { return "http://" + cfg.host + ":" + cfg.port; }

    private void tick() {
        try {
            if (!cfg.enabled) return;
            fetchSong();
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            status = Status.OFFLINE;
            song = null;
        } catch (Exception e) {
            status = Status.OFFLINE;
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (!msg.equals(lastError)) {
                lastError = msg;
                PearHudClient.LOGGER.warn("Pear poll error: {}", msg, e);
            }
        }
    }

    /** POST /auth/{id} : Pear affiche une popup "autoriser" (mode Authorize at first request). */
    private void authenticate() throws Exception {
        if (System.currentTimeMillis() < nextAuthAt) return;
        status = Status.WAITING_AUTH;
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/auth/" + cfg.authId))
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!o.has("accessToken")) { status = Status.DENIED; nextAuthAt = System.currentTimeMillis() + 30_000; return; }
            cfg.accessToken = o.get("accessToken").getAsString();
            cfg.save();
            status = Status.CONNECTING;
        } else {
            status = Status.DENIED;
            nextAuthAt = System.currentTimeMillis() + 30_000;
        }
    }

    /** GET /api/v1/song (avec token seulement s'il y en a un). */
    private void fetchSong() throws Exception {
        boolean hasToken = cfg.accessToken != null && !cfg.accessToken.isBlank();
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base() + "/api/v1/song"))
                .timeout(Duration.ofSeconds(3));
        if (hasToken) rb.header("Authorization", "Bearer " + cfg.accessToken);
        HttpResponse<String> res = http.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        if (code == 200) {
            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            song = new Song(
                    str(o, "title"), str(o, "artist"),
                    o.has("isPaused") && o.get("isPaused").getAsBoolean(),
                    num(o, "songDuration"), num(o, "elapsedSeconds"),
                    System.currentTimeMillis());
            status = Status.OK;
            requestCover(str(o, "imageSrc"));
        } else if (code == 204 || code == 404) {
            song = null;
            cover = null;
            wantedCoverUrl = "";
            status = Status.NO_SONG;
        } else if (code == 401 || code == 403) {
            if (hasToken) { cfg.accessToken = ""; cfg.save(); }
            song = null;
            authenticate();
        } else {
            status = Status.OFFLINE;
        }
    }

    // ---- Pochette ----

    private void requestCover(String url) {
        if (url == null) url = "";
        if (url.equals(wantedCoverUrl)) return; // deja demandee
        wantedCoverUrl = url;
        if (url.isBlank()) { cover = null; return; }
        final String finalUrl = url;
        coverExec.submit(() -> loadCover(finalUrl));
    }

    private void loadCover(String url) {
        try {
            // Demande une version JPEG 128x128 (ImageIO ne lit pas le WebP)
            String u = url.replaceAll("=w\\d+-h\\d+.*$", "=w128-h128-l90-rj");
            HttpRequest req = HttpRequest.newBuilder(URI.create(u))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) return;
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(res.body()));
            if (src == null) return;

            BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, 64, 64, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            if (url.equals(wantedCoverUrl)) cover = new Cover(url, bos.toByteArray());
        } catch (Exception e) {
            PearHudClient.LOGGER.warn("Pochette illisible: {}", e.toString());
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }
}
