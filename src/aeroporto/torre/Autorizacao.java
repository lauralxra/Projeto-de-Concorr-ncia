package aeroporto.torre;

import aeroporto.aviao.Aviao;
import aeroporto.pista.Pista;

/**
 * "Bilhete" que a torre entrega ao avião: diz qual pista ele pode usar e para quê.
 * É imutável (todos os campos final), então pode ser passado entre threads sem lock.
 */
public final class Autorizacao {

    private final Aviao aviao;
    private final Pista pista;
    private final TipoOperacao tipo;
    private final long concedidaEmMillis;

    public Autorizacao(Aviao aviao, Pista pista, TipoOperacao tipo) {
        this.aviao = aviao;
        this.pista = pista;
        this.tipo = tipo;
        this.concedidaEmMillis = System.currentTimeMillis();
    }

    public Aviao getAviao() { return aviao; }
    public Pista getPista() { return pista; }
    public TipoOperacao getTipo() { return tipo; }
    public long getConcedidaEmMillis() { return concedidaEmMillis; }

    @Override
    public String toString() {
        return tipo + " de " + aviao.getCodigoVoo() + " na pista " + pista.getNumero();
    }
}
