package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// Gerenciador simples de heartbeat entre OrquestradorBackup e PrincipalOrquestrador
public class SimpleHeartbeatManager {
    
    private final String backupId;
    private final String primaryId;
    private final RelógioLamport relógioLamport;
    private final ComunicacaoMulticast comunicacaoMulticast;
    private final Runnable onPrimaryFailure;
    
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private final AtomicLong ultimaRespostaRecebida = new AtomicLong(0);
    private final AtomicLong heartbeatsEnviados = new AtomicLong(0);
    private final AtomicLong respostasRecebidas = new AtomicLong(0);
    
    private static final long HEARTBEAT_INTERVAL_MS = 60000; // 1 minuto
    private static final long RESPONSE_TIMEOUT_MS = 10000;   // 10 segundos
    
    public SimpleHeartbeatManager(String backupId, String primaryId, 
                                RelógioLamport relógioLamport, 
                                ComunicacaoMulticast comunicacaoMulticast,
                                Runnable onPrimaryFailure) {
        this.backupId = backupId;
        this.primaryId = primaryId;
        this.relógioLamport = relógioLamport;
        this.comunicacaoMulticast = comunicacaoMulticast;
        this.onPrimaryFailure = onPrimaryFailure;
    }
    
    public void iniciar() {
        if (ativo.get()) {
            return;
        }
        
        ativo.set(true);
        scheduler = Executors.newScheduledThreadPool(1);
        
        // Configurar handler para resposta do heartbeat
        comunicacaoMulticast.registrarHandler("SIMPLE_HEARTBEAT_RESPONSE", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String responseBackupId = (String) dados.get("backupId");
            String responsePrimaryId = (String) dados.get("primaryId");
            
            if (backupId.equals(responseBackupId) && primaryId.equals(responsePrimaryId)) {
                ultimaRespostaRecebida.set(System.currentTimeMillis());
                respostasRecebidas.incrementAndGet();
                
                System.out.println("[HEARTBEAT] Resposta recebida do PrincipalOrquestrador: " + responsePrimaryId);
            }
        });
        
        // Iniciar envio de heartbeats
        scheduler.scheduleAtFixedRate(this::enviarHeartbeat, 0, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        System.out.println("[HEARTBEAT] Sistema de heartbeat simples iniciado (intervalo: 1 minuto)");
    }
    
    private void enviarHeartbeat() {
        if (!ativo.get()) {
            return;
        }
        
        try {
            Map<String, Object> heartbeat = Map.of(
                "backupId", backupId,
                "primaryId", primaryId,
                "timestamp", System.currentTimeMillis(),
                "lamportTimestamp", relógioLamport.tick()
            );
            
            comunicacaoMulticast.enviarMensagem("SIMPLE_HEARTBEAT", heartbeat);
            heartbeatsEnviados.incrementAndGet();
            
            System.out.println("[HEARTBEAT] Heartbeat enviado para PrincipalOrquestrador: " + primaryId);
            
            // Verificar se houve resposta do heartbeat anterior
            verificarRespostaPrimary();
            
        } catch (Exception e) {
            System.err.println("[HEARTBEAT] Erro ao enviar heartbeat: " + e.getMessage());
        }
    }
    
    private void verificarRespostaPrimary() {
        long agora = System.currentTimeMillis();
        long ultimaResposta = ultimaRespostaRecebida.get();
        
        // Se nunca recebeu resposta ou se a última resposta foi há mais de 1 minuto + timeout
        if (ultimaResposta == 0 || (agora - ultimaResposta) > (HEARTBEAT_INTERVAL_MS + RESPONSE_TIMEOUT_MS)) {
            // Só verifica falha se já enviou pelo menos 2 heartbeats
            if (heartbeatsEnviados.get() >= 2) {
                System.out.println("[HEARTBEAT] PrincipalOrquestrador não está respondendo. Assumindo liderança...");
                
                if (onPrimaryFailure != null) {
                    onPrimaryFailure.run();
                }
            }
        }
    }
    
    /**
     * Para o heartbeat manager
     */
    public void parar() {
        ativo.set(false);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        System.out.println("[SIMPLE_HEARTBEAT] Manager parado");
    }

    /**
     * Retorna estatísticas do heartbeat manager
     */
    public Map<String, Object> getEstatisticas() {
        return Map.of(
            "ativo", ativo.get(),
            "heartbeatsEnviados", heartbeatsEnviados.get(),
            "respostasRecebidas", respostasRecebidas.get(),
            "ultimaRespostaRecebida", ultimaRespostaRecebida.get(),
            "intervalHeartbeat", HEARTBEAT_INTERVAL_MS,
            "timeoutResposta", RESPONSE_TIMEOUT_MS
        );
    }
}