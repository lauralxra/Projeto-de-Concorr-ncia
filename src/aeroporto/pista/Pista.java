package aeroporto.pista;

import aeroporto.aviao.Aviao;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Pista de pouso/decolagem: recurso compartilhado de acesso exclusivo.
 * Só pode ter UM avião por vez.
 *
 * Quem garante isso no funcionamento normal é a torre, que reserva cada pista
 * para um avião só ([CONTROLE-CONCORRENCIA #T1] em TorreDeControle).
 *
 * A Pista é a "fiscal": se, por falha no controle, dois aviões tentarem usá-la
 * ao mesmo tempo, ela percebe e lança ExcecaoPistaOcupada.
 *
 * A detecção usa AtomicReference.compareAndSet, uma operação atômica do processador:
 * "se o ocupante for null, coloque este avião; senão, falhe". Verificar e ocupar
 * acontecem num passo só, então dois aviões nunca conseguem os dois achar a pista
 * livre. Por isso a detecção funciona mesmo com os locks desligados.
 */
public class Pista {

    private final int numero;
    private final AtomicReference<Aviao> ocupante = new AtomicReference<>(null);

    public Pista(int numero) {
        if (numero <= 0) {
            throw new IllegalArgumentException("Número da pista deve ser positivo: " + numero);
        }
        this.numero = numero;
    }

    public int getNumero() {
        return numero;
    }

    /** true se não há avião na pista. */
    public boolean estaLivre() {
        return ocupante.get() == null;
    }

    /** Avião que está na pista agora (null se livre). Usado pelo painel. */
    public Aviao getOcupante() {
        return ocupante.get();
    }

    /**
     * O avião entra na pista para pousar ou decolar.
     * @throws ExcecaoPistaOcupada se já houver outro avião nela
     */
    public void ocupar(Aviao aviao) {
        if (aviao == null) {
            throw new IllegalArgumentException("Avião nulo tentou ocupar a pista " + numero);
        }
        // Atômico: só troca null -> aviao se a pista estiver realmente livre.
        if (!ocupante.compareAndSet(null, aviao)) {
            throw new ExcecaoPistaOcupada(numero, codigoDe(ocupante.get()), aviao.getCodigoVoo());
        }
    }

    /**
     * O avião sai da pista depois de pousar/decolar.
     * @throws ExcecaoLiberacaoIndevida se o avião não for o ocupante atual
     */
    public void liberar(Aviao aviao) {
        if (aviao == null) {
            throw new IllegalArgumentException("Avião nulo tentou liberar a pista " + numero);
        }
        // Atômico: só troca aviao -> null se ESTE avião for o ocupante.
        if (!ocupante.compareAndSet(aviao, null)) {
            throw new ExcecaoLiberacaoIndevida(numero, codigoDe(ocupante.get()), aviao.getCodigoVoo());
        }
    }

    private static String codigoDe(Aviao aviao) {
        return aviao == null ? null : aviao.getCodigoVoo();
    }

    @Override
    public String toString() {
        Aviao atual = ocupante.get();
        return "Pista " + numero + (atual == null ? " [livre]" : " [ocupada por " + atual.getCodigoVoo() + "]");
    }
}