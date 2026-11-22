package aeroporto.pista;

/** Lançada quando um avião tenta liberar uma pista que não é dele. */
public class ExcecaoLiberacaoIndevida extends ExcecaoCondicaoDeCorrida {
    public ExcecaoLiberacaoIndevida(int numeroPista, String ocupanteAtual, String quemTentou) {
        super(String.format(
            "%s tentou liberar a pista %d, mas o ocupante registrado é %s. " +
            "A pista foi tomada por outro avião durante a operação.",
            quemTentou, numeroPista,
            ocupanteAtual == null ? "ninguém (pista já livre)" : ocupanteAtual));
    }
}
