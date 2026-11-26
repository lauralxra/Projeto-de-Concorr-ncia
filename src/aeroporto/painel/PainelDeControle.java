package aeroporto.painel;

import aeroporto.aviao.Aviao;
import aeroporto.aviao.EstadoAviao;
import aeroporto.config.Configuracao;
import aeroporto.monitor.MonitorDeInvariantes;
import aeroporto.pista.Pista;
import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.EstatisticasDaTorre;
import aeroporto.torre.RetratoDaTorre;
import aeroporto.torre.TorreDeControle;

import java.util.List;

/**
 * Painel de controle visual animado para o terminal.
 * Executa em thread própria redesenhando a cada N milissegundos com códigos ANSI.
 * Mostra a movimentação dos aviões no ar, nas pistas e em solo com arte ASCII interativa.
 */
public class PainelDeControle implements Runnable {

    // Cores e estilos ANSI
    private static final String RESET = "\033[0m";
    private static final String NEGRITO = "\033[1m";
    private static final String VERMELHO = "\033[91m";
    private static final String VERDE = "\033[92m";
    private static final String AMARELO = "\033[93m";
    private static final String AZUL = "\033[94m";
    private static final String MAGENTA = "\033[95m";
    private static final String CIANO = "\033[96m";
    private static final String BRANCO = "\033[97m";
    private static final String CINZA = "\033[90m";

    // Controles de cursor ANSI
    private static final String LIMPAR_TELA = "\033[H\033[2J";
    private static final String MOVER_INICIO = "\033[H";
    private static final String OCULTAR_CURSOR = "\033[?25l";
    private static final String MOSTRAR_CURSOR = "\033[?25h";

    private final Configuracao config;
    private final TorreDeControle torre;
    private final List<Aviao> todosAvioes;
    private final MonitorDeInvariantes monitor;

    private int frameTick = 0;
    private volatile boolean rodando = true;

    public PainelDeControle(
            Configuracao config,
            TorreDeControle torre,
            List<Aviao> todosAvioes,
            MonitorDeInvariantes monitor) {
        this.config = config;
        this.torre = torre;
        this.todosAvioes = todosAvioes;
        this.monitor = monitor;
    }

    public void parar() {
        this.rodando = false;
    }

    @Override
    public void run() {
        // Ativa modo silencioso no registro para que sysouts concorrentes não sujem o terminal
        RegistroDeEventos.setSilencioso(true);

        // Oculta cursor piscante e limpa a tela uma vez
        System.out.print(OCULTAR_CURSOR + LIMPAR_TELA);
        System.out.flush();

        try {
            while (rodando && !Thread.currentThread().isInterrupted()) {
                desenharQuadro();
                frameTick++;
                Thread.sleep(config.getIntervaloPainelMs());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Restaura o cursor antes de finalizar
            System.out.print(MOSTRAR_CURSOR + RESET);
            System.out.flush();
        }
    }

    private void desenharQuadro() {
        RetratoDaTorre retrato = torre.getRetrato();
        EstatisticasDaTorre estatisticas = torre.getEstatisticas();
        List<Pista> pistas = torre.getPistas();

        StringBuilder sb = new StringBuilder(4096);
        sb.append(MOVER_INICIO);

        // 1. Cabeçalho Principal
        desenharCabecalho(sb, estatisticas);

        // 2. Barra de Integridade do Monitor de Invariantes
        desenharBarraStatus(sb);

        // 3. Espaço Aéreo e Fila de Pouso (com animação de aproximação e combustível)
        desenharEspacoAereo(sb, retrato);

        // 4. Pistas de Pouso/Decolagem (com asfalto animado e avião se deslocando)
        desenharPistas(sb, pistas, estatisticas);

        // 5. Pátio e Fila de Decolagem
        desenharSoloETaxiway(sb, retrato);

        // 6. Painel de Estatísticas
        desenharEstatisticas(sb, estatisticas);

        // 7. Registro de Últimos Eventos
        desenharLogEventos(sb);

        // Rodapé com instruções
        sb.append(CINZA).append("─────────────────────────────────────────────────────────────────────────────────────────────\n").append(RESET);
        sb.append(CINZA).append(" Pressione ").append(NEGRITO).append(BRANCO).append("Ctrl + C").append(RESET)
          .append(CINZA).append(" a qualquer momento para encerrar a simulação e ver o relatório final.\n").append(RESET);

        System.out.print(sb.toString());
        System.out.flush();
    }

    private void desenharCabecalho(StringBuilder sb, EstatisticasDaTorre est) {
        long segundos = (est != null) ? est.getTempoDecorridoMillis() / 1000 : 0;
        String tempoStr = String.format("%02d:%02d", segundos / 60, segundos % 60);

        sb.append(CIANO).append(NEGRITO)
          .append("╔═══════════════════════════════════════════════════════════════════════════════════════════╗\n")
          .append("║  ✈  SIMULADOR DE AEROPORTO CONCORRENTE — TORRE DE CONTROLE & TRÁFEGO AÉREO               ║\n")
          .append("╠═══════════════════════════════════════════════════════════════════════════════════════════╣\n")
          .append(RESET);

        sb.append(String.format("║  Cenário: %-42s  Tempo: %s%-7s%s  Frame: %-6d ║\n",
                truncar(config.getNomeCenario(), 42),
                AMARELO + NEGRITO, tempoStr, RESET,
                frameTick));

        sb.append(CIANO).append(NEGRITO)
          .append("╚═══════════════════════════════════════════════════════════════════════════════════════════╝\n")
          .append(RESET);
    }

    private void desenharBarraStatus(StringBuilder sb) {
        sb.append(CINZA).append(" [STATUS] ").append(RESET);

        if (monitor != null) {
            if (monitor.isConsistente()) {
                sb.append(VERDE).append(NEGRITO).append("● ").append(monitor.getStatusAtual()).append(RESET);
            } else {
                sb.append(VERMELHO).append(NEGRITO).append("▲ ").append(monitor.getStatusAtual()).append(RESET);
            }
        }

        sb.append("\n");
    }

    private void desenharEspacoAereo(StringBuilder sb, RetratoDaTorre retrato) {
        List<Aviao> filaPouso = (retrato != null) ? retrato.getFilaDePouso() : List.of();

        sb.append("\n").append(AZUL).append(NEGRITO)
          .append("☁  ESPAÇO AÉREO & FILA DE POUSO ").append(RESET)
          .append(CINZA).append("(").append(filaPouso.size()).append(" aguardando autorização):").append(RESET).append("\n");

        if (filaPouso.isEmpty()) {
            String nuvemAnimada = (frameTick % 4 == 0) ? "      ☁              ☁                 ☁" :
                                  (frameTick % 4 == 1) ? "        ☁                ☁                 ☁" :
                                  (frameTick % 4 == 2) ? "          ☁                ☁                 ☁" :
                                                         "    ☁                ☁                 ☁";
            sb.append(CINZA).append(nuvemAnimada).append("\n").append(RESET);
            sb.append(VERDE).append("    [ Céu Livre — Nenhuma aeronave na fila de aproximação ]\n").append(RESET);
        } else {
            int pos = 1;
            for (Aviao aviao : filaPouso) {
                boolean emergencia = aviao.estaEmEmergencia();
                String corVoo = emergencia ? (VERMELHO + NEGRITO) : (BRANCO + NEGRITO);
                String tagEmergencia = emergencia ? (VERMELHO + NEGRITO + " [🚨 EMERGÊNCIA]" + RESET) : "";
                int combustivel = aviao.porcentagemCombustivel();

                // Animação de rastro de voo se aproximando da torre
                int espacos = Math.max(2, 24 - (pos * 3) + (frameTick % 3));
                String rastro = ".".repeat(Math.max(1, espacos));

                sb.append(String.format("  #%d %-10s %s✈ %s %-12s (Combustível: %d%%)%s\n",
                        pos,
                        corVoo + aviao.getCodigoVoo() + RESET,
                        AMARELO + rastro + RESET,
                        corVoo + "──▶" + RESET,
                        tagEmergencia,
                        combustivel,
                        RESET));
                pos++;
            }
        }
    }

    private void desenharPistas(StringBuilder sb, List<Pista> pistas, EstatisticasDaTorre est) {
        sb.append("\n").append(AMARELO).append(NEGRITO)
          .append("════ PISTAS DE POUSO E DECOLAGEM ═══════════════════════════════════════════════════════════\n")
          .append(RESET);

        for (Pista pista : pistas) {
            int num = pista.getNumero();
            Aviao ocupante = pista.getOcupante();
            boolean fechada = pista.estaFechada();
            double ocupacao = (est != null) ? est.getOcupacaoPercentual(num) : 0.0;

            sb.append(String.format("%s[ PISTA %02d ]%s ", NEGRITO + CIANO, num, RESET));

            if (fechada) {
                String motivo = pista.getMotivoFechamento() != null ? pista.getMotivoFechamento() : "Interditada";
                sb.append(VERMELHO).append(NEGRITO)
                  .append("🚧 🚧  INTERDITADA: ")
                  .append(motivo.toUpperCase())
                  .append("  🚧 🚧")
                  .append(RESET);
                sb.append(String.format("%s (Uso: %.1f%%)%s\n", CINZA, ocupacao, RESET));
            } else if (ocupante == null) {
                String faixaCentro = (frameTick % 2 == 0) ? "─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─" : " ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ";
                sb.append(CINZA).append("══[").append(VERDE).append(" ").append(faixaCentro).append(" ")
                  .append(CINZA).append("]══ ").append(VERDE).append(NEGRITO).append("LIVRE").append(RESET);
                sb.append(String.format("%s (Uso: %.1f%%)%s\n", CINZA, ocupacao, RESET));
            } else {
                EstadoAviao estadoOcupante = ocupante.getEstado();
                boolean pouso = (estadoOcupante == EstadoAviao.POUSANDO);
                String icone = pouso ? "🛬" : "🛫";
                String acaoStr = pouso ? "POUSANDO" : "DECOLANDO";
                String corAcao = pouso ? (AMARELO + NEGRITO) : (CIANO + NEGRITO);

                // Deslocamento visual na pista com base no frameTick
                int etapa = (frameTick % 6);
                int antes = Math.max(1, etapa * 5);
                int depois = Math.max(1, 28 - antes);

                String trilhoAntes = "=".repeat(antes);
                String trilhoDepois = "-".repeat(depois);

                sb.append(CINZA).append("══[").append(corAcao)
                  .append(trilhoAntes).append(" ").append(icone).append(" ")
                  .append(BRANCO).append(NEGRITO).append(ocupante.getCodigoVoo()).append(" ")
                  .append(corAcao).append("[").append(acaoStr).append("]").append(" ")
                  .append(trilhoDepois)
                  .append(CINZA).append("]══ ").append(corAcao).append("OCUPADA").append(RESET);

                sb.append(String.format("%s (Uso: %.1f%%)%s\n", CINZA, ocupacao, RESET));
            }
        }
    }

    private void desenharSoloETaxiway(StringBuilder sb, RetratoDaTorre retrato) {
        List<Aviao> filaDecolagem = (retrato != null) ? retrato.getFilaDeDecolagem() : List.of();

        sb.append("\n").append(VERDE).append(NEGRITO)
          .append("🚏 SOLO, PÁTIO & CABECEIRA DE DECOLAGEM ").append(RESET)
          .append(CINZA).append("(").append(filaDecolagem.size()).append(" na taxiway):").append(RESET).append("\n");

        if (filaDecolagem.isEmpty()) {
            sb.append(CINZA).append("    [ Taxiway desimpedida — Nenhuma aeronave aguardando decolagem ]\n").append(RESET);
        } else {
            sb.append("    ");
            for (int i = 0; i < filaDecolagem.size(); i++) {
                Aviao a = filaDecolagem.get(i);
                sb.append(CIANO).append(NEGRITO).append("🛫 ").append(a.getCodigoVoo()).append(RESET);
                if (i < filaDecolagem.size() - 1) {
                    sb.append(" ──▶ ");
                }
            }
            sb.append("\n");
        }

        // Aviões em trânsito no pátio / voando
        sb.append(CINZA).append("    Outros voos: ").append(RESET);
        int impressos = 0;
        for (Aviao a : todosAvioes) {
            if (a.getEstado() == EstadoAviao.EM_SOLO || a.getEstado() == EstadoAviao.EM_VOO) {
                String icone = (a.getEstado() == EstadoAviao.EM_VOO) ? "✈ (em rota)" : "🏷 (portão/pátio)";
                sb.append(BRANCO).append(a.getCodigoVoo()).append(CINZA).append(" ").append(icone).append("  ").append(RESET);
                impressos++;
            }
        }
        if (impressos == 0) {
            sb.append(CINZA).append("Nenhum avião em trânsito secundário.").append(RESET);
        }
        sb.append("\n");
    }

    private void desenharEstatisticas(StringBuilder sb, EstatisticasDaTorre est) {
        if (est == null) return;

        sb.append("\n").append(MAGENTA).append(NEGRITO)
          .append("📊 ESTATÍSTICAS DA OPERAÇÃO [Extra 6] ═════════════════════════════════════════════════════\n")
          .append(RESET);

        int pousos = est.getTotalPousos();
        int decolagens = est.getTotalDecolagens();
        int emergencias = est.getEmergenciasAtendidas();
        double esperaPouso = est.getEsperaMediaPousoMillis() / 1000.0;
        double esperaDecolagem = est.getEsperaMediaDecolagemMillis() / 1000.0;
        long tempoTotalSegundos = Math.max(1, est.getTempoDecorridoMillis() / 1000);
        double vazaoMinuto = ((pousos + decolagens) / (double) tempoTotalSegundos) * 60.0;

        sb.append(String.format("  Pousos Concluídos: %s%-4d%s │ Decolagens: %s%-4d%s │ Emergências Atendidas: %s%-3d%s\n",
                VERDE + NEGRITO, pousos, RESET,
                CIANO + NEGRITO, decolagens, RESET,
                VERMELHO + NEGRITO, emergencias, RESET));

        sb.append(String.format("  Média Espera Pouso: %s%.2fs%s │ Média Espera Decolagem: %s%.2fs%s │ Vazão: %s%.1f ops/min%s\n",
                AMARELO, esperaPouso, RESET,
                AMARELO, esperaDecolagem, RESET,
                BRANCO + NEGRITO, vazaoMinuto, RESET));
    }

    private void desenharLogEventos(StringBuilder sb) {
        sb.append("\n").append(CINZA).append(NEGRITO)
          .append("📻 ÚLTIMAS TRANSMISSÕES DA TORRE (Registro de Eventos) ────────────────────────────────────\n")
          .append(RESET);

        List<String> ultimas = RegistroDeEventos.obterUltimas(5);
        if (ultimas.isEmpty()) {
            sb.append(CINZA).append("  [Nenhuma mensagem recente registrada]\n").append(RESET);
        } else {
            for (String msg : ultimas) {
                String linha = msg;
                if (linha.contains("EMERGÊNCIA")) {
                    linha = VERMELHO + NEGRITO + linha + RESET;
                } else if (linha.contains("autorizado") || linha.contains("liberada") || linha.contains("pousou") || linha.contains("decolou")) {
                    linha = VERDE + linha + RESET;
                } else if (linha.contains("FECHADA") || linha.contains("ALERTA")) {
                    linha = AMARELO + linha + RESET;
                } else {
                    linha = CINZA + linha + RESET;
                }
                sb.append("  ").append(linha).append("\n");
            }
        }
    }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return (s.length() <= max) ? s : s.substring(0, max - 3) + "...";
    }
}
