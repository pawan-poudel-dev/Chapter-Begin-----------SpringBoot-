package com.aidesktopagent.music;

import com.aidesktopagent.browser.BrowserController;
import com.aidesktopagent.browser.BrowserException;
import com.aidesktopagent.logging.LoggingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Spotify integration: Web API when authenticated, otherwise safe browser fallbacks.
 *
 * <p><strong>Requirements for API playback:</strong>
 * <ul>
 *   <li>Spotify Developer app (Client ID / Secret)</li>
 *   <li>Authorization Code flow → refresh token with scopes
 *       {@code user-modify-playback-state user-read-playback-state}</li>
 *   <li>Spotify Premium</li>
 *   <li>An active Spotify Connect device</li>
 * </ul>
 * Passwords are never accepted or stored. Tokens only from environment variables.
 */
public final class SpotifyController {

    private static final String API = "https://api.spotify.com/v1";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";

    private final BrowserController browser;
    private final MediaSessionContext session;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final LoggingService log;
    private final Duration timeout;

    /** Optional injectable token for tests. */
    private final String testAccessToken;

    public SpotifyController(BrowserController browser, MediaSessionContext session) {
        this(browser, session,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(),
                LoggingService.forComponent("Spotify"),
                Duration.ofSeconds(20),
                null);
    }

    public SpotifyController(BrowserController browser,
                             MediaSessionContext session,
                             HttpClient http,
                             ObjectMapper mapper,
                             LoggingService log,
                             Duration timeout,
                             String testAccessToken) {
        this.browser = Objects.requireNonNull(browser);
        this.session = session != null ? session : new MediaSessionContext();
        this.http = http;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.log = log != null ? log : LoggingService.forComponent("Spotify");
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(20);
        this.testAccessToken = testAccessToken;
    }

    public MediaStatus openAppOrWeb() {
        try {
            browser.openUrl("https://open.spotify.com");
            session.rememberSpotify("home");
            return MediaStatus.opened(MediaStatus.Source.SPOTIFY,
                    "Opened Spotify Web Player. Sign in if prompted.");
        } catch (BrowserException e) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, e.getMessage());
        }
    }

    public MediaStatus search(String query) {
        if (query == null || query.isBlank()) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, "Missing search query");
        }
        try {
            String q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            browser.openUrl("https://open.spotify.com/search/" + q);
            session.rememberSpotify(query.trim());
            return MediaStatus.opened(MediaStatus.Source.SPOTIFY,
                    "Opened Spotify search for \"" + query.trim() + "\".");
        } catch (BrowserException e) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, e.getMessage());
        }
    }

    /**
     * Try Web API play; fall back to opening search in browser.
     */
    public MediaStatus play(String queryOrUri) {
        if (queryOrUri == null || queryOrUri.isBlank()) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, "Missing track, playlist, or query");
        }
        String target = queryOrUri.trim();
        session.rememberSpotify(target);

        Optional<String> token = resolveAccessToken();
        if (token.isEmpty()) {
            MediaStatus opened = search(target);
            return MediaStatus.needsAuth(MediaStatus.Source.SPOTIFY,
                    "Spotify is not authenticated for playback control. "
                            + opened.getMessage()
                            + " Set SPOTIFY_ACCESS_TOKEN (Premium + active device) for direct play. "
                            + "See docs: Authorization Code flow with user-modify-playback-state.");
        }

        try {
            if (target.startsWith("spotify:")) {
                return apiPlayContext(token.get(), target);
            }
            // Search via API then play first track
            Optional<String> trackUri = apiSearchFirstTrack(token.get(), target);
            if (trackUri.isPresent()) {
                return apiPlayUri(token.get(), trackUri.get());
            }
            return search(target);
        } catch (SpotifyApiException e) {
            return mapApiError(e, target);
        }
    }

    public MediaStatus pause() {
        return transport("PUT", "/me/player/pause", "Paused Spotify playback.");
    }

    public MediaStatus resume() {
        return transport("PUT", "/me/player/play", "Resumed Spotify playback.");
    }

    public MediaStatus next() {
        return transport("POST", "/me/player/next", "Skipped to next track.");
    }

    public MediaStatus previous() {
        return transport("POST", "/me/player/previous", "Went to previous track.");
    }

    public MediaStatus status() {
        Optional<String> token = resolveAccessToken();
        if (token.isEmpty()) {
            return MediaStatus.needsAuth(MediaStatus.Source.SPOTIFY,
                    "No Spotify token configured — cannot read playback status.");
        }
        try {
            HttpRequest req = authorized(token.get(), "GET", "/me/player");
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204) {
                return new MediaStatus(MediaStatus.Outcome.SUCCESS, MediaStatus.Source.SPOTIFY,
                        "No active Spotify playback.", null, false);
            }
            if (res.statusCode() == 401) {
                return MediaStatus.needsAuth(MediaStatus.Source.SPOTIFY, "Spotify token invalid or expired.");
            }
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return MediaStatus.failure(MediaStatus.Source.SPOTIFY,
                        "Status HTTP " + res.statusCode());
            }
            JsonNode root = mapper.readTree(res.body());
            boolean playing = root.path("is_playing").asBoolean(false);
            String name = root.path("item").path("name").asText("unknown");
            String msg = playing ? "Playing: " + name : "Paused: " + name;
            return new MediaStatus(MediaStatus.Outcome.SUCCESS, MediaStatus.Source.SPOTIFY,
                    msg, null, playing);
        } catch (Exception e) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private MediaStatus transport(String method, String path, String successMsg) {
        Optional<String> token = resolveAccessToken();
        if (token.isEmpty()) {
            return MediaStatus.needsAuth(MediaStatus.Source.SPOTIFY,
                    "Spotify is not authenticated. Configure SPOTIFY_ACCESS_TOKEN "
                            + "(Premium account + active device required for playback control).");
        }
        try {
            HttpRequest req = authorized(token.get(), method, path);
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204 || res.statusCode() == 200) {
                log.info("Spotify transport OK: " + path);
                return MediaStatus.success(MediaStatus.Source.SPOTIFY, successMsg);
            }
            if (res.statusCode() == 401) {
                return MediaStatus.needsAuth(MediaStatus.Source.SPOTIFY, "Spotify token invalid or expired.");
            }
            if (res.statusCode() == 404) {
                return MediaStatus.noDevice(MediaStatus.Source.SPOTIFY,
                        "No active Spotify device. Open Spotify on a phone/desktop/web player first.");
            }
            if (res.statusCode() == 403) {
                return MediaStatus.failure(MediaStatus.Source.SPOTIFY,
                        "Forbidden — Premium may be required, or missing scopes.");
            }
            if (res.statusCode() == 429) {
                return MediaStatus.failure(MediaStatus.Source.SPOTIFY, "Spotify rate limit exceeded. Try later.");
            }
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY,
                    "Spotify HTTP " + res.statusCode());
        } catch (Exception e) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, e.getMessage());
        }
    }

    private MediaStatus apiPlayUri(String token, String trackUri) throws SpotifyApiException {
        try {
            String body = "{\"uris\":[\"" + trackUri + "\"]}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API + "/me/player/play"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204 || res.statusCode() == 200) {
                return MediaStatus.success(MediaStatus.Source.SPOTIFY, "Playing on Spotify: " + trackUri);
            }
            throw new SpotifyApiException(res.statusCode(), res.body());
        } catch (SpotifyApiException e) {
            throw e;
        } catch (Exception e) {
            throw new SpotifyApiException(0, e.getMessage());
        }
    }

    private MediaStatus apiPlayContext(String token, String contextUri) throws SpotifyApiException {
        try {
            String body = "{\"context_uri\":\"" + contextUri + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API + "/me/player/play"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204 || res.statusCode() == 200) {
                return MediaStatus.success(MediaStatus.Source.SPOTIFY, "Playing Spotify context.");
            }
            throw new SpotifyApiException(res.statusCode(), res.body());
        } catch (SpotifyApiException e) {
            throw e;
        } catch (Exception e) {
            throw new SpotifyApiException(0, e.getMessage());
        }
    }

    private Optional<String> apiSearchFirstTrack(String token, String query) {
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = authorized(token, "GET",
                    "/search?type=track&limit=1&q=" + q);
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode items = mapper.readTree(res.body()).path("tracks").path("items");
            if (items.isArray() && !items.isEmpty()) {
                return Optional.ofNullable(items.get(0).path("uri").asText(null));
            }
        } catch (Exception e) {
            log.warn("Spotify search failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    private HttpRequest authorized(String token, String method, String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(path.startsWith("http") ? path : API + path))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token);
        return switch (method) {
            case "GET" -> b.GET().build();
            case "PUT" -> b.PUT(HttpRequest.BodyPublishers.noBody()).build();
            case "POST" -> b.POST(HttpRequest.BodyPublishers.noBody()).build();
            default -> b.method(method, HttpRequest.BodyPublishers.noBody()).build();
        };
    }

    private Optional<String> resolveAccessToken() {
        if (testAccessToken != null && !testAccessToken.isBlank()) {
            return Optional.of(testAccessToken);
        }
        Optional<String> direct = SpotifyAuthConfig.accessToken();
        if (direct.isPresent()) {
            return direct;
        }
        // Optional refresh (client credentials style for refresh token grant)
        Optional<String> refresh = SpotifyAuthConfig.refreshToken();
        Optional<String> id = SpotifyAuthConfig.clientId();
        Optional<String> secret = SpotifyAuthConfig.clientSecret();
        if (refresh.isEmpty() || id.isEmpty() || secret.isEmpty()) {
            return Optional.empty();
        }
        try {
            String form = "grant_type=refresh_token&refresh_token="
                    + URLEncoder.encode(refresh.get(), StandardCharsets.UTF_8);
            String basic = java.util.Base64.getEncoder().encodeToString(
                    (id.get() + ":" + secret.get()).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(timeout)
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Spotify token refresh failed HTTP " + res.statusCode());
                return Optional.empty();
            }
            String access = mapper.readTree(res.body()).path("access_token").asText(null);
            return Optional.ofNullable(access);
        } catch (Exception e) {
            log.warn("Spotify token refresh error: " + e.getMessage());
            return Optional.empty();
        }
    }

    private MediaStatus mapApiError(SpotifyApiException e, String fallbackQuery) {
        if (e.status == 401) {
            return MediaStatus.needsAuth(MediaStatus.Source.SPOTIFY, "Spotify token invalid or expired.");
        }
        if (e.status == 404) {
            return MediaStatus.noDevice(MediaStatus.Source.SPOTIFY,
                    "No active Spotify device. Open the Spotify app first.");
        }
        if (e.status == 403) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY,
                    "Playback forbidden (Premium/scopes). Opening search instead.");
        }
        if (e.status == 429) {
            return MediaStatus.failure(MediaStatus.Source.SPOTIFY, "Rate limited by Spotify.");
        }
        MediaStatus opened = search(fallbackQuery);
        return MediaStatus.failure(MediaStatus.Source.SPOTIFY,
                "API play failed; " + opened.getMessage());
    }

    static final class SpotifyApiException extends Exception {
        final int status;

        SpotifyApiException(int status, String body) {
            super("HTTP " + status);
            this.status = status;
        }
    }
}
