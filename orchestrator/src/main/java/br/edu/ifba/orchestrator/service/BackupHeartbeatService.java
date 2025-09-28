package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço de heartbeat para OrquestradorBackup.
 * Envia heartbeats para o PrincipalOrquestrador a cada 1 minuto.
 * Se não receber resposta, ativa o modo failover.
 */
public class BackupHeartbeatService {
    
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 1 minuto
    private static final long RESPONSE_TIMEOUT_MS = 30000; // 30 segundos para timeout
    private static final String PRIMARY_ID = "orchestrator-primary";
    
    private final String backupId;
    private final RelógioLamport relógioLamport;
    private final ComunicacaoMulticast comunicacaoMulticast;
    private final ScheduledExecutorService scheduler;
    
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private final AtomicBoolean primaryResponsive = new AtomicBoolean(true);
    private final AtomicLong ultimoHeartbeatEnviado = new AtomicLong(0);
    private final AtomicLong ultimaRespostaRecebida = new AtomicLong(0);
    private final AtomicLong heartbeatsEnviados = new AtomicLong(0);
    private final AtomicLong respostasRecebidas = new AtomicLong(0);
    
    // Callback para quando o primary não responder
    private Runnable onPrimaryFailure;
    
    public BackupHeartbeatService(String backupId, RelógioLamport relógioLamport, 
                                 ComunicacaoMulticast comunicacaoMulticast) {
        this.backupId = backupId;
        this.relógioLamport = relógioLamport;
        this.comunicacaoMulticast = comunicacaoMulticast;
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        configurarHandlers();
    }
    
    /**
     * Define o callback para quando o primary falhar
     */
    public void setOnPrimaryFailure(Runnable callback) {
        this.onPrimaryFailure = callback;
    }
    
    /**
     * Inicia o serviço de heartbeat
     */
    public void iniciar() {
        if (ativo.compareAndSet(false, true)) {
            System.out.println("[BACKUP-HEARTBEAT] Iniciando serviço de heartbeat: " + backupId);
            
            // Agenda heartbeats a cada 1 minuto
            scheduler.scheduleAtFixedRate(this::enviarHeartbeat, 
                                        30, // Delay inicial de 30 segundos
                                        HEARTBEAT_INTERVAL_MS / 1000, // 1 minuto em segundos
                                        TimeUnit.SECONDS);
            
            // Monitora timeouts de resposta
            scheduler.scheduleAtFixedRate(this::verificarTimeout, 
                                        60, // Delay inicial de 1 minuto
                                        30, // Verifica a cada 30 segundos
                                        TimeUnit.SECONDS);
        }
    }
    
    /**
     * Para o serviço de heartbeat
     */
    public void parar() {
        if (ativo.compareAndSet(true, false)) {
            System.out.println("[BACKUP-HEARTBEAT] Parando serviço de heartbeat");
            
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Envia heartbeat para o PrincipalOrquestrador
     */
    private void enviarHeartbeat() {
        if (!ativo.get()) {
            return;
        }
        
        try {
            long timestamp = relógioLamport.tick();
            ultimoHeartbeatEnviado.set(System.currentTimeMillis());
            heartbeatsEnviados.incrementAndGet();
            
            Map<String, Object> heartbeatData = Map.of(
                "tipo", "BACKUP_HEARTBEAT",
                "remetente", backupId,
                "destinatario", PRIMARY_ID,
                "timestamp", timestamp,
                "timestampReal", System.currentTimeMillis()
            );
            
            comunicacaoMulticast.enviarMensagem("BACKUP_HEARTBEAT", heartbeatData);
            System.out.println("[BACKUP-HEARTBEAT] Heartbeat enviado para " + PRIMARY_ID + 
                             " (timestamp: " + timestamp + ")");
            
        } catch (Exception e) {
            System.err.println("[BACKUP-HEARTBEAT] Erro ao enviar heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Verifica se o primary está respondendo
     */
    private void verificarTimeout() {
        if (!ativo.get()) {
            return;
        }
        
        long agora = System.currentTimeMillis();
        long tempoSemResposta = agora - ultimaRespostaRecebida.get();
        
        // Se passou mais que o timeout sem resposta e já enviamos pelo menos um heartbeat
        if (tempoSemResposta > RESPONSE_TIMEOUT_MS && ultimoHeartbeatEnviado.get() > 0) {
            if (primaryResponsive.compareAndSet(true, false)) {
                System.err.println("[BACKUP-HEARTBEAT] TIMEOUT: PrincipalOrquestrador não está respondendo há " + 
                                 (tempoSemResposta / 1000) + " segundos");
                
                // Ativa o failover
                if (onPrimaryFailure != null) {
                    onPrimaryFailure.run();
                }
            }
        }
    }
    
    /**
     * Configura os handlers para receber respostas
     */
    private void configurarHandlers() {
        // Handler para resposta de heartbeat do primary
        comunicacaoMulticast.registrarHandler("HEARTBEAT_RESPONSE", mensagem -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> dados = (Map<String, Object>) mensagem;
                
                String remetente = (String) dados.get("remetente");
                String destinatario = (String) dados.get("destinatario");
                
                // Verifica se a resposta é para este backup
                if (PRIMARY_ID.equals(remetente) && backupId.equals(destinatario)) {
                    ultimaRespostaRecebida.set(System.currentTimeMillis());
                    respostasRecebidas.incrementAndGet();
                    
                    // Marca o primary como responsivo
                    if (primaryResponsive.compareAndSet(false, true)) {
                        System.out.println("[BACKUP-HEARTBEAT] PrincipalOrquestrador voltou a responder");
                    }
                    
                    // Atualiza relógio de Lamport
                    Object timestampObj = dados.get("timestamp");
                    if (timestampObj instanceof Number) {
                        relógioLamport.update(((Number) timestampObj).longValue());
                    }
                    
                    System.out.println("[BACKUP-HEARTBEAT] Resposta recebida do " + remetente);
                }
                
            } catch (Exception e) {
                System.err.println("[BACKUP-HEARTBEAT] Erro ao processar resposta de heartbeat: " + e.getMessage());
            }
        });
    }
    
    /**
     * Retorna se o primary está responsivo
     */
    public boolean isPrimaryResponsive() {
        return primaryResponsive.get();
    }
    
    /**
     * Retorna estatísticas do serviço
     */
    public Map<String, Object> getEstatisticas() {
        return Map.of(
            "ativo", ativo.get(),
            "primaryResponsive", primaryResponsive.get(),
            "heartbeatsEnviados", heartbeatsEnviados.get(),
            "respostasRecebidas", respostasRecebidas.get(),
            "ultimoHeartbeatEnviado", ultimoHeartbeatEnviado.get(),
            "ultimaRespostaRecebida", ultimaRespostaRecebida.get(),
            "backupId", backupId,
            "primaryId", PRIMARY_ID
        );
    }
}