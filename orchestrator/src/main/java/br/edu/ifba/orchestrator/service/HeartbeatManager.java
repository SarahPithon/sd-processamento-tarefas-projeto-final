package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.worker.GerenciadorWorkers;
import br.edu.ifba.orchestrator.model.Tarefa;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class HeartbeatManager {
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 45;
    
    private final Map<String, Long> ultimoHeartbeat;
    private final ScheduledExecutorService scheduler;
    private final GerenciadorWorkers gerenciadorWorkers;
    private final GerenciadorTarefas gerenciadorTarefas;
    private volatile boolean ativo;
    
    // Estatísticas
    private final AtomicInteger heartbeatsEnviados;
    private final AtomicInteger heartbeatsRecebidos;
    private final AtomicInteger workersDesconectados;
    
    public HeartbeatManager(GerenciadorWorkers gerenciadorWorkers, GerenciadorTarefas gerenciadorTarefas) {
        this.ultimoHeartbeat = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.gerenciadorWorkers = gerenciadorWorkers;
        this.gerenciadorTarefas = gerenciadorTarefas;
        this.ativo = false;
        
        // Inicializar estatísticas
        this.heartbeatsEnviados = new AtomicInteger(0);
        this.heartbeatsRecebidos = new AtomicInteger(0);
        this.workersDesconectados = new AtomicInteger(0);
    }
    
    public void iniciar() {
        if (ativo) {
            return;
        }
        
        ativo = true;
        System.out.println("Sistema de heartbeat iniciado (intervalo: " + HEARTBEAT_INTERVAL_SECONDS + "s)");
        
        // Agendar envio de heartbeats
        scheduler.scheduleAtFixedRate(
            this::enviarHeartbeats,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // Agendar verificação de timeouts
        scheduler.scheduleAtFixedRate(
            this::verificarTimeouts,
            HEARTBEAT_TIMEOUT_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }
    
    public void parar() {
        ativo = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Sistema de heartbeat parado");
    }
    
    public void registrarWorker(String workerId) {
        ultimoHeartbeat.put(workerId, System.currentTimeMillis());
        System.out.println("Worker " + workerId + " registrado no sistema de heartbeat");
    }
    
    public void removerWorker(String workerId) {
        ultimoHeartbeat.remove(workerId);
        System.out.println("Worker " + workerId + " removido do sistema de heartbeat");
    }
    
    public void receberHeartbeatResponse(String workerId) {
        if (ultimoHeartbeat.containsKey(workerId)) {
            ultimoHeartbeat.put(workerId, System.currentTimeMillis());
            heartbeatsRecebidos.incrementAndGet();
            // System.out.println("Heartbeat recebido de " + workerId); // Log muito verboso
        }
    }
    
    private void enviarHeartbeats() {
        if (!ativo) return;
        
        Set<String> workersConectados = gerenciadorWorkers.obterWorkersConectados();
        
        for (String workerId : workersConectados) {
            try {
                boolean enviado = gerenciadorWorkers.enviarHeartbeat(workerId);
                if (enviado) {
                    heartbeatsEnviados.incrementAndGet();
                } else {
                    System.err.println("Falha ao enviar heartbeat para worker " + workerId);
                }
            } catch (Exception e) {
                System.err.println("Erro ao enviar heartbeat para worker " + workerId + ": " + e.getMessage());
            }
        }
    }
    
    private void verificarTimeouts() {
        if (!ativo) return;
        
        long agora = System.currentTimeMillis();
        long timeoutMillis = HEARTBEAT_TIMEOUT_SECONDS * 1000L;
        
        List<String> workersDesconectados = new ArrayList<>();
        
        for (Map.Entry<String, Long> entry : ultimoHeartbeat.entrySet()) {
            String workerId = entry.getKey();
            long ultimoHeartbeatTime = entry.getValue();
            
            if (agora - ultimoHeartbeatTime > timeoutMillis) {
                workersDesconectados.add(workerId);
            }
        }
        
        // Processar workers desconectados
        for (String workerId : workersDesconectados) {
            processarWorkerDesconectado(workerId);
        }
    }
    
    private void processarWorkerDesconectado(String workerId) {
        System.out.println("Worker " + workerId + " detectado como desconectado (timeout de heartbeat)");
        
        // Incrementar estatística
        workersDesconectados.incrementAndGet();
        
        // Remover worker do gerenciador
        gerenciadorWorkers.removerWorker(workerId);
        
        // Remover do sistema de heartbeat
        removerWorker(workerId);
        
        // Realocar tarefas do worker desconectado
        List<String> workersDisponiveis = new ArrayList<>(gerenciadorWorkers.obterWorkersConectados());
        if (!workersDisponiveis.isEmpty()) {
            // Primeiro, obter as tarefas que precisam ser realocadas
            List<Tarefa> tarefasParaRealocar = gerenciadorTarefas.getTarefasPorWorker(workerId);
            
            if (!tarefasParaRealocar.isEmpty()) {
                System.out.println("Realocando " + tarefasParaRealocar.size() + " tarefas do worker " + workerId);
                
                // Realocar as tarefas no gerenciador
                gerenciadorTarefas.realocarTarefasDoWorker(workerId, workersDisponiveis);
                
                // Enviar as tarefas realocadas para os novos workers
                gerenciadorWorkers.enviarTarefasRealocadas(tarefasParaRealocar, workersDisponiveis);
            } else {
                System.out.println("Worker " + workerId + " não tinha tarefas pendentes para realocar");
            }
        } else {
            System.out.println("Nenhum worker disponível para realocar tarefas de " + workerId);
        }
    }
    
    public Map<String, Integer> getEstatisticas() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("enviados", heartbeatsEnviados.get());
        stats.put("recebidos", heartbeatsRecebidos.get());
        stats.put("desconectados", workersDesconectados.get());
        stats.put("ativos", ultimoHeartbeat.size());
        return stats;
    }
    
    public Map<String, Object> getEstatisticasHeartbeat() {
        Map<String, Object> stats = new HashMap<>();
        
        long agora = System.currentTimeMillis();
        Map<String, Long> temposUltimoHeartbeat = new HashMap<>();
        
        for (Map.Entry<String, Long> entry : ultimoHeartbeat.entrySet()) {
            String workerId = entry.getKey();
            long ultimoTime = entry.getValue();
            long tempoDecorrido = (agora - ultimoTime) / 1000; // em segundos
            temposUltimoHeartbeat.put(workerId, tempoDecorrido);
        }
        
        stats.put("workers_monitorados", ultimoHeartbeat.size());
        stats.put("tempo_ultimo_heartbeat_segundos", temposUltimoHeartbeat);
        stats.put("heartbeat_ativo", ativo);
        stats.put("intervalo_heartbeat_segundos", HEARTBEAT_INTERVAL_SECONDS);
        stats.put("timeout_heartbeat_segundos", HEARTBEAT_TIMEOUT_SECONDS);
        
        return stats;
    }
    
    public boolean isWorkerAtivo(String workerId) {
        if (!ultimoHeartbeat.containsKey(workerId)) {
            return false;
        }
        
        long agora = System.currentTimeMillis();
        long ultimoTime = ultimoHeartbeat.get(workerId);
        long timeoutMillis = HEARTBEAT_TIMEOUT_SECONDS * 1000L;
        
        return (agora - ultimoTime) <= timeoutMillis;
    }
}