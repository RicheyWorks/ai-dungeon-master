package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.QuestScriptRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap Phase 5 (pack upload): zip validation, safe extraction, and
 * runtime registration through the startup-scan code path.
 */
class PackUploadServiceTest {

    private static final String MANIFEST = """
            id: "uploaded-pack"
            displayName: "Uploaded Pack"
            version: "1.0.0"
            """;

    private static final String ITEMS = """
            { "tonic": { "name": "Grave Tonic", "description": "Bitter.", "consumable": true,
                         "effectKey": "HEAL", "power": 10 } }
            """;

    private static final String QUEST = """
            { "id": "uploaded-quest", "title": "Uploaded Quest", "description": "From a zip.",
              "scenes": [ { "id": "only", "description": "One scene.", "choices": [], "isFinalScene": true } ] }
            """;

    @TempDir
    Path packsDir;

    private PackUploadService service;

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
        service = new PackUploadService(packsDir.toString());
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
        QuestScriptRegistry.clearForTests();
    }

    private static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static Map<String, String> validPack(String prefix) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(prefix + "pack.yaml", MANIFEST);
        files.put(prefix + "items/items.json", ITEMS);
        files.put(prefix + "quests/uploaded-quest.json", QUEST);
        return files;
    }

    @Test
    void flatZipInstallsRegistersAndExtracts() throws Exception {
        PackUploadService.InstalledPack installed = service.install(zip(validPack("")), false);

        assertEquals("uploaded-pack", installed.pack().id());
        assertFalse(installed.replaced());
        assertTrue(ContentRegistry.isKnown("uploaded-pack"));
        assertTrue(ContentRegistry.items().containsKey("tonic"));
        // Quests inside the pack register too, exactly like the startup scan.
        assertTrue(QuestScriptRegistry.isRegistered("uploaded-quest"));
        assertTrue(Files.isRegularFile(packsDir.resolve("uploaded-pack/pack.yaml")));
    }

    @Test
    void singleTopLevelDirectoryIsStripped() throws Exception {
        PackUploadService.InstalledPack installed =
                service.install(zip(validPack("my-pack-folder/")), false);

        assertEquals("uploaded-pack", installed.pack().id());
        assertTrue(Files.isRegularFile(packsDir.resolve("uploaded-pack/pack.yaml")),
                "top-level zip folder should be stripped; layout keyed by pack id");
    }

    @Test
    void zipSlipEntriesAreRejected() throws Exception {
        Map<String, String> files = validPack("");
        files.put("../evil.txt", "escape attempt");

        PackUploadService.PackUploadException ex = assertThrows(
                PackUploadService.PackUploadException.class,
                () -> service.install(zip(files), false));
        assertTrue(ex.getMessage().contains("unsafe zip entry"));
        assertFalse(Files.exists(packsDir.getParent().resolve("evil.txt")));
    }

    @Test
    void missingManifestIsRejected() throws Exception {
        byte[] noManifest = zip(Map.of("items/items.json", ITEMS));
        PackUploadService.PackUploadException ex = assertThrows(
                PackUploadService.PackUploadException.class,
                () -> service.install(noManifest, false));
        assertTrue(ex.getMessage().contains("pack.yaml"));
    }

    @Test
    void unsafePackIdIsRejected() throws Exception {
        byte[] badId = zip(Map.of("pack.yaml", "id: \"../Escape Pack\"\n"));
        assertThrows(PackUploadService.PackUploadException.class,
                () -> service.install(badId, false));
        // Nothing extracted.
        try (var children = Files.list(packsDir)) {
            assertEquals(0, children.count());
        }
    }

    @Test
    void garbageBytesAreRejected() {
        assertThrows(PackUploadService.PackUploadException.class,
                () -> service.install("this is not a zip".getBytes(StandardCharsets.UTF_8), false));
    }

    @Test
    void duplicateIdConflictsUnlessReplace() throws Exception {
        service.install(zip(validPack("")), false);

        PackUploadService.PackUploadException ex = assertThrows(
                PackUploadService.PackUploadException.class,
                () -> service.install(zip(validPack("")), false));
        assertTrue(ex.isConflict());

        PackUploadService.InstalledPack replaced = service.install(zip(validPack("")), true);
        assertTrue(replaced.replaced());
        assertTrue(ContentRegistry.isEnabled("uploaded-pack"));
    }
}
