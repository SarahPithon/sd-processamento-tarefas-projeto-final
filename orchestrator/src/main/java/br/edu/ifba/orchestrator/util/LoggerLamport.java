package br.edu.ifba.orchestrator.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Logger especializado para sistemas distribuídos com suporte a timestamps Lamport.
// Fornece logs estruturados que incluem tanto timestamps físicos quanto lógicos.
public class LoggerLamport {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final String componentName;
    private final RelógioLamport relógioLamport;
    
    public LoggerLamport(String componentName, RelógioLamport relógioLamport) {
        this.componentName = componentName;
        this.relógioLamport = relógioLamport;
    }
    
    // Log de informação com timestamp Lamport
    public void info(String message) {
        log("INFO", message, false);
    }
    
    // Log de informação com timestamp Lamport e tick automático
    public void infoWithTick(String message) {
        log("INFO", message, true);
    }
    
    // Log de warning com timestamp Lamport
    public void warn(String message) {
        log("WARN", message, false);
    }
    
    // Log de warning com timestamp Lamport e tick automático
    public void warnWithTick(String message) {
        log("WARN", message, true);
    }
    
    // Log de erro com timestamp Lamport
    public void error(String message) {
        log("ERROR", message, false);
    }
    
    // Log de erro com timestamp Lamport e tick automático
    public void errorWithTick(String message) {
        log("ERROR", message, true);
    }
    
    // Log de debug com timestamp Lamport
    public void debug(String message) {
        log("DEBUG", message, false);
    }
    
    // Log de debug com timestamp Lamport e tick automático
    public void debugWithTick(String message) {
        log("DEBUG", message, true);
    }
    
    // Log de evento de sistema distribuído (sempre com tick)
    public void distributed(String event, String details) {
        long lamportTimestamp = relógioLamport.tick();
        String formattedMessage = String.format("[DISTRIBUTED] %s: %s", event, details);
        logFormatted("DIST", formattedMessage, lamportTimestamp);
    }
    
    // Log de comunicação entre nós (sempre com tick)
    public void communication(String direction, String peer, String messageType, String details) {
        long lamportTimestamp = relógioLamport.tick();
        String formattedMessage = String.format("[COMM] %s %s - %s: %s", direction, peer, messageType, details);
        logFormatted("COMM", formattedMessage, lamportTimestamp);
    }
    
    // Log de sincronização com timestamp recebido
    public void sync(String event, long receivedTimestamp, String details) {
        long newTimestamp = relógioLamport.update(receivedTimestamp);
        String formattedMessage = String.format("[SYNC] %s (received:%d, new:%d): %s", 
                                               event, receivedTimestamp, newTimestamp, details);
        logFormatted("SYNC", formattedMessage, newTimestamp);
    }
    
    // Método interno de log
    private void log(String level, String message, boolean doTick) {
        long lamportTimestamp = doTick ? relógioLamport.tick() : relógioLamport.obterTimestampAtual();
        logFormatted(level, message, lamportTimestamp);
    }
    
    // Método interno de formatação e saída do log
    private void logFormatted(String level, String message, long lamportTimestamp) {
        String physicalTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String nodeId = relógioLamport.obterIdNo();
        
        String logEntry = String.format("[%s] [%s] [%s:L%d] %s: %s",
                physicalTimestamp,
                level,
                nodeId,
                lamportTimestamp,
                componentName,
                message);
        
        System.out.println(logEntry);
    }
    
    // Cria um logger filho para um subcomponente
    public LoggerLamport createChildLogger(String childComponentName) {
        return new LoggerLamport(componentName + "." + childComponentName, relógioLamport);
    }
    
    // Obtém o timestamp Lamport atual sem modificá-lo
    public long obterTimestampLamportAtual() {
        return relógioLamport.obterTimestampAtual();
    }
    
    // Obtém o ID do nó
    public String obterIdNo() {
        return relógioLamport.obterIdNo();
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public long getCurrentLamportTimestamp() { return obterTimestampLamportAtual(); }
    
    @Deprecated
    public String getNodeId() { return obterIdNo(); }
}