import base.Util;
import net.cubespace.Yamler.Config.ConfigSection;
import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.cubespace.Yamler.Config.YamlConfig;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regression tests for three bugs found during code review:
 *
 *  1. {@code Converter.Config.newInstance} crashed on static nested config
 *     classes with NoSuchMethodException.
 *  2. {@code Converter.Map.fromConfig} crashed when a field was declared as
 *     the {@code Map} interface rather than a concrete {@code HashMap}.
 *  3. {@code ConfigSection.get()} mutated the tree (created empty sections)
 *     when reading a non-existent dotted path.
 */
public class RegressionTest {
    private File file;

    @BeforeSuite
    public void before() {
        file = new File("temp", "regressionTest.yml");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Bug 1: a config that uses a STATIC nested config class must load without
     * NoSuchMethodException. Before the fix, getEnclosingClass() returned the
     * outer class for static nested types and the code tried to resolve a
     * synthetic (Outer) constructor that does not exist.
     */
    @Test(priority = 1)
    public void staticNestedConfigClassLoads() throws InvalidConfigurationException, IOException {
        WithStaticNested config = new WithStaticNested();
        config.init(file);

        // Sub-config value round-trips through YAML
        Assert.assertEquals(config.nested.innerField, "innerValue");

        String fileContents = Util.readFile(file);
        Assert.assertTrue(fileContents.contains("innerField: innerValue"),
                "Static nested config should serialize. Actual:\n" + fileContents);

        // Reload into a fresh instance to exercise the load (newInstance) path
        WithStaticNested reloaded = new WithStaticNested();
        reloaded.load(file);
        Assert.assertEquals(reloaded.nested.innerField, "innerValue");
    }

    /**
     * Bug 2: a field declared as the {@code Map} interface (not a concrete
     * HashMap) must load. Before the fix, getDeclaredConstructor() on the
     * Map interface threw NoSuchMethodException which escaped the narrow
     * catch (InstantiationException) block.
     */
    @Test(priority = 2)
    public void mapInterfaceFieldLoads() throws InvalidConfigurationException, IOException {
        File ifaceFile = new File("temp", "regressionMapInterface.yml");
        if (ifaceFile.exists()) {
            ifaceFile.delete();
        }

        WithMapInterface config = new WithMapInterface();
        config.init(ifaceFile);

        Assert.assertNotNull(config.numbers, "Map interface field should be populated");
        Assert.assertEquals(config.numbers.size(), 1);
        Assert.assertEquals(config.numbers.get("one"), Integer.valueOf(1));

        // Reload to exercise fromConfig on the interface type
        WithMapInterface reloaded = new WithMapInterface();
        reloaded.load(ifaceFile);
        Assert.assertEquals(reloaded.numbers.get("one"), Integer.valueOf(1));
    }

    /**
     * Bug 3: reading a non-existent dotted path must NOT create empty
     * ConfigSection nodes. Before the fix, get() leaked nodes into the tree.
     */
    @Test(priority = 3)
    public void getOnMissingPathHasNoSideEffect() {
        ConfigSection section = new ConfigSection();

        Assert.assertFalse(section.has("a.b.c"));
        Assert.assertNull(section.get("a.b.c"), "get on missing path should return null");
        Assert.assertFalse(section.has("a.b.c"), "has() must still be false after get()");
        Assert.assertTrue(section.getRawMap().isEmpty(),
                "get() must not create empty nodes. Keys were: " + section.getRawMap().keySet());
    }

    /**
     * Bug 3 (second angle): serializing a config after a converter read a
     * missing dotted path should not emit leaked empty sections.
     */
    @Test(priority = 4)
    public void missingNestedFieldDoesNotLeakEmptySection() throws InvalidConfigurationException, IOException {
        File leakFile = new File("temp", "regressionLeak.yml");
        if (leakFile.exists()) {
            leakFile.delete();
        }

        // Seed a file that is missing the nested field declared below.
        try (FileWriter w = new FileWriter(leakFile)) {
            w.write("topField: present\n");
        }

        WithNestedMissing config = new WithNestedMissing();
        config.load(leakFile);

        String fileContents = Util.readFile(leakFile);
        Assert.assertFalse(fileContents.contains("missing: {}"),
                "No leaked empty section should be written. Actual:\n" + fileContents);
    }

    // ---- Config fixtures (default package, matching existing test style) ----

    public static class WithStaticNested extends YamlConfig {
        public String topField = "topValue";
        public StaticNested nested = new StaticNested();
    }

    /** Static nested config class — triggers Bug 1 before the fix. */
    public static class StaticNested extends YamlConfig {
        public String innerField = "innerValue";
    }

    public static class WithMapInterface extends YamlConfig {
        // Declared as the Map interface — triggers Bug 2 before the fix.
        public Map<String, Integer> numbers = new LinkedHashMap<>();
        {
            numbers.put("one", 1);
        }
    }

    public static class WithNestedMissing extends YamlConfig {
        public String topField = "topValue";
        // Resolves to dotted path "missing.nested" in DEFAULT mode.
        public String missing_nested = "default";
    }
}
