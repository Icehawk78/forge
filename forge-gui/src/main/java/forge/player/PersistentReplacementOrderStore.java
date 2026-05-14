package forge.player;

import forge.localinstance.properties.ForgeConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * File-backed singleton mapping a canonical replacement-effect menu key to the
 * {@code toString()} of the effect the player previously chose. Used by
 * {@code PlayerControllerHuman.chooseSingleReplacementEffect} to skip the
 * "choose order" prompt when the same set of replacement effects applies to
 * the same kind of event again.
 *
 * Key shape: {@code <ReplacementType.name()>:<sorted "|"-joined effect.toString() values>}.
 * Value: the chosen effect's {@code toString()}.
 *
 * Key stability: effect descriptions are not guaranteed stable across Forge
 * versions or card text edits. A stale persisted key silently fails to match
 * (the prompt is shown as today) — acceptable; the user can re-train as needed.
 */
public class PersistentReplacementOrderStore {
    private static final String ENTRY_PREFIX = "replOrder.";
    private static volatile PersistentReplacementOrderStore instance;

    public static PersistentReplacementOrderStore get() {
        PersistentReplacementOrderStore local = instance;
        if (local != null) return local;
        synchronized (PersistentReplacementOrderStore.class) {
            if (instance == null) {
                instance = new PersistentReplacementOrderStore(
                        Paths.get(ForgeConstants.USER_DIR, "auto-replacement-orders.dat"));
            }
            return instance;
        }
    }

    private final Path persistFile;
    private final Map<String, String> chosenByMenu = new HashMap<>();

    PersistentReplacementOrderStore(Path persistFile) {
        this.persistFile = persistFile;
        load();
    }

    public synchronized String getChosen(String menuKey) {
        return chosenByMenu.get(menuKey);
    }

    public synchronized void setChosen(String menuKey, String chosenValue) {
        String prev = chosenByMenu.put(menuKey, chosenValue);
        if (prev == null || !prev.equals(chosenValue)) save();
    }

    private void load() {
        if (persistFile == null || !Files.exists(persistFile)) return;
        try (BufferedReader r = Files.newBufferedReader(persistFile)) {
            Properties p = new Properties();
            p.load(r);
            for (String name : p.stringPropertyNames()) {
                if (name.startsWith(ENTRY_PREFIX)) {
                    String key = URLDecoder.decode(name.substring(ENTRY_PREFIX.length()), "UTF-8");
                    String value = URLDecoder.decode(p.getProperty(name), "UTF-8");
                    chosenByMenu.put(key, value);
                }
            }
        } catch (IOException ignored) {
            // UnsupportedEncodingException is unreachable ("UTF-8" is always supported).
            // IOException: stale or corrupt file — treat as empty; next save overwrites.
        }
    }

    private void save() {
        if (persistFile == null) return;
        try {
            Properties p = new Properties();
            for (Map.Entry<String, String> e : chosenByMenu.entrySet()) {
                p.setProperty(
                        ENTRY_PREFIX + URLEncoder.encode(e.getKey(), "UTF-8"),
                        URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            Path parent = persistFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (BufferedWriter w = Files.newBufferedWriter(persistFile)) {
                p.store(w, "Forge persistent replacement-effect orderings");
            }
        } catch (IOException ignored) {
            // Best-effort persistence; in-memory state remains correct for this session.
        }
    }
}
