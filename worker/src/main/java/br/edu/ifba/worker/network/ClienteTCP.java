package br.edu.ifba.worker.network;

import br.edu.ifba.worker.model.Tarefa;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ClienteTCP {
    private static final String DEFAULT_SERVIDOR_HOST = "localhost";
    private static final int DEFAULT_SERVIDOR_PORTA = 8080;
    
    private final String servidorHost;
    private final int servidorPorta;
    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter saida;
    private ObjectMapper objectMapper;
    private BlockingQueue<Tarefa> filaTarefas;
    private String workerId;
    private boolean conectado;
    
    public ClienteTCP(String workerId) {
        this(workerId, DEFAULT_SERVIDOR_HOST, DEFAULT_SERVIDOR_PORTA);
    }
    
    public ClienteTCP(String workerId, String host, int porta) {
        this.workerId = workerId;
        this.servidorHost = host;
        this.servidorPorta = porta;
        this.objectMapper = new ObjectMapper();
        this.filaTarefas = new LinkedBlockingQueue<>();
        this.conectado = false;
    }
    
    public boolean conectar() {
        try {
            socket = new Socket(servidorHost, servidorPorta);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida = new PrintWriter(socket.getOutputStream(), true);
            
            // Registrar worker no orquestrador
            registrarWorker();
            
            // Iniciar thread para receber tarefas
            iniciarThreadRecepcao();
            
            conectado = true;
            System.out.println("Worker " + workerId + " conectado ao orquestrador em " + servidorHost + ":" + servidorPorta);
            return true;
            
        } catch (IOException e) {
            System.err.println("Erro ao conectar com o orquestrador: " + e.getMessage());
            return false;
        }
    }
    
    private void registrarWorker() {
        try {
            String mensagemRegistro = "{\"tipo\":\"REGISTRO\",\"workerId\":\"" + workerId + "\"}";
            saida.println(mensagemRegistro);
        } catch (Exception e) {
            System.err.println("Erro ao registrar worker: " + e.getMessage());
        }
    }
    
    private void iniciarThreadRecepcao() {
        Thread threadRecepcao = new Thread(() -> {
            try {
                String linha;
                while ((linha = entrada.readLine()) != null && conectado) {
                    processarMensagem(linha);
                }
            } catch (IOException e) {
                if (conectado) {
                    System.err.println("Erro na comunicação com o orquestrador: " + e.getMessage());
                }
            }
        });
        threadRecepcao.setDaemon(true);
        threadRecepcao.start();
    }
    
    private void processarMensagem(String mensagem) {
        try {
            // Parse da mensagem JSON
            if (mensagem.contains("\"tipo\":\"TAREFA\"")) {
                // Extrair tarefa da mensagem
                Tarefa tarefa = objectMapper.readValue(mensagem.substring(mensagem.indexOf("\"tarefa\":")+9, mensagem.lastIndexOf("}")+1), Tarefa.class);
                tarefa.setWorkerId(workerId);
                filaTarefas.offer(tarefa);
                System.out.println("Nova tarefa recebida: " + tarefa.getTitulo());
            } else if (mensagem.contains("\"tipo\":\"HEARTBEAT\"")) {
                // Responder ao heartbeat do orquestrador
                responderHeartbeat();
            } else if (mensagem.contains("\"tipo\":\"MUDANCA_ORQUESTRADOR\"")) {
                // Processar notificação de mudança de orquestrador
                processarMudancaOrquestrador(mensagem);
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem: " + e.getMessage());
        }
    }
    
    // Mudança de orquestrador, quando o principal cai e entra o de backup
    private void processarMudancaOrquestrador(String mensagem) {
        try {
            System.out.println("\n*** NOTIFICAÇÃO DE MUDANÇA DE ORQUESTRADOR ***");
            System.out.println("Mensagem recebida: " + mensagem);
            
            // Extrair informações da nova conexão
            String novoHost = extrairValor(mensagem, "novoHost");
            String novaPortaStr = extrairValor(mensagem, "novaPorta");
            String novoOrquestradorId = extrairValor(mensagem, "novoOrquestradorId");
            
            if (novoHost != null && novaPortaStr != null) {
                int novaPorta = Integer.parseInt(novaPortaStr);
                
                System.out.println("Novo orquestrador: " + novoOrquestradorId);
                System.out.println("Nova conexão: " + novoHost + ":" + novaPorta);
                System.out.println("Desconectando do orquestrador atual...");
                
                // Desconectar da conexão atual
                desconectar();
                
                // Aguardar um pouco antes de tentar reconectar
                Thread.sleep(3000);
                
                // Tentar reconectar ao novo orquestrador
                System.out.println("Tentando reconectar ao novo orquestrador...");
                if (reconectarNovoOrquestrador(novoHost, novaPorta)) {
                    System.out.println("*** RECONEXÃO REALIZADA COM SUCESSO ***");
                } else {
                    System.err.println("*** FALHA NA RECONEXÃO ***");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao processar mudança de orquestrador: " + e.getMessage());
        }
    }
    
    // Extrai valor do JSON
    private String extrairValor(String json, String chave) {
        String busca = "\"" + chave + "\":";
        int inicio = json.indexOf(busca);
        if (inicio == -1) return null;
        
        inicio += busca.length();
        if (json.charAt(inicio) == '"') {
            inicio++; // Pular aspas
            int fim = json.indexOf('"', inicio);
            return json.substring(inicio, fim);
        } else {
            int fim = json.indexOf(',', inicio);
            if (fim == -1) fim = json.indexOf('}', inicio);
            return json.substring(inicio, fim).trim();
        }
    }
    
    // Para reconectar com outro orquestrador
    private boolean reconectarNovoOrquestrador(String novoHost, int novaPorta) {
        try {
            // Fechar conexão atual se ainda estiver aberta
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            // Conectar ao novo orquestrador
            socket = new Socket(novoHost, novaPorta);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida = new PrintWriter(socket.getOutputStream(), true);
            
            // Registrar worker no novo orquestrador
            registrarWorker();
            
            // Reiniciar thread de recepção
            iniciarThreadRecepcao();
            
            conectado = true;
            System.out.println("Worker " + workerId + " reconectado ao novo orquestrador em " + novoHost + ":" + novaPorta);
            return true;
            
        } catch (IOException e) {
            System.err.println("Erro ao reconectar com o novo orquestrador: " + e.getMessage());
            return false;
        }
    }
    
    private void responderHeartbeat() {
        try {
            String resposta = "{\"tipo\":\"HEARTBEAT_RESPONSE\",\"workerId\":\"" + workerId + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
            saida.println(resposta);
            // System.out.println("Heartbeat respondido ao orquestrador"); // Log muito verboso, comentado
        } catch (Exception e) {
            System.err.println("Erro ao responder heartbeat: " + e.getMessage());
        }
    }
    
    public void enviarConclusaoTarefa(String tarefaId) {
        try {
            String mensagem = "{\"tipo\":\"CONCLUSAO\",\"workerId\":\"" + workerId + "\",\"tarefaId\":\"" + tarefaId + "\"}";
            saida.println(mensagem);
            System.out.println("Conclusão da tarefa " + tarefaId + " enviada ao orquestrador");
        } catch (Exception e) {
            System.err.println("Erro ao enviar conclusão da tarefa: " + e.getMessage());
        }
    }
    
    public Tarefa receberTarefa() {
        try {
            return filaTarefas.take(); // Bloqueia até uma tarefa estar disponível
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    public boolean temTarefasPendentes() {
        return !filaTarefas.isEmpty();
    }
    
    public int getNumeroTarefasPendentes() {
        return filaTarefas.size();
    }
    
    public boolean isConectado() {
        return conectado && socket != null && !socket.isClosed();
    }
    
    public void desconectar() {
        conectado = false;
        try {
            if (saida != null) {
                String mensagem = "{\"tipo\":\"DESCONEXAO\",\"workerId\":\"" + workerId + "\"}";
                saida.println(mensagem);
                saida.close();
            }
            if (entrada != null) entrada.close();
            if (socket != null) socket.close();
            System.out.println("Worker " + workerId + " desconectado do orquestrador");
        } catch (IOException e) {
            System.err.println("Erro ao desconectar: " + e.getMessage());
        }
    }
}