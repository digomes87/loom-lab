package dev.loomlab.aggregator;

/**
 * Contexto de rastreio baseado em <em>Scoped Values</em> (JEP 506, final no JDK 25).
 *
 * <p>Onde antes usaríamos {@code ThreadLocal} (mutável, fácil de esquecer de limpar,
 * não herdado por threads filhas), o {@link ScopedValue} é:</p>
 * <ul>
 *   <li><b>imutável</b> durante o escopo do binding ({@code ScopedValue.where(...).run(...)});</li>
 *   <li><b>herdado</b> pelas subtasks de um {@code StructuredTaskScope} — cada
 *       virtual thread forkada enxerga o mesmo valor;</li>
 *   <li><b>desfeito automaticamente</b> ao sair do escopo, sem {@code remove()}.</li>
 * </ul>
 */
public final class TraceContext {

    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

    private TraceContext() {
    }
}
