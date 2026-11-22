package aeroporto.torre;

/**
 * Lançada quando a torre detecta uma autorização que não deveria existir:
 * avião autorizado sem estar na fila, avião nas duas filas ao mesmo tempo,
 * ou avião concluindo operação numa pista que não recebeu.
 *
 * Herda de RuntimeException para não obrigar todo mundo a declarar "throws".
 * (Se a Pessoa 3 criar ExcecaoCondicaoDeCorrida como base, troque o "extends" por ela.)
 */
public class ExcecaoAutorizacaoIndevida extends RuntimeException {
    public ExcecaoAutorizacaoIndevida(String mensagem) {
        super(mensagem);
    }
}
