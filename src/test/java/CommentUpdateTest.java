import base.Util;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import org.testng.Assert;
import java.io.IOException;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;

/**
 * Regression test for comments being lost when {@code load()} auto-saves newly
 * discovered fields.
 * <p>
 * Before the fix, {@code internalLoad} resolved field paths differently from
 * {@code collectComments} in DEFAULT mode (it kept underscore field names as-is
 * instead of replacing {@code _} with {@code .}). As a result, when a config file
 * was missing fields, the auto-save branch wrote data under one key but registered
 * comments under another, so the emitted YAML had no comments.
 */
public class CommentUpdateTest {
    private CommentUpdateConfig config;
    private File file;

    @BeforeSuite
    public void before() throws Exception {
        file = new File("temp", "commentUpdate.yml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (file.exists()) {
            file.delete();
        }

        // Seed a file that is MISSING a field declared in the config. This forces the
        // auto-save path in internalLoad (the branch that adds the missing field back).
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write("server_port: 25565\n");
        }
    }

    @Test(priority = 1)
    public void loadKeepsCommentsWhenAddingField() throws InvalidConfigurationException, IOException {
        config = new CommentUpdateConfig();
        config.load(file);

        String fileContents = Util.readFile(file);

        // In DEFAULT mode an underscore-separated field name resolves to a dotted
        // (nested) path, so "server_host" / "server_port" become server.host / server.port.
        Assert.assertTrue(fileContents.contains("# The host the server binds to."),
                "Comment for server.host should be preserved after auto-save. Actual:\n" + fileContents);
        Assert.assertTrue(fileContents.contains("# The port the server binds to."),
                "Comment for server.port should be preserved after auto-save. Actual:\n" + fileContents);
        Assert.assertTrue(fileContents.contains("host: localhost"),
                "Missing nested field server.host should have been written. Actual:\n" + fileContents);
    }
}
