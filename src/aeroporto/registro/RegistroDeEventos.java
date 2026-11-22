package aeroporto.registro;

/**
 * ESBOÇO TEMPORÁRIO — dono real: Pessoa 4 (buffer circular thread-safe).
 * Aqui só imprime, para a torre poder ser testada sozinha.
 */
public final class RegistroDeEventos {

    private static volatile boolean silencioso = false;

    private RegistroDeEventos() {}

    public static void publicar(String mensagem) {
        if (!silencioso) System.out.println(mensagem);
    }

    public static void setSilencioso(boolean valor) { silencioso = valor; }
}
