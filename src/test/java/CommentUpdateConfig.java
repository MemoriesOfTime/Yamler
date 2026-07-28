import net.cubespace.Yamler.Config.Comment;
import net.cubespace.Yamler.Config.YamlConfig;

/**
 * Regression config for "comments lost when load() auto-saves new fields".
 * Uses a DEFAULT-mode config with underscore-separated field names, which is the
 * case where the old internalLoad computed a different path key than the comment
 * collector (field name kept as-is vs. underscore replaced with dots).
 */
public class CommentUpdateConfig extends YamlConfig {
    @Comment("The host the server binds to.")
    public String server_host = "localhost";

    @Comment("The port the server binds to.")
    public int server_port = 25565;
}
