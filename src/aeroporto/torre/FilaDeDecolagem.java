package aeroporto.torre;

/** Aviões no solo esperando para decolar. Emergência não se aplica aqui. */
public class FilaDeDecolagem extends FilaDeEspera {

    public FilaDeDecolagem(long limiteAgingMillis) {
        super(limiteAgingMillis);
    }

    @Override
    protected boolean respeitaEmergencia() { return false; }

    @Override
    protected String nome() { return "fila de decolagem"; }
}
