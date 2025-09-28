package br.edu.ifba.client;

import br.edu.ifba.orchestrator.atividade.OrquestradorAtividadeProto;
import br.edu.ifba.orchestrator.atividade.ServicoAtividadeGrpc;
import br.edu.ifba.client.util.RelogioLamport;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

public class ServicoClienteImpl {
    private ManagedChannel canal;
    private ServicoAtividadeGrpc.ServicoAtividadeBlockingStub blockingStub;
    private final RelogioLamport relogioLamport;
    private String currentHost;
    private int currentPort;
    private boolean conexaoAtiva = false;
    private static final int MAX_TENTATIVAS_RECONEXAO = 3;
    private static final long INTERVALO_RECONEXAO_MS = 5000;

    private final String host;
    private final int port;
    
    public ServicoClienteImpl(String host, int port) {
        this.host = host;
        this.port = port;
        this.currentHost = host;
        this.currentPort = port;
        this.relogioLamport = new RelogioLamport("client-" + System.currentTimeMillis());
        conectar();
    }
    
    // Realiza a conexão com o servidor
    public boolean conectar() {
        try {
            canal = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
            
            blockingStub = ServicoAtividadeGrpc.newBlockingStub(canal);
            conexaoAtiva = true;
            System.out.println("Conectado ao servidor " + host + ":" + port);
            return true;
        } catch (Exception e) {
            System.out.println("Erro ao conectar: " + e.getMessage());
            conexaoAtiva = false;
            return false;
        }
    }
    
    // Realiza um teste da conexão
    private boolean testarConexao() {
        try {
            relogioLamport.incremento();
            long timestamp = relogioLamport.obterTimestampAtual();

            System.out.println("Testando conexão com servidor");

            return conexaoAtiva;
        } catch (Exception e) {
            System.out.println("Erro ao testar conexão: " + e.getMessage());
            return false;
        }
    }

    // Realiza o envio de uma atividade
    public boolean enviarAtividade(String titulo, String descricao) {
        if (!conexaoAtiva || blockingStub == null) {
            System.out.println("Erro: Não conectado ao servidor");
            return false;
        }

        try {
            // Incrementa o relógio lógico antes de enviar
            long timestamp = relogioLamport.incremento();
            
            OrquestradorAtividadeProto.Atividade atividade = OrquestradorAtividadeProto.Atividade.newBuilder()
                    .setTitulo(titulo)
                    .setDescricao(descricao)
                    .setMarcaTempo(timestamp)
                    .build();

            OrquestradorAtividadeProto.EnviarAtividadeRequisicao request = OrquestradorAtividadeProto.EnviarAtividadeRequisicao.newBuilder()
                    .setAtividade(atividade)
                    .build();

            OrquestradorAtividadeProto.EnviarAtividadeResposta response = blockingStub.enviarAtividade(request);
            
            if (response.getSucesso()) {
                System.out.println("Atividade enviada com sucesso: " + response.getMensagem());
                return true;
            } else {
                System.out.println("Erro ao enviar atividade: " + response.getMensagem());
                return false;
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar atividade: " + e.getMessage());
            return false;
        }
    }

    public void listarAtividades() {
        if (!conexaoAtiva || blockingStub == null) {
            System.out.println("Erro: Não conectado ao servidor");
            return;
        }

        try {
            OrquestradorAtividadeProto.ListarAtividadesRequisicao request = OrquestradorAtividadeProto.ListarAtividadesRequisicao.newBuilder().build();
            OrquestradorAtividadeProto.ListarAtividadesResposta response = blockingStub.listarAtividades(request);
            
            System.out.println("\n=== ATIVIDADES DO CLIENTE ===");
            if (response.getAtividadesCount() == 0) {
                System.out.println("Nenhuma atividade encontrada.");
            } else {
                for (OrquestradorAtividadeProto.Atividade atividade : response.getAtividadesList()) {
                    System.out.println("Título: " + atividade.getTitulo());
                    System.out.println("Descrição: " + atividade.getDescricao());
                    System.out.println("Timestamp: " + atividade.getMarcaTempo());
                    System.out.println("---");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar atividades: " + e.getMessage());
        }
    }

    // Para saber se a conexão está ativa
    public boolean isConexaoAtiva() {
        return conexaoAtiva && canal != null && !canal.isShutdown();
    }

    // Encerra conexão
    public void encerrar() throws InterruptedException {
        System.out.println("Encerrando cliente...");
        if (canal != null) {
            canal.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        conexaoAtiva = false;
        System.out.println("Cliente encerrado");
    }
}