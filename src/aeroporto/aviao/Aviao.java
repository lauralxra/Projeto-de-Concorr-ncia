package aeroporto.aviao;

import aeroporto.pista.ExcecaoCondicaoDeCorrida;
import aeroporto.pista.Pista;
import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.Autorizacao;
import aeroporto.torre.ExcecaoAutorizacaoIndevida;
import aeroporto.torre.TipoOperacao;
import aeroporto.torre.TorreDeControle;

/**
 * Avião: uma thread com ciclo de vida infinito.
 * Fala SÓ com a torre e só usa a pista que veio na Autorizacao.
 *
 * Ciclo: em voo → pede pouso → espera → pousa → fica em solo
 *        → pede decolagem → espera → decola → em voo → ...
 */
public class Aviao implements Runnable {

    private final String codigoVoo;
    private final TorreDeControle torre;
    private final long delaySoloMillis;      // tempo parado em solo
    private final long delayVooMillis;       // tempo voando antes de pedir pouso
    private final long delayOperacaoMillis;  // tempo ocupando a pista

    // Combustível (Extra 3)
    private final long autonomiaMillis;      // quanto tempo aguenta no ar
    private final double limiteEmergencia;   // ex.: 0.25 = abaixo de 25% vira emergência

    // volatile: só a thread do avião escreve; torre e painel leem e veem a mudança na hora
    private volatile EstadoAviao estado;
    private volatile long inicioVooMillis;
    private volatile boolean emergenciaForcada;  // usada nos testes da torre
    private volatile int totalPousos;
    private volatile int totalDecolagens;

    public Aviao(String codigoVoo, EstadoAviao estadoInicial, TorreDeControle torre,
                long delaySoloMillis, long delayVooMillis, long delayOperacaoMillis,
                long autonomiaMillis, double limiteEmergencia) {
        this.codigoVoo = codigoVoo;
        this.estado = estadoInicial;
        this.torre = torre;
        this.delaySoloMillis = delaySoloMillis;
        this.delayVooMillis = delayVooMillis;
        this.delayOperacaoMillis = delayOperacaoMillis;
        this.autonomiaMillis = autonomiaMillis;
        this.limiteEmergencia = limiteEmergencia;
        this.inicioVooMillis = System.currentTimeMillis();
    }

    /** Avião "só de identidade", usado nos testes da torre e da pista (não roda como thread). */
    public Aviao(String codigoVoo) {
        this(codigoVoo, EstadoAviao.EM_VOO, null, 1000, 1000, 100, 60_000, 0.25);
    }

    // ================= Ciclo de vida =================

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (estado == EstadoAviao.EM_VOO) {
                    dormir(delayVooMillis);
                    realizarPouso();
                } else {
                    dormir(delaySoloMillis);
                    realizarDecolagem();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // fim da simulação (Ctrl+C)
        } catch (ExcecaoCondicaoDeCorrida | ExcecaoTransicaoInvalida e) {
            // deixa o erro visível no painel e derruba a thread
            RegistroDeEventos.publicar("!!! ERRO no voo " + codigoVoo + ": " + e.getMessage());
            throw e;
        }
    }

    private void realizarPouso() throws InterruptedException {
        mudarEstado(EstadoAviao.AGUARDANDO_POUSO);
        if (estaEmEmergencia()) {
            RegistroDeEventos.publicar(codigoVoo + " declarou EMERGÊNCIA (combustível "
                + porcentagemCombustivel() + "%) e pediu pouso");
        } else {
            RegistroDeEventos.publicar(codigoVoo + " pediu autorização para pousar");
        }

        Autorizacao autorizacao = torre.pedirPouso(this);     // BLOQUEIA até a torre autorizar
        verificarAutorizacao(autorizacao, TipoOperacao.POUSO);
        Pista pista = autorizacao.getPista();

        mudarEstado(EstadoAviao.POUSANDO);
        pista.ocupar(this);
        RegistroDeEventos.publicar(codigoVoo + " está pousando na pista " + pista.getNumero());
        dormir(delayOperacaoMillis);
        pista.liberar(this);

        mudarEstado(EstadoAviao.EM_SOLO);
        emergenciaForcada = false;
        totalPousos++;
        RegistroDeEventos.publicar(codigoVoo + " pousou e está em solo");
        torre.concluirOperacao(this, pista);                   // avisa a torre: pista liberada
    }

    private void realizarDecolagem() throws InterruptedException {
        mudarEstado(EstadoAviao.AGUARDANDO_DECOLAGEM);
        RegistroDeEventos.publicar(codigoVoo + " pediu autorização para decolar");

        Autorizacao autorizacao = torre.pedirDecolagem(this);
        verificarAutorizacao(autorizacao, TipoOperacao.DECOLAGEM);
        Pista pista = autorizacao.getPista();

        mudarEstado(EstadoAviao.DECOLANDO);
        pista.ocupar(this);
        RegistroDeEventos.publicar(codigoVoo + " está decolando na pista " + pista.getNumero());
        dormir(delayOperacaoMillis);
        pista.liberar(this);

        mudarEstado(EstadoAviao.EM_VOO);
        inicioVooMillis = System.currentTimeMillis();          // tanque cheio: reinicia o combustível
        totalDecolagens++;
        RegistroDeEventos.publicar(codigoVoo + " decolou e está em voo");
        torre.concluirOperacao(this, pista);
    }

    // ================= Máquina de estados e asserções =================

    /**
     * Único lugar onde o estado muda. Se a transição não for a do ciclo, lança exceção.
     * Isso já garante as asserções do enunciado: só pousa quem estava aguardando pouso
     * (vindo de EM_VOO) e só decola quem estava aguardando decolagem (vindo de EM_SOLO).
     */
    void mudarEstado(EstadoAviao novo) {
        if (!estado.podeIrPara(novo)) {
            throw new ExcecaoTransicaoInvalida(codigoVoo, estado, novo);
        }
        estado = novo;
    }

    /** Confere se o "bilhete" da torre é mesmo deste avião e do tipo certo. */
    private void verificarAutorizacao(Autorizacao autorizacao, TipoOperacao esperado) {
        if (autorizacao.getAviao() != this || autorizacao.getTipo() != esperado) {
            throw new ExcecaoAutorizacaoIndevida(codigoVoo + " recebeu uma autorização que não é dele: "
                + autorizacao + " (esperava " + esperado + ")");
        }
    }

    // ================= Combustível (Extra 3) =================

    /** De 1.0 (tanque cheio) até 0.0 (vazio). Só gasta em voo e esperando pouso. */
    public double getCombustivel() {
        EstadoAviao e = estado;
        if (e != EstadoAviao.EM_VOO && e != EstadoAviao.AGUARDANDO_POUSO) {
            return 1.0;
        }
        long gasto = System.currentTimeMillis() - inicioVooMillis;
        return Math.max(0.0, 1.0 - (double) gasto / autonomiaMillis);
    }

    public int porcentagemCombustivel() {
        return (int) Math.round(getCombustivel() * 100);
    }

    /** A torre chama isto para decidir quem fura a fila de pouso. */
    public boolean estaEmEmergencia() {
        if (emergenciaForcada) return true;
        EstadoAviao e = estado;
        return (e == EstadoAviao.EM_VOO || e == EstadoAviao.AGUARDANDO_POUSO)
            && getCombustivel() < limiteEmergencia;
    }

    /** Força emergência manualmente (usado nos testes da torre). */
    public void setEmergencia(boolean emergencia) {
        this.emergenciaForcada = emergencia;
    }

    // ================= Leitura para torre, painel e estatísticas =================

    public String getCodigoVoo()      { return codigoVoo; }
    public EstadoAviao getEstado()    { return estado; }
    public int getTotalPousos()       { return totalPousos; }
    public int getTotalDecolagens()   { return totalDecolagens; }

    private void dormir(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    @Override
    public String toString() { return codigoVoo; }
}