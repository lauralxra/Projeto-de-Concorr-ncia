package aeroporto.pista;

import aeroporto.aviao.Aviao;

/**
 * ESBOÇO TEMPORÁRIO — dono real: Pessoa 3.
 * Só existe para o módulo da torre compilar e ser testado.
 * Quando a Pessoa 3 subir a versão dela, este arquivo é substituído.
 * Contrato combinado: estaLivre(), ocupar(a), liberar(a), getOcupante(), getNumero().
 */
public class Pista {

    private final int numero;
    private Aviao ocupante;

    public Pista(int numero) { this.numero = numero; }

    public int getNumero() { return numero; }

    public synchronized boolean estaLivre() { return ocupante == null; }

    public synchronized Aviao getOcupante() { return ocupante; }

    public synchronized void ocupar(Aviao a) {
        if (ocupante != null) {
            throw new IllegalStateException("Pista " + numero + " já ocupada por "
                + ocupante.getCodigoVoo() + " quando " + a.getCodigoVoo() + " tentou usar");
        }
        ocupante = a;
    }

    public synchronized void liberar(Aviao a) {
        if (ocupante != a) {
            throw new IllegalStateException(a.getCodigoVoo() + " tentou liberar a pista "
                + numero + " sem estar nela");
        }
        ocupante = null;
    }
}
