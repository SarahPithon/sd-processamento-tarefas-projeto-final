package br.edu.ifba.orchestrator.network;

import br.edu.ifba.orchestrator.util.RelógioLamport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// Gerencia comunicação multicast entre orquestradores para sincronização de estado
public class ComunicacaoMulticast {
    private static final String MULTICAST_ADDRESS = "224.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int BUFFER_SIZE = 1024;
    
    private MulticastSocket socket;
    private InetAddress group;
    private NetworkInterface networkInterface;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;
    private final RelógioLamport relógioLamport;
    private final String orquestradorId;
    private volatile boolean running = false;
    
    // Callbacks para diferentes tipos de mensagem
    private final ConcurrentHashMap<String, Consumer<MensagemMulticast>> messageHandlers;
    
    public ComunicacaoMulticast(String orquestradorId, RelógioLamport relógioLamport) {
        this.orquestradorId = orquestradorId;
        this.relógioLamport = relógioLamport;
        this.executor = Executors.newFixedThreadPool(2);
        this.objectMapper = new ObjectMapper();
        this.messageHandlers = new ConcurrentHashMap<>();
        
        try {
            this.group = InetAddress.getByName(MULTICAST_ADDRESS);
            this.networkInterface = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        } catch (Exception e) {
            System.err.println("[MULTICAST] Erro ao inicializar endereços: " + e.getMessage());
        }
    }
    
    // Inicia o serviço multicast
    public void iniciar() throws IOException {
        socket = new MulticastSocket(MULTICAST_PORT);
        socket.joinGroup(new InetSocketAddress(group, MULTICAST_PORT), networkInterface);
        running = true;
        
        // Thread para escutar mensagens
        executor.submit(this::escutarMensagens);
        
        System.out.println("[MULTICAST] " + orquestradorId + " - Serviço iniciado no grupo " + 
                          MULTICAST_ADDRESS + ":" + MULTICAST_PORT);
    }
    
    // Para o serviço multicast
    public void parar() {
        running = false;
        try {
            if (socket != null) {
                socket.leaveGroup(new InetSocketAddress(group, MULTICAST_PORT), networkInterface);
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[MULTICAST] Erro ao parar serviço: " + e.getMessage());
        }
        executor.shutdown();
        System.out.println("[MULTICAST] " + orquestradorId + " - Serviço parado");
    }
    
    /**
     * Envia mensagem multicast para outros orquestradores
     */
    public void enviarMensagem(String tipo, Object dados) {
        try {
            long timestamp = relógioLamport.tick();
            
            MensagemMulticast mensagem = new MensagemMulticast(
                orquestradorId, tipo, dados, timestamp, System.currentTimeMillis()
            );
            
            String json = objectMapper.writeValueAsString(mensagem);
            byte[] buffer = json.getBytes();
            
            DatagramPacket packet = new DatagramPacket(
                buffer, buffer.length, group, MULTICAST_PORT
            );
            
            socket.send(packet);
            
            // Log apenas heartbeats entre orquestradores
            if (isHeartbeatMessage(tipo)) {
                System.out.println("[HEARTBEAT] " + orquestradorId + " - Enviado: " + tipo + 
                                  " [Lamport:" + timestamp + "]");
            }
            
        } catch (Exception e) {
            System.err.println("[MULTICAST] Erro ao enviar mensagem: " + e.getMessage());
        }
    }
    
    // Registra handler para tipo específico de mensagem
    public void registrarHandler(String tipo, Consumer<MensagemMulticast> handler) {
        messageHandlers.put(tipo, handler);
        System.out.println("[MULTICAST] Handler registrado para tipo: " + tipo);
    }
    
    // Verifica se a mensagem é um heartbeat entre orquestradores
    private boolean isHeartbeatMessage(String tipo) {
        return tipo.equals("SIMPLE_HEARTBEAT") || 
               tipo.equals("SIMPLE_HEARTBEAT_RESPONSE") ||
               tipo.equals("BACKUP_HEARTBEAT") ||
               tipo.equals("PRIMARY_HEARTBEAT");
    }
    
    // Thread para escutar mensagens multicast
    private void escutarMensagens() {
        byte[] buffer = new byte[BUFFER_SIZE];
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String json = new String(packet.getData(), 0, packet.getLength());
                MensagemMulticast mensagem = objectMapper.readValue(json, MensagemMulticast.class);
                
                // Ignora mensagens próprias
                if (mensagem.getRemetenteId().equals(orquestradorId)) {
                    continue;
                }
                
                // Atualiza relógio de Lamport
                relógioLamport.update(mensagem.getTimestampLamport());
                
                // Log apenas heartbeats entre orquestradores
                if (isHeartbeatMessage(mensagem.getTipo())) {
                    System.out.println("[HEARTBEAT] " + orquestradorId + " - Recebido de " + 
                                      mensagem.getRemetenteId() + ": " + mensagem.getTipo() + 
                                      " [Lamport:" + mensagem.getTimestampLamport() + "]");
                }
                
                // Processa mensagem com handler apropriado
                Consumer<MensagemMulticast> handler = messageHandlers.get(mensagem.getTipo());
                if (handler != null) {
                    executor.submit(() -> handler.accept(mensagem));
                } else {
                    System.out.println("[MULTICAST] Nenhum handler para tipo: " + mensagem.getTipo());
                }
                
            } catch (Exception e) {
                if (running) {
                    System.err.println("[MULTICAST] Erro ao receber mensagem: " + e.getMessage());
                }
            }
        }
    }
    
    // Classe para representar mensagens multicast
    public static class MensagemMulticast {
        private String remetenteId;
        private String tipo;
        private Object dados;
        private long timestampLamport;
        private long timestampFisico;
        
        // Construtor padrão para Jackson
        public MensagemMulticast() {}
        
        public MensagemMulticast(String remetenteId, String tipo, Object dados, 
                               long timestampLamport, long timestampFisico) {
            this.remetenteId = remetenteId;
            this.tipo = tipo;
            this.dados = dados;
            this.timestampLamport = timestampLamport;
            this.timestampFisico = timestampFisico;
        }
        
        // Getters e Setters traduzidos
        public String obterRemetenteId() { return remetenteId; }
        public void definirRemetenteId(String remetenteId) { this.remetenteId = remetenteId; }
        
        public String obterTipo() { return tipo; }
        public void definirTipo(String tipo) { this.tipo = tipo; }
        
        public Object obterDados() { return dados; }
        public void definirDados(Object dados) { this.dados = dados; }
        
        public long obterTimestampLamport() { return timestampLamport; }
        public void definirTimestampLamport(long timestampLamport) { this.timestampLamport = timestampLamport; }
        
        public long obterTimestampFisico() { return timestampFisico; }
        public void definirTimestampFisico(long timestampFisico) { this.timestampFisico = timestampFisico; }
        
        // Métodos de compatibilidade (deprecated)
        @Deprecated
        public String getRemetenteId() { return obterRemetenteId(); }
        @Deprecated
        public void setRemetenteId(String remetenteId) { definirRemetenteId(remetenteId); }
        
        @Deprecated
        public String getTipo() { return obterTipo(); }
        @Deprecated
        public void setTipo(String tipo) { definirTipo(tipo); }
        
        @Deprecated
        public Object getDados() { return obterDados(); }
        @Deprecated
        public void setDados(Object dados) { definirDados(dados); }
        
        @Deprecated
        public long getTimestampLamport() { return obterTimestampLamport(); }
        @Deprecated
        public void setTimestampLamport(long timestampLamport) { definirTimestampLamport(timestampLamport); }
        
        @Deprecated
        public long getTimestampFisico() { return obterTimestampFisico(); }
        @Deprecated
        public void setTimestampFisico(long timestampFisico) { definirTimestampFisico(timestampFisico); }
    }
}