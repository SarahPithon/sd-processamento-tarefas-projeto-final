package br.edu.ifba.worker.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Tarefa {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("title")
    private String titulo;
    
    @JsonProperty("description")
    private String descricao;
    
    @JsonProperty("timestamp")
    private long timestamp;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("workerId")
    private String workerId;
    
    public Tarefa() {
        this.status = "PENDENTE";
    }
    
    public Tarefa(String id, String titulo, String descricao, long timestamp) {
        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
        this.timestamp = timestamp;
        this.status = "PENDENTE";
    }
    
    // Getters e Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitulo() {
        return titulo;
    }
    
    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getWorkerId() {
        return workerId;
    }
    
    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
    
    @Override
    public String toString() {
        return "Tarefa{" +
                "id='" + id + '\'' +
                ", titulo='" + titulo + '\'' +
                ", descricao='" + descricao + '\'' +
                ", timestamp=" + timestamp +
                ", status='" + status + '\'' +
                ", workerId='" + workerId + '\'' +
                '}';
    }
}