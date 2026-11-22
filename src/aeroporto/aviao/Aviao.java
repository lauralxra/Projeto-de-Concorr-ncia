package aeroporto.aviao;

/**
 * ESBOÇO TEMPORÁRIO — dono real: Pessoa 2.
 * A torre só precisa de: getCodigoVoo() e estaEmEmergencia().
 * Quando a Pessoa 2 subir a classe real (implements Runnable, estados, combustível),
 * ela substitui este arquivo — só precisa manter esses dois métodos.
 */
public class Aviao {

    private final String codigoVoo;
    private volatile boolean emergencia;

    public Aviao(String codigoVoo) { this.codigoVoo = codigoVoo; }

    public String getCodigoVoo() { return codigoVoo; }

    public boolean estaEmEmergencia() { return emergencia; }

    public void setEmergencia(boolean emergencia) { this.emergencia = emergencia; }

    @Override
    public String toString() { return codigoVoo; }
}
