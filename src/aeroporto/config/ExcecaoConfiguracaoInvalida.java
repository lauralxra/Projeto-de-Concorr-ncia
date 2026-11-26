package aeroporto.config;

/**
 * Lançada quando o arquivo de configuração contém erros de sintaxe,
 * campos ausentes ou valores inconsistentes com as regras da simulação.
 */
public class ExcecaoConfiguracaoInvalida extends RuntimeException {

    public ExcecaoConfiguracaoInvalida(String mensagem) {
        super(mensagem);
    }

    public ExcecaoConfiguracaoInvalida(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
