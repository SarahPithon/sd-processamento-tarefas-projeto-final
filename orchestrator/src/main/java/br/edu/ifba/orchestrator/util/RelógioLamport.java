package br.edu.ifba.orchestrator.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação do Relógio Lógico de Lamport para sincronização em sistemas distribuídos.
 * 
 * O relógio de Lamport garante que eventos causalmente relacionados tenham timestamps
 * ordenados logicamente, mesmo em sistemas distribuídos onde não há sincronização
 * de relógio físico perfeita.
 */
public class RelógioLamport {
    private final AtomicLong timestamp;
    private final String nodeId;
    
    /**
     * Construtor do relógio de Lamport
     * @param nodeId Identificador único do nó (ex: "orchestrator-1", "worker-1")
     */
    public RelógioLamport(String nodeId) {
        this.timestamp = new AtomicLong(0);
        this.nodeId = nodeId;
    }
    
    /**
     * Incrementa o timestamp local antes de enviar uma mensagem
     * @return O novo timestamp após incremento
     */
    public long tick() {
        long newTimestamp = timestamp.incrementAndGet();
        System.out.println("[LAMPORT] " + nodeId + " - Tick: " + newTimestamp);
        return newTimestamp;
    }
    
    /**
     * Atualiza o timestamp local ao receber uma mensagem
     * @param receivedTimestamp Timestamp recebido na mensagem
     * @return O novo timestamp local após sincronização
     */
    public long update(long receivedTimestamp) {
        long currentTimestamp = timestamp.get();
        long newTimestamp = Math.max(currentTimestamp, receivedTimestamp) + 1;
        timestamp.set(newTimestamp);
        
        System.out.println("[LAMPORT] " + nodeId + " - Update: local=" + currentTimestamp + 
                          ", received=" + receivedTimestamp + ", new=" + newTimestamp);
        return newTimestamp;
    }
    
    /**
     * Obtém o timestamp atual sem modificá-lo
     * @return Timestamp atual
     */
    public long obterTimestampAtual() {
        return timestamp.get();
    }
    
    /**
     * Obtém o ID do nó
     * @return ID do nó
     */
    public String obterIdNo() {
        return nodeId;
    }
    
    /**
     * Cria um timestamp formatado para logs
     * @return String formatada com timestamp e nodeId
     */
    public String obterTimestampFormatado() {
        return "[" + nodeId + ":" + obterTimestampAtual() + "]";
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public long getCurrentTimestamp() { return obterTimestampAtual(); }
    
    @Deprecated
    public String getNodeId() { return obterIdNo(); }
    
    @Deprecated
    public String getFormattedTimestamp() { return obterTimestampFormatado(); }
    
    /**
     * Compara dois timestamps de Lamport
     * @param timestamp1 Primeiro timestamp
     * @param nodeId1 ID do nó do primeiro timestamp
     * @param timestamp2 Segundo timestamp
     * @param nodeId2 ID do nó do segundo timestamp
     * @return -1 se timestamp1 < timestamp2, 1 se timestamp1 > timestamp2, 
     *         comparação lexicográfica dos nodeIds se timestamps iguais
     */
    public static int compare(long timestamp1, String nodeId1, long timestamp2, String nodeId2) {
        if (timestamp1 < timestamp2) {
            return -1;
        } else if (timestamp1 > timestamp2) {
            return 1;
        } else {
            // Se timestamps são iguais, usa nodeId para desempate
            return nodeId1.compareTo(nodeId2);
        }
    }
    
    @Override
    public String toString() {
        return "RelógioLamport{nodeId='" + nodeId + "', timestamp=" + obterTimestampAtual() + "}";
    }
}