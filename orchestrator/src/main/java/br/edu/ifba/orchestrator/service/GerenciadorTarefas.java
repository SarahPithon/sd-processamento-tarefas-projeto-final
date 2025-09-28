package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.model.Tarefa;
import br.edu.ifba.orchestrator.model.Tarefa.StatusTarefa;
import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Classe para armazenar metadados do sistema no JSON
 */
class SistemaMetadados {
    private int roundRobinIndex;
    private List<String> workersConectados;
    private long ultimaAtualizacao;
    private String liderAtual;
    
    public SistemaMetadados() {
        this.roundRobinIndex = 0;
        this.workersConectados = new ArrayList<>();
        this.ultimaAtualizacao = System.currentTimeMillis();
        this.liderAtual = "orchestrator-principal";
    }
    
    // Getters e Setters
    public int getRoundRobinIndex() { return roundRobinIndex; }
    public void setRoundRobinIndex(int roundRobinIndex) { this.roundRobinIndex = roundRobinIndex; }
    
    public List<String> getWorkersConectados() { return workersConectados; }
    public void setWorkersConectados(List<String> workersConectados) { this.workersConectados = workersConectados; }
    
    public long getUltimaAtualizacao() { return ultimaAtualizacao; }
    public void setUltimaAtualizacao(long ultimaAtualizacao) { this.ultimaAtualizacao = ultimaAtualizacao; }
    
    public String getLiderAtual() { return liderAtual; }
    public void setLiderAtual(String liderAtual) { this.liderAtual = liderAtual; }
}

/**
 * Estrutura completa do arquivo JSON
 */
class SistemaCompleto {
    private SistemaMetadados metadados;
    private List<Tarefa> tarefas;
    
    public SistemaCompleto() {
        this.metadados = new SistemaMetadados();
        this.tarefas = new ArrayList<>();
    }
    
    public SistemaMetadados getMetadados() { return metadados; }
    public void setMetadados(SistemaMetadados metadados) { this.metadados = metadados; }
    
    public List<Tarefa> getTarefas() { return tarefas; }
    public void setTarefas(List<Tarefa> tarefas) { this.tarefas = tarefas; }
}

public class GerenciadorTarefas {
    private static final String ARQUIVO_TAREFAS = "tarefas.json";
    private final Map<String, Tarefa> tarefas;
    private final AtomicLong contadorId;
    private final ObjectMapper objectMapper;
    private RelógioLamport relógioLamport;
    private ComunicacaoMulticast comunicacaoMulticast;
    private SistemaMetadados metadados;
    
    public GerenciadorTarefas() {
        this.tarefas = new ConcurrentHashMap<>();
        this.contadorId = new AtomicLong(1);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.metadados = new SistemaMetadados();
        
        carregarTarefasDoArquivo();
    }
    
    /**
     * Define o relógio de Lamport para este gerenciador
     */
    public void setRelógioLamport(RelógioLamport relógioLamport) {
        this.relógioLamport = relógioLamport;
    }
    
    /**
     * Define a comunicação multicast para sincronização automática
     */
    public void setComunicacaoMulticast(ComunicacaoMulticast comunicacaoMulticast) {
        this.comunicacaoMulticast = comunicacaoMulticast;
    }
    
    /**
     * Recarrega as tarefas do arquivo (usado na sincronização)
     */
    public void recarregarTarefas() {
        try {
            tarefas.clear();
            carregarTarefasDoArquivo();
            System.out.println("[SYNC] Tarefas recarregadas - Total: " + tarefas.size());
        } catch (Exception e) {
            System.err.println("[SYNC] Erro ao recarregar tarefas: " + e.getMessage());
        }
    }
    
    /**
     * Atualiza o estado do round-robin nos metadados
     */
    public void atualizarEstadoRoundRobin(int roundRobinIndex, List<String> workersConectados) {
        metadados.setRoundRobinIndex(roundRobinIndex);
        metadados.setWorkersConectados(new ArrayList<>(workersConectados));
        metadados.setUltimaAtualizacao(System.currentTimeMillis());
        salvarTarefasNoArquivo();
        System.out.println("[ROUND-ROBIN] Estado atualizado - Índice: " + roundRobinIndex + ", Workers: " + workersConectados);
    }
    
    /**
     * Obtém o índice atual do round-robin
     */
    public int obterIndiceRoundRobin() {
        return metadados.getRoundRobinIndex();
    }
    
    /**
     * Obtém a lista de workers conectados dos metadados
     */
    public List<String> obterWorkersConectadosMetadados() {
        return new ArrayList<>(metadados.getWorkersConectados());
    }
    
    /**
     * Obtém os metadados completos do sistema
     */
    public SistemaMetadados obterMetadados() {
        return metadados;
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public int getRoundRobinIndex() { return obterIndiceRoundRobin(); }
    
    @Deprecated
    public List<String> getWorkersConectadosMetadados() { return obterWorkersConectadosMetadados(); }
    
    @Deprecated
    public SistemaMetadados getMetadados() { return obterMetadados(); }
    
    public Tarefa criarTarefa(String titulo, String descricao) {
        String id = gerarIdTarefa();
        Tarefa tarefa = new Tarefa(id, titulo, descricao);
        tarefa.definirHorarioRecebimento(LocalDateTime.now());
        
        // Adiciona timestamp Lamport se disponível
        if (relógioLamport != null) {
            long lamportTimestamp = relógioLamport.tick();
            tarefa.definirClockLamport(lamportTimestamp);
            System.out.println("[LAMPORT] Nova tarefa criada: " + id + " [Lamport:" + lamportTimestamp + "]");
        }
        
        tarefas.put(id, tarefa);
        salvarTarefasNoArquivo();
        
        System.out.println("Nova tarefa criada: " + tarefa);
        return tarefa;
    }
    
    public void finalizarTarefa(String tarefaId, String workerId) {
        Tarefa tarefa = tarefas.get(tarefaId);
        if (tarefa != null) {
            tarefa.definirStatus(StatusTarefa.FINALIZADA);
            System.out.println("Tarefa " + tarefaId + " finalizada pelo worker " + workerId);
            salvarTarefasNoArquivo();
        } else {
            System.err.println("Tarefa não encontrada: " + tarefaId);
        }
    }
    
    public void atribuirTarefaAoWorker(String tarefaId, String workerId) {
        Tarefa tarefa = tarefas.get(tarefaId);
        if (tarefa != null) {
            // Se a tarefa já tinha um worker responsável, incrementar contador de realocação
            if (tarefa.getWorkerResponsavel() != null && !tarefa.getWorkerResponsavel().equals(workerId)) {
                tarefa.incrementarRealocada();
                System.out.println("Tarefa " + tarefaId + " realocada de " + tarefa.getWorkerResponsavel() + " para " + workerId);
            }
            
            tarefa.definirWorkerResponsavel(workerId);
            salvarTarefasNoArquivo();
        }
    }
    
    public List<Tarefa> obterTarefasPorWorker(String workerId) {
        return tarefas.values().stream()
                .filter(tarefa -> workerId.equals(tarefa.getWorkerResponsavel()))
                .filter(tarefa -> tarefa.getStatus() == StatusTarefa.PENDENTE)
                .collect(Collectors.toList());
    }
    
    public List<Tarefa> obterTarefasPendentes() {
        return tarefas.values().stream()
                .filter(tarefa -> tarefa.getStatus() == StatusTarefa.PENDENTE)
                .collect(Collectors.toList());
    }
    
    public List<Tarefa> obterTarefasFinalizadas() {
        return tarefas.values().stream()
                .filter(tarefa -> tarefa.getStatus() == StatusTarefa.FINALIZADA)
                .collect(Collectors.toList());
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public List<Tarefa> getTarefasPorWorker(String workerId) { return obterTarefasPorWorker(workerId); }
    
    @Deprecated
    public List<Tarefa> getTarefasPendentes() { return obterTarefasPendentes(); }
    
    @Deprecated
    public List<Tarefa> getTarefasFinalizadas() { return obterTarefasFinalizadas(); }
    
    public Tarefa obterTarefa(String tarefaId) {
        return tarefas.get(tarefaId);
    }
    
    // Método de compatibilidade (deprecated)
    @Deprecated
    public Tarefa getTarefa(String tarefaId) { return obterTarefa(tarefaId); }
    
    public void realocarTarefasDoWorker(String workerDesconectado, List<String> workersDisponiveis) {
        if (workersDisponiveis.isEmpty()) {
            System.out.println("Nenhum worker disponível para realocação das tarefas de " + workerDesconectado);
            return;
        }
        
        List<Tarefa> tarefasParaRealocar = getTarefasPorWorker(workerDesconectado);
        
        if (tarefasParaRealocar.isEmpty()) {
            System.out.println("Worker " + workerDesconectado + " não tinha tarefas pendentes");
            return;
        }
        
        System.out.println("Realocando " + tarefasParaRealocar.size() + " tarefas do worker " + workerDesconectado);
        
        // Distribuir tarefas usando round robin
        int workerIndex = 0;
        for (Tarefa tarefa : tarefasParaRealocar) {
            String novoWorker = workersDisponiveis.get(workerIndex % workersDisponiveis.size());
            atribuirTarefaAoWorker(tarefa.getId(), novoWorker);
            workerIndex++;
        }
        
        salvarTarefasNoArquivo();
    }
    
    public Map<String, Integer> obterEstatisticas() {
        Map<String, Integer> stats = new HashMap<>();
        
        int totalTarefas = tarefas.size();
        int tarefasPendentes = obterTarefasPendentes().size();
        int tarefasFinalizadas = obterTarefasFinalizadas().size();
        int tarefasRealocadas = (int) tarefas.values().stream()
                .mapToInt(Tarefa::getRealocada)
                .sum();
        
        stats.put("total", totalTarefas);
        stats.put("pendentes", tarefasPendentes);
        stats.put("finalizadas", tarefasFinalizadas);
        stats.put("realocadas", tarefasRealocadas);
        
        return stats;
    }
    
    public Map<String, Object> obterEstatisticasDetalhadas() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalTarefas = tarefas.size();
        int tarefasPendentes = obterTarefasPendentes().size();
        int tarefasFinalizadas = obterTarefasFinalizadas().size();
        
        stats.put("total_tarefas", totalTarefas);
        stats.put("tarefas_pendentes", tarefasPendentes);
        stats.put("tarefas_finalizadas", tarefasFinalizadas);
        
        if (totalTarefas > 0) {
            stats.put("percentual_conclusao", (double) tarefasFinalizadas / totalTarefas * 100);
        } else {
            stats.put("percentual_conclusao", 0.0);
        }
        
        // Estatísticas por worker
        Map<String, Long> tarefasPorWorker = tarefas.values().stream()
                .filter(t -> t.getWorkerResponsavel() != null)
                .collect(Collectors.groupingBy(
                    Tarefa::getWorkerResponsavel,
                    Collectors.counting()
                ));
        stats.put("tarefas_por_worker", tarefasPorWorker);
        
        return stats;
    }
    
    private String gerarIdTarefa() {
        return "task-" + System.currentTimeMillis() + "-" + contadorId.getAndIncrement();
    }
    
    private void salvarTarefasNoArquivo() {
        try {
            // Atualizar timestamp dos metadados
            metadados.setUltimaAtualizacao(System.currentTimeMillis());
            
            // Criar estrutura completa com metadados e tarefas
            SistemaCompleto sistemaCompleto = new SistemaCompleto();
            sistemaCompleto.setMetadados(metadados);
            sistemaCompleto.setTarefas(new ArrayList<>(tarefas.values()));
            
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(ARQUIVO_TAREFAS), sistemaCompleto);
            
            // Notifica backups sobre mudanças
            notificarMudancaParaBackups();
        } catch (Exception e) {
            System.err.println("Erro ao salvar tarefas no arquivo: " + e.getMessage());
        }
    }
    
    /**
     * Notifica backups sobre mudanças no JSON de tarefas
     */
    private void notificarMudancaParaBackups() {
        if (comunicacaoMulticast != null) {
            try {
                // Lê o conteúdo atual do arquivo JSON
                String conteudoJson = Files.readString(Paths.get(ARQUIVO_TAREFAS));
                
                Map<String, Object> dadosSincronizacao = Map.of(
                    "tipo", "SYNC_TAREFAS_IMEDIATA",
                    "conteudoJson", conteudoJson,
                    "timestamp", System.currentTimeMillis(),
                    "lamportTimestamp", relógioLamport != null ? relógioLamport.getCurrentTimestamp() : 0L,
                    "totalTarefas", tarefas.size()
                );
                
                comunicacaoMulticast.enviarMensagem("SYNC_TAREFAS_IMEDIATA", dadosSincronizacao);
                System.out.println("[SYNC] Sincronização imediata enviada para backups - " + tarefas.size() + " tarefas");
                
            } catch (IOException e) {
                System.err.println("[SYNC] Erro ao enviar sincronização para backups: " + e.getMessage());
            }
        }
    }
    
    private void carregarTarefasDoArquivo() {
        try {
            File arquivo = new File(ARQUIVO_TAREFAS);
            if (arquivo.exists()) {
                try {
                    // Tentar carregar com a nova estrutura (com metadados)
                    SistemaCompleto sistemaCompleto = objectMapper.readValue(arquivo, SistemaCompleto.class);
                    
                    // Carregar metadados
                    if (sistemaCompleto.getMetadados() != null) {
                        this.metadados = sistemaCompleto.getMetadados();
                        System.out.println("[METADADOS] Carregados - Round-robin index: " + metadados.getRoundRobinIndex());
                    }
                    
                    // Carregar tarefas
                    if (sistemaCompleto.getTarefas() != null) {
                        for (Tarefa tarefa : sistemaCompleto.getTarefas()) {
                            tarefas.put(tarefa.getId(), tarefa);
                            
                            // Atualizar contador para evitar IDs duplicados
                            try {
                                String[] parts = tarefa.getId().split("-");
                                if (parts.length >= 3) {
                                    long id = Long.parseLong(parts[2]);
                                    if (id >= contadorId.get()) {
                                        contadorId.set(id + 1);
                                    }
                                }
                            } catch (NumberFormatException e) {
                                // Ignorar se não conseguir extrair o ID
                            }
                        }
                    }
                } catch (Exception e) {
                    // Fallback: tentar carregar com a estrutura antiga (apenas lista de tarefas)
                    System.out.println("[FALLBACK] Tentando carregar formato antigo do JSON...");
                    List<Tarefa> listaTarefas = objectMapper.readValue(arquivo, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Tarefa.class));
                    
                    for (Tarefa tarefa : listaTarefas) {
                        tarefas.put(tarefa.getId(), tarefa);
                        
                        // Atualizar contador para evitar IDs duplicados
                        try {
                            String[] parts = tarefa.getId().split("-");
                            if (parts.length >= 3) {
                                long id = Long.parseLong(parts[2]);
                                if (id >= contadorId.get()) {
                                    contadorId.set(id + 1);
                                }
                            }
                        } catch (NumberFormatException ex) {
                            // Ignorar se não conseguir extrair o ID
                        }
                    }
                    
                    // Inicializar metadados padrão para formato antigo
                    this.metadados = new SistemaMetadados();
                }
                
                System.out.println("Carregadas " + tarefas.size() + " tarefas do arquivo");
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar tarefas do arquivo: " + e.getMessage());
        }
    }
    
    public void limparTarefasFinalizadas() {
        List<String> idsParaRemover = tarefas.values().stream()
                .filter(tarefa -> tarefa.getStatus() == StatusTarefa.FINALIZADA)
                .map(Tarefa::getId)
                .collect(Collectors.toList());
        
        for (String id : idsParaRemover) {
            tarefas.remove(id);
        }
        
        if (!idsParaRemover.isEmpty()) {
            salvarTarefasNoArquivo();
            System.out.println("Removidas " + idsParaRemover.size() + " tarefas finalizadas");
        }
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public Map<String, Integer> getEstatisticas() { return obterEstatisticas(); }
    
    @Deprecated
    public Map<String, Object> getEstatisticasDetalhadas() { return obterEstatisticasDetalhadas(); }
}