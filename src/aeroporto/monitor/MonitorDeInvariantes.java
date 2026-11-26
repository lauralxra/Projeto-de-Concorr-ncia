package aeroporto.monitor;

import aeroporto.aviao.Aviao;
import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.RetratoDaTorre;
import aeroporto.torre.TorreDeControle;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extra 7 — Monitor de Invariantes.
 * Executa em thread independente fiscalizando a cada ciclo a coerência global do sistema:
 * 1. Usa o RetratoDaTorre (lido com a trava pega) para conferir se nenhum avião está
 *    duplicado em filas ou com pista reservada ao mesmo tempo.
 * 2. Confere a conservação do total de aeronaves registradas na simulação.
 */
public class MonitorDeInvariantes implements Runnable {

    private final List<Aviao> todosAvioes;
    private final TorreDeControle torre;
    private final long intervaloChecagemMs;

    private final AtomicLong totalChecagens = new AtomicLong(0);
    private final AtomicInteger violacoesDetectadas = new AtomicInteger(0);
    private final AtomicBoolean consistente = new AtomicBoolean(true);
    private volatile String statusAtual = "Inicializando fiscalização de invariantes...";

    public MonitorDeInvariantes(List<Aviao> todosAvioes, TorreDeControle torre, long intervaloChecagemMs) {
        this.todosAvioes = List.copyOf(todosAvioes);
        this.torre = torre;
        this.intervaloChecagemMs = Math.max(100, intervaloChecagemMs);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                verificarInvariantes();
                Thread.sleep(intervaloChecagemMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void verificarInvariantes() {
        totalChecagens.incrementAndGet();

        // 1. Conservação do número total de aviões cadastrados
        if (todosAvioes.size() != todosAvioes.stream().distinct().count()) {
            registrarViolacao("Aeronaves duplicadas detectadas no cadastro inicial!");
            return;
        }

        // 2. Consulta o Retrato consistente da Torre (tirado atomicamente com a trava da torre)
        RetratoDaTorre retrato = torre.getRetrato();
        List<String> problemas = retrato.verificarConsistencia();

        if (!problemas.isEmpty()) {
            for (String p : problemas) {
                registrarViolacao(p);
            }
            return;
        }

        // Se chegou até aqui, todas as regras foram cumpridas
        consistente.set(true);
        statusAtual = String.format("✔ SISTEMA ÍNTEGRO (%d checagens, %d aeronaves, 0 conflitos)",
                totalChecagens.get(), todosAvioes.size());
    }

    private void registrarViolacao(String motivo) {
        consistente.set(false);
        violacoesDetectadas.incrementAndGet();
        statusAtual = "❌ VIOLAÇÃO DE INVARIANTE: " + motivo;
        RegistroDeEventos.publicar("MONITOR DE INVARIANTES: " + statusAtual);
    }

    public boolean isConsistente() {
        return consistente.get();
    }

    public long getTotalChecagens() {
        return totalChecagens.get();
    }

    public int getViolacoesDetectadas() {
        return violacoesDetectadas.get();
    }

    public String getStatusAtual() {
        return statusAtual;
    }
}
