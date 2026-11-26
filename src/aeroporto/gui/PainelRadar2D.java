package aeroporto.gui;

import aeroporto.aviao.Aviao;
import aeroporto.aviao.EstadoAviao;
import aeroporto.pista.Pista;
import aeroporto.torre.TorreDeControle;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * Painel 2D de alta definição que desenha o Radar ATC, as Pistas e o Pátio do aeroporto.
 * Renderiza aviões se deslocando suavemente nas manobras de voo, aproximação, pouso e decolagem.
 */
public class PainelRadar2D extends JPanel {

    private volatile TorreDeControle torre;
    private volatile List<Pista> pistas;
    private volatile List<Aviao> todosAvioes;

    // Cores temáticas de Cockpit / ATC escuro
    private static final Color BG_RADAR = new Color(13, 17, 23);
    private static final Color COR_ASFALTO = new Color(35, 39, 47);
    private static final Color COR_GRAMA = new Color(18, 28, 22);
    private static final Color COR_GRID_RADAR = new Color(0, 255, 136, 35);
    private static final Color COR_FEIXE_RADAR = new Color(0, 255, 136, 70);
    private static final Color COR_PISTA_LIVRE = new Color(46, 160, 67);
    private static final Color COR_PISTA_OCUPADA = new Color(210, 153, 34);
    private static final Color COR_PISTA_FECHADA = new Color(248, 81, 73);
    private static final Color COR_EMERGENCIA = new Color(255, 40, 40);

    private double anguloRadar = 0;
    private double animacaoPistaOffset = 0;

    public PainelRadar2D(TorreDeControle torre, List<Pista> pistas, List<Aviao> todosAvioes) {
        this.torre = torre;
        this.pistas = pistas;
        this.todosAvioes = todosAvioes;
        this.setBackground(BG_RADAR);
        this.setPreferredSize(new Dimension(720, 600));

        // Timer de animação visual a 60 FPS (16ms) com rotação suave e calmo
        Timer timer = new Timer(16, e -> {
            anguloRadar += 0.018;
            if (anguloRadar > Math.PI * 2) anguloRadar = 0;
            animacaoPistaOffset = (animacaoPistaOffset + 0.6) % 300;
            repaint();
        });
        timer.start();
    }

    public void atualizarCenario(TorreDeControle novaTorre, List<Pista> novasPistas, List<Aviao> novosAvioes) {
        this.torre = novaTorre;
        this.pistas = List.copyOf(novasPistas);
        this.todosAvioes = List.copyOf(novosAvioes);
        this.animacaoPistaOffset = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        // Ativa Anti-Aliasing para gráficos vetoriais ultra nítidos
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Fundo e Terreno
        desenharTerreno(g2, w, h);

        // 2. Radar ATC Circular no Céu (Topo/Esquerda)
        desenharRadarATC(g2, 110, 110, 90);

        // 3. Pistas de Pouso e Decolagem
        desenharPistas(g2, w, h);

        // 4. Pátio de Aeronaves e Portões (Solo)
        desenharPatioPortoes(g2, w, h);

        // 5. Aeronaves no Céu (Fila de Pouso e Espera)
        desenharAvioesNoCeu(g2, w, h);

        g2.dispose();
    }

    private void desenharTerreno(Graphics2D g2, int w, int h) {
        g2.setColor(COR_GRAMA);
        g2.fillRect(0, 0, w, h);

        g2.setColor(new Color(45, 52, 60));
        g2.setStroke(new BasicStroke(18));
        g2.drawLine(240, 220, 240, h - 80);
        g2.drawLine(240, h - 140, w - 40, h - 140);
    }

    private void desenharRadarATC(Graphics2D g2, int cx, int cy, int raio) {
        g2.setColor(new Color(10, 24, 18));
        g2.fillOval(cx - raio, cy - raio, raio * 2, raio * 2);

        g2.setColor(COR_GRID_RADAR);
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(cx - raio, cy - raio, raio * 2, raio * 2);
        g2.drawOval(cx - (raio * 2 / 3), cy - (raio * 2 / 3), (raio * 4 / 3), (raio * 4 / 3));
        g2.drawOval(cx - (raio / 3), cy - (raio / 3), (raio * 2 / 3), (raio * 2 / 3));

        g2.drawLine(cx - raio, cy, cx + raio, cy);
        g2.drawLine(cx, cy - raio, cx, cy + raio);

        int fx = (int) (cx + raio * Math.cos(anguloRadar));
        int fy = (int) (cy + raio * Math.sin(anguloRadar));
        g2.setPaint(new GradientPaint(cx, cy, COR_FEIXE_RADAR, fx, fy, new Color(0, 255, 136, 0)));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawLine(cx, cy, fx, fy);

        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2.setColor(new Color(0, 255, 136, 200));
        g2.drawString("RADAR ATC - 20 NM", cx - 50, cy + raio + 16);
    }

    private void desenharPistas(Graphics2D g2, int w, int h) {
        int numPistas = pistas.size();
        int pistaYBase = 220;
        int espacamento = 110;
        int pistaLargura = w - 280;
        int pistaAltura = 55;
        int pistaX = 250;

        for (int i = 0; i < numPistas; i++) {
            Pista pista = pistas.get(i);
            int py = pistaYBase + (i * espacamento);

            g2.setColor(COR_ASFALTO);
            g2.fillRoundRect(pistaX, py, pistaLargura, pistaAltura, 10, 10);

            boolean fechada = pista.estaFechada();
            Aviao ocupante = pista.getOcupante();

            Color corBorda = fechada ? COR_PISTA_FECHADA : (ocupante != null ? COR_PISTA_OCUPADA : COR_PISTA_LIVRE);
            g2.setColor(corBorda);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawRoundRect(pistaX, py, pistaLargura, pistaAltura, 10, 10);

            // Marcação de cabeceira (threshold stripes)
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            for (int s = 0; s < 5; s++) {
                g2.drawLine(pistaX + 15, py + 10 + (s * 8), pistaX + 45, py + 10 + (s * 8));
                g2.drawLine(pistaX + pistaLargura - 45, py + 10 + (s * 8), pistaX + pistaLargura - 15, py + 10 + (s * 8));
            }

            // Identificador da pista
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString(String.format("%02d", pista.getNumero()), pistaX + 55, py + 33);

            // Linha central pontilhada
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{15, 12}, 0));
            g2.drawLine(pistaX + 80, py + (pistaAltura / 2), pistaX + pistaLargura - 60, py + (pistaAltura / 2));

            if (fechada) {
                g2.setColor(new Color(248, 81, 73, 210));
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String motivo = pista.getMotivoFechamento() != null ? pista.getMotivoFechamento() : "Interditada";
                g2.drawString("⚠ PISTA FECHADA: " + motivo.toUpperCase(), pistaX + 110, py + 33);
            } else if (ocupante != null) {
                desenharAviaoNaPista(g2, ocupante, pistaX + 90, py + (pistaAltura / 2), pistaLargura - 170);
            } else {
                g2.setColor(new Color(46, 160, 67, 190));
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.drawString("LIVRE / DISPONÍVEL", pistaX + pistaLargura - 190, py + 32);
            }
        }
    }

    private void desenharAviaoNaPista(Graphics2D g2, Aviao aviao, int inicioX, int centerY, int trajeto) {
        boolean pouso = (aviao.getEstado() == EstadoAviao.POUSANDO);
        double progresso = (animacaoPistaOffset % 300) / 300.0;
        int aviaoX = inicioX + (int) (progresso * trajeto);

        desenharIconeAviao(g2, aviaoX, centerY, 0, aviao.estaEmEmergencia() ? COR_EMERGENCIA : Color.CYAN, 1.2);

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(Color.WHITE);
        String texto = aviao.getCodigoVoo() + (pouso ? " [POUSANDO]" : " [DECOLANDO]");
        g2.drawString(texto, aviaoX - 25, centerY - 18);
    }

    private void desenharPatioPortoes(Graphics2D g2, int w, int h) {
        int patioY = h - 120;
        int patioX = 250;
        int patioW = w - 280;

        g2.setColor(new Color(25, 30, 38));
        g2.fillRoundRect(patioX, patioY, patioW, 90, 8, 8);
        g2.setColor(new Color(60, 70, 85));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(patioX, patioY, patioW, 90, 8, 8);

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(150, 160, 180));
        g2.drawString("PÁTIO DE ESTACIONAMENTO & PORTÕES (SOLO)", patioX + 15, patioY + 20);

        int slotX = patioX + 25;
        for (Aviao a : todosAvioes) {
            if (a.getEstado() == EstadoAviao.EM_SOLO || a.getEstado() == EstadoAviao.AGUARDANDO_DECOLAGEM) {
                boolean taxiway = (a.getEstado() == EstadoAviao.AGUARDANDO_DECOLAGEM);
                Color cor = taxiway ? Color.YELLOW : new Color(130, 200, 255);

                desenharIconeAviao(g2, slotX, patioY + 55, Math.PI / 2, cor, 0.9);

                g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                g2.setColor(Color.WHITE);
                g2.drawString(a.getCodigoVoo(), slotX - 18, patioY + 80);

                if (taxiway) {
                    g2.setColor(Color.YELLOW);
                    g2.drawString("Fila Decol.", slotX - 22, patioY + 40);
                }

                slotX += 75;
                if (slotX > patioX + patioW - 50) break;
            }
        }
    }

    private void desenharAvioesNoCeu(Graphics2D g2, int w, int h) {
        List<Aviao> filaPouso = torre.getRetrato().getFilaDePouso();

        int inicioY = 50;
        int slotX = 260;

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(new Color(100, 180, 255));
        g2.drawString("✈ APROXIMAÇÃO / ESPAÇO AÉREO (" + filaPouso.size() + " aguardando pouso)", slotX, 30);

        int idx = 1;
        for (Aviao aviao : filaPouso) {
            boolean em = aviao.estaEmEmergencia();
            Color corAviao = em ? COR_EMERGENCIA : new Color(0, 230, 255);

            int x = slotX + (idx * 95);
            int y = inicioY + ((idx % 2 == 0) ? 10 : 35);

            if (x < w - 60) {
                desenharIconeAviao(g2, x, y, Math.PI, corAviao, 1.0);

                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.setColor(em ? COR_EMERGENCIA : Color.WHITE);
                g2.drawString(aviao.getCodigoVoo() + " (" + aviao.porcentagemCombustivel() + "%)", x - 25, y + 25);

                if (em) {
                    g2.setColor(COR_EMERGENCIA);
                    g2.drawString("🚨 EMERGÊNCIA", x - 35, y - 16);
                }
            }
            idx++;
        }
    }

    private void desenharIconeAviao(Graphics2D g2, int x, int y, double angulo, Color cor, double escala) {
        AffineTransform old = g2.getTransform();
        g2.translate(x, y);
        g2.rotate(angulo);
        g2.scale(escala, escala);

        Path2D p = new Path2D.Double();
        // Nariz
        p.moveTo(16, 0);
        // Asa direita
        p.lineTo(2, 4);
        p.lineTo(-4, 16);
        p.lineTo(-7, 16);
        p.lineTo(-3, 4);
        // Cauda
        p.lineTo(-12, 3);
        p.lineTo(-15, 8);
        p.lineTo(-17, 8);
        p.lineTo(-15, 0);
        // Lado esquerdo
        p.lineTo(-17, -8);
        p.lineTo(-15, -8);
        p.lineTo(-12, -3);
        p.lineTo(-3, -4);
        p.lineTo(-7, -16);
        p.lineTo(-4, -16);
        p.lineTo(2, -4);
        p.closePath();

        g2.setColor(cor);
        g2.fill(p);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(p);

        g2.setTransform(old);
    }
}
