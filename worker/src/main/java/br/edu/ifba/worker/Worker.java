package br.edu.ifba.worker;

import br.edu.ifba.worker.model.Tarefa;
import br.edu.ifba.worker.network.ClienteTCP;
import br.edu.ifba.worker.util.RelogioLamport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public abstract class Worker {
    
    protected ClienteTCP clienteTCP;
    protected final Scanner scanner = new Scanner(System.in);
    protected final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    protected final List<Tarefa> tarefasRecebidas = new ArrayList<>();
    protected final List<Tarefa> tarefasConcluidas = new ArrayList<>();
    protected String workerId;
    protected String workerName;
    protected RelogioLamport relogioLamport;
    
    public Worker(String workerId, String workerName) {
        this.workerId = workerId;
        this.workerName = workerName;
        this.relogioLamport = new RelogioLamport(workerId);
    }
    
    public void iniciar() {
        iniciar("localhost", 8080);
    }
    
    public void iniciar(String host, int porta) {
        System.out.println("=== " + workerName + " ===\n");
        
        // Conectar ao orquestrador
        clienteTCP = new ClienteTCP(workerId, host, porta);
        
        if (!clienteTCP.conectar()) {
            System.err.println("Não foi possível conectar ao orquestrador. Verifique se ele está rodando.");
            return;
        }
        
        // Iniciar thread para receber tarefas automaticamente
        iniciarThreadRecepcaoTarefas();
        
        // Mostrar menu principal
        mostrarMenu();
        
        // Desconectar ao sair
        clienteTCP.desconectar();
    }
    
    protected void iniciarThreadRecepcaoTarefas() {
        Thread threadRecepcao = new Thread(() -> {
            while (clienteTCP.isConectado()) {
                try {
                    Tarefa tarefa = clienteTCP.receberTarefa();
                    if (tarefa != null) {
                        long lamportTimestamp = relogioLamport.incremento();
                        tarefasRecebidas.add(tarefa);
                        System.out.println("\n[TAREFA RECEBIDA] " + tarefa.getTitulo());
                        System.out.print("\nEscolha uma opção: ");
                    }
                } catch (Exception e) {
                    if (clienteTCP.isConectado()) {
                        System.err.println("Erro ao receber tarefa: " + e.getMessage());
                    }
                    break;
                }
            }
        });
        threadRecepcao.setDaemon(true);
        threadRecepcao.start();
    }
    
    protected void mostrarMenu() {
        System.out.println("Conectado ao orquestrador. Aguardando tarefas...");
        System.out.println();
        
        while (clienteTCP.isConectado()) {
            System.out.println("--- MENU " + workerName + " ---");
            System.out.println("1. Ver tarefas pendentes");
            System.out.println("2. Executar tarefa");
            System.out.println("3. Ver tarefas concluídas");
            System.out.println("4. Sair");
            System.out.print("Escolha uma opção: ");
            
            try {
                int opcao = Integer.parseInt(scanner.nextLine().trim());
                
                switch (opcao) {
                    case 1:
                        verTarefasPendentes();
                        break;
                    case 2:
                        executarTarefa();
                        break;
                    case 3:
                        verTarefasConcluidas();
                        break;
                    case 5:
                        System.out.println("Encerrando worker...");
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

    // Para visualizar as tarefas que não foram finalizadas
    protected void verTarefasPendentes() {
        List<Tarefa> tarefasPendentes = tarefasRecebidas.stream()
                .filter(t -> "PENDENTE".equals(t.getStatus()))
                .toList();
        
        if (tarefasPendentes.isEmpty()) {
            System.out.println("Nenhuma tarefa pendente.");
            return;
        }
        
        System.out.println("=== TAREFAS PENDENTES ===");
        System.out.println("Total: " + tarefasPendentes.size() + " tarefa(s)");
        System.out.println();
        
        for (int i = 0; i < tarefasPendentes.size(); i++) {
            Tarefa tarefa = tarefasPendentes.get(i);
            System.out.println("--- Tarefa " + (i + 1) + " ---");
            System.out.println("ID: " + tarefa.getId());
            System.out.println("Título: " + tarefa.getTitulo());
            System.out.println("Descrição: " + tarefa.getDescricao());
            
            if (tarefa.getTimestamp() > 0) {
                Date date = new Date(tarefa.getTimestamp());
                System.out.println("Recebida em: " + dateFormat.format(date));
            }
            
            System.out.println();
        }
    }

    // Para finalizar uma tarefa
    protected void executarTarefa() {
        List<Tarefa> tarefasPendentes = tarefasRecebidas.stream()
                .filter(t -> "PENDENTE".equals(t.getStatus()))
                .toList();
        
        if (tarefasPendentes.isEmpty()) {
            System.out.println("Nenhuma tarefa pendente para executar.");
            return;
        }
        
        System.out.println("=== EXECUTAR TAREFA ===");
        System.out.println("Tarefas disponíveis:");
        
        for (int i = 0; i < tarefasPendentes.size(); i++) {
            Tarefa tarefa = tarefasPendentes.get(i);
            System.out.println((i + 1) + ". " + tarefa.getTitulo());
        }
        
        System.out.print("Escolha o número da tarefa para executar (0 para cancelar): ");
        
        try {
            int escolha = Integer.parseInt(scanner.nextLine().trim());
            
            if (escolha == 0) {
                System.out.println("Operação cancelada.");
                return;
            }
            
            if (escolha < 1 || escolha > tarefasPendentes.size()) {
                System.out.println("Número inválido!");
                return;
            }
            
            Tarefa tarefaEscolhida = tarefasPendentes.get(escolha - 1);
            
            System.out.println("\n--- EXECUTANDO TAREFA ---");
            System.out.println("Título: " + tarefaEscolhida.getTitulo());
            System.out.println("Descrição: " + tarefaEscolhida.getDescricao());
            System.out.println();
            System.out.println("Simulando execução da tarefa...");
            
            // Simular tempo de execução
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            System.out.print("Tarefa executada com sucesso! Confirmar conclusão? (s/n): ");
            String confirmacao = scanner.nextLine().trim().toLowerCase();
            
            if ("s".equals(confirmacao) || "sim".equals(confirmacao)) {
                // Marcar como concluída
                tarefaEscolhida.setStatus("CONCLUIDA");
                tarefasConcluidas.add(tarefaEscolhida);
                
                // Notificar orquestrador
                clienteTCP.enviarConclusaoTarefa(tarefaEscolhida.getId());
                
                System.out.println("[" + workerName + "] Tarefa '" + tarefaEscolhida.getTitulo() + "' concluída com sucesso!");
            } else {
                System.out.println("Conclusão cancelada. Tarefa permanece pendente.");
            }
            
        } catch (NumberFormatException e) {
            System.out.println("Por favor, digite um número válido.");
        }
    }

    // Para ver as tarefas que já foram finalizadas
    protected void verTarefasConcluidas() {
        if (tarefasConcluidas.isEmpty()) {
            System.out.println("Nenhuma tarefa concluída ainda.");
            return;
        }
        
        System.out.println("=== TAREFAS CONCLUÍDAS ===");
        System.out.println("Total: " + tarefasConcluidas.size() + " tarefa(s)");
        System.out.println();
        
        for (int i = 0; i < tarefasConcluidas.size(); i++) {
            Tarefa tarefa = tarefasConcluidas.get(i);
            System.out.println("--- Tarefa " + (i + 1) + " ---");
            System.out.println("ID: " + tarefa.getId());
            System.out.println("Título: " + tarefa.getTitulo());
            System.out.println("Descrição: " + tarefa.getDescricao());
            System.out.println("Status: " + tarefa.getStatus());
            
            if (tarefa.getTimestamp() > 0) {
                Date date = new Date(tarefa.getTimestamp());
                System.out.println("Recebida em: " + dateFormat.format(date));
            }
            
            System.out.println();
        }
    }
}