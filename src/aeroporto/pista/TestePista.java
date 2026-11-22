package aeroporto.pista;

import aeroporto.aviao.Aviao;
import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.Autorizacao;
import aeroporto.torre.TorreDeControle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testes do módulo de pistas.
 * Rodar:  java -cp out aeroporto.pista.TestePista
 *
 *  Teste 1: dois aviões tentam ocupar a mesma pista NO MESMO INSTANTE, 10.000 vezes.
 *           Em todas, exatamente um entra e o outro recebe ExcecaoPistaOcupada.
 *  Teste 2: avião que não está na pista não consegue liberá-la.
 *  Teste 3: simulação com a torre real por alguns segundos.
 *           Com o controle ligado: nenhuma exceção.
 *           Com [CONTROLE-CONCORRENCIA #T1] comentada: mostra a exceção e o motivo.
 */
public class TestePista {

    public static void main(String[] args) throws Exception {
        testeOcupacaoSimultanea(10_000);
        testeLiberacaoIndevida();
        testeSimulacaoComTorre(5);
    }

    static void testeOcupacaoSimultanea(int rodadas) throws Exception {
        System.out.println("=== Teste 1: ocupação simultânea, " + rodadas + " rodadas ===");
        Aviao a = new Aviao("AD 2774");
        Aviao b = new Aviao("G3 1450");
        int detectadas = 0;

        for (int i = 0; i < rodadas; i++) {
            Pista pista = new Pista(1);
            CountDownLatch largada = new CountDownLatch(1); // solta as duas threads juntas
            AtomicInteger entraram = new AtomicInteger();
            AtomicInteger barrados = new AtomicInteger();

            Runnable tentar1 = () -> tentarOcupar(pista, a, largada, entraram, barrados);
            Runnable tentar2 = () -> tentarOcupar(pista, b, largada, entraram, barrados);
            Thread t1 = new Thread(tentar1);
            Thread t2 = new Thread(tentar2);
            t1.start(); t2.start();
            largada.countDown();
            t1.join(); t2.join();

            if (entraram.get() != 1 || barrados.get() != 1) {
                throw new AssertionError("Rodada " + i + ": entraram=" + entraram
                    + " barrados=" + barrados + " (esperado 1 e 1)");
            }
            detectadas++;
        }
        System.out.println("OK: em " + detectadas + " rodadas, sempre 1 avião entrou e 1 foi barrado");
    }

    private static void tentarOcupar(Pista pista, Aviao aviao, CountDownLatch largada,
                                     AtomicInteger entraram, AtomicInteger barrados) {
        try {
            largada.await();
            pista.ocupar(aviao);
            entraram.incrementAndGet();
        } catch (ExcecaoPistaOcupada e) {
            barrados.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void testeLiberacaoIndevida() {
        System.out.println("\n=== Teste 2: liberação indevida ===");
        Pista pista = new Pista(2);
        Aviao dono = new Aviao("LA 3302");
        Aviao intruso = new Aviao("2Z 2201");
        pista.ocupar(dono);
        try {
            pista.liberar(intruso);
            throw new AssertionError("O intruso conseguiu liberar a pista de outro avião");
        } catch (ExcecaoLiberacaoIndevida e) {
            System.out.println("OK: " + e.getMessage());
        }
        pista.liberar(dono);
        if (!pista.estaLivre()) throw new AssertionError("Pista deveria estar livre");
    }

    static void testeSimulacaoComTorre(int segundos) throws Exception {
        System.out.println("\n=== Teste 3: simulação com a torre, 8 aviões, 2 pistas, " + segundos + "s ===");
        RegistroDeEventos.setSilencioso(true);

        List<Pista> pistas = List.of(new Pista(1), new Pista(2));
        TorreDeControle torre = new TorreDeControle(pistas, 200, 2);
        AtomicReference<RuntimeException> primeiroErro = new AtomicReference<>();
        AtomicInteger operacoes = new AtomicInteger();

        String[] voos = {"AD 2774", "G3 1450", "LA 3302", "2Z 2201",
                         "AD 4410", "G3 1987", "LA 3555", "2Z 2810"};
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < voos.length; i++) {
            Aviao aviao = new Aviao(voos[i]);
            boolean comecaNoAr = i % 2 == 0;
            Thread t = new Thread(() -> {
                boolean noAr = comecaNoAr;
                try {
                    while (primeiroErro.get() == null) {
                        Autorizacao aut = noAr ? torre.pedirPouso(aviao) : torre.pedirDecolagem(aviao);
                        aut.getPista().ocupar(aviao);
                        Thread.sleep(2);                       // tempo na pista
                        aut.getPista().liberar(aviao);
                        torre.concluirOperacao(aviao, aut.getPista());
                        operacoes.incrementAndGet();
                        noAr = !noAr;
                    }
                } catch (InterruptedException e) {
                    // fim do teste
                } catch (RuntimeException e) {
                    primeiroErro.compareAndSet(null, e);
                }
            }, voos[i]);
            threads.add(t);
            t.start();
        }

        long fim = System.currentTimeMillis() + segundos * 1000L;
        while (System.currentTimeMillis() < fim && primeiroErro.get() == null) {
            Thread.sleep(50);
        }
        for (Thread t : threads) t.interrupt();
        for (Thread t : threads) t.join(2000);
        RegistroDeEventos.setSilencioso(false);

        RuntimeException erro = primeiroErro.get();
        if (erro == null) {
            System.out.println("OK: " + operacoes + " pousos/decolagens sem nenhuma condição de corrida."
                + " O controle de concorrência está ativo.");
        } else {
            System.out.println("CONDIÇÃO DE CORRIDA DETECTADA após " + operacoes + " operações:");
            System.out.println("  " + erro.getClass().getSimpleName() + ": " + erro.getMessage());
            System.exit(1);
        }
    }
}