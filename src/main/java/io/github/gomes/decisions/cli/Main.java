package io.github.gomes.decisions.cli;

import io.github.gomes.decisions.DecisionService;
import io.github.gomes.decisions.ProviderRegistry;
import io.github.gomes.decisions.RequestCodec;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.server.DecisionServer;
import io.github.gomes.decisions.spi.DecisionException;
import io.github.gomes.decisions.spi.DecisionProvider;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Command-line entry point. Run without arguments for usage. */
public final class Main {

    private static final String USAGE = """
            Usage: decisions <command> [options]

            Commands:
              providers                         List configured providers
              example                           Print an example request JSON
              decide  -p <provider> [-f file] [-m model]
                                                Send a request (file or stdin) to one provider
              compare [-p a,b,...] [-f file]    Send the same request to several providers (default: all)
              serve   [--host 127.0.0.1] [--port 8080]
                                                Start the HTTP gateway (token: DECISIONS_GATEWAY_TOKEN)

            Providers are enabled by environment variables (see README):
              TYPESAFE_API_KEY, OPENAI_API_KEY, AZURE_OPENAI_API_KEY + AZURE_OPENAI_ENDPOINT,
              GEMINI_API_KEY, FOUNDRY_API_KEY + FOUNDRY_ENDPOINT, SYSTEMONE_ENDPOINT/_API_KEY/_MODEL.
              The offline 'local' provider is always available.
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err, ProviderRegistry.fromEnvironment()));
    }

    static int run(String[] args, PrintStream out, PrintStream err, ProviderRegistry registry) {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            out.print(USAGE);
            return args.length == 0 ? 2 : 0;
        }
        Map<String, String> opts;
        try {
            opts = options(Arrays.copyOfRange(args, 1, args.length));
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 2;
        }
        DecisionService service = new DecisionService(registry);
        try {
            switch (args[0]) {
                case "providers" -> {
                    for (DecisionProvider p : registry.all()) {
                        out.printf("%-14s %-36s model=%s max_questions=%d images=%s%n", p.id(), p.displayName(),
                                p.defaultModel(), p.capabilities().maxQuestions(), p.capabilities().images());
                    }
                }
                case "example" -> out.print(RequestCodec.EXAMPLE);
                case "decide" -> {
                    String provider = opts.get("p");
                    if (provider == null) {
                        err.println("decide needs -p <provider>");
                        return 2;
                    }
                    DecisionRequest req = RequestCodec.read(readInput(opts.get("f")));
                    if (opts.containsKey("m")) req = req.withModel(opts.get("m"));
                    out.println(RequestCodec.write(service.decide(provider, req)));
                }
                case "compare" -> {
                    DecisionRequest req = RequestCodec.read(readInput(opts.get("f")));
                    List<String> ids = opts.containsKey("p") ? List.of(opts.get("p").split(",")) : List.of();
                    out.println(RequestCodec.write(service.compare(req, ids, Map.of())));
                }
                case "serve" -> {
                    String host = opts.getOrDefault("host", "127.0.0.1");
                    int port = Integer.parseInt(opts.getOrDefault("port", "8080"));
                    DecisionServer server = new DecisionServer(service, host, port,
                            System.getenv("DECISIONS_GATEWAY_TOKEN")).start();
                    out.printf("Decision gateway listening on http://%s:%d (providers: %s)%n", host, server.port(),
                            registry.all().stream().map(DecisionProvider::id).toList());
                    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                    new CountDownLatch(1).await();
                }
                default -> {
                    err.println("unknown command: " + args[0]);
                    err.print(USAGE);
                    return 2;
                }
            }
            return 0;
        } catch (IllegalArgumentException | java.util.NoSuchElementException e) {
            err.println("error: " + e.getMessage());
            return 2;
        } catch (DecisionException e) {
            err.println("error: " + e.getMessage());
            if (e.responseBody() != null) err.println(e.responseBody());
            return 1;
        } catch (IOException e) {
            err.println("I/O error: " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    private static String readInput(String file) throws IOException {
        if (file == null || file.equals("-")) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return Files.readString(Path.of(file));
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("-")) throw new IllegalArgumentException("unexpected argument: " + a);
            String key = a.replaceFirst("^-+", "");
            if (i + 1 >= args.length) throw new IllegalArgumentException("missing value for " + a);
            out.put(key, args[++i]);
        }
        return out;
    }
}
