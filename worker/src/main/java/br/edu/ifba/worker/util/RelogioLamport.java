package br.edu.ifba.worker.util;

import java.util.concurrent.atomic.AtomicLong;

// Implementação do Relógio Lógico de Lamport para sincronização em sistemas distribuídos.
public class RelogioLamport {
    private final AtomicLong timestamp;
    private final String nodeId;

    public RelogioLamport(String nodeId) {
        this.timestamp = new AtomicLong(0);
        this.nodeId = nodeId;
    }

    // Realiza o incremento do Relógio de Lamport
    public long incremento() {
        long newTimestamp = timestamp.incrementAndGet();
        System.out.println("[LAMPORT]: " + newTimestamp);
        return newTimestamp;
    }

    // Atualiza o clock a partir do recebimento de uma mensagem
    public long update(long receivedTimestamp) {
        long currentTimestamp = timestamp.get();
        long newTimestamp = Math.max(currentTimestamp, receivedTimestamp) + 1;
        timestamp.set(newTimestamp);

        System.out.println("[LAMPORT]: " + newTimestamp);
        return newTimestamp;
    }

    // Obtém o clock atual
    public long obterTimestampAtual() {
        return timestamp.get();
    }

    public String obterIdNo() {
        return nodeId;
    }

    // Compara dois clocks do algoritmo de Lamport
    public static int comparar(long timestamp1, String idNo1, long timestamp2, String idNo2) {
        if (timestamp1 < timestamp2) {
            return -1;
        } else if (timestamp1 > timestamp2) {
            return 1;
        } else {
            // Se timestamps são iguais, usa nodeId para desempate
            return idNo1.compareTo(idNo2);
        }
    }

    @Override
    public String toString() {
        return "RelógioLamport{nodeId='" + nodeId + "', timestamp=" + obterTimestampAtual() + "}";
    }
}