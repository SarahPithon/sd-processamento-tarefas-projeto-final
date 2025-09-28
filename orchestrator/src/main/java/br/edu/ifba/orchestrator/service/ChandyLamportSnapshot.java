package br.edu.ifba.orchestrator.service;

import br.edu.ifba.orchestrator.util.RelógioLamport;
import br.edu.ifba.orchestrator.network.ComunicacaoMulticast;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

// Implementação do algoritmo de Chandy-Lamport para snapshot de estado global
public class ChandyLamportSnapshot {
    
    private final String nodeId;
    private final RelógioLamport relógioLamport;
    private final ComunicacaoMulticast comunicacaoMulticast;
    private final ObjectMapper objectMapper;
    
    // Estado do snapshot
    private final AtomicBoolean snapshotAtivo = new AtomicBoolean(false);
    private final AtomicLong snapshotId = new AtomicLong(0);
    private final Map<String, Object> estadoLocal = new ConcurrentHashMap<>();
    private final Map<String, List<String>> mensagensCanal = new ConcurrentHashMap<>();
    private final Set<String> nosParticipantes = ConcurrentHashMap.newKeySet();
    
    // Estatísticas
    private final AtomicLong snapshotsRealizados = new AtomicLong(0);
    private volatile long ultimoSnapshot = 0;
    
    public ChandyLamportSnapshot(String nodeId, RelógioLamport relógioLamport, 
                                ComunicacaoMulticast comunicacaoMulticast) {
        this.nodeId = nodeId;
        this.relógioLamport = relógioLamport;
        this.comunicacaoMulticast = comunicacaoMulticast;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        configurarHandlers();
    }
    
    // Inicia um novo snapshot global
    public synchronized boolean iniciarSnapshot() {
        if (snapshotAtivo.get()) {
            System.out.println("Snapshot já está em andamento");
            return false;
        }
        
        long currentSnapshotId = snapshotId.incrementAndGet();
        snapshotAtivo.set(true);
        ultimoSnapshot = System.currentTimeMillis();
        
        System.out.println("Iniciando snapshot global #" + currentSnapshotId);
        
        // Salvar estado local
        salvarEstadoLocal();
        
        // Enviar marcadores para todos os nós
        enviarMarcadores(currentSnapshotId);
        
        // Iniciar gravação de mensagens dos canais
        iniciarGravacaoCanais();
        
        return true;
    }
    
    // Salva o estado local atual
    private void salvarEstadoLocal() {
        estadoLocal.clear();
        estadoLocal.put("nodeId", nodeId);
        estadoLocal.put("timestamp", System.currentTimeMillis());
        estadoLocal.put("lamportClock", relógioLamport.getCurrentTimestamp());
        estadoLocal.put("snapshotId", snapshotId.get());
        
        // Adicionar informações específicas do orquestrador
        estadoLocal.put("status", "ativo");
        estadoLocal.put("tipo", "orquestrador-principal");
        
        System.out.println("Estado local salvo para snapshot #" + snapshotId.get());
    }
    
    // Envia marcadores para todos os nós participantes
    private void enviarMarcadores(long snapshotId) {
        Map<String, Object> marcador = new HashMap<>();
        marcador.put("tipo", "SNAPSHOT_MARKER");
        marcador.put("snapshotId", snapshotId);
        marcador.put("origem", nodeId);
        marcador.put("timestamp", relógioLamport.getCurrentTimestamp());
        
        try {
            comunicacaoMulticast.enviarMensagem("SNAPSHOT_MARKER", marcador);
            System.out.println("Marcadores enviados para snapshot #" + snapshotId);
        } catch (Exception e) {
            System.err.println("Erro ao enviar marcadores: " + e.getMessage());
        }
    }
    
    /**
     * Inicia a gravação de mensagens dos canais
     */
    private void iniciarGravacaoCanais() {
        mensagensCanal.clear();
        // Inicializar listas para cada nó conhecido
        nosParticipantes.forEach(no -> mensagensCanal.put(no, new ArrayList<>()));
    }
    
    /**
     * Processa marcador recebido de outro nó
     */
    private void processarMarcador(Map<String, Object> marcador) {
        long receivedSnapshotId = ((Number) marcador.get("snapshotId")).longValue();
        String origem = (String) marcador.get("origem");
        
        if (!snapshotAtivo.get() && receivedSnapshotId == snapshotId.get()) {
            // Primeiro marcador recebido - iniciar snapshot local
            snapshotAtivo.set(true);
            salvarEstadoLocal();
            
            // Reenviar marcadores para outros nós
            enviarMarcadores(receivedSnapshotId);
            iniciarGravacaoCanais();
        }
        
        // Parar gravação do canal de onde veio o marcador
        if (mensagensCanal.containsKey(origem)) {
            System.out.println("Parando gravação do canal de " + origem);
        }
        
        // Verificar se snapshot está completo
        verificarSnapshotCompleto();
    }
    
    /**
     * Grava mensagem recebida durante snapshot
     */
    private void gravarMensagemCanal(String origem, Map<String, Object> mensagem) {
        if (snapshotAtivo.get() && mensagensCanal.containsKey(origem)) {
            try {
                String mensagemJson = objectMapper.writeValueAsString(mensagem);
                mensagensCanal.get(origem).add(mensagemJson);
            } catch (Exception e) {
                System.err.println("Erro ao gravar mensagem do canal: " + e.getMessage());
            }
        }
    }
    
    /**
     * Verifica se o snapshot está completo
     */
    private void verificarSnapshotCompleto() {
        // Simplificado: considera completo após receber marcadores
        // Em implementação completa, verificaria todos os canais
        finalizarSnapshot();
    }
    
    /**
     * Finaliza o snapshot e salva em arquivo
     */
    private synchronized void finalizarSnapshot() {
        if (!snapshotAtivo.get()) {
            return;
        }
        
        try {
            Map<String, Object> snapshotCompleto = new HashMap<>();
            snapshotCompleto.put("estadoLocal", estadoLocal);
            snapshotCompleto.put("mensagensCanais", mensagensCanal);
            snapshotCompleto.put("participantes", new ArrayList<>(nosParticipantes));
            snapshotCompleto.put("timestampFinalizacao", System.currentTimeMillis());
            
            salvarSnapshotEmArquivo(snapshotCompleto);
            
            snapshotAtivo.set(false);
            snapshotsRealizados.incrementAndGet();
            
            System.out.println("Snapshot #" + snapshotId.get() + " finalizado com sucesso");
            
        } catch (Exception e) {
            System.err.println("Erro ao finalizar snapshot: " + e.getMessage());
            snapshotAtivo.set(false);
        }
    }
    
    /**
     * Salva o snapshot em arquivo JSON
     */
    private void salvarSnapshotEmArquivo(Map<String, Object> snapshot) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String nomeArquivo = String.format("snapshot_%s_%d_%s.json", 
                                         nodeId, snapshotId.get(), timestamp);
        
        File diretorioSnapshots = new File("snapshots");
        if (!diretorioSnapshots.exists()) {
            diretorioSnapshots.mkdirs();
        }
        
        File arquivoSnapshot = new File(diretorioSnapshots, nomeArquivo);
        objectMapper.writeValue(arquivoSnapshot, snapshot);
        
        System.out.println("Snapshot salvo em: " + arquivoSnapshot.getAbsolutePath());
    }
    
    /**
     * Configura handlers para mensagens de snapshot
     */
    private void configurarHandlers() {
        comunicacaoMulticast.registrarHandler("SNAPSHOT_MARKER", (mensagem) -> {
            Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
            processarMarcador(dados);
        });
        
        // Handler genérico para gravar mensagens durante snapshot
        comunicacaoMulticast.registrarHandler("*", (mensagem) -> {
            String tipo = mensagem.getTipo();
            String origem = mensagem.getRemetenteId();
            
            if (!"SNAPSHOT_MARKER".equals(tipo) && origem != null && !origem.equals(nodeId)) {
                Map<String, Object> dados = (Map<String, Object>) mensagem.getDados();
                gravarMensagemCanal(origem, dados);
            }
        });
    }
    
    /**
     * Adiciona um nó participante
     */
    public void adicionarParticipante(String nodeId) {
        nosParticipantes.add(nodeId);
    }
    
    /**
     * Remove um nó participante
     */
    public void removerParticipante(String nodeId) {
        nosParticipantes.remove(nodeId);
    }
    
    /**
     * Retorna estatísticas do sistema de snapshot
     */
    public Map<String, Object> getEstatisticas() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("snapshotAtivo", snapshotAtivo.get());
        stats.put("snapshotsRealizados", snapshotsRealizados.get());
        stats.put("ultimoSnapshot", ultimoSnapshot);
        stats.put("participantes", nosParticipantes.size());
        stats.put("snapshotIdAtual", snapshotId.get());
        
        if (ultimoSnapshot > 0) {
            long tempoDesdeUltimo = System.currentTimeMillis() - ultimoSnapshot;
            stats.put("tempoDesdeUltimoSnapshot", tempoDesdeUltimo / 1000);
        }
        
        return stats;
    }
    
    /**
     * Força a finalização de um snapshot em andamento
     */
    public void forcarFinalizacao() {
        if (snapshotAtivo.get()) {
            System.out.println("Forçando finalização do snapshot #" + snapshotId.get());
            finalizarSnapshot();
        }
    }
    
    /**
     * Para o serviço de snapshot
     */
    public void parar() {
        forcarFinalizacao();
        System.out.println("[SNAPSHOT] Serviço de snapshot parado");
    }
    
    /**
     * Executa um snapshot (alias para iniciarSnapshot)
     */
    public boolean executarSnapshot() {
        return iniciarSnapshot();
    }
}