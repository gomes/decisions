package io.github.gomes.decisions.cli;

import io.github.gomes.decisions.ProviderRegistry;
import io.github.gomes.decisions.RequestCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    record Run(int code, String out, String err) {
    }

    static Run run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code = Main.run(args, new PrintStream(out, true), new PrintStream(err, true),
                ProviderRegistry.fromEnvironment(Map.of()));
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void providersAndExample() {
        assertTrue(run("providers").out().contains("local"));
        assertEquals(RequestCodec.EXAMPLE, run("example").out());
        assertEquals(2, run().code());
    }

    @Test
    void decideFromFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("req.json");
        Files.writeString(f, RequestCodec.EXAMPLE);
        Run r = run("decide", "-p", "local", "-f", f.toString());
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("\"shipping\""));

        r = run("decide", "-p", "openai", "-f", f.toString());
        assertEquals(2, r.code());
        assertTrue(r.err().contains("not configured"));
    }
}
