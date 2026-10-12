package unblonded.anthrax.modules.world;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV log of every base found ({@code config/anthrax-bases.csv}). Rows for the current server and
 * dimension are loaded on join so bases you have already found are recognised as "known".
 */
final class BaseLog {
    static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("anthrax-bases.csv");
    private static final String HEADER = "time,server,dimension,type,x,y,z,score,confidence,chunks,summary";
    private static final List<int[]> KNOWN = new ArrayList<>();

    /** One logged find, as stored in the CSV. */
    record Entry(String time, String type, int x, int y, int z, int score, int confidence, String summary) {}
    /** Every logged find for the server and dimension last loaded (the Intel map draws these). */
    private static final List<Entry> HISTORY = new ArrayList<>();
    private static String loadedFor = "";

    private BaseLog() {}

    static String server() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        if (mc.getSingleplayerServer() != null) return "singleplayer/" + mc.getSingleplayerServer().getWorldData().getLevelName();
        return "unknown";
    }

    static void load(ClientLevel level) {
        KNOWN.clear();
        HISTORY.clear();
        String server = server(), dim = level.dimension().identifier().toString();
        loadedFor = server + "|" + dim;
        if (!Files.isRegularFile(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] p = splitCsv(line);
                if (p.length < 7 || !p[1].equals(server) || !p[2].equals(dim)) continue;
                try {
                    int x = Integer.parseInt(p[4]), z = Integer.parseInt(p[6]);
                    KNOWN.add(new int[]{x, z});
                    HISTORY.add(new Entry(p[0], p[3], x, Integer.parseInt(p[5]), z,
                            p.length > 7 ? Integer.parseInt(p[7]) : 0, p.length > 8 ? Integer.parseInt(p[8]) : 0,
                            p.length > 10 ? p[10] : ""));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Anthrax: could not read base log: " + e.getMessage());
        }
    }

    static boolean isKnown(int x, int z) {
        for (int[] k : KNOWN) {
            long dx = k[0] - x, dz = k[1] - z;
            if (dx * dx + dz * dz < 96 * 96) return true;
        }
        return false;
    }

    /** Logged finds for the level the player is in now (loads them if the server or dimension changed). */
    static List<Entry> history(ClientLevel level) {
        if (level != null && !loadedFor.equals(server() + "|" + level.dimension().identifier())) load(level);
        return List.copyOf(HISTORY);
    }

    static void append(ClientLevel level, BaseFinderModule.Base b) {
        KNOWN.add(new int[]{b.x, b.z});
        HISTORY.add(new Entry(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), b.type, b.x, b.y, b.z,
                Math.round(b.score), b.confidence, b.summary));
        String row = String.join(",", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                csv(server()), csv(level.dimension().identifier().toString()), csv(b.type),
                Integer.toString(b.x), Integer.toString(b.y), Integer.toString(b.z),
                Integer.toString(Math.round(b.score)), Integer.toString(b.confidence), Integer.toString(b.chunks), csv(b.summary));
        // File I/O off the client thread; rows are tiny and order does not matter.
        Thread.ofVirtual().start(() -> {
            synchronized (BaseLog.class) {
                try {
                    Files.createDirectories(FILE.getParent());
                    boolean fresh = !Files.exists(FILE);
                    try (BufferedWriter w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                        if (fresh) { w.write(HEADER); w.newLine(); }
                        w.write(row);
                        w.newLine();
                    }
                } catch (Exception e) {
                    System.err.println("Anthrax: could not write base log: " + e.getMessage());
                }
            }
        });
    }

    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
