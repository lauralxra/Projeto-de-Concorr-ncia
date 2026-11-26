package aeroporto.simulador;

import aeroporto.gui.JanelaSimuladorSwing;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Ponto de entrada gráfico com interface Java Swing interativa.
 */
public class SimuladorSwing {

    public static void main(String[] args) {
        String argumentoCenario = (args != null && args.length > 0) ? args[0] : "C1";

        // Inicia a interface gráfica na Event Dispatch Thread (EDT) do Swing
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}

            JanelaSimuladorSwing janela = new JanelaSimuladorSwing(argumentoCenario);
            janela.setVisible(true);
        });
    }
}
