package aeroporto.config;

/**
 * Objeto imutável contendo todos os parâmetros da simulação já validados.
 */
public class Configuracao {

    private final String nomeCenario;
    private final int numeroPistas;
    private final int avioesPousoInicial;
    private final int avioesDecolagemInicial;
    private final long intervaloPainelMs;
    private final long delayVooMillis;
    private final long delaySoloMillis;
    private final long delayOperacaoMillis;
    private final long autonomiaMillis;
    private final double limiteEmergencia;
    private final long limiteAgingMillis;
    private final int limitePousosSeguidos;
    private final boolean ativarManutencao;
    private final long manutencaoIntervaloMillis;
    private final long manutencaoDuracaoMillis;

    public Configuracao(
            String nomeCenario,
            int numeroPistas,
            int avioesPousoInicial,
            int avioesDecolagemInicial,
            long intervaloPainelMs,
            long delayVooMillis,
            long delaySoloMillis,
            long delayOperacaoMillis,
            long autonomiaMillis,
            double limiteEmergencia,
            long limiteAgingMillis,
            int limitePousosSeguidos,
            boolean ativarManutencao,
            long manutencaoIntervaloMillis,
            long manutencaoDuracaoMillis) {

        this.nomeCenario = (nomeCenario == null || nomeCenario.isBlank()) ? "Cenário Padrão" : nomeCenario;
        this.numeroPistas = numeroPistas;
        this.avioesPousoInicial = avioesPousoInicial;
        this.avioesDecolagemInicial = avioesDecolagemInicial;
        this.intervaloPainelMs = intervaloPainelMs;
        this.delayVooMillis = delayVooMillis;
        this.delaySoloMillis = delaySoloMillis;
        this.delayOperacaoMillis = delayOperacaoMillis;
        this.autonomiaMillis = autonomiaMillis;
        this.limiteEmergencia = limiteEmergencia;
        this.limiteAgingMillis = limiteAgingMillis;
        this.limitePousosSeguidos = limitePousosSeguidos;
        this.ativarManutencao = ativarManutencao;
        this.manutencaoIntervaloMillis = manutencaoIntervaloMillis;
        this.manutencaoDuracaoMillis = manutencaoDuracaoMillis;

        validar();
    }

    private void validar() {
        if (numeroPistas <= 0) {
            throw new ExcecaoConfiguracaoInvalida("O número de pistas deve ser >= 1. Informado: " + numeroPistas);
        }
        if (avioesPousoInicial < 0 || avioesDecolagemInicial < 0 || (avioesPousoInicial + avioesDecolagemInicial == 0)) {
            throw new ExcecaoConfiguracaoInvalida("O simulador precisa de pelo menos 1 avião ativo.");
        }
        if (intervaloPainelMs < 30) {
            throw new ExcecaoConfiguracaoInvalida("Intervalo do painel deve ser >= 30ms para evitar sobrecarga. Informado: " + intervaloPainelMs);
        }
        if (delayVooMillis <= 0 || delaySoloMillis <= 0 || delayOperacaoMillis <= 0) {
            throw new ExcecaoConfiguracaoInvalida("Todos os delays de operação devem ser positivos.");
        }
        if (autonomiaMillis <= 0) {
            throw new ExcecaoConfiguracaoInvalida("A autonomia de combustível deve ser positiva.");
        }
        if (limiteEmergencia <= 0.0 || limiteEmergencia >= 1.0) {
            throw new ExcecaoConfiguracaoInvalida("Limite de emergência deve estar entre 0.0 e 1.0. Informado: " + limiteEmergencia);
        }
        if (limiteAgingMillis <= 0) {
            throw new ExcecaoConfiguracaoInvalida("Limite de aging da torre deve ser positivo.");
        }
        if (limitePousosSeguidos <= 0) {
            throw new ExcecaoConfiguracaoInvalida("Limite de pousos seguidos deve ser >= 1.");
        }
        if (ativarManutencao && (manutencaoIntervaloMillis <= 0 || manutencaoDuracaoMillis <= 0)) {
            throw new ExcecaoConfiguracaoInvalida("Intervalo e duração de manutenção de pista devem ser positivos.");
        }
    }

    // Cenários pré-definidos do PDF (ritmo calmo e visível para apresentação)
    public static Configuracao cenarioC1() {
        return new Configuracao(
                "C1 - Balanceado (3 pousos, 3 decolagens, 2 pistas)",
                2,          // pistas
                3,          // pousos iniciais
                3,          // decolagens iniciais
                120,        // painel a cada 120ms
                3500,       // voo: 3.5s
                3000,       // solo: 3.0s
                2500,       // operação na pista: 2.5s (dá tempo de ver!)
                40_000,     // autonomia: 40s
                0.30,       // abaixo de 30% vira emergência
                6000,       // aging: 6s
                2,          // no máx 2 pousos seguidos antes de alternar
                true,       // manutenção ligada
                15_000,     // fecha a cada 15s
                4000        // fica fechada por 4s
        );
    }

    public static Configuracao cenarioC2() {
        return new Configuracao(
                "C2 - Pista Única / Estresse de Pouso (4 pousos, 1 pista)",
                1,          // 1 pista
                4,          // 4 aviões querendo pousar
                0,          // 0 decolando inicialmente
                120,        // painel a cada 120ms
                3000,       // voo: 3.0s
                3000,       // solo: 3.0s
                2200,       // operação na pista: 2.2s
                35_000,     // autonomia: 35s
                0.35,       // abaixo de 35% vira emergência
                5000,       // aging: 5s
                2,          // alternância
                false,      // manutenção desligada (com 1 pista não pode fechar)
                15_000,
                4000
        );
    }

    public String getNomeCenario() { return nomeCenario; }
    public int getNumeroPistas() { return numeroPistas; }
    public int getAvioesPousoInicial() { return avioesPousoInicial; }
    public int getAvioesDecolagemInicial() { return avioesDecolagemInicial; }
    public int getTotalAvioes() { return avioesPousoInicial + avioesDecolagemInicial; }
    public long getIntervaloPainelMs() { return intervaloPainelMs; }
    public long getDelayVooMillis() { return delayVooMillis; }
    public long getDelaySoloMillis() { return delaySoloMillis; }
    public long getDelayOperacaoMillis() { return delayOperacaoMillis; }
    public long getAutonomiaMillis() { return autonomiaMillis; }
    public double getLimiteEmergencia() { return limiteEmergencia; }
    public long getLimiteAgingMillis() { return limiteAgingMillis; }
    public int getLimitePousosSeguidos() { return limitePousosSeguidos; }
    public boolean isAtivarManutencao() { return ativarManutencao; }
    public long getManutencaoIntervaloMillis() { return manutencaoIntervaloMillis; }
    public long getManutencaoDuracaoMillis() { return manutencaoDuracaoMillis; }
}
