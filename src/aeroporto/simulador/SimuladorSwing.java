package aeroporto.simulador;

import aeroporto.aviao.Aviao;
import aeroporto.aviao.EstadoAviao;
import aeroporto.aviao.GeradorDeVoos;
import aeroporto.config.Configuracao;
import aeroporto.config.LeitorDeConfiguracao;
import aeroporto.gui.JanelaSimuladorSwing;
import aeroporto.monitor.MonitorDeInvariantes;
import aeroporto.pista.ControladorDeManutencao;
import aeroporto.pista.Pista;
import aeroporto.torre.TorreDeControle;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto de entrada gráfico com interface Java Swing interativa.
 */
public class SimuladorSwing {

    public static void main(String[] args) {
        String argumentoCenario = (args != null && args.length > 0) ? args[0] : "C1";
        Configuracao config = LeitorDeConfiguracao.carregar(argumentoCenario);

        // 1. Criação das pistas (Pessoa 3)
        List<Pista> pistas = new ArrayList<>();
        for (int i = 1; i <= config.getNumeroPistas(); i++) {
            pistas.add(new Pista(i));
        }

        // 2. Torre de controle (Pessoa 1)
        TorreDeControle torre = new TorreDeControle(
                pistas,
                config.getLimiteAgingMillis(),
                config.getLimitePousosSeguidos()
        );

        // 3. Criação dos aviões (Pessoa 2)
        GeradorDeVoos gerador = new GeradorDeVoos();
        List<Aviao> todosAvioes = new ArrayList<>();
        List<Thread> threadsAvioes = new ArrayList<>();

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

        // 5. Controlador de Manutenção (Extra 4 — Pessoa 3)
        Thread threadManutencao = null;
        if (config.isAtivarManutencao() && pistas.size() > 1) {
            ControladorDeManutencao controlador = new ControladorDeManutencao(
                    pistas, torre, config.getManutencaoIntervaloMillis(), config.getManutencaoDuracaoMillis()
            );
            threadManutencao = new Thread(controlador, "Thread-ManutencaoPistas");
        }

        // 6. Inicia a interface gráfica na Event Dispatch Thread (EDT) do Swing
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}

            JanelaSimuladorSwing janela = new JanelaSimuladorSwing(
                    config, torre, pistas, todosAvioes, monitor
            );
            janela.setVisible(true);
        });

        // 7. Inicia as threads concorrentes
        threadMonitor.start();
        if (threadManutencao != null) {
            threadManutencao.start();
        }
        for (Thread t : threadsAvioes) {
            t.start();
        }
    }
}
