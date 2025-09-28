package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço de heartbeat para PrincipalOrquestrador.
 * Responde aos heartbeats recebidos dos backups.
 */
public class PrimaryHeartbeatService {
    
    private final String primaryId;
    private final RelógioLamport relógioLamport;
    private final ComunicacaoMulticast comunicacaoMulticast;
    
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private final AtomicLong heartbeatsRecebidos = new AtomicLong(0);
    private final AtomicLong respostasEnviadas = new AtomicLong(0);
    private final AtomicLong ultimoHeartbeatRecebido = new AtomicLong(0);
    
    public PrimaryHeartbeatService(String primaryId, RelógioLamport relógioLamport, 
                                  ComunicacaoMulticast comunicacaoMulticast) {
        this.primaryId = primaryId;
        this.relógioLamport = relógioLamport;
        this.comunicacaoMulticast = comunicacaoMulticast;
        
        configurarHandlers();
    }
    
    /**
     * Inicia o serviço de heartbeat
     */
    public void iniciar() {
        if (ativo.compareAndSet(false, true)) {
            System.out.println("[PRIMARY-HEARTBEAT] Iniciando serviço de heartbeat: " + primaryId);
        }
    }
    
    /**
     * Para o serviço de heartbeat
     */
    public void parar() {
        if (ativo.compareAndSet(true, false)) {
            System.out.println("[PRIMARY-HEARTBEAT] Parando serviço de heartbeat");
        }
    }
    
    /**
     * Configura os handlers para receber heartbeats
     */
    private void configurarHandlers() {
        // Handler para heartbeat de backup
        comunicacaoMulticast.registrarHandler("BACKUP_HEARTBEAT", mensagem -> {
            if (!ativo.get()) {
                return;
            }
            
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> dados = (Map<String, Object>) mensagem;
                
                String remetente = (String) dados.get("remetente");
                String destinatario = (String) dados.get("destinatario");
                
                // Verifica se o heartbeat é para este primary
                if (primaryId.equals(destinatario)) {
                    ultimoHeartbeatRecebido.set(System.currentTimeMillis());
                    heartbeatsRecebidos.incrementAndGet();
                    
                    // Atualiza relógio de Lamport
                    Object timestampObj = dados.get("timestamp");
                    if (timestampObj instanceof Number) {
                        relógioLamport.update(((Number) timestampObj).longValue());
                    }
                    
                    System.out.println("[PRIMARY-HEARTBEAT] Heartbeat recebido de " + remetente);
                    
                    // Envia resposta
                    enviarRespostaHeartbeat(remetente);
                }
                
            } catch (Exception e) {
                System.err.println("[PRIMARY-HEARTBEAT] Erro ao processar heartbeat: " + e.getMessage());
            }
        });
    }
    
    /**
     * Envia resposta de heartbeat para o backup
     */
    private void enviarRespostaHeartbeat(String backupId) {
        try {
            long timestamp = relógioLamport.tick();
            respostasEnviadas.incrementAndGet();
            
            Map<String, Object> responseData = Map.of(
                "tipo", "HEARTBEAT_RESPONSE",
                "remetente", primaryId,
                "destinatario", backupId,
                "timestamp", timestamp,
                "timestampReal", System.currentTimeMillis(),
                "status", "active"
            );
            
            comunicacaoMulticast.enviarMensagem("HEARTBEAT_RESPONSE", responseData);
            System.out.println("[PRIMARY-HEARTBEAT] Resposta enviada para " + backupId + 
                             " (timestamp: " + timestamp + ")");
            
        } catch (Exception e) {
            System.err.println("[PRIMARY-HEARTBEAT] Erro ao enviar resposta de heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Retorna estatísticas do serviço
     */
    public Map<String, Object> getEstatisticas() {
        return Map.of(
            "ativo", ativo.get(),
            "heartbeatsRecebidos", heartbeatsRecebidos.get(),
            "respostasEnviadas", respostasEnviadas.get(),
            "ultimoHeartbeatRecebido", ultimoHeartbeatRecebido.get(),
            "primaryId", primaryId
        );
    }
}