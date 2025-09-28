package br.edu.ifba.orchestrator;

import br.edu.ifba.orchestrator.network.ServidorTCP;
import br.edu.ifba.orchestrator.worker.GerenciadorWorkers;
import br.edu.ifba.orchestrator.service.GerenciadorTarefas;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ServidorOrquestrador {
    
    private static final int DEFAULT_GRPC_PORT = 9090;
    private static final int DEFAULT_TCP_PORT = 8080;
    
    private final int grpcPort;
    private final int tcpPort;
    private Server server;
    private ServicoAtividadeImpl servicoAtividade;
    private ServidorTCP servidorTCP;
    private GerenciadorWorkers gerenciadorWorkers;
    private GerenciadorTarefas gerenciadorTarefas;
    
    public ServidorOrquestrador() {
        this(DEFAULT_GRPC_PORT, DEFAULT_TCP_PORT);
    }
    
    public ServidorOrquestrador(int grpcPort, int tcpPort) {
        this.grpcPort = grpcPort;
        this.tcpPort = tcpPort;
    }
    
    public void iniciar(GerenciadorTarefas gerenciadorTarefas) throws IOException {
        this.gerenciadorTarefas = gerenciadorTarefas;
        
        // Inicializar componentes
        gerenciadorWorkers = new GerenciadorWorkers();
        gerenciadorWorkers.setGerenciadorTarefas(gerenciadorTarefas);
        
        servicoAtividade = new ServicoAtividadeImpl();
        servicoAtividade.setGerenciadorWorkers(gerenciadorWorkers);
        servicoAtividade.setGerenciadorTarefas(gerenciadorTarefas);
        
        // Iniciar servidor TCP para workers
        servidorTCP = new ServidorTCP(gerenciadorWorkers, tcpPort);
        servidorTCP.iniciar();
        
        // Iniciar servidor gRPC para clientes
        server = ServerBuilder.forPort(grpcPort)
            .addService(servicoAtividade.bindService())
            .build()
            .start();
            
        System.out.println("Servidor gRPC iniciado na porta " + grpcPort + " (clientes)");
        System.out.println("Orquestrador pronto para receber clientes e workers");
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Parando servidores devido ao shutdown da JVM");
            try {
                ServidorOrquestrador.this.parar();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("Servidores parados");
        }));
    }
    
    public void parar() throws InterruptedException {
        // Parar servidor TCP
        if (servidorTCP != null) {
            servidorTCP.parar();
        }
        
        // Fechar conexões com workers
        if (gerenciadorWorkers != null) {
            gerenciadorWorkers.fecharTodosWorkers();
        }
        
        // Parar servidor gRPC
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }
    
    public void bloquearAteDesligamento() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public ServicoAtividadeImpl obterServicoAtividade() {
        return servicoAtividade;
    }
    
    public boolean estaRodando() {
        return server != null && !server.isShutdown();
    }
    
    public GerenciadorWorkers obterGerenciadorWorkers() {
        return gerenciadorWorkers;
    }
    
    public ServidorTCP obterServidorTCP() {
        return servidorTCP;
    }
}