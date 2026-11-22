/// Loom Lab — demo standalone do Java 26, sem Spring e sem build.
///
/// Este único arquivo demonstra três cortes de verbosidade do Java moderno:
///
///   1. "import module" (JEP 511, final no JDK 25): importa TODOS os pacotes
///      públicos de java.base — nada de meia dúzia de imports de java.util.*.
///   2. Compact source file + instance main (JEP 512, final no JDK 25):
///      a classe é declarada implicitamente e main() é um método de instância
///      sem `static public void main(String[] args)`.
///   3. Virtual threads + Structured Concurrency: 100 mil threads leves
///      fan-out com escopo estruturado e timeout.
///
/// Rodar com:
///   java --enable-preview --source 26 demo/LoomLabDemo.java
/// (--enable-preview é necessário apenas pela Structured Concurrency, JEP 525)

import module java.base;

void main() throws InterruptedException {
    IO.println("☕ Java " + Runtime.version().feature() + " — demo Loom Lab");
    IO.println();

    // --- Virtual threads (JEP 444): 100.000 threads custam o mesmo que 200 ---
    var start = System.nanoTime();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        IntStream.rangeClosed(1, 100_000).forEach(i ->
            executor.submit(() -> {
                Thread.sleep(200);   // I/O simulado — a thread "desaparece" do SO durante o sleep
                return i;
            }));
    }   // try-with-resources espera tudo e fecha o executor
    IO.println("100.000 virtual threads com sleep de 200ms terminaram em %dms"
        .formatted((System.nanoTime() - start) / 1_000_000));

    // --- Structured Concurrency (JEP 525, preview): fan-out com timeout único ---
    var suppliers = List.of("SkyOne:300", "FlyFast:100", "SlowJet:1500");
    try (var scope = StructuredTaskScope.open(
             StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow(),
             config -> config.withTimeout(Duration.ofMillis(800)))) {

        for (var supplier : suppliers) {
            var name = supplier.split(":")[0];
            var latency = Long.parseLong(supplier.split(":")[1]);
            scope.fork(() -> {
                Thread.sleep(latency);
                return "cotação de " + name;
            });
        }

        var quotes = scope.join();   // todas — ou TimeoutException cancela as vagarosas
        IO.println("StructuredTaskScope coletou " + quotes.size() + " cotações (limite de 800ms)");
    } catch (StructuredTaskScope.TimeoutException e) {
        IO.println("timeout estruturado: fornecedores lentos foram cancelados em bloco");
    }
}
