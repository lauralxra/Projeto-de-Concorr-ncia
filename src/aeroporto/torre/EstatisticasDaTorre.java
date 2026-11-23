package aeroporto.torre;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Números da torre num instante (Extra 6), para o painel mostrar.
 *
 * É imutável: a torre monta este objeto com a trava pega e entrega uma cópia.
 * O painel pode ler à vontade, de qualquer thread, sem lock e sem atrapalhar a torre.
 *
 * Como cada número é medido:
 *   - espera: do momento em que o avião entra na fila até a torre conceder a pista;
 *   - pousos/decolagens: contados quando o avião conclui a operação (concluirOperacao);
 *   - ocupação da pista: tempo em que a pista ficou reservada para algum avião
 *     (da autorização até concluirOperacao), dividido pelo tempo total da simulação.
 */
public final class EstatisticasDaTorre {

    private final long inicioMillis;
    private final long instanteMillis;
    private final int totalPousos;
    private final int totalDecolagens;
    private final int autorizacoesPouso;
    private final int autorizacoesDecolagem;
    private final long somaEsperaPousoMillis;
    private final long somaEsperaDecolagemMillis;
    private final long maiorEsperaMillis;
    private final int emergenciasAtendidas;
    private final Map<Integer, Long> tempoReservadoPorPista;
    private final Map<Integer, Integer> operacoesPorPista;

    EstatisticasDaTorre(long inicioMillis, long instanteMillis,
                        int totalPousos, int totalDecolagens,
                        int autorizacoesPouso, int autorizacoesDecolagem,
                        long somaEsperaPousoMillis, long somaEsperaDecolagemMillis,
                        long maiorEsperaMillis, int emergenciasAtendidas,
                        Map<Integer, Long> tempoReservadoPorPista,
                        Map<Integer, Integer> operacoesPorPista) {
        this.inicioMillis = inicioMillis;
        this.instanteMillis = instanteMillis;
        this.totalPousos = totalPousos;
        this.totalDecolagens = totalDecolagens;
        this.autorizacoesPouso = autorizacoesPouso;
        this.autorizacoesDecolagem = autorizacoesDecolagem;
        this.somaEsperaPousoMillis = somaEsperaPousoMillis;
        this.somaEsperaDecolagemMillis = somaEsperaDecolagemMillis;
        this.maiorEsperaMillis = maiorEsperaMillis;
        this.emergenciasAtendidas = emergenciasAtendidas;
        this.tempoReservadoPorPista = Collections.unmodifiableMap(new TreeMap<>(tempoReservadoPorPista));
        this.operacoesPorPista = Collections.unmodifiableMap(new TreeMap<>(operacoesPorPista));
    }

    /** Pousos concluídos desde o início da simulação. */
    public int getTotalPousos() { return totalPousos; }

    /** Decolagens concluídas desde o início da simulação. */
    public int getTotalDecolagens() { return totalDecolagens; }

    /** Tempo médio entre entrar na fila de pouso e receber a pista (0 se ninguém foi atendido). */
    public long getEsperaMediaPousoMillis() {
        return autorizacoesPouso == 0 ? 0 : somaEsperaPousoMillis / autorizacoesPouso;
    }

    /** Tempo médio entre entrar na fila de decolagem e receber a pista (0 se ninguém foi atendido). */
    public long getEsperaMediaDecolagemMillis() {
        return autorizacoesDecolagem == 0 ? 0 : somaEsperaDecolagemMillis / autorizacoesDecolagem;
    }

    /** Tempo médio de espera considerando pousos e decolagens juntos. */
    public long getEsperaMediaGeralMillis() {
        int total = autorizacoesPouso + autorizacoesDecolagem;
        return total == 0 ? 0 : (somaEsperaPousoMillis + somaEsperaDecolagemMillis) / total;
    }

    /** A maior espera que algum avião já teve até receber a pista. */
    public long getMaiorEsperaMillis() { return maiorEsperaMillis; }

    /** Quantos aviões em emergência receberam pista para pousar (Extra 1). */
    public int getEmergenciasAtendidas() { return emergenciasAtendidas; }

    /** Pousos + decolagens concluídos em cada pista (número da pista -> total). */
    public Map<Integer, Integer> getOperacoesPorPista() { return operacoesPorPista; }

    /**
     * Porcentagem do tempo da simulação em que a pista ficou reservada (0 a 100).
     * Conta só operações já concluídas.
     */
    public double getOcupacaoPercentual(int numeroPista) {
        long decorrido = getTempoDecorridoMillis();
        if (decorrido <= 0) return 0;
        long reservado = tempoReservadoPorPista.getOrDefault(numeroPista, 0L);
        return Math.min(100.0, 100.0 * reservado / decorrido);
    }

    /** Tempo desde que a torre foi criada até o instante destas estatísticas. */
    public long getTempoDecorridoMillis() { return instanteMillis - inicioMillis; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Pousos: ").append(totalPousos)
          .append(" | Decolagens: ").append(totalDecolagens)
          .append(" | Espera média: pouso ").append(getEsperaMediaPousoMillis()).append(" ms, decolagem ")
          .append(getEsperaMediaDecolagemMillis()).append(" ms")
          .append(" | Maior espera: ").append(maiorEsperaMillis).append(" ms")
          .append(" | Emergências: ").append(emergenciasAtendidas);
        for (Integer numero : operacoesPorPista.keySet()) {
            sb.append(String.format(" | Pista %d: %.0f%%", numero, getOcupacaoPercentual(numero)));
        }
        return sb.toString();
    }
}
