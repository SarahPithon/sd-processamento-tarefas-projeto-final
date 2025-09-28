package br.edu.ifba.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Tarefa {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("horario_recebimento")
    private LocalDateTime horarioRecebimento;
    
    @JsonProperty("titulo")
    private String titulo;
    
    @JsonProperty("descricao")
    private String descricao;
    
    @JsonProperty("status")
    private StatusTarefa status;
    
    @JsonProperty("realocada")
    private int realocada;
    
    @JsonProperty("worker_responsavel")
    private String workerResponsavel;
    
    @JsonProperty("clock_lamport")
    private long clockLamport;
    
    public enum StatusTarefa {
        PENDENTE,
        FINALIZADA
    }
    
    // Construtor padrão
    public Tarefa() {
        this.realocada = 0;
        this.status = StatusTarefa.PENDENTE;
        this.horarioRecebimento = LocalDateTime.now();
    }
    
    // Construtor com parâmetros
    public Tarefa(String id, String titulo, String descricao) {
        this();
        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
    }
    
    // Getters e Setters
    public String obterIdentificador() {
        return id;
    }
    
    public void definirIdentificador(String id) {
        this.id = id;
    }
    
    public LocalDateTime obterHorarioRecebimento() {
        return horarioRecebimento;
    }
    
    public void definirHorarioRecebimento(LocalDateTime horarioRecebimento) {
        this.horarioRecebimento = horarioRecebimento;
    }
    
    public String obterTitulo() {
        return titulo;
    }
    
    public void definirTitulo(String titulo) {
        this.titulo = titulo;
    }
    
    public String obterDescricao() {
        return descricao;
    }
    
    public void definirDescricao(String descricao) {
        this.descricao = descricao;
    }
    
    public StatusTarefa obterStatus() {
        return status;
    }
    
    public void definirStatus(StatusTarefa status) {
        this.status = status;
    }
    
    public int obterRealocada() {
        return realocada;
    }
    
    public void definirRealocada(int realocada) {
        this.realocada = realocada;
    }
    
    public void incrementarRealocada() {
        this.realocada++;
    }
    
    public String obterWorkerResponsavel() {
        return workerResponsavel;
    }
    
    public void definirWorkerResponsavel(String workerResponsavel) {
        this.workerResponsavel = workerResponsavel;
    }
    
    public long obterClockLamport() {
        return clockLamport;
    }
    
    public void definirClockLamport(long clockLamport) {
        this.clockLamport = clockLamport;
    }
    
    public String obterHorarioRecebimentoFormatado() {
        if (horarioRecebimento != null) {
            return horarioRecebimento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        }
        return "";
    }
    
    // Métodos de compatibilidade (deprecated)
    @Deprecated
    public String getId() { return obterIdentificador(); }
    
    @Deprecated
    public String getTitulo() { return obterTitulo(); }
    
    @Deprecated
    public String getDescricao() { return obterDescricao(); }
    
    @Deprecated
    public StatusTarefa getStatus() { return obterStatus(); }
    
    @Deprecated
    public int getRealocada() { return obterRealocada(); }
    
    @Deprecated
    public String getWorkerResponsavel() { return obterWorkerResponsavel(); }
    
    @Deprecated
    public long getClockLamport() { return obterClockLamport(); }
    
    @Override
    public String toString() {
        return "Tarefa{" +
                "id='" + id + '\'' +
                ", titulo='" + titulo + '\'' +
                ", status=" + status +
                ", workerResponsavel='" + workerResponsavel + '\'' +
                ", realocada=" + realocada +
                ", clockLamport=" + clockLamport +
                '}';
    }
}