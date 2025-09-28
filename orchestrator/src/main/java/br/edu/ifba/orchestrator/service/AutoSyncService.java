package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;
import br.edu.ifba.orchestrator.util.RelógioLamport;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Serviço de sincronização automática do arquivo JSON entre orquestradores
public class AutoSyncService {
    private final String nodeId;
    private final RelógioLamport relógioLamport;
    private final ComunicacaoMulticast comunicacaoMulticast;
    private final String jsonFilePath;
    private final ExecutorService executor;
    private final AtomicBoolean ativo;
    private WatchService watchService;
    private String lastKnownHash;
    
    // Estatísticas
    private int arquivosEnviados = 0;
    private int arquivosRecebidos = 0;
    private long ultimaSincronizacao = 0;
    
    public AutoSyncService(String nodeId, RelógioLamport relógioLamport, 
                          ComunicacaoMulticast comunicacaoMulticast, String jsonFilePath) {
        this.nodeId = nodeId;
        this.relógioLamport = relógioLamport;
        this.comunicacaoMulticast = comunicacaoMulticast;
        this.jsonFilePath = jsonFilePath;
        this.executor = Executors.newSingleThreadExecutor();
        this.ativo = new AtomicBoolean(false);
        this.lastKnownHash = calculateFileHash();
    }
    
    /**
     * Inicia o serviço de sincronização
     */
    public void iniciar() {
        if (ativo.compareAndSet(false, true)) {
            try {
                // Configurar monitoramento de arquivo
                setupFileWatcher();
                
                // Configurar handlers de mensagens
                setupMessageHandlers();
                
                System.out.println("[AUTO-SYNC] Serviço iniciado para: " + jsonFilePath);
                
            } catch (Exception e) {
                System.err.println("[AUTO-SYNC] Erro ao iniciar serviço: " + e.getMessage());
                ativo.set(false);
            }
        }
    }
    
    /**
     * Para o serviço de sincronização
     */
    public void parar() {
        if (ativo.compareAndSet(true, false)) {
            try {
                if (watchService != null) {
                    watchService.close();
                }
                executor.shutdown();
                System.out.println("[AUTO-SYNC] Serviço parado");
            } catch (Exception e) {
                System.err.println("[AUTO-SYNC] Erro ao parar serviço: " + e.getMessage());
            }
        }
    }
    
    /**
     * Configura o monitoramento do arquivo JSON
     */
    private void setupFileWatcher() throws IOException {
        Path path = Paths.get(jsonFilePath).getParent();
        watchService = path.getFileSystem().newWatchService();
        path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        
        executor.submit(() -> {
            while (ativo.get()) {
                try {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            Path changed = (Path) event.context();
                            if (changed.toString().equals(Paths.get(jsonFilePath).getFileName().toString())) {
                                handleFileChange();
                            }
                        }
                    }
                    
                    key.reset();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("[AUTO-SYNC] Erro no monitoramento: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Configura os handlers de mensagens multicast
     */
    private void setupMessageHandlers() {
        // Handler para receber sincronização de arquivo
        comunicacaoMulticast.registrarHandler("FILE_SYNC", (mensagem) -> {
            try {
                Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
                String senderId = (String) dados.get("senderId");
                String fileHash = (String) dados.get("fileHash");
                String fileContent = (String) dados.get("fileContent");
                Long lamportTimestamp = (Long) dados.get("lamportTimestamp");
                
                // Não processar nossa própria mensagem
                if (senderId.equals(nodeId)) {
                    return;
                }
                
                // Atualizar relógio Lamport
                relógioLamport.update(lamportTimestamp);
                
                // Verificar se precisamos atualizar o arquivo
                if (!fileHash.equals(lastKnownHash)) {
                    updateLocalFile(fileContent, fileHash);
                    arquivosRecebidos++;
                    ultimaSincronizacao = System.currentTimeMillis();
                    
                    System.out.println("[AUTO-SYNC] Arquivo sincronizado de: " + senderId);
                }
                
            } catch (Exception e) {
                System.err.println("[AUTO-SYNC] Erro ao processar sincronização: " + e.getMessage());
            }
        });
    }
    
    /**
     * Manipula mudanças no arquivo
     */
    private void handleFileChange() {
        try {
            // Aguardar um pouco para garantir que a escrita terminou
            Thread.sleep(500);
            
            String currentHash = calculateFileHash();
            if (!currentHash.equals(lastKnownHash)) {
                lastKnownHash = currentHash;
                sendFileSync();
                arquivosEnviados++;
                ultimaSincronizacao = System.currentTimeMillis();
            }
            
        } catch (Exception e) {
            System.err.println("[AUTO-SYNC] Erro ao processar mudança: " + e.getMessage());
        }
    }
    
    /**
     * Envia sincronização do arquivo
     */
    private void sendFileSync() {
        try {
            String fileContent = readFileContent();
            String fileHash = calculateFileHash();
            
            Map<String, Object> syncMessage = Map.of(
                "senderId", nodeId,
                "fileHash", fileHash,
                "fileContent", fileContent,
                "lamportTimestamp", relógioLamport.tick(),
                "timestamp", System.currentTimeMillis()
            );
            
            comunicacaoMulticast.enviarMensagem("FILE_SYNC", syncMessage);
            System.out.println("[AUTO-SYNC] Arquivo enviado para sincronização");
            
        } catch (Exception e) {
            System.err.println("[AUTO-SYNC] Erro ao enviar sincronização: " + e.getMessage());
        }
    }
    
    /**
     * Atualiza o arquivo local
     */
    private void updateLocalFile(String content, String expectedHash) {
        try {
            // Decodificar conteúdo
            byte[] decodedContent = Base64.getDecoder().decode(content);
            
            // Escrever arquivo
            Files.write(Paths.get(jsonFilePath), decodedContent);
            
            // Verificar hash
            String actualHash = calculateFileHash();
            if (!actualHash.equals(expectedHash)) {
                System.err.println("[AUTO-SYNC] Aviso: Hash do arquivo não confere após atualização");
            }
            
            lastKnownHash = actualHash;
            
        } catch (Exception e) {
            System.err.println("[AUTO-SYNC] Erro ao atualizar arquivo: " + e.getMessage());
        }
    }
    
    /**
     * Lê o conteúdo do arquivo
     */
    private String readFileContent() throws IOException {
        byte[] content = Files.readAllBytes(Paths.get(jsonFilePath));
        return Base64.getEncoder().encodeToString(content);
    }
    
    /**
     * Calcula hash do arquivo
     */
    private String calculateFileHash() {
        try {
            File file = new File(jsonFilePath);
            if (!file.exists()) {
                return "";
            }
            
            byte[] content = Files.readAllBytes(file.toPath());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (Exception e) {
            System.err.println("[AUTO-SYNC] Erro ao calcular hash: " + e.getMessage());
            return "";
        }
    }
    
    // Getters para estatísticas
    public boolean isAtivo() { return ativo.get(); }
    public int getArquivosEnviados() { return arquivosEnviados; }
    public int getArquivosRecebidos() { return arquivosRecebidos; }
    public long getUltimaSincronizacao() { return ultimaSincronizacao; }
    public String getJsonFilePath() { return jsonFilePath; }
}