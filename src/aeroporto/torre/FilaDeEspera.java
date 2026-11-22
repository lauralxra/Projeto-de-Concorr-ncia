package aeroporto.torre;

import aeroporto.aviao.Aviao;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Fila de aviões esperando a torre. Base comum de FilaDePouso e FilaDeDecolagem.
 *
 * Ordem de saída (quem é atendido primeiro):
 *   1. Emergência (Extra 1) — só faz sentido na fila de pouso
 *   2. Quem esperou mais que o limite de aging (Extra 2 — anti-starvation)
 *   3. Ordem de chegada (FIFO)
 *
 * Todos os métodos são synchronized: a fila é segura mesmo se o painel
 * ler uma cópia enquanto a torre mexe nela.
 */
public abstract class FilaDeEspera {

    /** Um lugar na fila: o avião + o instante em que entrou. */
    private static final class Entrada {
        final Aviao aviao;
        final long entrouEmMillis;
        Entrada(Aviao aviao) {
            this.aviao = aviao;
            this.entrouEmMillis = System.currentTimeMillis();
        }
    }

    private final LinkedList<Entrada> entradas = new LinkedList<>();
    private final long limiteAgingMillis;

    protected FilaDeEspera(long limiteAgingMillis) {
        this.limiteAgingMillis = limiteAgingMillis;
    }

    /** Se emergência deve furar a fila (true para pouso). */
    protected abstract boolean respeitaEmergencia();

    public synchronized void entrar(Aviao aviao) {
        if (contem(aviao)) {
            throw new ExcecaoAutorizacaoIndevida(
                aviao.getCodigoVoo() + " tentou entrar duas vezes em " + nome());
        }
        entradas.addLast(new Entrada(aviao));
    }

    public synchronized boolean contem(Aviao aviao) {
        for (Entrada e : entradas) {
            if (e.aviao == aviao) return true;
        }
        return false;
    }

    public synchronized boolean estaVazia() {
        return entradas.isEmpty();
    }

    public synchronized int tamanho() {
        return entradas.size();
    }

    /** Mostra quem seria o próximo, sem tirar da fila. */
    public synchronized Aviao espiarProximo() {
        Entrada e = escolherProxima();
        return e == null ? null : e.aviao;
    }

    /** Tira da fila quem deve ser atendido agora. */
    public synchronized Aviao retirarProximo() {
        Entrada e = escolherProxima();
        if (e == null) return null;
        entradas.remove(e);
        return e.aviao;
    }

    /** O próximo a sair é emergência? */
    public synchronized boolean proximoEhEmergencia() {
        Entrada e = escolherProxima();
        return e != null && respeitaEmergencia() && e.aviao.estaEmEmergencia();
    }

    /** Alguém esperou além do limite de aging? (usado para dar a vez à outra fila) */
    public synchronized boolean temAvioEnvelhecido() {
        long agora = System.currentTimeMillis();
        for (Entrada e : entradas) {
            if (agora - e.entrouEmMillis >= limiteAgingMillis) return true;
        }
        return false;
    }

    /** Remove um avião específico (usado em testes e no Extra 4, se precisar). */
    public synchronized boolean remover(Aviao aviao) {
        Iterator<Entrada> it = entradas.iterator();
        while (it.hasNext()) {
            if (it.next().aviao == aviao) { it.remove(); return true; }
        }
        return false;
    }

    /** Cópia para o painel: mexer na lista devolvida não afeta a fila real. */
    public synchronized List<Aviao> copia() {
        List<Aviao> lista = new ArrayList<>(entradas.size());
        for (Entrada e : entradas) lista.add(e.aviao);
        return lista;
    }

    /** Tempo que o primeiro da fila já esperou (para estatísticas do painel). */
    public synchronized long maiorEsperaMillis() {
        if (entradas.isEmpty()) return 0;
        return System.currentTimeMillis() - entradas.getFirst().entrouEmMillis;
    }

    protected abstract String nome();

    // Chamado sempre com o monitor da fila (synchronized) já pego.
    private Entrada escolherProxima() {
        if (entradas.isEmpty()) return null;

        if (respeitaEmergencia()) {
            for (Entrada e : entradas) {
                if (e.aviao.estaEmEmergencia()) return e;          // Extra 1
            }
        }
        long agora = System.currentTimeMillis();
        for (Entrada e : entradas) {
            if (agora - e.entrouEmMillis >= limiteAgingMillis) return e; // Extra 2
        }
        return entradas.getFirst();                                 // FIFO
    }
}
