package aeroporto.pista;

/**
 * Exceção base para condições de corrida não tratadas.
 * É RuntimeException para não obrigar try/catch em todo lugar
 * e para "derrubar" a thread do avião, deixando o erro visível.
 */
public class ExcecaoCondicaoDeCorrida extends RuntimeException {
    public ExcecaoCondicaoDeCorrida(String mensagem) {
        super("[CONDIÇÃO DE CORRIDA] " + mensagem);
    }
}