package br.edu.ifba.orchestrator.network;

import br.edu.ifba.orchestrator.worker.GerenciadorWorkers;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServidorTCP {
    
    private static final int DEFAULT_PORTA = 8080;
    private final int porta;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private GerenciadorWorkers gerenciadorWorkers;
    private boolean rodando;
    
    public ServidorTCP(GerenciadorWorkers gerenciadorWorkers) {
        this(gerenciadorWorkers, DEFAULT_PORTA);
    }
    
    public ServidorTCP(GerenciadorWorkers gerenciadorWorkers, int porta) {
        this.gerenciadorWorkers = gerenciadorWorkers;
        this.porta = porta;
        this.executorService = Executors.newCachedThreadPool();
        this.rodando = false;
    }
    
    public void iniciar() {
        try {
            serverSocket = new ServerSocket(porta);
            rodando = true;
            
            System.out.println("Servidor TCP iniciado na porta " + porta + " para comunicação com workers");
            
            // Thread principal para aceitar conexões
            Thread threadAceitacao = new Thread(this::aceitarConexoes);
            threadAceitacao.setDaemon(true);
            threadAceitacao.start();
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar servidor TCP: " + e.getMessage());
        }
    }
    
    private void aceitarConexoes() {
        while (rodando && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                
                // Processar conexão em thread separada
                executorService.submit(() -> processarConexaoWorker(clientSocket));
                
            } catch (IOException e) {
                if (rodando) {
                    System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }
    
    private void processarConexaoWorker(Socket socket) {
        try {
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Aguardar mensagem de registro do worker
            String mensagemRegistro = entrada.readLine();
            
            if (mensagemRegistro != null && mensagemRegistro.contains("\"tipo\":\"REGISTRO\"")) {
                String workerId = extrairWorkerId(mensagemRegistro);
                
                if (workerId != null) {
                    // Registrar worker no gerenciador
                    gerenciadorWorkers.adicionarWorker(workerId, socket);
                } else {
                    System.err.println("ID do worker não encontrado na mensagem de registro");
                    socket.close();
                }
            } else {
                System.err.println("Mensagem de registro inválida recebida");
                socket.close();
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao processar conexão do worker: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ex) {
                System.err.println("Erro ao fechar socket: " + ex.getMessage());
            }
        }
    }
    
    private String extrairWorkerId(String mensagem) {
        try {
            String busca = "\"workerId\":\"";
            int inicio = mensagem.indexOf(busca);
            if (inicio != -1) {
                inicio += busca.length();
                int fim = mensagem.indexOf("\"", inicio);
                if (fim != -1) {
                    return mensagem.substring(inicio, fim);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao extrair worker ID: " + e.getMessage());
        }
        return null;
    }
    
    public void parar() {
        rodando = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            System.out.println("Servidor TCP parado");
            
        } catch (IOException e) {
            System.err.println("Erro ao parar servidor TCP: " + e.getMessage());
        }
    }
    
    public boolean estaRodando() {
        return rodando && serverSocket != null && !serverSocket.isClosed();
    }
    
    public int obterPorta() {
        return porta;
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public boolean isRodando() {
        return estaRodando();
    }
    
    @Deprecated
    public int getPorta() {
        return obterPorta();
    }
}