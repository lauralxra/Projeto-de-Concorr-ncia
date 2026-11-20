package aeroporto.torre;

/** Aviões no ar esperando para pousar. Emergência fura a fila. */
public class FilaDePouso extends FilaDeEspera {

    public FilaDePouso(long limiteAgingMillis) {
        super(limiteAgingMillis);
    }

    @Override
    protected boolean respeitaEmergencia() { return true; }

    @Override
    protected String nome() { return "fila de pouso"; }
}
