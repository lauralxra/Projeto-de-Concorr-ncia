package aeroporto.pista;

/** Lançada quando um avião tenta usar uma pista que já tem outro avião. */
public class ExcecaoPistaOcupada extends ExcecaoCondicaoDeCorrida {
    public ExcecaoPistaOcupada(int numeroPista, String ocupanteAtual, String intruso) {
        super(String.format(
            "Pista %d já ocupada por %s quando %s tentou entrar nela. " +
            "Dois aviões na mesma pista ao mesmo tempo: o controle de acesso falhou.",
            numeroPista, ocupanteAtual, intruso));
    }
}