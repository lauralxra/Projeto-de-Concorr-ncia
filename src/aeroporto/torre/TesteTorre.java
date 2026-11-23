package aeroporto.torre;

import aeroporto.aviao.Aviao;
import aeroporto.pista.Pista;
import aeroporto.registro.RegistroDeEventos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Teste do módulo da torre, sem depender do resto do sistema.
 * Rodar:  java -cp out aeroporto.torre.TesteTorre
 *
 *  Teste 1: emergência fura a fila de pouso.
 *  Teste 2: avião interrompido na fila sai dela e não prende a pista.
 *  Teste 3: concluir a operação sem liberar a pista é barrado pela torre.
 *  Teste 4: estatísticas (totais, esperas, ocupação) e retrato consistente da torre.
 *  Teste 5: estresse — 8 aviões, 2 pistas, ciclo pouso/decolagem por alguns segundos.
 *           Se dois aviões caírem na mesma pista, a Pista lança exceção e o teste falha.
 *           Se as operações pararem de acontecer, a torre travou e o teste falha.
 *           Um monitor tira retratos o tempo todo e falha se algum avião estiver em dois lugares.
 */
public class TesteTorre {

    public static void main(String[] args) throws Exception {
        testeEmergencia();
        testeInterrupcaoNaFila();
        testeConcluirSemLiberar();
        testeEstatisticas();
        testeEstresse(5);
        System.out.println("\nTODOS OS TESTES DA TORRE PASSARAM");
    }

    static void testeEmergencia() throws Exception {
        System.out.println("=== Teste 1: emergência fura a fila ===");
        Pista pista = new Pista(1);
        TorreDeControle torre = new TorreDeControle(List.of(pista), 60_000, 3);

        Aviao bloqueador = new Aviao("LA 3302");
        Autorizacao primeira = torre.pedirPouso(bloqueador); // pega a única pista
        pista.ocupar(bloqueador);

        Aviao normal = new Aviao("AD 2774");
        Aviao emergencia = new Aviao("G3 1450");
        emergencia.setEmergencia(true);

        List<String> ordem = new ArrayList<>();
        Thread t1 = new Thread(() -> usarPista(torre, normal, ordem), "AD 2774");
        t1.start();
        Thread.sleep(100); // garante que o normal chegou antes
        Thread t2 = new Thread(() -> usarPista(torre, emergencia, ordem), "G3 1450");
        t2.start();
        Thread.sleep(100);

        pista.liberar(bloqueador);
        torre.concluirOperacao(bloqueador, primeira.getPista());
        esperarOuFalhar(t1, t2);

        if (ordem.size() != 2 || !ordem.get(0).equals("G3 1450")) {
            throw new AssertionError("Emergência deveria pousar primeiro, ordem foi " + ordem);
        }
        System.out.println("OK: ordem de pouso = " + ordem);
    }

    /** Espera as threads terminarem; se alguma ficar presa, a torre travou. */
    private static void esperarOuFalhar(Thread... threads) throws InterruptedException {
        for (Thread t : threads) {
            t.join(3000);
            if (t.isAlive()) {
                for (Thread outra : threads) outra.interrupt();
                throw new AssertionError("A torre travou: " + t.getName()
                    + " continua dormindo no await() depois de 3s sem ninguém para acordá-lo");
            }
        }
    }

    private static void usarPista(TorreDeControle torre, Aviao a, List<String> ordem) {
        try {
            Autorizacao aut = torre.pedirPouso(a);
            synchronized (ordem) { ordem.add(a.getCodigoVoo()); }
            aut.getPista().ocupar(a);
            Thread.sleep(20);
            aut.getPista().liberar(a);
            torre.concluirOperacao(a, aut.getPista());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void testeInterrupcaoNaFila() throws Exception {
        System.out.println("\n=== Teste 2: avião interrompido na fila ===");
        Pista pista = new Pista(1);
        TorreDeControle torre = new TorreDeControle(List.of(pista), 60_000, 3);

        Aviao dono = new Aviao("LA 3302");
        Autorizacao aut = torre.pedirPouso(dono); // pega a única pista
        pista.ocupar(dono);

        Aviao desistente = new Aviao("AD 2774");
        Thread t = new Thread(() -> {
            try {
                torre.pedirPouso(desistente);
                throw new AssertionError("AD 2774 não deveria receber autorização");
            } catch (InterruptedException e) {
                // esperado: foi interrompido enquanto esperava
            }
        });
        t.start();
        Thread.sleep(100);
        if (!torre.getFilaDePouso().contains(desistente)) {
            throw new AssertionError("AD 2774 deveria estar na fila antes da interrupção");
        }
        t.interrupt();
        t.join(2000);
        if (torre.getFilaDePouso().contains(desistente)) {
            throw new AssertionError("AD 2774 foi interrompido mas continuou na fila");
        }

        // A pista não pode ficar presa: o próximo avião precisa conseguir usá-la.
        pista.liberar(dono);
        torre.concluirOperacao(dono, aut.getPista());
        Aviao proximo = new Aviao("G3 1450");
        Thread t2 = new Thread(() -> usarPista(torre, proximo, new ArrayList<>()));
        t2.start();
        t2.join(2000);
        if (t2.isAlive()) {
            t2.interrupt();
            throw new AssertionError("A pista ficou reservada para o avião que desistiu");
        }
        if (!torre.getReservas().isEmpty()) {
            throw new AssertionError("Sobrou reserva de pista: " + torre.getReservas());
        }
        System.out.println("OK: o avião interrompido saiu da fila e a pista continuou disponível");
    }

    static void testeConcluirSemLiberar() throws Exception {
        System.out.println("\n=== Teste 3: concluir operação sem liberar a pista ===");
        Pista pista = new Pista(1);
        TorreDeControle torre = new TorreDeControle(List.of(pista), 60_000, 3);
        Aviao a = new Aviao("2Z 2201");
        Autorizacao aut = torre.pedirPouso(a);
        pista.ocupar(a);
        try {
            torre.concluirOperacao(a, aut.getPista()); // esqueceu o pista.liberar(a)
            throw new AssertionError("A torre aceitou concluir com o avião ainda na pista");
        } catch (ExcecaoAutorizacaoIndevida e) {
            System.out.println("OK: " + e.getMessage());
        }
        // A reserva continua de pé até o avião fazer do jeito certo.
        pista.liberar(a);
        torre.concluirOperacao(a, aut.getPista());
    }

    static void testeEstresse(int segundos) throws Exception {
        System.out.println("\n=== Teste 5: estresse, 8 aviões, 2 pistas, " + segundos + "s ===");
        RegistroDeEventos.setSilencioso(true);

        List<Pista> pistas = List.of(new Pista(1), new Pista(2));
        TorreDeControle torre = new TorreDeControle(pistas, 200, 2);
        AtomicBoolean rodando = new AtomicBoolean(true);
        AtomicInteger pousos = new AtomicInteger();
        AtomicInteger decolagens = new AtomicInteger();
        List<Throwable> erros = new ArrayList<>();

        String[] voos = {"AD 2774", "G3 1450", "LA 3302", "2Z 2201",
                         "AD 4410", "G3 1987", "LA 3555", "2Z 2810"};
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < voos.length; i++) {
            Aviao a = new Aviao(voos[i]);
            boolean comecaNoAr = i % 2 == 0;
            Thread t = new Thread(() -> {
                boolean noAr = comecaNoAr;
                try {
                    while (rodando.get()) {
                        Autorizacao aut = noAr ? torre.pedirPouso(a) : torre.pedirDecolagem(a);
                        aut.getPista().ocupar(a);
                        Thread.sleep(2);
                        aut.getPista().liberar(a);
                        torre.concluirOperacao(a, aut.getPista());
                        if (noAr) pousos.incrementAndGet(); else decolagens.incrementAndGet();
                        noAr = !noAr;
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    // fim do teste
                } catch (Throwable e) {
                    synchronized (erros) { erros.add(e); }
                }
            }, voos[i]);
            threads.add(t);
            t.start();
        }

        // Monitor: tira retratos da torre o tempo todo, enquanto os aviões mexem nela,
        // e confere que nenhum avião aparece em dois lugares (o que o Extra 7 vai fazer).
        AtomicInteger retratos = new AtomicInteger();
        Thread monitor = new Thread(() -> {
            while (rodando.get()) {
                RetratoDaTorre r = torre.getRetrato();
                List<String> problemas = r.verificarConsistencia();
                if (!problemas.isEmpty() || r.getTotalDeAvioes() > voos.length) {
                    synchronized (erros) {
                        erros.add(new AssertionError("Retrato inconsistente: " + problemas + " em " + r));
                    }
                    return;
                }
                retratos.incrementAndGet();
                try { Thread.sleep(1); } catch (InterruptedException e) { return; }
            }
        }, "monitor");
        threads.add(monitor);
        monitor.start();

        // Confere a cada segundo se o aeroporto continua andando.
        // Se nenhuma operação acontecer em um segundo inteiro, a torre travou
        // (ex.: [CONTROLE-CONCORRENCIA #T3] comentada: aviões dormindo para sempre).
        int ultimaContagem = 0;
        for (int s = 0; s < segundos && erros.isEmpty(); s++) {
            Thread.sleep(1000);
            int contagem = pousos.get() + decolagens.get();
            if (contagem == ultimaContagem) {
                rodando.set(false);
                for (Thread t : threads) t.interrupt();
                RegistroDeEventos.setSilencioso(false);
                throw new AssertionError("A torre travou: nenhuma operação no último segundo"
                    + " (pousos=" + pousos + " decolagens=" + decolagens + ")."
                    + " Aviões dormindo no await() sem ninguém para acordá-los.");
            }
            ultimaContagem = contagem;
        }
        rodando.set(false);
        for (Thread t : threads) t.interrupt();
        for (Thread t : threads) t.join(2000);
        RegistroDeEventos.setSilencioso(false);

        if (!erros.isEmpty()) {
            throw new AssertionError("Erro de concorrência: " + erros.get(0), erros.get(0));
        }
        if (decolagens.get() == 0 || pousos.get() == 0) {
            throw new AssertionError("Alguma fila travou (starvation): pousos=" + pousos
                + " decolagens=" + decolagens);
        }
        EstatisticasDaTorre est = torre.getEstatisticas();
        if (est.getTotalPousos() != pousos.get() || est.getTotalDecolagens() != decolagens.get()) {
            throw new AssertionError("Estatísticas da torre não batem com o que os aviões fizeram: torre diz "
                + est.getTotalPousos() + "/" + est.getTotalDecolagens() + ", aviões fizeram "
                + pousos + "/" + decolagens);
        }
        System.out.println("OK: " + pousos + " pousos e " + decolagens
            + " decolagens sem nenhum conflito de pista");
        System.out.println("OK: " + retratos + " retratos consistentes tirados durante o teste");
        System.out.println("Estatísticas: " + est);
    }

    static void testeEstatisticas() throws Exception {
        System.out.println("\n=== Teste 4: estatísticas da torre ===");
        Pista pista = new Pista(1);
        TorreDeControle torre = new TorreDeControle(List.of(pista), 60_000, 3);

        // LA 3302 pega a pista e fica nela 200 ms; AD 2774 espera esse tempo na fila.
        Aviao primeiro = new Aviao("LA 3302");
        Autorizacao aut = torre.pedirPouso(primeiro);
        pista.ocupar(primeiro);

        Aviao segundo = new Aviao("AD 2774");
        segundo.setEmergencia(true);
        Thread t = new Thread(() -> {
            try {
                Autorizacao a2 = torre.pedirDecolagem(segundo);
                a2.getPista().ocupar(segundo);
                a2.getPista().liberar(segundo);
                torre.concluirOperacao(segundo, a2.getPista());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "AD 2774");
        t.start();
        Thread.sleep(50);

        RetratoDaTorre r = torre.getRetrato();
        if (!r.getFilaDeDecolagem().contains(segundo) || r.getAviaoNaPista(1) != primeiro) {
            throw new AssertionError("Retrato errado: " + r);
        }

        Thread.sleep(150);
        pista.liberar(primeiro);
        torre.concluirOperacao(primeiro, aut.getPista());
        esperarOuFalhar(t);

        EstatisticasDaTorre est = torre.getEstatisticas();
        System.out.println(est);
        if (est.getTotalPousos() != 1 || est.getTotalDecolagens() != 1) {
            throw new AssertionError("Esperava 1 pouso e 1 decolagem: " + est);
        }
        if (est.getEsperaMediaPousoMillis() > 50) {
            throw new AssertionError("LA 3302 não esperou nada, média de pouso deveria ser ~0: " + est);
        }
        if (est.getEsperaMediaDecolagemMillis() < 150) {
            throw new AssertionError("AD 2774 esperou ~200 ms, média de decolagem veio baixa: " + est);
        }
        if (est.getEmergenciasAtendidas() != 0) {
            throw new AssertionError("Emergência só conta em pouso, não em decolagem: " + est);
        }
        if (est.getOperacoesPorPista().get(1) != 2 || est.getOcupacaoPercentual(1) <= 0) {
            throw new AssertionError("Pista 1 deveria ter 2 operações e ocupação > 0: " + est);
        }
        System.out.println("OK: totais, esperas e ocupação da pista conferem");
    }
}
