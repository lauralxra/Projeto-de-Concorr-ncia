package aeroporto.aviao;

import aeroporto.pista.Pista;
import aeroporto.registro.RegistroDeEventos;
import aeroporto.torre.TorreDeControle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Testes do módulo dos aviões.
 * Rodar:  java -cp out aeroporto.aviao.TesteAviao
 *
 *  Teste 1: a máquina de estados só aceita o próximo estado do ciclo.
 *  Teste 2: o gerador não repete número de voo.
 *  Teste 3: pular etapa lança ExcecaoTransicaoInvalida.
 *  Teste 4: combustível acaba e o avião entra em emergência.
 *  Teste 5: 6 aviões e 2 pistas com a torre real: ciclo infinito sem erro.
 */
public class TesteAviao {

    public static void main(String[] args) throws Exception {
        testeEstados();
        testeGerador();
        testeTransicaoInvalida();
        testeCombustivel();
        testeCicloComTorre(5);
        System.out.println("\nTODOS OS TESTES DOS AVIÕES PASSARAM");
    }

    static void testeEstados() {
        System.out.println("=== Teste 1: máquina de estados ===");
        for (EstadoAviao atual : EstadoAviao.values()) {
            for (EstadoAviao outro : EstadoAviao.values()) {
                boolean esperado = (outro == atual.proximo());
                if (atual.podeIrPara(outro) != esperado) {
                    throw new AssertionError("Transição errada: " + atual + " -> " + outro);
                }
            }
        }
        EstadoAviao e = EstadoAviao.EM_VOO;
        for (int i = 0; i < 6; i++) e = e.proximo();
        if (e != EstadoAviao.EM_VOO) throw new AssertionError("O ciclo não fecha");
        System.out.println("OK: só existe um caminho e ele fecha o ciclo de 6 estados");
    }

    static void testeGerador() {
        System.out.println("\n=== Teste 2: gerador de voos ===");
        GeradorDeVoos gerador = new GeradorDeVoos();
        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < 1000; i++) codigos.add(gerador.gerarCodigo());
        if (codigos.size() != 1000) throw new AssertionError("Gerou código repetido");
        System.out.println("OK: 1000 códigos diferentes, exemplo: " + codigos.iterator().next());
    }

    static void testeTransicaoInvalida() {
        System.out.println("\n=== Teste 3: transição inválida ===");
        Aviao aviao = new Aviao("AD 2774");
        aviao.mudarEstado(EstadoAviao.AGUARDANDO_POUSO);       // válido
        try {
            aviao.mudarEstado(EstadoAviao.DECOLANDO);          // pulou etapa
            throw new AssertionError("Deveria ter lançado ExcecaoTransicaoInvalida");
        } catch (ExcecaoTransicaoInvalida e) {
            System.out.println("OK: " + e.getMessage());
        }
    }

    static void testeCombustivel() throws Exception {
        System.out.println("\n=== Teste 4: combustível e emergência ===");
        Aviao aviao = new Aviao("G3 1450", EstadoAviao.EM_VOO, null, 100, 100, 10, 200, 0.25);
        if (aviao.estaEmEmergencia()) throw new AssertionError("Tanque cheio não é emergência");
        Thread.sleep(250);
        if (!aviao.estaEmEmergencia()) throw new AssertionError("Sem combustível deveria ser emergência");
        System.out.println("OK: combustível em " + aviao.porcentagemCombustivel() + "% -> emergência");
    }

    static void testeCicloComTorre(int segundos) throws Exception {
        System.out.println("\n=== Teste 5: 6 aviões, 2 pistas, " + segundos + "s ===");
        RegistroDeEventos.setSilencioso(true);
        AtomicReference<Throwable> primeiroErro = new AtomicReference<>();
        List<Aviao> avioes = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        try {
            List<Pista> pistas = List.of(new Pista(1), new Pista(2));
            TorreDeControle torre = new TorreDeControle(pistas, 200, 2);
            GeradorDeVoos gerador = new GeradorDeVoos();
            for (int i = 0; i < 6; i++) {
                EstadoAviao inicial = (i % 2 == 0) ? EstadoAviao.EM_VOO : EstadoAviao.EM_SOLO;
                Aviao a = new Aviao(gerador.gerarCodigo(), inicial, torre, 20, 20, 5, 60_000, 0.25);
                avioes.add(a);
                Thread t = new Thread(a, a.getCodigoVoo());
                t.setUncaughtExceptionHandler((th, ex) -> primeiroErro.compareAndSet(null, ex));
                threads.add(t);
                t.start();
            }
            Thread.sleep(segundos * 1000L);
            for (Thread t : threads) t.interrupt();
            for (Thread t : threads) t.join(2000);
        } finally {
            RegistroDeEventos.setSilencioso(false);
        }

        if (primeiroErro.get() != null) {
            throw new AssertionError("Erro num avião: " + primeiroErro.get(), primeiroErro.get());
        }
        int pousos = 0, decolagens = 0;
        for (Aviao a : avioes) {
            if (a.getTotalPousos() + a.getTotalDecolagens() == 0) {
                throw new AssertionError(a.getCodigoVoo() + " nunca conseguiu operar (starvation)");
            }
            pousos += a.getTotalPousos();
            decolagens += a.getTotalDecolagens();
        }
        System.out.println("OK: " + pousos + " pousos e " + decolagens
            + " decolagens, todos os aviões operaram e nenhum erro");
    }
}