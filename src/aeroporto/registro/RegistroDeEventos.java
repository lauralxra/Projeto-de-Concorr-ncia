package aeroporto.registro;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registro de eventos (log do sistema): thread-safe com buffer circular.
 * 
 * Permite que todas as threads (torre, pistas, aviões, manutenção) publiquem
 * eventos concorrentemente sem causar travamento ou concorrência descontrolada.
 * O buffer circular mantém as últimas N mensagens em memória para que o painel
 * possa exibi-las em tempo real sem estourar a memória.
 */
public final class RegistroDeEventos {

    private static final int CAPACIDADE_PADRAO = 50;
    private static final DateTimeFormatter FORMATADOR_HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final ReentrantLock trava = new ReentrantLock();
    private static final String[] buffer = new String[CAPACIDADE_PADRAO];
    private static int inicio = 0;
    private static int tamanho = 0;

    /**
     * Se false, imprime no console (útil para testes unitários).
     * Se true, apenas armazena no buffer (usado pelo Painel para não sujar a animação).
     */
    private static volatile boolean silencioso = false;

    private RegistroDeEventos() {}

    /**
     * Publica uma mensagem no buffer circular.
     * Operação thread-safe com timestamp.
     */
    public static void publicar(String mensagem) {
        if (mensagem == null) return;

        String hora = LocalTime.now().format(FORMATADOR_HORA);
        String mensagemFormatada = "[" + hora + "] " + mensagem;

        trava.lock();
        try {
            int posicao = (inicio + tamanho) % CAPACIDADE_PADRAO;
            buffer[posicao] = mensagemFormatada;

            if (tamanho < CAPACIDADE_PADRAO) {
                tamanho++;
            } else {
                inicio = (inicio + 1) % CAPACIDADE_PADRAO;
            }
        } finally {
            trava.unlock();
        }

        if (!silencioso) {
            System.out.println(mensagemFormatada);
        }
    }

    /**
     * Devolve uma cópia segura (snapshot) das últimas N mensagens publicadas,
     * ordenadas da mais antiga para a mais recente.
     */
    public static List<String> obterUltimas(int quantidade) {
        trava.lock();
        try {
            int qtd = Math.min(quantidade, tamanho);
            List<String> resultado = new ArrayList<>(qtd);
            int primeiroIndice = (inicio + (tamanho - qtd)) % CAPACIDADE_PADRAO;

            for (int i = 0; i < qtd; i++) {
                int idx = (primeiroIndice + i) % CAPACIDADE_PADRAO;
                resultado.add(buffer[idx]);
            }
            return resultado;
        } finally {
            trava.unlock();
        }
    }

    /**
     * Limpa o buffer de eventos.
     */
    public static void limpar() {
        trava.lock();
        try {
            inicio = 0;
            tamanho = 0;
        } finally {
            trava.unlock();
        }
    }

    public static boolean isSilencioso() {
        return silencioso;
    }

    public static void setSilencioso(boolean valor) {
        silencioso = valor;
    }
}
