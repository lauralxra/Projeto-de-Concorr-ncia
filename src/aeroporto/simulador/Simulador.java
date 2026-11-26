package aeroporto.simulador;

import aeroporto.aviao.Aviao;
import aeroporto.aviao.EstadoAviao;
import aeroporto.aviao.GeradorDeVoos;
import aeroporto.config.Configuracao;
import aeroporto.config.LeitorDeConfiguracao;
import aeroporto.monitor.MonitorDeInvariantes;
import aeroporto.painel.PainelDeControle;
import aeroporto.pista.ControladorDeManutencao;
import aeroporto.pista.Pista;
import aeroporto.torre.EstatisticasDaTorre;
import aeroporto.torre.TorreDeControle;

import java.util.ArrayList;
import java.util.List;

/**
 * Ponto de entrada (main) do Simulador de Aeroporto Concorrente.
 * Orquestra a inicialização de todos os módulos, gerencia o ciclo de vida das threads
 * e assegura o encerramento limpo via Shutdown Hook (Ctrl + C).
 */
public class Simulador {

    public static void main(String[] args) {
        boolean modoGrafico = false;
        String argumentoCenario = "C1";

        if (args != null) {
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--gui") || arg.equalsIgnoreCase("-gui")) {
                    modoGrafico = true;
                } else if (!arg.startsWith("-")) {
                    argumentoCenario = arg;
                }
            }
        }

        if (modoGrafico) {
            SimuladorSwing.main(new String[]{argumentoCenario});
            return;
        }

        Configuracao config = LeitorDeConfiguracao.carregar(argumentoCenario);
        System.out.println("Iniciando simulação com cenário: " + config.getNomeCenario());

        // 1. Criação das pistas (Pessoa 3)
        List<Pista> pistas = new ArrayList<>();
        for (int i = 1; i <= config.getNumeroPistas(); i++) {
            pistas.add(new Pista(i));
        }

        // 2. Criação da torre de controle (Pessoa 1)
        TorreDeControle torre = new TorreDeControle(
                pistas,
                config.getLimiteAgingMillis(),
                config.getLimitePousosSeguidos()
        );

        // 3. Criação dos aviões (Pessoa 2)
        GeradorDeVoos gerador = new GeradorDeVoos();
        List<Aviao> todosAvioes = new ArrayList<>();
        List<Thread> threadsAvioes = new ArrayList<>();

        // Aviões que iniciam no ar (desejando pousar)
        for (int i = 0; i < config.getAvioesPousoInicial(); i++) {
            String codigo = gerador.gerarCodigo();
            Aviao aviao = new Aviao(
                    codigo,
                    EstadoAviao.EM_VOO,
                    torre,
                    config.getDelaySoloMillis(),
                    config.getDelayVooMillis(),
                    config.getDelayOperacaoMillis(),
                    config.getAutonomiaMillis(),
                    config.getLimiteEmergencia()
            );
            todosAvioes.add(aviao);
            threadsAvioes.add(new Thread(aviao, "Thread-Aviao-" + codigo));
        }

        // Aviões que iniciam em solo (desejando decolar)
        for (int i = 0; i < config.getAvioesDecolagemInicial(); i++) {
            String codigo = gerador.gerarCodigo();
            Aviao aviao = new Aviao(
                    codigo,
                    EstadoAviao.EM_SOLO,
                    torre,
                    config.getDelaySoloMillis(),
                    config.getDelayVooMillis(),
                    config.getDelayOperacaoMillis(),
                    config.getAutonomiaMillis(),
                    config.getLimiteEmergencia()
            );
            todosAvioes.add(aviao);
            threadsAvioes.add(new Thread(aviao, "Thread-Aviao-" + codigo));
        }

        // 4. Monitor de Invariantes (Extra 7 — Pessoa 4)
        MonitorDeInvariantes monitor = new MonitorDeInvariantes(todosAvioes, torre, 300);
        Thread threadMonitor = new Thread(monitor, "Thread-MonitorInvariantes");

        // 5. Controlador de Manutenção/Clima (Extra 4 — Pessoa 3)
        Thread threadManutencao = null;
        if (config.isAtivarManutencao() && pistas.size() > 1) {
            ControladorDeManutencao controladorManutencao = new ControladorDeManutencao(
                    pistas, torre, config.getManutencaoIntervaloMillis(), config.getManutencaoDuracaoMillis()
            );
            threadManutencao = new Thread(controladorManutencao, "Thread-ManutencaoPistas");
        }

        // 6. Painel de Controle Visual Animado (Pessoa 4)
        PainelDeControle painel = new PainelDeControle(config, torre, todosAvioes, monitor);
        Thread threadPainel = new Thread(painel, "Thread-PainelControle");

        // 7. Shutdown Hook para encerramento gracioso no Ctrl + C
        Thread finalThreadManutencao = threadManutencao;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            painel.parar();

            // Interrompe todas as threads com segurança
            threadsAvioes.forEach(Thread::interrupt);
            threadMonitor.interrupt();
            if (finalThreadManutencao != null) {
                finalThreadManutencao.interrupt();
            }
            threadPainel.interrupt();

            // Restaura o cursor ANSI do terminal
            System.out.print("\033[?25h\033[0m\n\n");
            System.out.println("===================================================================");
            System.out.println("              SIMULAÇÃO FINALIZADA (Ctrl + C)                      ");
            System.out.println("===================================================================");
            System.out.printf(" Cenário executado: %s\n", config.getNomeCenario());

            EstatisticasDaTorre est = torre.getEstatisticas();
            if (est != null) {
                System.out.printf(" Tempo total de simulação: %.1f segundos\n", est.getTempoDecorridoMillis() / 1000.0);
                System.out.printf(" Total de pousos concluídos: %d\n", est.getTotalPousos());
                System.out.printf(" Total de decolagens concluídas: %d\n", est.getTotalDecolagens());
                System.out.printf(" Emergências atendidas: %d\n", est.getEmergenciasAtendidas());
                System.out.printf(" Tempo médio de espera no pouso: %.2fs\n", est.getEsperaMediaPousoMillis() / 1000.0);
                System.out.printf(" Tempo médio de espera na decolagem: %.2fs\n", est.getEsperaMediaDecolagemMillis() / 1000.0);
                System.out.println("-------------------------------------------------------------------");
                for (Pista p : pistas) {
                    System.out.printf(" Ocupação da Pista %02d: %.1f%% (%d operações)\n",
                            p.getNumero(), est.getOcupacaoPercentual(p.getNumero()),
                            est.getOperacoesPorPista().getOrDefault(p.getNumero(), 0));
                }
            }
            System.out.println("-------------------------------------------------------------------");
            System.out.printf(" Monitor de Invariantes: %d checagens, %d violações.\n",
                    monitor.getTotalChecagens(), monitor.getViolacoesDetectadas());
            System.out.println("===================================================================");
        }));

        // Inicia todas as threads concorrentes
        threadMonitor.start();
        if (threadManutencao != null) {
            threadManutencao.start();
        }
        for (Thread t : threadsAvioes) {
            t.start();
        }
        threadPainel.start();

        try {
            threadPainel.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
