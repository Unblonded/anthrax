package unblonded.anthrax.modules.visual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unblonded.anthrax.modules.FeatureModule;
import unblonded.anthrax.modules.Setting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Now playing and synced lyrics. Reads whatever is playing in Windows' media controls (Spotify, browsers, any
 * player) through a small PowerShell helper, so no sign-in is needed, and fetches synced lyrics from lrclib.net.
 * The HUD draws the card and the lyric line (HudExtras).
 */
public final class MusicModule extends FeatureModule {
    public static final MusicModule INSTANCE = new MusicModule();
    private static final Logger LOG = LoggerFactory.getLogger("Anthrax/Music");

    private final Setting.Bool card = setting(Setting.Bool.standalone("Now playing card", true), "Title, artist and a progress bar on the HUD (move it in HUD Layout).");
    private final Setting.Bool cardLyric = setting(Setting.Bool.standalone("Lyric on the card", true), "Shows the current lyric line on the card.");
    private final Setting.Bool crosshairLyric = setting(Setting.Bool.standalone("Lyric under crosshair", false), "Shows the current lyric line just under your crosshair.");
    private final Setting.Bool fetch = setting(Setting.Bool.standalone("Fetch lyrics online", true), "Looks up synced lyrics on lrclib.net. This sends the song title and artist there.");
    private final Setting.Bool hidePaused = setting(Setting.Bool.standalone("Hide when paused", false), "Hide the card while nothing is playing.");

    public record Line(long ms, String text) {}

    private volatile String title = "", artist = "", status = "";
    private volatile long position, duration, positionAt;
    private volatile List<Line> lyrics = List.of();
    private volatile String lyricsFor = "", lyricsState = "";
    private Process helper;
    private Thread reader;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

    private MusicModule() { super("Music", "Shows the song you are playing and its synced lyrics. Reads Spotify (or any media player) from Windows media controls, so no sign-in is needed", "Visuals", false); }

    @Override protected void onToggle(boolean enabled) {
        if (enabled) start(); else stop();
    }

    private void start() {
        stop();
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) { status = "Windows only"; return; }
        try {
            String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            helper = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            Process proc = helper;
            reader = new Thread(() -> read(proc), "Anthrax music reader");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception e) {
            status = "Could not start the media reader";
            LOG.warn("Music helper failed to start", e);
        }
    }

    /** Ends the helper process on game exit, so no PowerShell is left running. */
    public void shutdown() { if (helper != null) helper.destroy(); }

    private void stop() {
        if (helper != null) { helper.destroy(); helper = null; }
        title = artist = ""; status = ""; lyrics = List.of(); lyricsFor = "";
    }

    private void read(Process proc) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("{")) continue;
                try {
                    JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                    if (!o.has("title")) { title = ""; artist = ""; status = "Nothing playing"; continue; }
                    String t = str(o, "title"), a = str(o, "artist");
                    status = str(o, "status");
                    duration = (long) num(o, "dur");
                    long pos = (long) num(o, "pos"), updated = (long) num(o, "updated");
                    // players only refresh the timeline now and then; extrapolate from when they did
                    long now = System.currentTimeMillis();
                    long live = status.equals("Playing") && updated > 0 ? pos + Math.max(0, now - updated) : pos;
                    position = Math.min(duration > 0 ? duration : Long.MAX_VALUE, live);
                    positionAt = now;
                    if (!t.equals(title) || !a.equals(artist)) { title = t; artist = a; requestLyrics(); }
                } catch (RuntimeException ignored) { }
            }
        } catch (Exception ignored) {
        }
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static double num(JsonObject o, String k) { try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0; } catch (RuntimeException e) { return 0; } }

    private void requestLyrics() {
        lyrics = List.of();
        String key = artist + " - " + title;
        lyricsFor = key;
        if (!fetch.get() || title.isBlank()) { lyricsState = ""; return; }
        lyricsState = "Looking up lyrics";
        String url = "https://lrclib.net/api/get?artist_name=" + enc(artist) + "&track_name=" + enc(title)
                + (duration > 0 ? "&duration=" + Math.round(duration / 1000.0) : "");
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Anthrax client (https://github.com/Unblonded/anthrax)").GET().build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(resp -> {
            if (!key.equals(lyricsFor)) return;
            if (resp.statusCode() != 200) { lyricsState = "No lyrics found"; return; }
            try {
                JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
                String synced = str(o, "syncedLyrics");
                if (synced.isBlank()) { lyricsState = str(o, "plainLyrics").isBlank() ? "No lyrics found" : "Only unsynced lyrics"; return; }
                lyrics = parse(synced);
                lyricsState = "";
            } catch (RuntimeException e) { lyricsState = "No lyrics found"; }
        }).exceptionally(e -> { if (key.equals(lyricsFor)) lyricsState = "Lyrics unavailable"; return null; });
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static final Pattern LRC = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s*(.*)");

    private static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            long ms = Long.parseLong(m.group(1)) * 60000 + Math.round(Double.parseDouble(m.group(2)) * 1000);
            out.add(new Line(ms, m.group(3)));
        }
        out.sort((a, b) -> Long.compare(a.ms, b.ms));
        return List.copyOf(out);
    }

    // ---- read by the HUD
    public boolean hasTrack() { return !title.isBlank(); }
    public boolean showCard() { return isEnabled() && card.get() && (hasTrack() || !status.isBlank()) && !(hidePaused.get() && !status.equals("Playing")); }
    public boolean showCardLyric() { return cardLyric.get(); }
    public boolean showCrosshairLyric() { return isEnabled() && crosshairLyric.get() && hasTrack(); }
    public String title() { return title; }
    public String artist() { return artist; }
    public String status() { return status; }
    public boolean playing() { return status.equals("Playing"); }
    public long duration() { return duration; }

    /** Current playback position, moving on smoothly between updates. */
    public long position() {
        long p = position + (playing() ? System.currentTimeMillis() - positionAt : 0);
        return duration > 0 ? Math.min(duration, p) : p;
    }

    /** The lyric line for the current position, or a status message, or "". */
    public String lyric() {
        List<Line> l = lyrics;
        if (l.isEmpty()) return lyricsState;
        long p = position() + 150;
        String cur = "";
        for (Line line : l) { if (line.ms > p) break; cur = line.text; }
        return cur;
    }

    @Override public String hudInfo() { return hasTrack() ? title : null; }

    /** Polls Windows' global media session once a second and prints one JSON line per poll. */
    private static final String SCRIPT = """
            $ErrorActionPreference = 'SilentlyContinue'
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
            [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null
            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            while ($true) {
              $s = $mgr.GetCurrentSession()
              if ($s) {
                $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $t = $s.GetTimelineProperties()
                $pb = $s.GetPlaybackInfo()
                $o = [ordered]@{ title = $p.Title; artist = $p.Artist; pos = [math]::Round($t.Position.TotalMilliseconds); dur = [math]::Round($t.EndTime.TotalMilliseconds); updated = $t.LastUpdatedTime.ToUnixTimeMilliseconds(); status = "$($pb.PlaybackStatus)" }
                [Console]::Out.WriteLine(($o | ConvertTo-Json -Compress))
              } else { [Console]::Out.WriteLine('{}') }
              [Console]::Out.Flush()
              Start-Sleep -Milliseconds 1000
            }
            """;
}
