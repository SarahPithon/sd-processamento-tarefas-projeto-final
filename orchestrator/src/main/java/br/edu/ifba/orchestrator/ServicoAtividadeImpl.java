package br.edu.ifba.orchestrator;

import br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto.*;
import br.edu.ifba.orchestrator.atividade.ServicoAtividadeGrpc;
import br.edu.ifba.orchestrator.worker.GerenciadorWorkers;
import br.edu.ifba.orchestrator.service.GerenciadorTarefas;
import br.edu.ifba.orchestrator.util.RelógioLamport;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServicoAtividadeImpl extends ServicoAtividadeGrpc.ServicoAtividadeImplBase {
    
    private final List<Atividade> atividades = new CopyOnWriteArrayList<>();
    private GerenciadorWorkers gerenciadorWorkers;
    private GerenciadorTarefas gerenciadorTarefas;
    private RelógioLamport relógioLamport;
    
    public void setGerenciadorWorkers(GerenciadorWorkers gerenciadorWorkers) {
        this.gerenciadorWorkers = gerenciadorWorkers;
    }
    
    public void setGerenciadorTarefas(GerenciadorTarefas gerenciadorTarefas) {
        this.gerenciadorTarefas = gerenciadorTarefas;
    }
    
    public void setRelógioLamport(RelógioLamport relógioLamport) {
        this.relógioLamport = relógioLamport;
    }
    
    @Override
    public void enviarAtividade(EnviarAtividadeRequisicao requisicao, StreamObserver<EnviarAtividadeResposta> observadorResposta) {
        try {
            Atividade atividade = requisicao.getAtividade();
            
            if (atividade.getTitulo().trim().isEmpty()) {
                EnviarAtividadeResposta resposta = EnviarAtividadeResposta.newBuilder()
                    .setSucesso(false)
                    .setMensagem("Título da atividade não pode estar vazio")
                    .build();
                observadorResposta.onNext(resposta);
                observadorResposta.onCompleted();
                return;
            }
            
            Atividade.Builder construtorAtividade = atividade.toBuilder();
            if (atividade.getMarcaTempo() == 0) {
                construtorAtividade.setMarcaTempo(System.currentTimeMillis());
            }
            
            Atividade atividadeFinal = construtorAtividade.build();
            atividades.add(atividadeFinal);
            
            // Incrementar Lamport ao receber tarefa do cliente
            if (relógioLamport != null) {
                long marcaTempoLamport = relógioLamport.tick();
                System.out.println("[LAMPORT] Tarefa recebida do cliente - Tick: " + marcaTempoLamport);
            }
            
            System.out.println("Nova atividade recebida:");
            System.out.println("  Título: " + atividadeFinal.getTitulo());
            System.out.println("  Descrição: " + atividadeFinal.getDescricao());
            System.out.println("  Timestamp: " + atividadeFinal.getMarcaTempo());
            
            // Tentar distribuir para workers
            boolean distribuida = false;
            if (gerenciadorWorkers != null && gerenciadorWorkers.obterNumeroWorkersConectados() > 0) {
                distribuida = gerenciadorWorkers.distribuirTarefa(atividadeFinal);
                if (distribuida) {
                    System.out.println("  Status: Distribuída para worker");
                } else {
                    System.out.println("  Status: Erro ao distribuir para worker");
                }
            } else {
                System.out.println("  Status: Nenhum worker disponível - tarefa armazenada");
            }
            System.out.println();
            
            EnviarAtividadeResposta resposta = EnviarAtividadeResposta.newBuilder()
                .setSucesso(true)
                .setMensagem(distribuida ? "Atividade distribuída para worker com sucesso" : "Atividade recebida e armazenada (nenhum worker disponível)")
                .build();
                
            observadorResposta.onNext(resposta);
            observadorResposta.onCompleted();
            
        } catch (Exception e) {
            EnviarAtividadeResposta resposta = EnviarAtividadeResposta.newBuilder()
                .setSucesso(false)
                .setMensagem("Erro ao processar atividade: " + e.getMessage())
                .build();
                
            observadorResposta.onNext(resposta);
            observadorResposta.onCompleted();
        }
    }
    
    @Override
    public void listarAtividades(ListarAtividadesRequisicao requisicao, StreamObserver<ListarAtividadesResposta> observadorResposta) {
        try {
            ListarAtividadesResposta resposta = ListarAtividadesResposta.newBuilder()
                .addAllAtividades(atividades)
                .build();
                
            observadorResposta.onNext(resposta);
            observadorResposta.onCompleted();
            
        } catch (Exception e) {
            observadorResposta.onError(e);
        }
    }
    
    public int obterContadorAtividades() {
        return atividades.size();
    }
    
    public List<Atividade> obterAtividades() {
        return new ArrayList<>(atividades);
    }
    
    public GerenciadorWorkers obterGerenciadorWorkers() {
        return gerenciadorWorkers;
    }
    
    public GerenciadorTarefas obterGerenciadorTarefas() {
        return gerenciadorTarefas;
    }
    
    /**
     * Método para adicionar atividades diretamente (usado para testes)
     */
    public void adicionarAtividade(Atividade atividade) {
        atividades.add(atividade);
    }
}