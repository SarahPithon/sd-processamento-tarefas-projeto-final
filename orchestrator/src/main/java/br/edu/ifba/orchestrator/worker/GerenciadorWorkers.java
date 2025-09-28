package br.edu.ifba.orchestrator.worker;

import br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.Atividade;
import br.edu.ifba.orchestrator.model.Tarefa;
import br.edu.ifba.orchestrator.service.GerenciadorTarefas;
import br.edu.ifba.orchestrator.service.HeartbeatManager;
import br.edu.ifba.orchestrator.util.RelógioLamport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GerenciadorWorkers {
    
    private final Map<String, WorkerConnection> workersConectados;
    private final AtomicInteger roundRobinIndex;
    private final ObjectMapper objectMapper;
    private GerenciadorTarefas gerenciadorTarefas;
    private HeartbeatManager heartbeatManager;
    private RelógioLamport relógioLamport;
    
    public GerenciadorWorkers() {
        this.workersConectados = new ConcurrentHashMap<>();
        this.roundRobinIndex = new AtomicInteger(0);
        this.objectMapper = new ObjectMapper();
    }
    
    public void setGerenciadorTarefas(GerenciadorTarefas gerenciadorTarefas) {
        this.gerenciadorTarefas = gerenciadorTarefas;
    }
    
    public void setHeartbeatManager(HeartbeatManager heartbeatManager) {
        this.heartbeatManager = heartbeatManager;
    }
    
    public void setRelógioLamport(RelógioLamport relógioLamport) {
        this.relógioLamport = relógioLamport;
    }
    
    public void adicionarWorker(String workerId, Socket socket) {
        try {
            WorkerConnection connection = new WorkerConnection(workerId, socket);
            workersConectados.put(workerId, connection);
            
            // Registrar worker no sistema de heartbeat
            if (heartbeatManager != null) {
                heartbeatManager.registrarWorker(workerId);
            }
            
            // Iniciar thread para escutar mensagens do worker
            iniciarThreadEscuta(connection);
            
            System.out.println("Worker " + workerId + " conectado. Total de workers: " + workersConectados.size());
            
        } catch (IOException e) {
            System.err.println("Erro ao adicionar worker " + workerId + ": " + e.getMessage());
        }
    }
    
    private void iniciarThreadEscuta(WorkerConnection connection) {
        Thread thread = new Thread(() -> {
            try {
                String mensagem;
                while ((mensagem = connection.getEntrada().readLine()) != null) {
                    processarMensagemWorker(connection.getWorkerId(), mensagem);
                }
            } catch (IOException e) {
                System.out.println("Worker " + connection.getWorkerId() + " desconectado");
            } finally {
                removerWorker(connection.getWorkerId());
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
    
    private void processarMensagemWorker(String workerId, String mensagem) {
        try {
            if (mensagem.contains("\"tipo\":\"CONCLUSAO\"")) {
                // Extrair ID da tarefa concluída
                String tarefaId = extrairValorJson(mensagem, "tarefaId");
                if (tarefaId != null && gerenciadorTarefas != null) {
                    // Incrementar Lamport ao receber confirmação de conclusão
                    if (relógioLamport != null) {
                        long lamportTimestamp = relógioLamport.tick();
                        System.out.println("[LAMPORT] Confirmação de conclusão recebida - Tick: " + lamportTimestamp);
                    }
                    gerenciadorTarefas.finalizarTarefa(tarefaId, workerId);
                }
            } else if (mensagem.contains("\"tipo\":\"HEARTBEAT_RESPONSE\"")) {
                // Processar resposta de heartbeat
                if (heartbeatManager != null) {
                    heartbeatManager.receberHeartbeatResponse(workerId);
                }
            } else if (mensagem.contains("\"tipo\":\"DESCONEXAO\"")) {
                System.out.println("Worker " + workerId + " solicitou desconexão");
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem do worker " + workerId + ": " + e.getMessage());
        }
    }
    
    private String extrairValorJson(String json, String chave) {
        try {
            String busca = "\"" + chave + "\":\"";
            int inicio = json.indexOf(busca);
            if (inicio != -1) {
                inicio += busca.length();
                int fim = json.indexOf("\"", inicio);
                if (fim != -1) {
                    return json.substring(inicio, fim);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao extrair valor JSON: " + e.getMessage());
        }
        return null;
    }
    
    public boolean distribuirTarefa(Atividade atividade) {
        if (workersConectados.isEmpty()) {
            System.out.println("Nenhum worker disponível para receber a tarefa");
            return false;
        }
        
        // Criar tarefa no gerenciador
        if (gerenciadorTarefas == null) {
            System.err.println("GerenciadorTarefas não foi configurado");
            return false;
        }
        
        Tarefa tarefa = gerenciadorTarefas.criarTarefa(atividade.getTitulo(), atividade.getDescricao());
        
        // Algoritmo Round Robin melhorado
        List<String> workerIds = new ArrayList<>(workersConectados.keySet());
        Collections.sort(workerIds); // Garantir ordem consistente
        
        // Garantir que o índice seja válido para o tamanho atual da lista
        int currentIndex = roundRobinIndex.get() % workerIds.size();
        String workerEscolhido = workerIds.get(currentIndex);
        
        // Incrementar para a próxima distribuição
        int novoIndice = (currentIndex + 1) % workerIds.size();
        roundRobinIndex.set(novoIndice);
        
        // Atualizar estado do round-robin no GerenciadorTarefas
        gerenciadorTarefas.atualizarEstadoRoundRobin(novoIndice, workerIds);
        
        System.out.println("Distribuindo tarefa para worker " + workerEscolhido + " (índice " + currentIndex + " de " + workerIds.size() + " workers)");
        
        // Atribuir tarefa ao worker
        gerenciadorTarefas.atribuirTarefaAoWorker(tarefa.getId(), workerEscolhido);
        
        // Incrementar Lamport ao distribuir tarefa para worker
        if (relógioLamport != null) {
            long lamportTimestamp = relógioLamport.tick();
            System.out.println("[LAMPORT] Tarefa distribuída para worker - Tick: " + lamportTimestamp);
        }
        
        WorkerConnection connection = workersConectados.get(workerEscolhido);
        if (connection != null) {
            return enviarTarefaParaWorker(connection, tarefa);
        }
        
        return false;
    }
    
    private boolean enviarTarefaParaWorker(WorkerConnection connection, Tarefa tarefa) {
        try {
            // Converter LocalDateTime para timestamp em milissegundos
            long timestampMillis = 0;
            if (tarefa.obterHorarioRecebimento() != null) {
                timestampMillis = tarefa.obterHorarioRecebimento()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            }
            
            // Criar mensagem JSON com timestamp como número
            String mensagem = String.format(
                "{\"tipo\":\"TAREFA\",\"tarefa\":{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\",\"timestamp\":%d}}",
                tarefa.getId(),
                tarefa.getTitulo().replace("\"", "\\\""),
                tarefa.getDescricao().replace("\"", "\\\""),
                timestampMillis
            );
            
            connection.getSaida().println(mensagem);
            
            System.out.println("Tarefa '" + tarefa.getTitulo() + "' enviada para worker " + connection.getWorkerId());
            return true;
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar tarefa para worker " + connection.getWorkerId() + ": " + e.getMessage());
            return false;
        }
    }
    
    public void removerWorker(String workerId) {
        WorkerConnection connection = workersConectados.remove(workerId);
        if (connection != null) {
            try {
                connection.fechar();
            } catch (IOException e) {
                System.err.println("Erro ao fechar conexão do worker " + workerId + ": " + e.getMessage());
            }
            
            // Remover do sistema de heartbeat
            if (heartbeatManager != null) {
                heartbeatManager.removerWorker(workerId);
            }
            
            System.out.println("Worker " + workerId + " removido. Total de workers: " + workersConectados.size());
            
            // Redistribuir tarefas do worker desconectado imediatamente
            if (gerenciadorTarefas != null && !workersConectados.isEmpty()) {
                List<Tarefa> tarefasParaRealocar = gerenciadorTarefas.getTarefasPorWorker(workerId);
                
                if (!tarefasParaRealocar.isEmpty()) {
                    System.out.println("Redistribuindo " + tarefasParaRealocar.size() + " tarefas do worker " + workerId + " imediatamente");
                    
                    // Realocar as tarefas no gerenciador
                    List<String> workersDisponiveis = new ArrayList<>(workersConectados.keySet());
                    gerenciadorTarefas.realocarTarefasDoWorker(workerId, workersDisponiveis);
                    
                    // Enviar as tarefas realocadas para os novos workers
                    enviarTarefasRealocadas(tarefasParaRealocar, workersDisponiveis);
                } else {
                    System.out.println("Worker " + workerId + " não tinha tarefas pendentes para redistribuir");
                }
            } else if (workersConectados.isEmpty()) {
                System.out.println("Nenhum worker disponível para redistribuir tarefas de " + workerId);
            }
        }
    }
    
    public int obterNumeroWorkersConectados() {
        return workersConectados.size();
    }
    
    public Set<String> obterWorkersConectados() {
        return new HashSet<>(workersConectados.keySet());
    }
    
    public int obterNumeroTarefasEnviadas() {
        if (gerenciadorTarefas != null) {
            return gerenciadorTarefas.getTarefasPendentes().size();
        }
        return 0;
    }
    
    public boolean enviarHeartbeat(String workerId) {
        WorkerConnection connection = workersConectados.get(workerId);
        if (connection != null) {
            try {
                String mensagem = "{\"tipo\":\"HEARTBEAT\",\"timestamp\":" + System.currentTimeMillis() + "}";
                connection.getSaida().println(mensagem);
                return true;
            } catch (Exception e) {
                System.err.println("Erro ao enviar heartbeat para worker " + workerId + ": " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    public void enviarTarefasRealocadas(List<Tarefa> tarefasParaRealocar, List<String> workersDisponiveis) {
        if (tarefasParaRealocar.isEmpty() || workersDisponiveis.isEmpty()) {
            return;
        }
        
        // Usar round robin para distribuir as tarefas
        int workerIndex = 0;
        for (Tarefa tarefa : tarefasParaRealocar) {
            String novoWorker = workersDisponiveis.get(workerIndex % workersDisponiveis.size());
            
            System.out.println("Enviando tarefa realocada " + tarefa.getId() + " para worker " + novoWorker);
            
            WorkerConnection connection = workersConectados.get(novoWorker);
            if (connection != null) {
                enviarTarefaParaWorker(connection, tarefa);
            }
            
            workerIndex++;
        }
    }

    public void redistribuirTarefasRealocadas(String workerDesconectado) {
        if (gerenciadorTarefas == null) {
            return;
        }
        
        List<Tarefa> tarefasRealocadas = gerenciadorTarefas.getTarefasPorWorker(workerDesconectado);
        
        for (Tarefa tarefa : tarefasRealocadas) {
            // Encontrar novo worker usando round robin melhorado
            List<String> workersDisponiveis = new ArrayList<>(workersConectados.keySet());
            if (!workersDisponiveis.isEmpty()) {
                Collections.sort(workersDisponiveis);
                
                // Usar a mesma lógica melhorada de round robin
                int currentIndex = roundRobinIndex.get() % workersDisponiveis.size();
                String novoWorker = workersDisponiveis.get(currentIndex);
                
                // Incrementar para a próxima redistribuição
                roundRobinIndex.set((currentIndex + 1) % workersDisponiveis.size());
                
                System.out.println("Redistribuindo tarefa " + tarefa.getId() + " para worker " + novoWorker);
                
                WorkerConnection connection = workersConectados.get(novoWorker);
                if (connection != null) {
                    enviarTarefaParaWorker(connection, tarefa);
                }
            }
        }
    }
    
    public void fecharTodosWorkers() {
        System.out.println("Fechando conexões com todos os workers...");
        for (WorkerConnection connection : workersConectados.values()) {
            try {
                connection.fechar();
            } catch (IOException e) {
                System.err.println("Erro ao fechar conexão: " + e.getMessage());
            }
        }
        workersConectados.clear();
        System.out.println("Todas as conexões foram fechadas.");
    }
    
    /**
     * Restaura o estado do round-robin a partir dos metadados salvos
     */
    public void restaurarEstadoRoundRobin() {
        if (gerenciadorTarefas != null) {
            int indiceRestaurado = gerenciadorTarefas.getRoundRobinIndex();
            List<String> workersMetadados = gerenciadorTarefas.getWorkersConectadosMetadados();
            
            roundRobinIndex.set(indiceRestaurado);
            
            System.out.println("[RESTORE] Estado round-robin restaurado - Índice: " + indiceRestaurado);
            System.out.println("[RESTORE] Workers nos metadados: " + workersMetadados);
            System.out.println("[RESTORE] Workers atualmente conectados: " + workersConectados.keySet());
            
            // Se há workers conectados atualmente, ajustar o índice para o tamanho atual
            if (!workersConectados.isEmpty()) {
                List<String> workersAtuais = new ArrayList<>(workersConectados.keySet());
                Collections.sort(workersAtuais);
                
                // Ajustar índice para o tamanho atual da lista de workers
                int indiceAjustado = indiceRestaurado % workersAtuais.size();
                roundRobinIndex.set(indiceAjustado);
                
                // Atualizar metadados com workers atuais
                gerenciadorTarefas.atualizarEstadoRoundRobin(indiceAjustado, workersAtuais);
                
                System.out.println("[RESTORE] Índice ajustado para workers atuais: " + indiceAjustado + " de " + workersAtuais.size() + " workers");
            }
        }
    }
    
    /**
     * Notifica todos os workers conectados sobre mudança de orquestrador
     */
    public void notificarMudancaOrquestrador(String novoOrquestradorId, String novoHost, int novaPorta) {
        System.out.println("[FAILOVER] Notificando " + workersConectados.size() + " workers sobre mudança de orquestrador");
        
        Map<String, Object> notificacao = Map.of(
            "tipo", "MUDANCA_ORQUESTRADOR",
            "novoOrquestradorId", novoOrquestradorId,
            "novoHost", novoHost,
            "novaPorta", novaPorta,
            "timestamp", System.currentTimeMillis(),
            "mensagem", "O orquestrador principal mudou. Reconecte-se ao novo orquestrador."
        );
        
        try {
            String mensagemJson = objectMapper.writeValueAsString(notificacao);
            
            // Enviar notificação para todos os workers conectados
            for (WorkerConnection connection : workersConectados.values()) {
                try {
                    connection.getSaida().println(mensagemJson);
                    System.out.println("[FAILOVER] Notificação enviada para worker: " + connection.getWorkerId());
                } catch (Exception e) {
                    System.err.println("[FAILOVER] Erro ao notificar worker " + connection.getWorkerId() + ": " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("[FAILOVER] Erro ao criar notificação JSON: " + e.getMessage());
        }
    }
    
    // Classe interna para representar uma conexão com worker
    private static class WorkerConnection {
        private final String workerId;
        private final Socket socket;
        private final BufferedReader entrada;
        private final PrintWriter saida;
        
        public WorkerConnection(String workerId, Socket socket) throws IOException {
            this.workerId = workerId;
            this.socket = socket;
            this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.saida = new PrintWriter(socket.getOutputStream(), true);
        }
        
        public String getWorkerId() {
            return workerId;
        }
        
        public BufferedReader getEntrada() {
            return entrada;
        }
        
        public PrintWriter getSaida() {
            return saida;
        }
        
        public void fechar() throws IOException {
            entrada.close();
            saida.close();
            socket.close();
        }
    }
}