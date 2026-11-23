package aeroporto.aviao;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Gerar códigos de voos sem repetiro o número.
 * GeradorDeVoos
 */

public class GeradorDeVoos {
    private static final String[] EMPRESAS = {"AD", "G3", "LA", "2Z"}; //Azul, Gol, Latam, Voepass
    private static final Set<Integer> numerosUsados = new HashSet<>();
    private static final Random sorteio = new Random();    

    //para garantir que não saia o código repetido
    public synchronized String gerarCodigo() {
        int numero;
        do {
            numero = 1000 + sorteio.nextInt(9000); // 1000 a 9000
        } while (!numerosUsados.add(numero)); // retorna false se já tinha o número
        return EMPRESAS[sorteio.nextInt(EMPRESAS.length)] + " " + numero;
    }

}
