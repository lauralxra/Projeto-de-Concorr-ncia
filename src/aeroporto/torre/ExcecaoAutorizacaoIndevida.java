package aeroporto.torre;

import aeroporto.pista.ExcecaoCondicaoDeCorrida;

/**
 * Lançada quando a torre detecta uma autorização que não deveria existir:
 * avião autorizado sem estar na fila, avião nas duas filas ao mesmo tempo,
 * ou avião concluindo operação numa pista que não recebeu.
 *
 * Herda de ExcecaoCondicaoDeCorrida, que é RuntimeException:
 * não obriga ninguém a declarar "throws" e entra na mesma família das exceções da pista.
 */
public class ExcecaoAutorizacaoIndevida extends ExcecaoCondicaoDeCorrida {
    public ExcecaoAutorizacaoIndevida(String mensagem) {
        super(mensagem);
    }
}
