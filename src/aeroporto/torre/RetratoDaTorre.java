package aeroporto.torre;

import aeroporto.aviao.Aviao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Foto CONSISTENTE da torre num instante: as duas filas e as reservas de pista,
 * tiradas todas juntas com a trava da torre pega.
 *
 * Por que existe: se o painel ou o monitor de invariantes (Extra 7) chamassem
 * getFilaDePouso(), getFilaDeDecolagem() e getReservas() separadamente, um avião
 * poderia mudar de lugar entre uma chamada e outra. Ele apareceria em dois lugares
 * (ou em nenhum) sem que houvesse erro de verdade na torre. Com o retrato, tudo
 * foi lido no mesmo instante, então qualquer inconsistência encontrada é real.
 *
 * É imutável: pode ser lido de qualquer thread sem lock.
 */
public final class RetratoDaTorre {

    private final long instanteMillis;
    private final List<Aviao> filaDePouso;
    private final List<Aviao> filaDeDecolagem;
    private final Map<Integer, Autorizacao> reservas;

    RetratoDaTorre(long instanteMillis, List<Aviao> filaDePouso, List<Aviao> filaDeDecolagem,
                   Map<Integer, Autorizacao> reservas) {
        this.instanteMillis = instanteMillis;
        this.filaDePouso = Collections.unmodifiableList(new ArrayList<>(filaDePouso));
        this.filaDeDecolagem = Collections.unmodifiableList(new ArrayList<>(filaDeDecolagem));
        this.reservas = Collections.unmodifiableMap(new TreeMap<>(reservas));
    }

    public long getInstanteMillis() { return instanteMillis; }

    /** Aviões na fila de pouso, na ordem em que estão. */
    public List<Aviao> getFilaDePouso() { return filaDePouso; }

    /** Aviões na fila de decolagem, na ordem em que estão. */
    public List<Aviao> getFilaDeDecolagem() { return filaDeDecolagem; }

    /** Número da pista -> autorização que está com ela (pistas sem reserva não aparecem). */
    public Map<Integer, Autorizacao> getReservas() { return reservas; }

    /** Avião para quem a pista está reservada, ou null. */
    public Aviao getAviaoNaPista(int numeroPista) {
        Autorizacao aut = reservas.get(numeroPista);
        return aut == null ? null : aut.getAviao();
    }

    /** Quantos aviões estão sob controle da torre agora (nas filas ou com pista reservada). */
    public int getTotalDeAvioes() {
        return filaDePouso.size() + filaDeDecolagem.size() + reservas.size();
    }

    /**
     * Confere as invariantes da torre neste retrato. Lista vazia = tudo certo.
     *   - nenhum avião aparece duas vezes na mesma fila;
     *   - nenhum avião está nas duas filas;
     *   - nenhum avião está numa fila e com pista reservada ao mesmo tempo;
     *   - nenhum avião tem duas pistas reservadas.
     * Pensado para o monitor de invariantes (Extra 7).
     */
    public List<String> verificarConsistencia() {
        List<String> problemas = new ArrayList<>();
        Map<Aviao, String> ondeEsta = new HashMap<>();
        for (Aviao a : filaDePouso) registrar(a, "fila de pouso", ondeEsta, problemas);
        for (Aviao a : filaDeDecolagem) registrar(a, "fila de decolagem", ondeEsta, problemas);
        for (Map.Entry<Integer, Autorizacao> e : reservas.entrySet()) {
            registrar(e.getValue().getAviao(), "pista " + e.getKey(), ondeEsta, problemas);
        }
        return problemas;
    }

    private static void registrar(Aviao aviao, String lugar, Map<Aviao, String> ondeEsta,
                                  List<String> problemas) {
        String anterior = ondeEsta.putIfAbsent(aviao, lugar);
        if (anterior != null) {
            problemas.add(aviao.getCodigoVoo() + " está em dois lugares ao mesmo tempo: "
                + anterior + " e " + lugar);
        }
    }

    @Override
    public String toString() {
        return "Fila de pouso: " + filaDePouso + " | Fila de decolagem: " + filaDeDecolagem
            + " | Reservas: " + reservas;
    }
}
