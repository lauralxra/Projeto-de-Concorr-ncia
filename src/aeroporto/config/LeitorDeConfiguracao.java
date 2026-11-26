package aeroporto.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Lê arquivos de configuração no formato .properties ou resolve cenários pré-definidos.
 */
public final class LeitorDeConfiguracao {

    private LeitorDeConfiguracao() {}

    /**
     * Carrega a configuração com base em um argumento de linha de comando:
     * - "C1": cenário balanceado (2 pistas, 3 pousos, 3 decolagens)
     * - "C2": cenário de pista única com estresse de pouso (1 pista, 4 pousos)
     * - caminho_para_arquivo.properties: lê o arquivo especificado
     * Se argumento for nulo ou vazio, retorna C1 por padrão.
     */
    public static Configuracao carregar(String argumento) {
        if (argumento == null || argumento.trim().isEmpty() || argumento.equalsIgnoreCase("C1")) {
            return Configuracao.cenarioC1();
        }
        if (argumento.equalsIgnoreCase("C2")) {
            return Configuracao.cenarioC2();
        }

        // Tenta ler como arquivo no disco
        File arquivo = new File(argumento);
        if (!arquivo.exists() || !arquivo.isFile()) {
            // Tenta procurar na pasta cenarios/ se o usuário passou só o nome
            File arquivoNaPasta = new File("cenarios", argumento.endsWith(".properties") ? argumento : argumento + ".properties");
            if (arquivoNaPasta.exists()) {
                arquivo = arquivoNaPasta;
            } else {
                throw new ExcecaoConfiguracaoInvalida("Arquivo de configuração não encontrado: " + argumento);
            }
        }

        return carregarDeArquivo(arquivo);
    }

    public static Configuracao carregarDeArquivo(File arquivo) {
        Properties prop = new Properties();
        try (InputStream in = new FileInputStream(arquivo)) {
            prop.load(in);
        } catch (IOException e) {
            throw new ExcecaoConfiguracaoInvalida("Falha ao abrir arquivo de configuração: " + arquivo.getPath(), e);
        }

        try {
            String nome = prop.getProperty("cenario.nome", arquivo.getName());
            int pistas = Integer.parseInt(prop.getProperty("pistas.quantidade", "2"));
            int pousoInicial = Integer.parseInt(prop.getProperty("avioes.pouso_inicial", "3"));
            int decolagemInicial = Integer.parseInt(prop.getProperty("avioes.decolagem_inicial", "3"));
            long intervaloPainel = Long.parseLong(prop.getProperty("painel.intervalo_ms", "120"));

            long delayVoo = Long.parseLong(prop.getProperty("delays.voo_ms", "3500"));
            long delaySolo = Long.parseLong(prop.getProperty("delays.solo_ms", "3000"));
            long delayOperacao = Long.parseLong(prop.getProperty("delays.operacao_pista_ms", "2500"));

            long autonomia = Long.parseLong(prop.getProperty("avioes.autonomia_ms", "40000"));
            double limiteEmergencia = Double.parseDouble(prop.getProperty("avioes.limite_emergencia", "0.30"));

            long aging = Long.parseLong(prop.getProperty("torre.limite_aging_ms", "6000"));
            int limitePousos = Integer.parseInt(prop.getProperty("torre.limite_pousos_seguidos", "2"));

            boolean manutencao = Boolean.parseBoolean(prop.getProperty("manutencao.ativa", "true"));
            long manutencaoIntervalo = Long.parseLong(prop.getProperty("manutencao.intervalo_ms", "15000"));
            long manutencaoDuracao = Long.parseLong(prop.getProperty("manutencao.duracao_ms", "4000"));

            return new Configuracao(
                    nome, pistas, pousoInicial, decolagemInicial, intervaloPainel,
                    delayVoo, delaySolo, delayOperacao,
                    autonomia, limiteEmergencia,
                    aging, limitePousos,
                    manutencao, manutencaoIntervalo, manutencaoDuracao
            );
        } catch (NumberFormatException e) {
            throw new ExcecaoConfiguracaoInvalida("Valor numérico inválido no arquivo de configuração: " + e.getMessage(), e);
        }
    }
}
