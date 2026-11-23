package aeroporto.aviao;
/**
 * para quando o avião tenta pular uma etapa ciclo de vida
 * ExcecaoTrasicaoInvalida
 */
public class ExcecaoTransicaoInvalida extends RuntimeException {
    public ExcecaoTransicaoInvalida(String codigoVoo, EstadoAviao estadoAtual, EstadoAviao pretendido) {
        super("Transição inválida de voo " + codigoVoo + ": não pode ir de " + estadoAtual 
        + " para " + pretendido + "(o próximo estado correto seria " + estadoAtual.proximo() + ")");
    }

}