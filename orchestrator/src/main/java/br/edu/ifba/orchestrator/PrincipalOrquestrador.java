package br.edu.ifba.orchestrator;

import br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.Atividade;
import br.edu.ifba.orchestrator.service.GerenciadorTarefas;
import br.edu.ifba.orchestrator.service.HeartbeatManager;
import br.edu.ifba.orchestrator.service.PrimaryHeartbeatService;
import br.edu.ifba.orchestrator.service.SincronizadorDados;
import br.edu.ifba.orchestrator.service.ChandyLamportSnapshot;
import br.edu.ifba.orchestrator.service.AutoSyncService;
import br.edu.ifba.orchestrator.worker.GerenciadorWorkers;
import br.edu.ifba.orchestrator.model.Tarefa;
import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PrincipalOrquestrador {
    
    private static final String ORCHESTRATOR_ID = "orchestrator-primary";
    
    private static ServidorOrquestrador server;
    private static GerenciadorTarefas gerenciadorTarefas;
    private static HeartbeatManager heartbeatManager;
    private static PrimaryHeartbeatService primaryHeartbeatService;
    private static SincronizadorDados sincronizadorDados;
    private static ChandyLamportSnapshot snapshotManager;
    private static AutoSyncService autoSyncService;
    private static RelógioLamport relógioLamport;
    private static ComunicacaoMulticast comunicacaoMulticast;
    private static ScheduledExecutorService scheduler;
    private static Scanner scanner = new Scanner(System.in);
    private static SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    
    public static void main(String[] args) {
        try {
            System.out.println("=== ORQUESTRADOR PRINCIPAL INICIANDO ===");
            System.out.println("ID: " + ORCHESTRATOR_ID);
            
            // Inicializar componentes
            relógioLamport = new RelógioLamport(ORCHESTRATOR_ID);
            comunicacaoMulticast = new ComunicacaoMulticast(ORCHESTRATOR_ID, relógioLamport);
            scheduler = Executors.newScheduledThreadPool(2);
            gerenciadorTarefas = new GerenciadorTarefas();
            gerenciadorTarefas.setRelógioLamport(relógioLamport);
            gerenciadorTarefas.setComunicacaoMulticast(comunicacaoMulticast);
            
            // Configurar handlers multicast
            configurarHandlersMulticast();
            
            // Iniciar comunicação multicast
            comunicacaoMulticast.iniciar();
            
            server = new ServidorOrquestrador();
            
            Thread serverThread = new Thread(() -> {
                try {
                    server.iniciar(gerenciadorTarefas);
                    
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
                        
                        // Inicializar sistema de heartbeat
                        heartbeatManager = new HeartbeatManager(gerenciadorWorkers, gerenciadorTarefas);
                        gerenciadorWorkers.setHeartbeatManager(heartbeatManager);
                        heartbeatManager.iniciar();
                        
                        // Inicializar sistema de sincronização de dados
                        sincronizadorDados = new SincronizadorDados(
                            ORCHESTRATOR_ID, relógioLamport, comunicacaoMulticast, gerenciadorTarefas
                        );
                        sincronizadorDados.iniciar();
                        
                        // Inicializar serviço de heartbeat para backups
                        primaryHeartbeatService = new PrimaryHeartbeatService(
                            ORCHESTRATOR_ID, relógioLamport, comunicacaoMulticast
                        );
                        primaryHeartbeatService.iniciar();
                        
                        // Inicializar sistema de snapshot Chandy-Lamport
                        snapshotManager = new ChandyLamportSnapshot(
                            ORCHESTRATOR_ID, relógioLamport, comunicacaoMulticast
                        );
                        
                        // Inicializar sincronização automática de arquivos
                        autoSyncService = new AutoSyncService(
                            ORCHESTRATOR_ID, relógioLamport, comunicacaoMulticast, "tarefas.json"
                        );
                        autoSyncService.iniciar();
                        
                        // Iniciar heartbeats multicast para backup
                        iniciarHeartbeatsMulticast();
                    }
                    
                    server.bloquearAteDesligamento();
                } catch (IOException | InterruptedException e) {
                    System.err.println("Erro ao iniciar servidor: " + e.getMessage());
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();
            
            Thread.sleep(1000);
            
            showMenu();
            
        } catch (Exception e) {
            System.err.println("Erro na aplicação: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Parar sistema de heartbeat
            if (heartbeatManager != null) {
                heartbeatManager.parar();
            }
            
            // Parar serviço de heartbeat para backups
            if (primaryHeartbeatService != null) {
                primaryHeartbeatService.parar();
            }
            
            // Parar sistema de sincronização
            if (sincronizadorDados != null) {
                sincronizadorDados.parar();
            }
            
            // Parar snapshot manager
            if (snapshotManager != null) {
                snapshotManager.parar();
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
            }
            
            if (server != null) {
                try {
                    server.parar();
                } catch (InterruptedException e) {
                    System.err.println("Erro ao parar servidor: " + e.getMessage());
                }
            }
        }
    }
    
    private static void showMenu() {
        System.out.println("=== ORQUESTRADOR DE ATIVIDADES ===");
        System.out.println("Servidor gRPC rodando na porta 9090 (clientes)");
        System.out.println("Servidor TCP rodando na porta 8080 (workers)");
        System.out.println();
        
        while (true) {
            System.out.println("--- MENU ---");
            System.out.println("1. Ver atividades recebidas");
            System.out.println("2. Ver workers conectados");
            System.out.println("3. Visualizar tarefas");
            System.out.println("4. Ver estatísticas");
            System.out.println("5. Executar snapshot global");
            System.out.println("6. Sair");
            System.out.print("Escolha uma opção: ");
            
            try {
                int option = Integer.parseInt(scanner.nextLine().trim());
                
                switch (option) {
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
                        showStatistics();
                        break;
                    case 5:
                        executarSnapshot();
                        break;
                    case 6:
                        System.out.println("Encerrando orquestrador...");
                        return;
                    default:
                        System.out.println("Opção inválida! Tente novamente.");
                }
                
            } catch (NumberFormatException e) {
                System.out.println("Por favor, digite um número válido.");
            }
            
            System.out.println();
        }
    }
    
    private static void showActivities() {
        if (server == null || server.obterServicoAtividade() == null) {
            System.out.println("Servidor não está rodando ou serviço não está disponível");
            return;
        }
        
        List<Atividade> atividades = server.obterServicoAtividade().obterAtividades();
        
        if (atividades.isEmpty()) {
            System.out.println("Nenhuma atividade recebida ainda.");
            return;
        }
        
        System.out.println("=== ATIVIDADES RECEBIDAS ===");
        System.out.println("Total: " + atividades.size() + " atividade(s)");
        System.out.println();
        
        for (int i = 0; i < atividades.size(); i++) {
            Atividade atividade = atividades.get(i);
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
    
    private static void showWorkers() {
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
    
    private static void showStatistics() {
        if (server == null || server.obterServicoAtividade() == null) {
            System.out.println("Servidor não está rodando");
            return;
        }
        
        int totalAtividades = server.obterServicoAtividade().obterContadorAtividades();
        boolean serverRunning = server.estaRodando();
        boolean tcpServerRunning = server.obterServidorTCP() != null && server.obterServidorTCP().estaRodando();
        int workersConectados = server.obterGerenciadorWorkers() != null ? server.obterGerenciadorWorkers().obterNumeroWorkersConectados() : 0;
        int tarefasEnviadas = server.obterGerenciadorWorkers() != null ? server.obterGerenciadorWorkers().obterNumeroTarefasEnviadas() : 0;
        
        System.out.println("=== ESTATÍSTICAS ===");
        System.out.println("ID do Orquestrador: " + ORCHESTRATOR_ID);
        System.out.println("Timestamp Lamport atual: " + (relógioLamport != null ? relógioLamport.getCurrentTimestamp() : "N/A"));
        System.out.println("Status servidor gRPC: " + (serverRunning ? "Rodando" : "Parado"));
        System.out.println("Status servidor TCP: " + (tcpServerRunning ? "Rodando" : "Parado"));
        System.out.println("Status comunicação multicast: " + (comunicacaoMulticast != null ? "Ativa" : "Inativa"));
        System.out.println("Porta gRPC (clientes): 9090");
        System.out.println("Porta TCP (workers): 8080");
        System.out.println("Grupo multicast: 224.0.0.1:4446");
        System.out.println("Workers conectados: " + workersConectados);
        System.out.println("Total de atividades recebidas: " + totalAtividades);
        System.out.println("Tarefas distribuídas para workers: " + tarefasEnviadas);
        
        if (gerenciadorTarefas != null) {
            Map<String, Integer> estatisticas = gerenciadorTarefas.getEstatisticas();
            System.out.println("Total de tarefas: " + estatisticas.get("total"));
            System.out.println("Tarefas pendentes: " + estatisticas.get("pendentes"));
            System.out.println("Tarefas finalizadas: " + estatisticas.get("finalizadas"));
            System.out.println("Tarefas realocadas: " + estatisticas.get("realocadas"));
        }
        
        if (heartbeatManager != null) {
            Map<String, Integer> heartbeatStats = heartbeatManager.getEstatisticas();
            System.out.println("Heartbeats enviados: " + heartbeatStats.get("enviados"));
            System.out.println("Heartbeats recebidos: " + heartbeatStats.get("recebidos"));
            System.out.println("Workers desconectados: " + heartbeatStats.get("desconectados"));
        }
        
        // Estatísticas do SincronizadorDados
        if (sincronizadorDados != null) {
            var syncStats = sincronizadorDados.getEstatisticas();
            System.out.println("\n=== SINCRONIZAÇÃO DE DADOS ===");
            System.out.println("Ativo: " + syncStats.get("ativo"));
            System.out.println("Sincronizações enviadas: " + syncStats.get("sincronizacoesEnviadas"));
            System.out.println("Sincronizações recebidas: " + syncStats.get("sincronizacoesRecebidas"));
            System.out.println("Última sincronização: " + syncStats.get("ultimaSincronizacao") + "ms atrás");
            System.out.println("É principal: " + syncStats.get("isPrincipal"));
            System.out.println("Node ID: " + syncStats.get("nodeId"));
        }
        
        // Estatísticas do PrimaryHeartbeatService
        if (primaryHeartbeatService != null) {
            var heartbeatStats = primaryHeartbeatService.getEstatisticas();
            System.out.println("\n=== HEARTBEAT BACKUP ===");
            System.out.println("Ativo: " + heartbeatStats.get("ativo"));
            System.out.println("Heartbeats recebidos: " + heartbeatStats.get("heartbeatsRecebidos"));
            System.out.println("Respostas enviadas: " + heartbeatStats.get("respostasEnviadas"));
            System.out.println("Último heartbeat: " + heartbeatStats.get("ultimoHeartbeatRecebido"));
        }
        
        // Estatísticas do sistema de snapshot
        if (snapshotManager != null) {
            var snapshotStats = snapshotManager.getEstatisticas();
            System.out.println("\n=== SISTEMA DE SNAPSHOT ===");
            System.out.println("Snapshot ativo: " + snapshotStats.get("snapshotAtivo"));
            System.out.println("Snapshots realizados: " + snapshotStats.get("snapshotsRealizados"));
            System.out.println("ID do snapshot atual: " + snapshotStats.get("snapshotIdAtual"));
            System.out.println("Participantes: " + snapshotStats.get("participantes"));
            
            if (snapshotStats.containsKey("tempoDesdeUltimoSnapshot")) {
                System.out.println("Tempo desde último snapshot: " + snapshotStats.get("tempoDesdeUltimoSnapshot") + "s");
            } else {
                System.out.println("Nenhum snapshot realizado ainda");
            }
        }
        
        // Estatísticas do AutoSyncService
        if (autoSyncService != null) {
            System.out.println("\n--- Sincronização Automática ---");
            System.out.println("Status: " + (autoSyncService.isAtivo() ? "Ativo" : "Inativo"));
            System.out.println("Arquivo monitorado: " + autoSyncService.getJsonFilePath());
            System.out.println("Arquivos enviados: " + autoSyncService.getArquivosEnviados());
            System.out.println("Arquivos recebidos: " + autoSyncService.getArquivosRecebidos());
            
            if (autoSyncService.getUltimaSincronizacao() > 0) {
                Date ultimaSync = new Date(autoSyncService.getUltimaSincronizacao());
                System.out.println("Última sincronização: " + dateFormat.format(ultimaSync));
            } else {
                System.out.println("Nenhuma sincronização realizada ainda");
            }
        }
        
        if (totalAtividades > 0) {
            List<Atividade> atividades = server.obterServicoAtividade().obterAtividades();
            Atividade ultimaAtividade = atividades.get(atividades.size() - 1);
            
            if (ultimaAtividade.getMarcaTempo() > 0) {
                Date ultimaData = new Date(ultimaAtividade.getMarcaTempo());
                System.out.println("Última atividade recebida em: " + dateFormat.format(ultimaData));
            }
        }
    }
    
    private static void visualizarTarefas() {
        if (gerenciadorTarefas != null) {
            System.out.println("\n=== TAREFAS ===");
            
            List<Tarefa> tarefasPendentes = gerenciadorTarefas.getTarefasPendentes();
            System.out.println("\n--- Tarefas Pendentes (" + tarefasPendentes.size() + ") ---");
            for (Tarefa tarefa : tarefasPendentes) {
                System.out.println(tarefa.toString());
            }
            
            List<Tarefa> tarefasFinalizadas = gerenciadorTarefas.getTarefasFinalizadas();
            System.out.println("\n--- Tarefas Finalizadas (" + tarefasFinalizadas.size() + ") ---");
            for (Tarefa tarefa : tarefasFinalizadas) {
                System.out.println(tarefa.toString());
            }
        } else {
            System.out.println("Gerenciador de tarefas não inicializado.");
        }
    }
    
    private static void visualizarHeartbeats() {
        if (heartbeatManager != null) {
            System.out.println("\n=== STATUS HEARTBEATS ===");
            Map<String, Integer> estatisticas = heartbeatManager.getEstatisticas();
            System.out.println("Heartbeats enviados: " + estatisticas.get("enviados"));
            System.out.println("Heartbeats recebidos: " + estatisticas.get("recebidos"));
            System.out.println("Workers desconectados por timeout: " + estatisticas.get("desconectados"));
            System.out.println("Intervalo de heartbeat: 20 segundos");
            System.out.println("Timeout de resposta: 30 segundos");
        } else {
            System.out.println("Sistema de heartbeat não inicializado.");
        }
    }
    
    /**
     * Configura handlers para mensagens multicast
     */
    private static void configurarHandlersMulticast() {
        // Handler para detecção de backup online
        comunicacaoMulticast.registrarHandler("BACKUP_ONLINE", mensagem -> {
            System.out.println("[MULTICAST] Backup detectado: " + mensagem.getRemetenteId() + 
                              " [Lamport:" + mensagem.getTimestampLamport() + "]");
        });
        
        // Handler para eleição de líder
        comunicacaoMulticast.registrarHandler("LEADER_ELECTION", mensagem -> {
            System.out.println("[MULTICAST] Eleição de líder iniciada por: " + mensagem.getRemetenteId());
            // Principal mantém liderança
        });
        
        // Handler para novo principal
        comunicacaoMulticast.registrarHandler("NEW_PRIMARY", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String newPrimaryId = (String) dados.get("newPrimaryId");
            
            if (!newPrimaryId.equals(ORCHESTRATOR_ID)) {
                System.out.println("[MULTICAST] ATENÇÃO: Novo principal detectado: " + newPrimaryId);
                System.out.println("[MULTICAST] Este orquestrador deveria parar ou se tornar backup!");
            }
        });
        
        // Handler para heartbeat simples do backup
        comunicacaoMulticast.registrarHandler("SIMPLE_HEARTBEAT", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String backupId = (String) dados.get("backupId");
            String targetPrimaryId = (String) dados.get("primaryId");
            
            // Verifica se o heartbeat é para este primary
            if (ORCHESTRATOR_ID.equals(targetPrimaryId)) {
                System.out.println("[HEARTBEAT] Heartbeat recebido do backup: " + backupId + 
                                  " [Lamport:" + mensagem.getTimestampLamport() + "]");
                
                // Responde ao backup
                Map<String, Object> resposta = Map.of(
                    "primaryId", ORCHESTRATOR_ID,
                    "backupId", backupId,
                    "timestamp", System.currentTimeMillis(),
                    "lamportTimestamp", relógioLamport.getCurrentTimestamp(),
                    "status", "ALIVE"
                );
                
                comunicacaoMulticast.enviarMensagem("SIMPLE_HEARTBEAT_RESPONSE", resposta);
            }
        });

        // Handler para heartbeat do backup (mantido para compatibilidade)
        comunicacaoMulticast.registrarHandler("BACKUP_HEARTBEAT", mensagem -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            String backupId = (String) dados.get("backupId");
            String targetLeaderId = (String) dados.get("leaderId");
            
            // Verifica se o heartbeat é para este líder
            if (ORCHESTRATOR_ID.equals(targetLeaderId)) {
                System.out.println("[HEARTBEAT] Heartbeat recebido do backup: " + backupId);
                
                // Responde ao backup
                Map<String, Object> resposta = Map.of(
                    "leaderId", ORCHESTRATOR_ID,
                    "backupId", backupId,
                    "timestamp", System.currentTimeMillis(),
                    "lamportTimestamp", relógioLamport.getCurrentTimestamp(),
                    "status", "ALIVE",
                    "workersConectados", server.obterGerenciadorWorkers().obterNumeroWorkersConectados(),
                    "tarefasPendentes", gerenciadorTarefas.getTarefasPendentes().size()
                );
                
                comunicacaoMulticast.enviarMensagem("BACKUP_HEARTBEAT_RESPONSE", resposta);
            }
        });
    }
    
    /**
     * Inicia envio de heartbeats multicast para backups
     */
    private static void iniciarHeartbeatsMulticast() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> estadoAtual = Map.of(
                    "workersConectados", server.obterGerenciadorWorkers().obterNumeroWorkersConectados(),
                    "tarefasPendentes", gerenciadorTarefas.getTarefasPendentes().size(),
                    "tarefasConcluidas", gerenciadorTarefas.getTarefasFinalizadas().size(),
                    "timestamp", System.currentTimeMillis(),
                    "lamportTimestamp", relógioLamport.getCurrentTimestamp() // Usar timestamp atual sem incrementar
                );
                
                comunicacaoMulticast.enviarMensagem("PRIMARY_HEARTBEAT", estadoAtual);
                
            } catch (Exception e) {
                System.err.println("[MULTICAST] Erro ao enviar heartbeat: " + e.getMessage());
            }
        }, 5, 20, TimeUnit.SECONDS); // Mudança para 20 segundos
        
        System.out.println("[MULTICAST] Heartbeats para backup iniciados (intervalo: 20s)");
    }
    
    /**
     * Executa um snapshot global do sistema usando o algoritmo Chandy-Lamport
     */
    private static void executarSnapshot() {
        try {
            System.out.println("\n=== EXECUTANDO SNAPSHOT GLOBAL ===");
            System.out.println("Iniciando snapshot do estado global do sistema...");
            
            // Executa o snapshot
            boolean sucesso = snapshotManager.executarSnapshot();
            
            if (sucesso) {
                System.out.println("Snapshot executado com sucesso!");
            } else {
                System.out.println("Falha ao executar snapshot!");
                return;
            }
            System.out.println("Timestamp: " + dateFormat.format(new Date()));
            System.out.println("Timestamp Lamport: " + relógioLamport.getCurrentTimestamp());
            
            // Aguarda um momento para o snapshot ser processado
            Thread.sleep(2000);
            
            // Mostra informações do snapshot
            System.out.println("\n--- Informações do Snapshot ---");
            System.out.println("Estado capturado do sistema distribuído:");
            System.out.println("- Workers conectados: " + server.obterGerenciadorWorkers().obterNumeroWorkersConectados());
            System.out.println("- Tarefas pendentes: " + gerenciadorTarefas.getTarefasPendentes().size());
            System.out.println("- Tarefas concluídas: " + gerenciadorTarefas.getTarefasFinalizadas().size());
            System.out.println("- Timestamp Lamport atual: " + relógioLamport.getCurrentTimestamp());
            
            System.out.println("\nSnapshot salvo e sincronizado com backups.");
            
        } catch (Exception e) {
            System.err.println("Erro ao executar snapshot: " + e.getMessage());
            e.printStackTrace();
        }
    }
}