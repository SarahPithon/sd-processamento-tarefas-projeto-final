package br.edu.ifba.orchestrator;

import br.edu.ifba.orchestrator.service.BackupHeartbeatService;
import br.edu.ifba.orchestrator.service.AutoSyncService;
import br.edu.ifba.orchestrator.service.GerenciadorTarefas;
import br.edu.ifba.orchestrator.service.HeartbeatManager;
import br.edu.ifba.orchestrator.worker.GerenciadorWorkers;
import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquestrador Backup que pode assumir liderança quando necessário
 */
public class OrquestradorBackup {
    
    private final String backupId;
    private static final String PRIMARY_ID = "orchestrator-primary";
    
    private ServidorOrquestrador server;
    private GerenciadorTarefas gerenciadorTarefas;
    private HeartbeatManager heartbeatManager;
    private BackupHeartbeatService backupHeartbeatService;
    private AutoSyncService autoSyncService;
    private RelógioLamport relógioLamport;
    private ComunicacaoMulticast comunicacaoMulticast;
    private ScheduledExecutorService scheduler;
    
    private final AtomicBoolean ativo = new AtomicBoolean(false);
    private final AtomicBoolean servidorRodando = new AtomicBoolean(false);
    private final AtomicBoolean souLider = new AtomicBoolean(false);
    
    private Scanner scanner = new Scanner(System.in);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    
    public OrquestradorBackup(String backupId) {
        this.backupId = backupId;
    }
    
    public void iniciar() {
        try {
            System.out.println("=== ORQUESTRADOR BACKUP INICIANDO ===");
            System.out.println("ID: " + backupId);
            System.out.println("Primary ID: " + PRIMARY_ID);
            
            // Inicializar componentes básicos
            relógioLamport = new RelógioLamport(backupId);
            comunicacaoMulticast = new ComunicacaoMulticast(backupId, relógioLamport);
            scheduler = Executors.newScheduledThreadPool(2);
            gerenciadorTarefas = new GerenciadorTarefas();
            gerenciadorTarefas.setRelógioLamport(relógioLamport);
            
            // Configurar handlers multicast
            configurarHandlersMulticast();
            
            // Iniciar comunicação multicast
            comunicacaoMulticast.iniciar();
            
            // Inicializar sistema de heartbeat para backup
            backupHeartbeatService = new BackupHeartbeatService(
                backupId, 
                relógioLamport, 
                comunicacaoMulticast
            );
            
            // Configurar callback para failover
            backupHeartbeatService.setOnPrimaryFailure(this::assumirLideranca);
            
            // Iniciar sistema de heartbeat
            backupHeartbeatService.iniciar();
            
            // Inicializar sincronização automática de arquivos
            autoSyncService = new AutoSyncService(
                backupId, relógioLamport, comunicacaoMulticast, "tarefas.json"
            );
            autoSyncService.iniciar();
            
            ativo.set(true);
            
            System.out.println("Backup iniciado. Monitorando PrincipalOrquestrador...");
            
            // Menu de monitoramento
            showMenu();
            
        } catch (Exception e) {
            System.err.println("Erro ao iniciar backup: " + e.getMessage());
            e.printStackTrace();
        } finally {
            parar();
        }
    }
    
    /**
     * Assume liderança quando o PrincipalOrquestrador falha
     */
    private void assumirLideranca() {
        if (souLider.get()) {
            return; // Já é líder
        }
        
        try {
            System.out.println("*** DETECTADA FALHA DO PRINCIPAL ORQUESTRADOR ***");
            System.out.println("Iniciando processo de eleição de líder...");
            
            // Verificar se deve assumir liderança (eleição simples baseada em ID)
            if (!deveAssumirLideranca()) {
                System.out.println("Outro backup com prioridade maior assumirá a liderança.");
                return;
            }
            
            System.out.println("*** ASSUMINDO LIDERANÇA ***");
            souLider.set(true);
            
            // Parar heartbeat para o primary (não faz mais sentido)
            if (backupHeartbeatService != null) {
                backupHeartbeatService.parar();
            }
            
            if (!servidorRodando.get()) {
                // Iniciar servidor nas mesmas portas do principal
                server = new ServidorOrquestrador(9090, 8080);
                
                Thread serverThread = new Thread(() -> {
                    try {
                        server.iniciar(gerenciadorTarefas);
                        servidorRodando.set(true);
                        
                        // Configurar dependências após o start
                        GerenciadorWorkers gerenciadorWorkers = server.obterGerenciadorWorkers();
                        if (gerenciadorWorkers != null) {
                            // Injetar relógio Lamport no ServicoAtividadeImpl
                            ServicoAtividadeImpl servicoAtividade = server.obterServicoAtividade();
                            if (servicoAtividade != null) {
                                servicoAtividade.setRelógioLamport(relógioLamport);
                            }
                            
                            // Injetar relógio Lamport no GerenciadorWorkers
                            gerenciadorWorkers.setRelógioLamport(relógioLamport);
                            
                            // Restaurar estado do round-robin dos metadados
                            gerenciadorWorkers.restaurarEstadoRoundRobin();
                            
                            // Descobrir e conectar workers existentes
                            descobrirWorkersExistentes(gerenciadorWorkers);
                            
                            // Inicializar sistema de heartbeat para workers (mesma estratégia do principal)
                            heartbeatManager = new HeartbeatManager(gerenciadorWorkers, gerenciadorTarefas);
                            gerenciadorWorkers.setHeartbeatManager(heartbeatManager);
                            heartbeatManager.iniciar();
                            
                            // Notificar workers sobre mudança de orquestrador
                            notificarMudancaOrquestrador(gerenciadorWorkers);
                        }
                        
                        server.bloquearAteDesligamento();
                    } catch (IOException | InterruptedException e) {
                        System.err.println("Erro ao iniciar servidor backup: " + e.getMessage());
                        servidorRodando.set(false);
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();
                
                System.out.println("Servidor backup iniciado nas portas 9090 (gRPC) e 8080 (TCP)");
                System.out.println("OrquestradorBackup agora é o novo PrincipalOrquestrador!");
                
                // Aguardar um pouco para o servidor inicializar
                Thread.sleep(2000);
                
                // Enviar notificação via multicast para todos os nós
                enviarNotificacaoMudancaViaMulticast();
                
                // Confirmar liderança para outros backups
                confirmarLideranca();
                
                // Verificar saúde do sistema após assumir liderança
                Thread.sleep(3000); // Aguardar estabilização
                verificarSaudeSistema();
                
                // Configurar monitoramento contínuo
                configurarMonitoramentoLider();
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao assumir liderança: " + e.getMessage());
            // Tentar recuperação automática
            tentarRecuperacao();
        }
    }
    
    /**
     * Descobre e conecta workers existentes baseado nos metadados salvos
     */
    private void descobrirWorkersExistentes(GerenciadorWorkers gerenciadorWorkers) {
        System.out.println("[DISCOVERY] Iniciando descoberta de workers existentes...");
        
        // Obter lista de workers dos metadados
        List<String> workersMetadados = gerenciadorTarefas.getWorkersConectadosMetadados();
        
        if (workersMetadados.isEmpty()) {
            System.out.println("[DISCOVERY] Nenhum worker encontrado nos metadados");
            return;
        }
        
        System.out.println("[DISCOVERY] Workers encontrados nos metadados: " + workersMetadados);
        
        // Tentar descobrir workers em portas conhecidas
        List<Integer> portasComuns = Arrays.asList(8082, 8083, 8084, 8085, 8086);
        
        for (String workerId : workersMetadados) {
            boolean workerEncontrado = false;
            
            // Tentar conectar em diferentes portas
            for (int porta : portasComuns) {
                if (tentarConectarWorker(workerId, "localhost", porta)) {
                    System.out.println("[DISCOVERY] Worker " + workerId + " encontrado na porta " + porta);
                    workerEncontrado = true;
                    break;
                }
            }
            
            if (!workerEncontrado) {
                System.out.println("[DISCOVERY] Worker " + workerId + " não encontrado em portas conhecidas");
            }
        }
        
        // Aguardar um pouco para conexões se estabelecerem
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("[DISCOVERY] Descoberta concluída. Workers conectados: " + gerenciadorWorkers.obterWorkersConectados());
    }
    
    /**
     * Tenta conectar com um worker específico
     */
    private boolean tentarConectarWorker(String workerId, String host, int porta) {
        try {
            // Simular tentativa de conexão (na prática, isso seria feito pelo worker se conectando)
            // Por enquanto, apenas logamos a tentativa
            System.out.println("[DISCOVERY] Tentando conectar com worker " + workerId + " em " + host + ":" + porta);
            
            // Em uma implementação real, aqui você enviaria uma mensagem de descoberta
            // ou tentaria estabelecer uma conexão TCP com o worker
            
            return false; // Por enquanto, retorna false pois não temos implementação real
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Notifica workers conectados sobre mudança de orquestrador
     */
    private void notificarMudancaOrquestrador(GerenciadorWorkers gerenciadorWorkers) {
        if (gerenciadorWorkers != null) {
            gerenciadorWorkers.notificarMudancaOrquestrador(
                backupId, 
                "localhost", 
                8081  // Nova porta TCP do backup
            );
        }
    }
    
    /**
     * Envia notificação via multicast sobre mudança de orquestrador
     */
    private void enviarNotificacaoMudancaViaMulticast() {
        try {
            Map<String, Object> notificacao = Map.of(
                "tipo", "NOVO_ORQUESTRADOR_PRINCIPAL",
                "novoOrquestradorId", backupId,
                "novoHost", "localhost",
                "novaPortaGrpc", 9091,
                "novaPortaTcp", 8081,
                "timestamp", System.currentTimeMillis(),
                "lamportTimestamp", relógioLamport.tick(),
                "mensagem", "Novo orquestrador principal assumiu liderança"
            );
            
            comunicacaoMulticast.enviarMensagem("NOVO_ORQUESTRADOR_PRINCIPAL", notificacao);
            System.out.println("[FAILOVER] Notificação de mudança enviada via multicast");
            
        } catch (Exception e) {
            System.err.println("[FAILOVER] Erro ao enviar notificação via multicast: " + e.getMessage());
        }
    }
    
    /**
     * Volta ao modo backup (caso o PrincipalOrquestrador volte)
     */
    private void voltarModoBackup() {
        if (!souLider.get()) {
            return; // Já está em modo backup
        }
        
        System.out.println("*** VOLTANDO AO MODO BACKUP ***");
        souLider.set(false);
        
        // Parar servidor se estiver rodando
        if (servidorRodando.get() && server != null) {
            try {
                if (heartbeatManager != null) {
                    heartbeatManager.parar();
                    heartbeatManager = null;
                }
                
                server.parar();
                servidorRodando.set(false);
                server = null;
                
                System.out.println("Servidor parado - voltando ao modo backup");
                
                // Reiniciar heartbeat para o primary
                if (heartbeatManager != null) {
                    heartbeatManager.iniciar();
                }
                
            } catch (Exception e) {
                System.err.println("Erro ao parar servidor: " + e.getMessage());
            }
        }
    }
    
    /**
     * Para o orquestrador backup
     */
    public void parar() {
        ativo.set(false);
        
        // Parar servidor se estiver rodando
        if (servidorRodando.get()) {
            voltarModoBackup();
        }
        
        // Parar sistema de heartbeat
        if (backupHeartbeatService != null) {
            backupHeartbeatService.parar();
        }
        
        // Parar sincronização automática
        if (autoSyncService != null) {
            autoSyncService.parar();
        }
        
        // Parar comunicação multicast
        if (comunicacaoMulticast != null) {
            comunicacaoMulticast.parar();
        }
        
        // Parar scheduler
        if (scheduler != null) {
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
        
        System.out.println("Orquestrador backup parado");
    }
    
    /**
     * Configura handlers para mensagens multicast
     */
    private void configurarHandlersMulticast() {
        // Handler para notificação de novo orquestrador principal
        comunicacaoMulticast.registrarHandler("NOVO_ORQUESTRADOR_PRINCIPAL", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String novoOrquestradorId = (String) dados.get("novoOrquestradorId");
            
            System.out.println("[BACKUP] Novo orquestrador principal detectado: " + novoOrquestradorId);
        });
        
        // Handler para candidatura de líder
        comunicacaoMulticast.registrarHandler("CANDIDATURA_LIDER", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String candidatoId = (String) dados.get("remetente");
            Integer prioridadeCandidato = (Integer) dados.get("prioridade");
            
            // Se não é nossa própria candidatura
            if (!backupId.equals(candidatoId)) {
                int minhaPrioridade = calcularPrioridade();
                
                // Se o candidato tem prioridade maior (menor valor = maior prioridade)
                if (prioridadeCandidato != null && prioridadeCandidato < minhaPrioridade) {
                    System.out.println("[ELEIÇÃO] Candidato " + candidatoId + " tem prioridade maior. Desistindo da candidatura.");
                } else {
                    // Contestar candidatura enviando nossa própria
                    System.out.println("[ELEIÇÃO] Contestando candidatura de " + candidatoId);
                    enviarCandidatura();
                }
            }
        });
        
        // Handler para confirmação de novo líder
        comunicacaoMulticast.registrarHandler("NOVO_LIDER_CONFIRMADO", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String novoLiderId = (String) dados.get("liderId");
            
            if (!backupId.equals(novoLiderId) && souLider.get()) {
                System.out.println("[ELEIÇÃO] Outro backup assumiu liderança: " + novoLiderId + ". Voltando ao modo backup.");
                voltarModoBackup();
            }
        });
    }
    
    /**
     * Menu de monitoramento
     */
    private void showMenu() {
        while (ativo.get()) {
            System.out.println("\n=== MENU ORQUESTRADOR BACKUP ===");
            if (souLider.get()) {
                System.out.println("Servidor gRPC rodando na porta 9090 (clientes)");
                System.out.println("Servidor TCP rodando na porta 8080 (workers)");
            } else {
                System.out.println("Modo: BACKUP - Monitorando PrincipalOrquestrador");
            }
            System.out.println();
            
            System.out.println("--- MENU ---");
            System.out.println("1. Ver atividades recebidas");
            System.out.println("2. Ver workers conectados");
            System.out.println("3. Visualizar tarefas");
            System.out.println("4. Sair");
            System.out.print("Escolha uma opção: ");
            
            try {
                int opcao = scanner.nextInt();
                scanner.nextLine(); // Consumir quebra de linha
                
                switch (opcao) {
                    case 1:
                        showActivities();
                        break;
                    case 2:
                        showWorkers();
                        break;
                    case 3:
                        visualizarTarefas();
                        break;
                    case 4:
                        System.out.println("Encerrando orquestrador backup...");
                        return;
                    default:
                        System.out.println("Opção inválida! Tente novamente.");
                }
            } catch (Exception e) {
                System.out.println("Por favor, digite um número válido.");
                scanner.nextLine(); // Limpar buffer
            }
            
            System.out.println();
        }
    }
    
    /**
     * Mostra estatísticas do backup
     */
    private void showStatistics() {
        System.out.println("=== ESTATÍSTICAS BACKUP ===");
        System.out.println("ID do Backup: " + backupId);
        System.out.println("Primary ID: " + PRIMARY_ID);
        System.out.println("Timestamp Lamport atual: " + (relógioLamport != null ? relógioLamport.getCurrentTimestamp() : "N/A"));
        System.out.println("Status: " + (ativo.get() ? "Ativo" : "Inativo"));
        System.out.println("Modo: " + (souLider.get() ? "LÍDER" : "BACKUP"));
        System.out.println("Servidor rodando: " + (servidorRodando.get() ? "SIM" : "NÃO"));
        System.out.println("Status comunicação multicast: " + (comunicacaoMulticast != null ? "Ativa" : "Inativa"));
        
        if (backupHeartbeatService != null) {
            System.out.println("=== HEARTBEAT BACKUP ===");
            Map<String, Object> stats = backupHeartbeatService.getEstatisticas();
            System.out.println("Sistema ativo: " + stats.get("ativo"));
            System.out.println("Heartbeats enviados: " + stats.get("heartbeatsEnviados"));
            System.out.println("Respostas recebidas: " + stats.get("respostasRecebidas"));
            System.out.println("Intervalo heartbeat: " + (stats.get("intervalHeartbeat")) + "ms");
            System.out.println("Timeout resposta: " + (stats.get("timeoutResposta")) + "ms");
            
            long ultimaResposta = (Long) stats.get("ultimaRespostaRecebida");
            if (ultimaResposta > 0) {
                long tempoSemResposta = System.currentTimeMillis() - ultimaResposta;
                System.out.println("Última resposta recebida há: " + (tempoSemResposta / 1000) + "s");
            } else {
                System.out.println("Nenhuma resposta recebida ainda");
            }
        }
        
        if (servidorRodando.get() && server != null) {
            System.out.println("=== SERVIDOR ===");
            System.out.println("Status servidor gRPC: " + (server.estaRodando() ? "Rodando" : "Parado"));
            System.out.println("Status servidor TCP: " + (server.obterServidorTCP() != null && server.obterServidorTCP().estaRodando() ? "Rodando" : "Parado"));
            
            if (server.obterGerenciadorWorkers() != null) {
                System.out.println("Workers conectados: " + server.obterGerenciadorWorkers().obterNumeroWorkersConectados());
        System.out.println("Tarefas distribuídas: " + server.obterGerenciadorWorkers().obterNumeroTarefasEnviadas());
            }
            
            if (gerenciadorTarefas != null) {
                Map<String, Integer> estatisticas = gerenciadorTarefas.getEstatisticas();
                System.out.println("Total de tarefas: " + estatisticas.get("total"));
                System.out.println("Tarefas pendentes: " + estatisticas.get("pendentes"));
                System.out.println("Tarefas finalizadas: " + estatisticas.get("finalizadas"));
                System.out.println("Tarefas realocadas: " + estatisticas.get("realocadas"));
            }
            
            if (heartbeatManager != null) {
                Map<String, Integer> heartbeatStats = heartbeatManager.getEstatisticas();
                System.out.println("Heartbeats para workers enviados: " + heartbeatStats.get("enviados"));
                System.out.println("Heartbeats de workers recebidos: " + heartbeatStats.get("recebidos"));
                System.out.println("Workers desconectados: " + heartbeatStats.get("desconectados"));
            }
        }
        
        // Estatísticas do sistema de sincronização automática
        if (autoSyncService != null) {
            System.out.println("\n=== SINCRONIZAÇÃO AUTOMÁTICA ===");
            System.out.println("Status: " + (autoSyncService.isAtivo() ? "Ativo" : "Inativo"));
            System.out.println("Arquivo monitorado: " + autoSyncService.getJsonFilePath());
            System.out.println("Arquivos enviados: " + autoSyncService.getArquivosEnviados());
            System.out.println("Arquivos recebidos: " + autoSyncService.getArquivosRecebidos());
            System.out.println("Última sincronização: " + 
                (autoSyncService.getUltimaSincronizacao() > 0 ? 
                    dateFormat.format(new Date(autoSyncService.getUltimaSincronizacao())) : "Nunca"));
        }
    }
    
    /**
     * Mostra status de liderança
     */
    private void showLeadershipStatus() {
        System.out.println("=== STATUS DE LIDERANÇA ===");
        System.out.println("ID do Backup: " + backupId);
        System.out.println("Primary ID: " + PRIMARY_ID);
        System.out.println("Modo atual: " + (souLider.get() ? "LÍDER" : "BACKUP"));
        System.out.println("Servidor rodando: " + (servidorRodando.get() ? "SIM" : "NÃO"));
        System.out.println("Status: " + (ativo.get() ? "Ativo" : "Inativo"));
        
        if (heartbeatManager != null) {
            Map<String, Integer> stats = heartbeatManager.getEstatisticas();
            Integer ativoValue = stats.get("ativo");
            boolean ativo = ativoValue != null && ativoValue > 0;
            System.out.println("Heartbeat para Primary: " + (ativo ? "Ativo" : "Inativo"));
            
            if (ativo) {
                Integer ultimaRespostaValue = stats.get("ultimaRespostaRecebida");
                long ultimaResposta = ultimaRespostaValue != null ? ultimaRespostaValue.longValue() : 0;
                if (ultimaResposta > 0) {
                    long tempoSemResposta = System.currentTimeMillis() - ultimaResposta;
                    System.out.println("Última resposta do Primary há: " + (tempoSemResposta / 1000) + "s");
                } else {
                    System.out.println("Nenhuma resposta do Primary recebida ainda");
                }
            }
        }
    }
    
    /**
     * Mostra atividades recebidas
     */
    private void showActivities() {
        if (!souLider.get()) {
            System.out.println("=== MONITORAMENTO DE ATIVIDADES ===");
            System.out.println("Modo: BACKUP - Monitorando PrincipalOrquestrador");
            if (heartbeatManager != null) {
                Map<String, Integer> stats = heartbeatManager.getEstatisticas();
                System.out.println("Heartbeats enviados: " + stats.get("enviados"));
                System.out.println("Heartbeats recebidos: " + stats.get("recebidos"));
            }
            return;
        }
        
        if (server == null || server.obterServicoAtividade() == null) {
            System.out.println("Servidor não está rodando ou serviço não está disponível");
            return;
        }
        
        List<br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.Atividade> atividades = 
            server.obterServicoAtividade().obterAtividades();
        
        if (atividades.isEmpty()) {
            System.out.println("Nenhuma atividade recebida ainda.");
            return;
        }
        
        System.out.println("=== ATIVIDADES RECEBIDAS ===");
        System.out.println("Total: " + atividades.size() + " atividade(s)");
        System.out.println();
        
        for (int i = 0; i < atividades.size(); i++) {
            br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.Atividade atividade = atividades.get(i);
            System.out.println("--- Atividade " + (i + 1) + " ---");
            System.out.println("Título: " + atividade.getTitulo());
            System.out.println("Descrição: " + atividade.getDescricao());
            
            if (atividade.getMarcaTempo() > 0) {
                Date date = new Date(atividade.getMarcaTempo());
                System.out.println("Recebida em: " + dateFormat.format(date));
            }
            
            System.out.println();
        }
    }
    
    /**
     * Mostra workers conectados
     */
    private void showWorkers() {
        if (!souLider.get()) {
            System.out.println("=== WORKERS ===");
            System.out.println("Modo: BACKUP - Não há workers conectados diretamente");
            System.out.println("Workers são gerenciados pelo PrincipalOrquestrador");
            return;
        }
        
        if (server == null || server.obterGerenciadorWorkers() == null) {
            System.out.println("Servidor não está disponível.");
            return;
        }
        
        var gerenciador = server.obterGerenciadorWorkers();
        var workersConectados = gerenciador.obterWorkersConectados();
        
        System.out.println("=== WORKERS CONECTADOS ===");
        System.out.println("Total de workers: " + workersConectados.size());
        
        if (workersConectados.isEmpty()) {
            System.out.println("Nenhum worker conectado.");
        } else {
            System.out.println("\nWorkers ativos:");
            for (String workerId : workersConectados) {
                System.out.println("  - " + workerId);
            }
        }
        
        System.out.println("\nTarefas enviadas para workers: " + gerenciador.obterNumeroTarefasEnviadas());
    }
    
    /**
     * Monitora atividades do sistema (método legado mantido para compatibilidade)
     */
    private void monitorarAtividades() {
        showActivities();
    }

    /**
     * Visualiza tarefas (apenas se for líder)
     */
    private void visualizarTarefas() {
        if (!souLider.get()) {
            System.out.println("Esta funcionalidade está disponível apenas quando o backup está no modo LÍDER");
            return;
        }
        
        if (gerenciadorTarefas == null) {
            System.out.println("Gerenciador de tarefas não inicializado");
            return;
        }
        
        System.out.println("=== VISUALIZAÇÃO DE TAREFAS ===");
        Map<String, Integer> estatisticas = gerenciadorTarefas.getEstatisticas();
        System.out.println("Total de tarefas: " + estatisticas.get("total"));
        System.out.println("Tarefas pendentes: " + estatisticas.get("pendentes"));
        System.out.println("Tarefas finalizadas: " + estatisticas.get("finalizadas"));
        System.out.println("Tarefas realocadas: " + estatisticas.get("realocadas"));
    }

    /**
     * Visualiza workers conectados (apenas se for líder)
     */
    private void visualizarWorkers() {
        if (!souLider.get()) {
            System.out.println("Esta funcionalidade está disponível apenas quando o backup está no modo LÍDER");
            return;
        }
        
        if (server == null || server.obterGerenciadorWorkers() == null) {
            System.out.println("Servidor ou gerenciador de workers não inicializado");
            return;
        }
        
        System.out.println("=== WORKERS CONECTADOS ===");
        System.out.println("Número de workers: " + server.obterGerenciadorWorkers().obterNumeroWorkersConectados());
        System.out.println("Tarefas distribuídas: " + server.obterGerenciadorWorkers().obterNumeroTarefasEnviadas());
        
        if (heartbeatManager != null) {
            Map<String, Integer> heartbeatStats = heartbeatManager.getEstatisticas();
            System.out.println("Heartbeats enviados: " + heartbeatStats.get("enviados"));
            System.out.println("Heartbeats recebidos: " + heartbeatStats.get("recebidos"));
            System.out.println("Workers desconectados: " + heartbeatStats.get("desconectados"));
            System.out.println("Workers ativos: " + heartbeatStats.get("ativos"));
        }
    }
    
    /**
     * Determina se este backup deve assumir liderança baseado em eleição simples
     */
    private boolean deveAssumirLideranca() {
        try {
            // Aguardar um tempo para outros backups se manifestarem
            Thread.sleep(2000);
            
            // Enviar mensagem de candidatura
            enviarCandidatura();
            
            // Aguardar respostas de outros backups
            Thread.sleep(3000);
            
            // Se chegou até aqui sem ser contestado, assume liderança
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Envia mensagem de candidatura para eleição
     */
    private void enviarCandidatura() {
        try {
            Map<String, Object> mensagem = new HashMap<>();
            mensagem.put("tipo", "CANDIDATURA_LIDER");
            mensagem.put("remetente", backupId);
            mensagem.put("timestamp", System.currentTimeMillis());
            mensagem.put("prioridade", calcularPrioridade());
            
            comunicacaoMulticast.enviarMensagem("CANDIDATURA_LIDER", mensagem);
            System.out.println("Candidatura enviada para eleição de líder");
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar candidatura: " + e.getMessage());
        }
    }
    
    /**
     * Calcula prioridade baseada no ID do backup (menor ID = maior prioridade)
     */
    private int calcularPrioridade() {
        // Extrair timestamp do ID para usar como prioridade
        // IDs mais antigos (menor timestamp) têm maior prioridade
        try {
            String[] partes = backupId.split("-");
            if (partes.length >= 3) {
                return Integer.parseInt(partes[2].substring(0, Math.min(partes[2].length(), 8)));
            }
        } catch (Exception e) {
            // Fallback para hash do ID
        }
        return backupId.hashCode();
    }
     
     /**
      * Confirma liderança para outros backups
      */
     private void confirmarLideranca() {
         try {
             Map<String, Object> mensagem = new HashMap<>();
             mensagem.put("tipo", "NOVO_LIDER_CONFIRMADO");
             mensagem.put("liderId", backupId);
             mensagem.put("timestamp", System.currentTimeMillis());
             
             comunicacaoMulticast.enviarMensagem("NOVO_LIDER_CONFIRMADO", mensagem);
             System.out.println("Liderança confirmada para outros backups");
             
         } catch (Exception e) {
             System.err.println("Erro ao confirmar liderança: " + e.getMessage());
         }
     }
     
     /**
      * Verifica se o sistema está funcionando corretamente após assumir liderança
     */
    private void verificarSaudeSistema() {
        if (!souLider.get()) {
            return;
        }
        
        try {
            boolean sistemaOk = true;
            StringBuilder problemas = new StringBuilder();
            
            // Verificar se o servidor está rodando
            if (!servidorRodando.get() || server == null) {
                sistemaOk = false;
                problemas.append("- Servidor não está rodando\n");
            }
            
            // Verificar se há workers conectados
            if (server != null && server.obterGerenciadorWorkers() != null) {
                int workersConectados = server.obterGerenciadorWorkers().obterNumeroWorkersConectados();
                if (workersConectados == 0) {
                    problemas.append("- Nenhum worker conectado\n");
                }
            }
            
            // Verificar se o gerenciador de tarefas está funcionando
            if (gerenciadorTarefas == null) {
                sistemaOk = false;
                problemas.append("- Gerenciador de tarefas não inicializado\n");
            }
            
            if (sistemaOk) {
                System.out.println("✓ Sistema funcionando corretamente como novo líder");
            } else {
                System.err.println("⚠ Problemas detectados no sistema:");
                System.err.println(problemas.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao verificar saúde do sistema: " + e.getMessage());
        }
    }
    
    /**
     * Implementa recuperação automática em caso de problemas
     */
    private void tentarRecuperacao() {
        if (!souLider.get()) {
            return;
        }
        
        System.out.println("Tentando recuperação automática do sistema...");
        
        try {
            // Tentar reinicializar componentes críticos
            if (!servidorRodando.get() && server == null) {
                System.out.println("Reinicializando servidor...");
                assumirLideranca(); // Tentar novamente
            }
            
            // Verificar novamente após tentativa de recuperação
            Thread.sleep(5000);
            verificarSaudeSistema();
            
        } catch (Exception e) {
            System.err.println("Falha na recuperação automática: " + e.getMessage());
        }
    }
     
     /**
      * Configura monitoramento contínuo após assumir liderança
      */
     private void configurarMonitoramentoLider() {
         if (!souLider.get()) {
             return;
         }
         
         // Agendar verificações periódicas de saúde do sistema
         scheduler.scheduleAtFixedRate(() -> {
             try {
                 verificarSaudeSistema();
                 
                 // Verificar se ainda há workers conectados
                 if (server != null && server.obterGerenciadorWorkers() != null) {
                     int workersConectados = server.obterGerenciadorWorkers().obterNumeroWorkersConectados();
                     if (workersConectados == 0) {
                         System.out.println("⚠ Aviso: Nenhum worker conectado. Tentando redescobrir workers...");
                         descobrirWorkersExistentes(server.obterGerenciadorWorkers());
                     }
                 }
                 
             } catch (Exception e) {
                 System.err.println("Erro no monitoramento do líder: " + e.getMessage());
                 tentarRecuperacao();
             }
         }, 30, 60, TimeUnit.SECONDS); // Verificar a cada 60 segundos, começando em 30 segundos
         
         System.out.println("✓ Monitoramento contínuo do líder configurado");
     }
     
     public static void main(String[] args) {
        String backupId = "orchestrator-backup-" + System.currentTimeMillis();
        
        if (args.length >= 1) {
            backupId = args[0];
        }
        
        OrquestradorBackup backup = new OrquestradorBackup(backupId);
        backup.iniciar();
    }
}