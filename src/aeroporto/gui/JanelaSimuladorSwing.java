package aeroporto.gui;

import aeroporto.aviao.Aviao;
import aeroporto.aviao.EstadoAviao;
import aeroporto.aviao.GeradorDeVoos;
import aeroporto.config.Configuracao;
import aeroporto.config.LeitorDeConfiguracao;
import aeroporto.monitor.MonitorDeInvariantes;
import aeroporto.pista.ControladorDeManutencao;
import aeroporto.pista.Pista;
import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.EstatisticasDaTorre;
import aeroporto.torre.RetratoDaTorre;
import aeroporto.torre.TorreDeControle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Interface gráfica completa em Java Swing com visual moderno escuro (Cockpit ATC).
 * Permite visualizar, interagir e alternar entre cenários da simulação em tempo real.
 */
public class JanelaSimuladorSwing extends JFrame {

    private volatile Configuracao config;
    private volatile TorreDeControle torre;
    private volatile List<Pista> pistas;
    private volatile List<Aviao> todosAvioes;
    private volatile MonitorDeInvariantes monitor;

    // Gerenciamento das threads em execução no cenário ativo
    private final List<Thread> threadsAtivas = new ArrayList<>();

    // Componentes gráficos
    private PainelRadar2D painelRadar;
    private JLabel lblSub;
    private JLabel lblTempo;
    private JLabel lblStatusInvariantes;
    private JLabel lblTotalPousos;
    private JLabel lblTotalDecolagens;
    private JLabel lblTotalEmergencias;
    private JLabel lblEsperaPouso;
    private JLabel lblEsperaDecolagem;
    private JLabel lblVazao;
    private JPanel pnlBarrasPistasContainer;
    private JProgressBar[] barrasPistas;
    private JTextArea txtLogs;
    private JComboBox<String> comboCenarios;

    private static final Color BG_DARK = new Color(13, 17, 23);
    private static final Color BG_CARD = new Color(22, 27, 34);
    private static final Color TEXT_MUTED = new Color(139, 148, 158);
    private static final Color TEXT_WHITE = new Color(240, 246, 252);
    private static final Color ACCENT_GREEN = new Color(46, 160, 67);
    private static final Color ACCENT_BLUE = new Color(56, 139, 253);
    private static final Color ACCENT_RED = new Color(248, 81, 73);
    private static final Color ACCENT_YELLOW = new Color(210, 153, 34);

    /**
     * Construtor principal que inicializa o simulador pelo identificador de cenário ("C1", "C2").
     */
    public JanelaSimuladorSwing(String cenarioInicial) {
        configurarJanela();
        montarComponentesBasicos();
        trocarCenario(cenarioInicial != null ? cenarioInicial : "C1");
        iniciarAtualizadorUI();
    }

    /**
     * Construtor de compatibilidade.
     */
    public JanelaSimuladorSwing(
            Configuracao config,
            TorreDeControle torre,
            List<Pista> pistas,
            List<Aviao> todosAvioes,
            MonitorDeInvariantes monitor) {
        this(config != null ? config.getNomeCenario() : "C1");
    }

    private void configurarJanela() {
        setTitle("✈ Simulador de Aeroporto Concorrente — Painel Gráfico ATC");
        setSize(1300, 840);
        setMinimumSize(new Dimension(1080, 720));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(8, 8));

        // Encerramento limpo das threads ao fechar a janela
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                interromperThreadsAtivas();
            }
        });
    }

    private void montarComponentesBasicos() {
        // 1. Barra Superior (Controles, Seletor de Cenários e Status)
        add(criarBarraSuperior(), BorderLayout.NORTH);

        // 2. Centro: Radar e Pistas em 2D (inicialmente com listas vazias, atualizadas em trocarCenario)
        painelRadar = new PainelRadar2D(torre, new ArrayList<>(), new ArrayList<>());
        add(painelRadar, BorderLayout.CENTER);

        // 3. Lado Direito: Dashboard de Estatísticas
        add(criarPainelLateralDireito(), BorderLayout.EAST);

        // 4. Rodapé: Terminal de Logs da Torre
        add(criarPainelLogs(), BorderLayout.SOUTH);
    }

    private JPanel criarBarraSuperior() {
        JPanel barra = new JPanel(new BorderLayout(15, 0));
        barra.setBackground(BG_CARD);
        barra.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(48, 54, 61), 1),
                new EmptyBorder(10, 16, 10, 16)
        ));

        // Título e Cenário
        JPanel pnlTitulo = new JPanel(new GridLayout(2, 1));
        pnlTitulo.setOpaque(false);
        JLabel lblTitulo = new JLabel("✈ CONTROLE DE TRÁFEGO AÉREO");
        lblTitulo.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblTitulo.setForeground(TEXT_WHITE);

        lblSub = new JLabel("Carregando cenário...");
        lblSub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblSub.setForeground(TEXT_MUTED);

        pnlTitulo.add(lblTitulo);
        pnlTitulo.add(lblSub);

        // Controles de Ação, Cenários e Relógio
        JPanel pnlControles = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        pnlControles.setOpaque(false);

        // Seletor de Cenários (Dropdown)
        JLabel lblTrocar = new JLabel("Cenário:");
        lblTrocar.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblTrocar.setForeground(TEXT_MUTED);

        String[] opcoes = {
            "C1 - Balanceado (2 pistas, 6 aviões)",
            "C2 - Pista Única (1 pista, 4 pousos)"
        };
        comboCenarios = new JComboBox<>(opcoes);
        comboCenarios.setFont(new Font("SansSerif", Font.BOLD, 12));
        comboCenarios.setBackground(new Color(33, 38, 45));
        comboCenarios.setForeground(TEXT_WHITE);
        comboCenarios.setFocusable(false);
        comboCenarios.addActionListener(e -> {
            int idx = comboCenarios.getSelectedIndex();
            trocarCenario(idx == 0 ? "C1" : "C2");
        });

        lblTempo = new JLabel("00:00");
        lblTempo.setFont(new Font("Monospaced", Font.BOLD, 16));
        lblTempo.setForeground(ACCENT_YELLOW);
        lblTempo.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(48, 54, 61), 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        lblStatusInvariantes = new JLabel("● INVARIANTES OK");
        lblStatusInvariantes.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatusInvariantes.setForeground(ACCENT_GREEN);
        lblStatusInvariantes.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(ACCENT_GREEN, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));

        JButton btnEmergencia = new JButton("🚨 Injetar Emergência");
        estilizarBotao(btnEmergencia, ACCENT_RED);
        btnEmergencia.addActionListener(e -> injetarEmergenciaManual());

        JButton btnManutencao = new JButton("🚧 Fechar/Reabrir Pista");
        estilizarBotao(btnManutencao, ACCENT_YELLOW);
        btnManutencao.addActionListener(e -> alternarFechamentoPista());

        pnlControles.add(lblTrocar);
        pnlControles.add(comboCenarios);
        pnlControles.add(lblStatusInvariantes);
        pnlControles.add(btnEmergencia);
        pnlControles.add(btnManutencao);
        pnlControles.add(lblTempo);

        barra.add(pnlTitulo, BorderLayout.WEST);
        barra.add(pnlControles, BorderLayout.EAST);

        return barra;
    }

    private JPanel criarPainelLateralDireito() {
        JPanel pnl = new JPanel();
        pnl.setLayout(new BoxLayout(pnl, BoxLayout.Y_AXIS));
        pnl.setBackground(BG_CARD);
        pnl.setPreferredSize(new Dimension(320, 0));
        pnl.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(48, 54, 61), 1),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel lblSecao = new JLabel("MÉTRICAS & ESTATÍSTICAS (Extra 6)");
        lblSecao.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblSecao.setForeground(TEXT_MUTED);
        lblSecao.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnl.add(lblSecao);
        pnl.add(Box.createVerticalStrut(12));

        // Cards de Contadores
        JPanel pnlCards = new JPanel(new GridLayout(2, 2, 8, 8));
        pnlCards.setOpaque(false);
        pnlCards.setMaximumSize(new Dimension(300, 130));
        pnlCards.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblTotalPousos = new JLabel("0", SwingConstants.CENTER);
        lblTotalDecolagens = new JLabel("0", SwingConstants.CENTER);
        lblTotalEmergencias = new JLabel("0", SwingConstants.CENTER);
        lblVazao = new JLabel("0.0", SwingConstants.CENTER);

        pnlCards.add(criarCardMetrica("POUSOS", lblTotalPousos, ACCENT_GREEN));
        pnlCards.add(criarCardMetrica("DECOLAGENS", lblTotalDecolagens, ACCENT_BLUE));
        pnlCards.add(criarCardMetrica("EMERGÊNCIAS", lblTotalEmergencias, ACCENT_RED));
        pnlCards.add(criarCardMetrica("OPS / MIN", lblVazao, TEXT_WHITE));
        pnl.add(pnlCards);

        pnl.add(Box.createVerticalStrut(16));

        // Médias de Tempo de Espera
        JLabel lblSecaoTempos = new JLabel("TEMPO MÉDIO DE ESPERA NA FILA");
        lblSecaoTempos.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblSecaoTempos.setForeground(TEXT_MUTED);
        lblSecaoTempos.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnl.add(lblSecaoTempos);
        pnl.add(Box.createVerticalStrut(8));

        lblEsperaPouso = new JLabel("🛬 Fila de Pouso: 0.00s");
        lblEsperaPouso.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lblEsperaPouso.setForeground(TEXT_WHITE);
        lblEsperaPouso.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnl.add(lblEsperaPouso);

        pnl.add(Box.createVerticalStrut(4));

        lblEsperaDecolagem = new JLabel("🛫 Fila de Decolagem: 0.00s");
        lblEsperaDecolagem.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lblEsperaDecolagem.setForeground(TEXT_WHITE);
        lblEsperaDecolagem.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnl.add(lblEsperaDecolagem);

        pnl.add(Box.createVerticalStrut(18));

        // Ocupação das Pistas
        JLabel lblSecaoPistas = new JLabel("OCUPAÇÃO DAS PISTAS (%)");
        lblSecaoPistas.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblSecaoPistas.setForeground(TEXT_MUTED);
        lblSecaoPistas.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnl.add(lblSecaoPistas);
        pnl.add(Box.createVerticalStrut(8));

        pnlBarrasPistasContainer = new JPanel();
        pnlBarrasPistasContainer.setLayout(new BoxLayout(pnlBarrasPistasContainer, BoxLayout.Y_AXIS));
        pnlBarrasPistasContainer.setOpaque(false);
        pnlBarrasPistasContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        pnl.add(pnlBarrasPistasContainer);

        pnl.add(Box.createVerticalGlue());
        return pnl;
    }

    private void reconstruirBarrasPistas() {
        pnlBarrasPistasContainer.removeAll();
        barrasPistas = new JProgressBar[pistas.size()];

        for (int i = 0; i < pistas.size(); i++) {
            Pista p = pistas.get(i);
            JLabel lblPista = new JLabel("Pista " + p.getNumero());
            lblPista.setFont(new Font("SansSerif", Font.BOLD, 11));
            lblPista.setForeground(TEXT_WHITE);
            lblPista.setAlignmentX(Component.LEFT_ALIGNMENT);
            pnlBarrasPistasContainer.add(lblPista);

            JProgressBar bar = new JProgressBar(0, 100);
            bar.setValue(0);
            bar.setStringPainted(true);
            bar.setForeground(ACCENT_BLUE);
            bar.setBackground(new Color(33, 38, 45));
            bar.setBorder(BorderFactory.createLineBorder(new Color(48, 54, 61)));
            bar.setMaximumSize(new Dimension(300, 20));
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            barrasPistas[i] = bar;

            pnlBarrasPistasContainer.add(bar);
            pnlBarrasPistasContainer.add(Box.createVerticalStrut(6));
        }

        pnlBarrasPistasContainer.revalidate();
        pnlBarrasPistasContainer.repaint();
    }

    private JPanel criarCardMetrica(String titulo, JLabel lblValor, Color corValor) {
        JPanel card = new JPanel(new GridLayout(2, 1));
        card.setBackground(new Color(30, 36, 46));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(48, 54, 61), 1),
                new EmptyBorder(6, 6, 6, 6)
        ));

        JLabel lblT = new JLabel(titulo, SwingConstants.CENTER);
        lblT.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblT.setForeground(TEXT_MUTED);

        lblValor.setFont(new Font("SansSerif", Font.BOLD, 18));
        lblValor.setForeground(corValor);

        card.add(lblT);
        card.add(lblValor);
        return card;
    }

    private JPanel criarPainelLogs() {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.setBackground(BG_CARD);
        pnl.setPreferredSize(new Dimension(0, 130));
        pnl.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(48, 54, 61), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JLabel lblTituloLogs = new JLabel("TRANSMISSÕES DA TORRE (REGISTRO DE EVENTOS)");
        lblTituloLogs.setFont(new Font("Monospaced", Font.BOLD, 11));
        lblTituloLogs.setForeground(TEXT_MUTED);
        pnl.add(lblTituloLogs, BorderLayout.NORTH);

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setBackground(new Color(10, 14, 20));
        txtLogs.setForeground(new Color(130, 255, 160));
        txtLogs.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtLogs.setLineWrap(true);

        JScrollPane scroll = new JScrollPane(txtLogs);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(33, 38, 45)));
        pnl.add(scroll, BorderLayout.CENTER);

        return pnl;
    }

    private void estilizarBotao(JButton btn, Color corDestaque) {
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(corDestaque.darker());
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(corDestaque, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    /**
     * Alterna o cenário dinamicamente: interrompe as threads anteriores com segurança
     * e instancia o novo cenário com novas pistas, torre e aviões.
     */
    public synchronized void trocarCenario(String idCenario) {
        interromperThreadsAtivas();

        this.config = LeitorDeConfiguracao.carregar(idCenario);
        lblSub.setText("Cenário: " + config.getNomeCenario());

        // 1. Cria novas pistas
        List<Pista> novasPistas = new ArrayList<>();
        for (int i = 1; i <= config.getNumeroPistas(); i++) {
            novasPistas.add(new Pista(i));
        }
        this.pistas = novasPistas;

        // 2. Cria nova torre
        this.torre = new TorreDeControle(
                novasPistas,
                config.getLimiteAgingMillis(),
                config.getLimitePousosSeguidos()
        );

        // 3. Cria novos aviões
        GeradorDeVoos gerador = new GeradorDeVoos();
        List<Aviao> novosAvioes = new ArrayList<>();

        for (int i = 0; i < config.getAvioesPousoInicial(); i++) {
            String codigo = gerador.gerarCodigo();
            novosAvioes.add(new Aviao(
                    codigo,
                    EstadoAviao.EM_VOO,
                    torre,
                    config.getDelaySoloMillis(),
                    config.getDelayVooMillis(),
                    config.getDelayOperacaoMillis(),
                    config.getAutonomiaMillis(),
                    config.getLimiteEmergencia()
            ));
        }

        for (int i = 0; i < config.getAvioesDecolagemInicial(); i++) {
            String codigo = gerador.gerarCodigo();
            novosAvioes.add(new Aviao(
                    codigo,
                    EstadoAviao.EM_SOLO,
                    torre,
                    config.getDelaySoloMillis(),
                    config.getDelayVooMillis(),
                    config.getDelayOperacaoMillis(),
                    config.getAutonomiaMillis(),
                    config.getLimiteEmergencia()
            ));
        }
        this.todosAvioes = novosAvioes;

        // 4. Cria novo monitor de invariantes
        this.monitor = new MonitorDeInvariantes(novosAvioes, torre, 300);

        // 5. Controlador de manutenção
        ControladorDeManutencao man = null;
        if (config.isAtivarManutencao() && novasPistas.size() > 1) {
            man = new ControladorDeManutencao(
                    novasPistas,
                    torre,
                    config.getManutencaoIntervaloMillis(),
                    config.getManutencaoDuracaoMillis()
            );
        }

        // 6. Atualiza a tela gráfica 2D e as barras laterais
        painelRadar.atualizarCenario(torre, pistas, todosAvioes);
        reconstruirBarrasPistas();

        // 7. Inicia as novas threads concorrentes
        Thread tMon = new Thread(monitor, "Thread-MonitorInvariantes");
        tMon.start();
        threadsAtivas.add(tMon);

        if (man != null) {
            Thread tMan = new Thread(man, "Thread-ManutencaoPistas");
            tMan.start();
            threadsAtivas.add(tMan);
        }

        for (Aviao a : novosAvioes) {
            Thread tAv = new Thread(a, "Thread-Aviao-" + a.getCodigoVoo());
            tAv.start();
            threadsAtivas.add(tAv);
        }

        RegistroDeEventos.publicar("SISTEMA: Carregado cenário " + config.getNomeCenario());
    }

    private synchronized void interromperThreadsAtivas() {
        for (Thread t : threadsAtivas) {
            t.interrupt();
        }
        threadsAtivas.clear();
    }

    private void iniciarAtualizadorUI() {
        Timer timer = new Timer(150, e -> atualizarDados());
        timer.start();
    }

    private void atualizarDados() {
        if (torre == null) return;

        EstatisticasDaTorre est = torre.getEstatisticas();
        if (est != null) {
            long seg = est.getTempoDecorridoMillis() / 1000;
            lblTempo.setText(String.format("%02d:%02d", seg / 60, seg % 60));

            lblTotalPousos.setText(String.valueOf(est.getTotalPousos()));
            lblTotalDecolagens.setText(String.valueOf(est.getTotalDecolagens()));
            lblTotalEmergencias.setText(String.valueOf(est.getEmergenciasAtendidas()));

            double vazao = seg > 0 ? ((est.getTotalPousos() + est.getTotalDecolagens()) / (double) seg) * 60.0 : 0.0;
            lblVazao.setText(String.format("%.1f", vazao));

            lblEsperaPouso.setText(String.format("🛬 Fila de Pouso: %.2fs", est.getEsperaMediaPousoMillis() / 1000.0));
            lblEsperaDecolagem.setText(String.format("🛫 Fila de Decolagem: %.2fs", est.getEsperaMediaDecolagemMillis() / 1000.0));

            if (barrasPistas != null) {
                for (int i = 0; i < Math.min(barrasPistas.length, pistas.size()); i++) {
                    int num = pistas.get(i).getNumero();
                    double oc = est.getOcupacaoPercentual(num);
                    barrasPistas[i].setValue((int) oc);
                    barrasPistas[i].setString(String.format("%.1f%%", oc));
                }
            }
        }

        if (monitor != null) {
            if (monitor.isConsistente()) {
                lblStatusInvariantes.setText("● " + monitor.getStatusAtual());
                lblStatusInvariantes.setForeground(ACCENT_GREEN);
            } else {
                lblStatusInvariantes.setText("▲ " + monitor.getStatusAtual());
                lblStatusInvariantes.setForeground(ACCENT_RED);
            }
        }

        // Atualiza logs do buffer circular
        List<String> logs = RegistroDeEventos.obterUltimas(20);
        StringBuilder sb = new StringBuilder();
        for (String l : logs) {
            sb.append(l).append("\n");
        }
        txtLogs.setText(sb.toString());
        txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
    }

    private void injetarEmergenciaManual() {
        if (torre == null) return;
        RetratoDaTorre r = torre.getRetrato();
        List<Aviao> fila = r.getFilaDePouso();
        if (!fila.isEmpty()) {
            Aviao a = fila.get(new Random().nextInt(fila.size()));
            a.setEmergencia(true);
            RegistroDeEventos.publicar("OPERADOR: Declarada emergência manual para " + a.getCodigoVoo());
        } else {
            for (Aviao a : todosAvioes) {
                if (!a.estaEmEmergencia()) {
                    a.setEmergencia(true);
                    RegistroDeEventos.publicar("OPERADOR: Declarada emergência manual para " + a.getCodigoVoo());
                    break;
                }
            }
        }
    }

    private void alternarFechamentoPista() {
        if (pistas.size() <= 1) {
            JOptionPane.showMessageDialog(this, "Não é permitido fechar a única pista em operação!",
                    "Operação Não Permitida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Pista p = pistas.get(pistas.size() - 1);
        if (p.estaFechada()) {
            p.reabrir();
            torre.notificarPistaLiberada(p);
            RegistroDeEventos.publicar("OPERADOR: Pista " + p.getNumero() + " foi REABERTA manualmente.");
        } else {
            p.fechar("Inspeção Manual");
            RegistroDeEventos.publicar("OPERADOR: Pista " + p.getNumero() + " foi FECHADA para inspeção.");
        }
    }
}
