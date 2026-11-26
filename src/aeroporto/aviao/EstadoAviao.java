package aeroporto.aviao;

/** 
 * Só existe um caminho em circulação: EM_VOO → AGUARDANDO_POUSO → POUSANDO → EM_SOLO → AGUARDANDO_DECOLAGEM → DECOLANDO → EM_VOO.
 */

public enum EstadoAviao {
    EM_VOO,
    AGUARDANDO_POUSO,
    POUSANDO,
    EM_SOLO,
    AGUARDANDO_DECOLAGEM,
    DECOLANDO;


    public EstadoAviao proximo() {
        switch (this) {
            case EM_VOO:               return AGUARDANDO_POUSO;
            case AGUARDANDO_POUSO:     return POUSANDO;
            case POUSANDO:             return EM_SOLO;
            case EM_SOLO:              return AGUARDANDO_DECOLAGEM;
            case AGUARDANDO_DECOLAGEM: return DECOLANDO;
            default:                   return EM_VOO;
        }
    }

    public boolean podeIrPara(EstadoAviao novo) {
        return this.proximo() == novo;
    }
}