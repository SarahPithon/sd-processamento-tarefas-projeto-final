package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;
import br.edu.ifba.orchestrator.model.Tarefa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gerenciador de sincronização de dados entre orquestradores líder e backup.
 * Cada orquestrador gerencia seu próprio arquivo JSON e sincroniza quando necessário.
 */
public class SincronizadorDados {
    
    private static final String ARQUIVO_TAREFAS_PRINCIPAL = "tarefas.json";
    private static final String ARQUIVO_TAREFAS_BACKUP = "tarefas_backup.json";
    private static final long SYNC_INTERVAL_MINUTES = 2; // Sincroniza a cada 2 minutos
    
    private final String nodeId;
    private final RelógioLamport relógioLamport;
    private final ComunicacaoMulticast comunicacaoMulticast;
    private final GerenciadorTarefas gerenciadorTarefas;
    private final ObjectMapper objectMapper;
    private final boolean isPrincipal; // Indica se é o orquestrador principal
    
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private final AtomicLong sincronizacoesEnviadas = new AtomicLong(0);
    private final AtomicLong sincronizacoesRecebidas = new AtomicLong(0);
    private final AtomicLong ultimaSincronizacao = new AtomicLong(0);
    
    public SincronizadorDados(String nodeId, RelógioLamport relógioLamport, 
                             ComunicacaoMulticast comunicacaoMulticast, 
                             GerenciadorTarefas gerenciadorTarefas) {
        this.nodeId = nodeId;
        this.relógioLamport = relógioLamport;
        this.comunicacaoMulticast = comunicacaoMulticast;
        this.gerenciadorTarefas = gerenciadorTarefas;
        this.isPrincipal = nodeId.equals("orchestrator-primary"); // Determina se é principal pelo ID
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        configurarHandlers();
    }
    
    /**
     * Inicia o sistema de sincronização
     */
    public void iniciar() {
        if (ativo.compareAndSet(false, true)) {
            System.out.println("[SYNC] Iniciando sistema de sincronização para: " + nodeId);
            
            // Agenda sincronização periódica
            scheduler.scheduleAtFixedRate(this::executarSincronizacao, 
                                        60, // Delay inicial de 1 minuto
                                        SYNC_INTERVAL_MINUTES * 60, // 2 minutos em segundos
                                        TimeUnit.SECONDS);
        }
    }
    
    /**
     * Para o sistema de sincronização
     */
    public void parar() {
        if (ativo.compareAndSet(true, false)) {
            System.out.println("[SYNC] Parando sistema de sincronização");
            
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
     * Executa sincronização baseada no papel atual (líder ou backup)
     */
    private void executarSincronizacao() {
        if (!ativo.get()) {
            return;
        }
        
        if (isPrincipal) {
            // Se sou principal, envio dados para backups
            enviarDadosParaBackups();
        } else {
            // Se sou backup, solicito dados do principal
            solicitarDadosDoLider();
        }
    }
    
    /**
     * Envia dados para orquestradores backup (quando é líder)
     */
    private void enviarDadosParaBackups() {
        try {
            // Lê arquivo de tarefas atual
            Path arquivoTarefas = Paths.get(ARQUIVO_TAREFAS_PRINCIPAL);
            if (!Files.exists(arquivoTarefas)) {
                System.out.println("[SYNC] Arquivo de tarefas não existe, criando vazio");
                criarArquivoVazio(arquivoTarefas);
                return;
            }
            
            String conteudoArquivo = Files.readString(arquivoTarefas);
            long timestampArquivo = Files.getLastModifiedTime(arquivoTarefas).toMillis();
            
            sincronizacoesEnviadas.incrementAndGet();
            ultimaSincronizacao.set(System.currentTimeMillis());
            
            System.out.println("[SYNC] Enviando sincronização de dados para backups");
            
            comunicacaoMulticast.enviarMensagem("DATA_SYNC", Map.of(
                "leaderId", nodeId,
                "timestamp", relógioLamport.tick(),
                "fileTimestamp", timestampArquivo,
                "fileContent", conteudoArquivo,
                "fileName", ARQUIVO_TAREFAS_PRINCIPAL,
                "syncType", "full"
            ));
            
        } catch (IOException e) {
            System.err.println("[SYNC] Erro ao enviar sincronização: " + e.getMessage());
        }
    }
    
    /**
     * Solicita dados do principal (quando é backup)
     */
    private void solicitarDadosDoLider() {
        String principalId = "orchestrator-primary";
        if (!principalId.equals(nodeId)) {
            System.out.println("[SYNC] Solicitando sincronização de dados do principal: " + principalId);
            
            comunicacaoMulticast.enviarMensagem("DATA_SYNC_REQUEST", Map.of(
                "backupId", nodeId,
                "leaderId", principalId,
                "timestamp", relógioLamport.tick(),
                "requestType", "full"
            ));
        }
    }
    
    /**
     * Configura handlers para mensagens de sincronização
     */
    private void configurarHandlers() {
        // Handler para sincronização de dados (backup recebe do principal)
        comunicacaoMulticast.registrarHandler("DATA_SYNC", mensagem -> {
            // Só processa se não for o principal
            if (isPrincipal) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String leaderId = (String) dados.get("leaderId");
            
            // Verifica se a sincronização vem do principal
            if ("orchestrator-primary".equals(leaderId)) {
                processarSincronizacaoRecebida(dados);
            }
        });
        
        // Handler para solicitação de sincronização (principal recebe do backup)
        comunicacaoMulticast.registrarHandler("DATA_SYNC_REQUEST", mensagem -> {
            // Só processa se for o principal
            if (!isPrincipal) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String backupId = (String) dados.get("backupId");
            String leaderId = (String) dados.get("leaderId");
            
            // Verifica se a solicitação é para este líder
            if (nodeId.equals(leaderId)) {
                System.out.println("[SYNC] Solicitação de sincronização recebida do backup: " + backupId);
                enviarDadosParaBackups(); // Envia dados imediatamente
            }
        });
        
        // Handler para sincronização imediata de tarefas (backup recebe do principal)
        comunicacaoMulticast.registrarHandler("SYNC_TAREFAS_IMEDIATA", mensagem -> {
            // Só processa se não for o principal
            if (isPrincipal) {
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            processarSincronizacaoImediata(dados);
        });
    }
    
    /**
     * Processa sincronização imediata de tarefas
     */
    private void processarSincronizacaoImediata(Map<String, Object> dados) {
        try {
            String conteudoJson = (String) dados.get("conteudoJson");
            Long timestamp = (Long) dados.get("timestamp");
            Long lamportTimestamp = (Long) dados.get("lamportTimestamp");
            Integer totalTarefas = (Integer) dados.get("totalTarefas");
            
            if (conteudoJson == null || conteudoJson.trim().isEmpty()) {
                System.err.println("[SYNC] Conteúdo JSON vazio na sincronização imediata");
                return;
            }
            
            // Atualiza relógio de Lamport
            if (lamportTimestamp != null) {
                relógioLamport.update(lamportTimestamp);
            }
            
            // Salva o conteúdo no arquivo de backup
            Path arquivoBackup = Paths.get(ARQUIVO_TAREFAS_BACKUP);
            Files.writeString(arquivoBackup, conteudoJson);
            
            // Recarrega as tarefas no gerenciador
            if (gerenciadorTarefas != null) {
                gerenciadorTarefas.recarregarTarefas();
            }
            
            sincronizacoesRecebidas.incrementAndGet();
            ultimaSincronizacao.set(System.currentTimeMillis());
            
            System.out.println("[SYNC] Sincronização imediata processada - " + totalTarefas + " tarefas");
            
        } catch (Exception e) {
            System.err.println("[SYNC] Erro ao processar sincronização imediata: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Processa sincronização recebida do líder
     */
    private void processarSincronizacaoRecebida(@SuppressWarnings("unchecked") Map<String, Object> dados) {
        try {
            String conteudoArquivo = (String) dados.get("fileContent");
            String nomeArquivo = (String) dados.get("fileName");
            Long timestampArquivo = (Long) dados.get("fileTimestamp");
            String leaderId = (String) dados.get("leaderId");
            
            if (conteudoArquivo == null || nomeArquivo == null) {
                System.err.println("[SYNC] Dados de sincronização inválidos");
                return;
            }
            
            sincronizacoesRecebidas.incrementAndGet();
            ultimaSincronizacao.set(System.currentTimeMillis());
            
            System.out.println("[SYNC] Processando sincronização recebida do líder: " + leaderId);
            
            // Salva arquivo de backup
            Path arquivoBackup = Paths.get(ARQUIVO_TAREFAS_BACKUP);
            Files.writeString(arquivoBackup, conteudoArquivo);
            
            if (timestampArquivo != null) {
                Files.setLastModifiedTime(arquivoBackup, 
                    java.nio.file.attribute.FileTime.fromMillis(timestampArquivo));
            }
            
            // Valida e carrega dados no gerenciador de tarefas
            if (validarArquivoTarefas(conteudoArquivo)) {
                // Cria backup do arquivo atual antes de substituir
                Path arquivoAtual = Paths.get(ARQUIVO_TAREFAS_PRINCIPAL);
                if (Files.exists(arquivoAtual)) {
                    Path backup = Paths.get(ARQUIVO_TAREFAS_PRINCIPAL + ".bak");
                    Files.copy(arquivoAtual, backup, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Substitui arquivo principal
                Files.writeString(arquivoAtual, conteudoArquivo);
                
                if (timestampArquivo != null) {
                    Files.setLastModifiedTime(arquivoAtual, 
                        java.nio.file.attribute.FileTime.fromMillis(timestampArquivo));
                }
                
                System.out.println("[SYNC] Arquivo de tarefas sincronizado com sucesso");
                
                // Recarrega dados no gerenciador de tarefas se necessário
                if (gerenciadorTarefas != null) {
                    // O gerenciador de tarefas carregará automaticamente na próxima operação
                    System.out.println("[SYNC] Dados sincronizados no gerenciador de tarefas");
                }
            } else {
                System.err.println("[SYNC] Arquivo de tarefas recebido é inválido, mantendo versão atual");
            }
            
        } catch (IOException e) {
            System.err.println("[SYNC] Erro ao processar sincronização: " + e.getMessage());
        }
    }
    
    /**
     * Valida se o conteúdo do arquivo de tarefas é válido
     */
    private boolean validarArquivoTarefas(String conteudo) {
        try {
            List<Tarefa> tarefas = objectMapper.readValue(conteudo,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Tarefa.class));
            
            // Validação básica
            if (tarefas == null) {
                return false;
            }
            
            // Verifica se todas as tarefas têm campos obrigatórios
            for (Tarefa tarefa : tarefas) {
                if (tarefa.getId() == null || tarefa.getTitulo() == null) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("[SYNC] Erro na validação do arquivo: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Cria arquivo vazio de tarefas
     */
    private void criarArquivoVazio(Path arquivo) throws IOException {
        Files.writeString(arquivo, "[]");
    }
    
    /**
     * Força sincronização imediata
     */
    public void sincronizarAgora() {
        if (ativo.get()) {
            System.out.println("[SYNC] Forçando sincronização imediata");
            executarSincronizacao();
        }
    }
    
    /**
     * Retorna estatísticas do sistema de sincronização
     */
    public Map<String, Object> getEstatisticas() {
        return Map.of(
            "ativo", ativo.get(),
            "sincronizacoesEnviadas", sincronizacoesEnviadas.get(),
            "sincronizacoesRecebidas", sincronizacoesRecebidas.get(),
            "ultimaSincronizacao", ultimaSincronizacao.get(),
            "isPrincipal", isPrincipal,
            "nodeId", nodeId,
            "arquivoPrincipal", ARQUIVO_TAREFAS_PRINCIPAL,
            "arquivoBackup", ARQUIVO_TAREFAS_BACKUP
        );
    }
}