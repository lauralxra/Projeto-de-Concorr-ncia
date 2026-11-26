package aeroporto.torre;

import aeroporto.aviao.Aviao;
import aeroporto.pista.Pista;
import aeroporto.registro.RegistroDeEventos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Torre de controle: a ÚNICA que decide quem usa qual pista e quando.
 *
 * Como funciona (resumo):
 *   - O avião chama pedirPouso()/pedirDecolagem() e fica BLOQUEADO lá dentro.
 *   - A torre coloca o avião na fila e chama despachar().
 *   - despachar() olha as pistas livres e escolhe quem sai da fila (política abaixo).
 *     Para cada escolhido, guarda uma Autorizacao no mapa e acorda todo mundo.
 *   - Cada avião acordado confere se a SUA autorização chegou; se não, volta a dormir.
 *   - Depois de usar a pista, o avião chama concluirOperacao(); a pista volta a ficar
 *     disponível para a torre e despachar() roda de novo.
 *
 * Política de despacho:
 *   1. Emergência na fila de pouso passa na frente de tudo (Extra 1).
 *   2. Se alguém da fila de decolagem esperou além do aging, decolagem ganha a vez (Extra 2).
 *   3. Pouso tem preferência, mas depois de LIMITE_POUSOS_SEGUIDOS pousos
 *      seguidos, uma decolagem passa (alternância para não travar as decolagens).
 *
 * Concorrência: um único ReentrantLock protege filas, mapa de autorizações e
 * conjunto de pistas reservadas. Uma Condition (autorizacaoDisponivel) é usada
 * para os aviões esperarem sem gastar CPU.
 */
public class TorreDeControle {

    private final ReentrantLock trava = new ReentrantLock(true); // justa: ordem de chegada
    private final Condition autorizacaoDisponivel = trava.newCondition();

    private final List<Pista> pistas;
    private final FilaDePouso filaDePouso;
    private final FilaDeDecolagem filaDeDecolagem;
    private final int limitePousosSeguidos;

    /** Autorizações já concedidas e ainda não "buscadas" pelo avião. */
    private final Map<Aviao, Autorizacao> autorizacoesPendentes = new HashMap<>();
    /** Pista -> autorização que está usando ela agora (reservada pela torre). */
    private final Map<Pista, Autorizacao> pistasReservadas = new HashMap<>();

    private int pousosSeguidos = 0;

    // --- Estatísticas (Extra 6). Todas protegidas pela mesma trava. ---
    private final long inicioMillis = System.currentTimeMillis();
    /** Instante em que cada avião que está esperando fez o pedido. */
    private final Map<Aviao, Long> pedidoEmMillis = new HashMap<>();
    private int totalPousos = 0;
    private int totalDecolagens = 0;
    private int autorizacoesPouso = 0;
    private int autorizacoesDecolagem = 0;
    private long somaEsperaPousoMillis = 0;
    private long somaEsperaDecolagemMillis = 0;
    private long maiorEsperaMillis = 0;
    private int emergenciasAtendidas = 0;
    private final Map<Integer, Long> tempoReservadoPorPista = new HashMap<>();
    private final Map<Integer, Integer> operacoesPorPista = new HashMap<>();

    public TorreDeControle(List<Pista> pistas, long limiteAgingMillis, int limitePousosSeguidos) {
        if (pistas == null || pistas.isEmpty()) {
            throw new IllegalArgumentException("A torre precisa de pelo menos uma pista");
        }
        this.pistas = new ArrayList<>(pistas);
        this.filaDePouso = new FilaDePouso(limiteAgingMillis);
        this.filaDeDecolagem = new FilaDeDecolagem(limiteAgingMillis);
        this.limitePousosSeguidos = Math.max(1, limitePousosSeguidos);
        for (Pista p : this.pistas) {
            tempoReservadoPorPista.put(p.getNumero(), 0L);
            operacoesPorPista.put(p.getNumero(), 0);
        }
    }

    // ------------------------------------------------------------------
    // Contrato usado pelo Avião (Pessoa 2)
    // ------------------------------------------------------------------

    /** Bloqueia até a torre conceder uma pista para pousar. */
    public Autorizacao pedirPouso(Aviao aviao) throws InterruptedException {
        return pedir(aviao, TipoOperacao.POUSO);
    }

    /** Bloqueia até a torre conceder uma pista para decolar. */
    public Autorizacao pedirDecolagem(Aviao aviao) throws InterruptedException {
        return pedir(aviao, TipoOperacao.DECOLAGEM);
    }

    /**
     * O avião avisa que terminou de pousar/decolar e já liberou a pista.
     * A torre devolve a pista ao conjunto de disponíveis e despacha o próximo.
     */
    public void concluirOperacao(Aviao aviao, Pista pista) {
        trava.lock();
        try {
            Autorizacao reserva = pistasReservadas.get(pista);
            if (reserva == null || reserva.getAviao() != aviao) {
                throw new ExcecaoAutorizacaoIndevida(
                    aviao.getCodigoVoo() + " tentou concluir operação na pista " + pista.getNumero()
                    + ", mas a torre tinha reservado essa pista para "
                    + (reserva == null ? "ninguém" : reserva.getAviao().getCodigoVoo()));
            }
            // Asserção: o avião precisa sair da pista (pista.liberar) ANTES de avisar a torre.
            // Senão a torre daria a pista ao próximo com este avião ainda em cima dela.
            if (pista.getOcupante() == aviao) {
                throw new ExcecaoAutorizacaoIndevida(aviao.getCodigoVoo()
                    + " avisou a torre que terminou, mas ainda está ocupando a pista "
                    + pista.getNumero() + " (faltou chamar pista.liberar antes de concluirOperacao)");
            }
            pistasReservadas.remove(pista);
            contabilizarOperacao(reserva);
            RegistroDeEventos.publicar("Torre: pista " + pista.getNumero()
                + " liberada por " + aviao.getCodigoVoo());
            despachar();
        } finally {
            trava.unlock();
        }
    }

    /**
     * Chamado quando uma pista volta a funcionar (ex.: fim de manutenção, Extra 4)
     * para a torre tentar despachar de novo.
     */
    public void notificarPistaLiberada(Pista pista) {
        trava.lock();
        try {
            RegistroDeEventos.publicar("Torre: pista " + pista.getNumero() + " disponível novamente");
            despachar();
        } finally {
            trava.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Contrato usado pelo Painel (Pessoa 4) — sempre CÓPIAS
    // ------------------------------------------------------------------

    public List<Aviao> getFilaDePouso() { return filaDePouso.copia(); }

    public List<Aviao> getFilaDeDecolagem() { return filaDeDecolagem.copia(); }

    public List<Pista> getPistas() { return new ArrayList<>(pistas); }

    /** Quem a torre reservou para cada pista (null = sem reserva). Útil para o painel. */
    public Map<Integer, String> getReservas() {
        trava.lock();
        try {
            Map<Integer, String> mapa = new HashMap<>();
            for (Map.Entry<Pista, Autorizacao> e : pistasReservadas.entrySet()) {
                mapa.put(e.getKey().getNumero(), e.getValue().toString());
            }
            return mapa;
        } finally {
            trava.unlock();
        }
    }

    /**
     * Foto consistente das filas e reservas, tirada toda de uma vez com a trava pega.
     * Use isto (e não getFilaDePouso + getFilaDeDecolagem separados) quando precisar
     * comparar uma coisa com a outra, como no monitor de invariantes (Extra 7).
     */
    public RetratoDaTorre getRetrato() {
        trava.lock();
        try {
            Map<Integer, Autorizacao> reservas = new HashMap<>();
            for (Map.Entry<Pista, Autorizacao> e : pistasReservadas.entrySet()) {
                reservas.put(e.getKey().getNumero(), e.getValue());
            }
            return new RetratoDaTorre(System.currentTimeMillis(),
                filaDePouso.copia(), filaDeDecolagem.copia(), reservas);
        } finally {
            trava.unlock();
        }
    }

    /** Estatísticas para o painel (Extra 6): totais, esperas médias e ocupação das pistas. */
    public EstatisticasDaTorre getEstatisticas() {
        trava.lock();
        try {
            return new EstatisticasDaTorre(inicioMillis, System.currentTimeMillis(),
                totalPousos, totalDecolagens, autorizacoesPouso, autorizacoesDecolagem,
                somaEsperaPousoMillis, somaEsperaDecolagemMillis, maiorEsperaMillis,
                emergenciasAtendidas, tempoReservadoPorPista, operacoesPorPista);
        } finally {
            trava.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    /** Registra quanto o avião esperou na fila até ganhar a pista. Trava pega. */
    private void contabilizarEspera(Autorizacao aut) {
        Long pedido = pedidoEmMillis.remove(aut.getAviao());
        if (pedido == null) return;
        long espera = aut.getConcedidaEmMillis() - pedido;
        if (aut.getTipo() == TipoOperacao.POUSO) {
            autorizacoesPouso++;
            somaEsperaPousoMillis += espera;
            if (aut.getAviao().estaEmEmergencia()) emergenciasAtendidas++;
        } else {
            autorizacoesDecolagem++;
            somaEsperaDecolagemMillis += espera;
        }
        maiorEsperaMillis = Math.max(maiorEsperaMillis, espera);
    }

    /** Registra uma operação concluída e quanto tempo a pista ficou reservada. Trava pega. */
    private void contabilizarOperacao(Autorizacao aut) {
        if (aut.getTipo() == TipoOperacao.POUSO) totalPousos++; else totalDecolagens++;
        int numero = aut.getPista().getNumero();
        long uso = System.currentTimeMillis() - aut.getConcedidaEmMillis();
        tempoReservadoPorPista.merge(numero, uso, Long::sum);
        operacoesPorPista.merge(numero, 1, Integer::sum);
    }

    private Autorizacao pedir(Aviao aviao, TipoOperacao tipo) throws InterruptedException {
        trava.lock();
        try {
            // Asserção: um avião nunca está nas duas filas ao mesmo tempo
            if (filaDePouso.contem(aviao) || filaDeDecolagem.contem(aviao)) {
                throw new ExcecaoAutorizacaoIndevida(aviao.getCodigoVoo()
                    + " pediu " + tipo + " mas já estava em uma fila da torre");
            }
            filaDe(tipo).entrar(aviao);
            pedidoEmMillis.put(aviao, System.currentTimeMillis());
            RegistroDeEventos.publicar("Torre: " + aviao.getCodigoVoo() + " entrou na "
                + (tipo == TipoOperacao.POUSO ? "fila de pouso" : "fila de decolagem")
                + (aviao.estaEmEmergencia() ? " (EMERGÊNCIA)" : ""));

            despachar();

            try {
                esperarAutorizacao(aviao);
            } catch (InterruptedException e) {
                desistir(aviao, tipo);
                throw e;
            }

            Autorizacao aut = autorizacoesPendentes.remove(aviao);
            // Asserção: ninguém sai da espera sem ter recebido a sua autorização.
            if (aut == null) {
                throw new ExcecaoAutorizacaoIndevida(aviao.getCodigoVoo() + " saiu da espera da torre sem"
                    + " ter recebido autorização de " + tipo + " (ainda estava na "
                    + (filaDe(tipo).contem(aviao) ? "fila" : "torre") + ")");
            }
            return aut;
        } finally {
            trava.unlock();
        }
    }

    /**
     * Dorme até a autorização DESTE avião aparecer no mapa.
     * O while (e não if) protege contra acordar à toa: signalAll acorda todos,
     * e cada um confere se a autorização é sua.
     * Só pode ser chamado com a trava pega.
     */
    private void esperarAutorizacao(Aviao aviao) throws InterruptedException {
        // [CONTROLE-CONCORRENCIA #T2] Comentar esta linha faz o avião sair da torre sem
        // esperar a vez; a asserção em pedir() lança ExcecaoAutorizacaoIndevida.
        while (!autorizacoesPendentes.containsKey(aviao)) autorizacaoDisponivel.await();
    }

    /**
     * O avião foi interrompido enquanto esperava (ex.: fim da simulação, Ctrl+C).
     * Desfaz o que a torre tinha feito por ele, senão uma pista poderia ficar
     * reservada para sempre para um avião que nunca vai usá-la.
     * Só pode ser chamado com a trava pega.
     */
    private void desistir(Aviao aviao, TipoOperacao tipo) {
        if (filaDe(tipo).remover(aviao)) {
            pedidoEmMillis.remove(aviao);
            RegistroDeEventos.publicar("Torre: " + aviao.getCodigoVoo() + " saiu da fila (interrompido)");
            return;
        }
        Autorizacao aut = autorizacoesPendentes.remove(aviao);
        if (aut != null) {
            pistasReservadas.remove(aut.getPista());
            RegistroDeEventos.publicar("Torre: " + aviao.getCodigoVoo() + " desistiu de " + tipo
                + "; pista " + aut.getPista().getNumero() + " devolvida");
            despachar(); // a pista devolvida pode ir para o próximo da fila
        }
    }

    /**
     * Distribui as pistas livres para quem está nas filas.
     * Só pode ser chamado com a trava pega.
     */
    private void despachar() {
        boolean concedeuAlguma = false;

        for (Pista pista : pistas) {
            // [CONTROLE-CONCORRENCIA #T1] Comentar esta linha faz a torre entregar a mesma
            // pista a dois aviões; a Pista detecta e lança a exceção de race condition.
            if (pistasReservadas.containsKey(pista)) continue; // a torre já deu para alguém
            if (!pista.estaLivre()) continue;                  // ocupada/fechada (Extra 4)

            TipoOperacao tipo = escolherProximaOperacao();
            if (tipo == null) break;                           // filas vazias

            FilaDeEspera fila = filaDe(tipo);
            Aviao escolhido = fila.retirarProximo();

            // Asserção: só recebe autorização quem estava na fila
            if (escolhido == null) {
                throw new ExcecaoAutorizacaoIndevida(
                    "Torre tentou autorizar " + tipo + " com a fila vazia");
            }
            if (autorizacoesPendentes.containsKey(escolhido)) {
                throw new ExcecaoAutorizacaoIndevida(escolhido.getCodigoVoo()
                    + " recebeu uma segunda autorização antes de usar a primeira");
            }

            Autorizacao aut = new Autorizacao(escolhido, pista, tipo);
            pistasReservadas.put(pista, aut);
            autorizacoesPendentes.put(escolhido, aut);
            pousosSeguidos = (tipo == TipoOperacao.POUSO) ? pousosSeguidos + 1 : 0;
            contabilizarEspera(aut);
            concedeuAlguma = true;

            RegistroDeEventos.publicar("Torre: autorizado " + aut);
        }

        // [CONTROLE-CONCORRENCIA #T3] Comentar esta linha faz a torre conceder a pista sem
        // acordar ninguém: os aviões dormem para sempre no await() e o aeroporto trava.
        if (concedeuAlguma) autorizacaoDisponivel.signalAll();
    }

    /** Decide se a próxima pista vai para um pouso ou uma decolagem. */
    private TipoOperacao escolherProximaOperacao() {
        boolean temPouso = !filaDePouso.estaVazia();
        boolean temDecolagem = !filaDeDecolagem.estaVazia();

        if (!temPouso && !temDecolagem) return null;
        if (!temPouso) return TipoOperacao.DECOLAGEM;
        if (!temDecolagem) return TipoOperacao.POUSO;

        if (filaDePouso.proximoEhEmergencia()) return TipoOperacao.POUSO;              // Extra 1
        if (filaDeDecolagem.temAvioEnvelhecido()
                && !filaDePouso.temAvioEnvelhecido()) return TipoOperacao.DECOLAGEM;    // Extra 2
        if (pousosSeguidos >= limitePousosSeguidos) return TipoOperacao.DECOLAGEM;      // alternância
        return TipoOperacao.POUSO;                                                      // preferência
    }

    private FilaDeEspera filaDe(TipoOperacao tipo) {
        return tipo == TipoOperacao.POUSO ? filaDePouso : filaDeDecolagem;
    }
}
