package aeroporto.pista;

import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.TorreDeControle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extra 4: fecha pistas de tempos em tempos (manutenção ou clima) e depois reabre.
 *
 * Roda na sua própria thread. A cada intervalo, sorteia uma pista aberta, fecha por
 * um tempo, reabre e avisa a torre para ela voltar a despachar aviões nela.
 * Se houver um avião usando a pista na hora do fechamento, ele termina a operação;
 * só os próximos deixam de ser autorizados nela.
 *
 * Regra de segurança: nunca fecha a última pista aberta, senão o aeroporto inteiro
 * pararia. Com uma pista só, nenhuma pista é fechada.
 */
public class ControladorDeManutencao implements Runnable {

    private static final String[] MOTIVOS = {"manutenção", "chuva forte", "neblina", "inspeção da pista"};

    private final List<Pista> pistas;
    private final TorreDeControle torre;
    private final long intervaloMillis;
    private final long duracaoMillis;
    private final Random sorteio = new Random();
    private final AtomicInteger totalFechamentos = new AtomicInteger();

    public ControladorDeManutencao(List<Pista> pistas, TorreDeControle torre,
                                   long intervaloMillis, long duracaoMillis) {
        if (intervaloMillis <= 0 || duracaoMillis <= 0) {
            throw new IllegalArgumentException("Intervalo e duração da manutenção devem ser positivos");
        }
        this.pistas = new ArrayList<>(pistas);
        this.torre = torre;
        this.intervaloMillis = intervaloMillis;
        this.duracaoMillis = duracaoMillis;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(intervaloMillis);

                Pista escolhida = escolherPistaParaFechar();
                if (escolhida == null) continue; // não dá para fechar nenhuma agora

                String motivo = MOTIVOS[sorteio.nextInt(MOTIVOS.length)];
                escolhida.fechar(motivo);
                totalFechamentos.incrementAndGet();
                RegistroDeEventos.publicar("Pista " + escolhida.getNumero() + " FECHADA: " + motivo);

                // Asserção: sempre sobra pelo menos uma pista aberta.
                if (contarAbertas() == 0) {
                    throw new IllegalStateException("Todas as pistas ficaram fechadas ao mesmo tempo");
                }

                try {
                    Thread.sleep(duracaoMillis);
                } finally {
                    // Reabre mesmo se a thread for interrompida durante o fechamento.
                    escolhida.reabrir();
                    RegistroDeEventos.publicar("Pista " + escolhida.getNumero() + " reaberta");
                    torre.notificarPistaLiberada(escolhida);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // fim da simulação
        }
    }

    /** Sorteia uma pista aberta, desde que não seja a última aberta. */
    private Pista escolherPistaParaFechar() {
        if (contarAbertas() <= 1) return null;
        List<Pista> candidatas = new ArrayList<>();
        for (Pista p : pistas) {
            if (!p.estaFechada()) candidatas.add(p);
        }
        if (candidatas.isEmpty()) return null;
        return candidatas.get(sorteio.nextInt(candidatas.size()));
    }

    private int contarAbertas() {
        int abertas = 0;
        for (Pista p : pistas) {
            if (!p.estaFechada()) abertas++;
        }
        return abertas;
    }

    /** Quantas vezes alguma pista foi fechada (para o painel/estatísticas). */
    public int getTotalFechamentos() {
        return totalFechamentos.get();
    }
}