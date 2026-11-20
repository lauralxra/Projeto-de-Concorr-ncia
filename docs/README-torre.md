# Torre de controle e filas (Pessoa 1)

> Seção para colar no README principal. Revisora: Pessoa 3.

## O que a torre faz

A torre é a única parte do sistema que decide quem usa qual pista e quando.
Os aviões nunca escolhem pista sozinhos: pedem à torre, esperam e usam só a pista
que veio na `Autorizacao`.

```
Avião ──pedirPouso()/pedirDecolagem()──▶ Torre ──(entra na fila, dorme)
                                          │
                       pista fica livre ──┤ despachar(): escolhe quem sai da fila,
                                          │ reserva a pista e acorda o avião
Avião ◀──────────── Autorizacao(pista) ───┘
Avião usa a pista ─▶ pista.liberar() ─▶ torre.concluirOperacao() ─▶ despachar() de novo
```

## Classes

| Classe | Papel |
|---|---|
| `TorreDeControle` | Recebe pedidos, mantém as filas, reserva pistas e entrega autorizações |
| `FilaDeEspera` | Base das filas: emergência → aging → ordem de chegada |
| `FilaDePouso` / `FilaDeDecolagem` | As duas filas da torre (só a de pouso respeita emergência) |
| `Autorizacao` | Bilhete imutável: avião + pista + tipo (pouso/decolagem) |
| `TipoOperacao` | `POUSO` ou `DECOLAGEM` |
| `ExcecaoAutorizacaoIndevida` | Asserções da torre (ver abaixo) |

## Política de prioridade

Quando uma pista fica livre, a torre decide nesta ordem:

1. **Emergência (Extra 1):** avião em emergência na fila de pouso passa na frente de todos.
2. **Anti-starvation / aging (Extra 2):** quem esperou mais que o limite configurado sobe
   de prioridade dentro da fila; se for alguém da fila de decolagem, a decolagem ganha a vez.
3. **Alternância:** pouso tem preferência (avião no ar gasta combustível), mas depois de
   N pousos seguidos uma decolagem passa, para as decolagens não travarem.
4. Fora isso, ordem de chegada (FIFO).

## Mecanismos de concorrência usados

- **`ReentrantLock` (justo)**: uma única trava protege as filas, o mapa de autorizações e as
  reservas de pista. Funciona como o `synchronized`, mas permite criar `Condition` e
  atender as threads na ordem de chegada (`new ReentrantLock(true)`).
- **`Condition` (`await` / `signalAll`)**: o avião que pediu fica dormindo em
  `autorizacaoDisponivel.await()` sem gastar CPU. Quando a torre concede alguma pista,
  chama `signalAll()`; cada avião acorda, confere se a autorização é **dele** e, se não for,
  volta a dormir (o `while` em volta do `await` protege contra acordar à toa).
- **Objetos imutáveis**: `Autorizacao` só tem campos `final`, então pode passar de uma
  thread para outra sem trava.
- **Cópias para o painel**: `getFilaDePouso()` e `getFilaDeDecolagem()` devolvem cópias,
  então o painel nunca percorre a lista real enquanto a torre mexe nela.

## Asserções (`ExcecaoAutorizacaoIndevida`)

- Um avião nunca está nas duas filas ao mesmo tempo.
- Só recebe autorização quem estava na fila; ninguém recebe duas autorizações seguidas.
- Só pode concluir operação na pista o avião para quem a torre reservou aquela pista.

## Linha que desativa o controle de concorrência

| Marcação | Arquivo | O que acontece se comentar |
|---|---|---|
| `[CONTROLE-CONCORRENCIA #T1]` | `TorreDeControle.java`, em `despachar()` | A torre deixa de checar se já reservou a pista e entrega a mesma pista a dois aviões. A `Pista` detecta dois ocupantes e lança a exceção, ex.: *"Pista 1 já ocupada por AD 2774 quando AD 4410 tentou usar"*. No teste de estresse aparece em menos de 1 segundo. |

## Como testar só a torre

```bash
javac -encoding UTF-8 -d out $(find src -name '*.java')
java -cp out aeroporto.torre.TesteTorre
```

O teste 1 confere que a emergência fura a fila. O teste 2 roda 8 aviões em 2 pistas por
5 segundos e falha se houver conflito de pista ou se alguma fila travar.
