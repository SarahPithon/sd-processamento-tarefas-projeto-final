package br.edu.ifba.client;

public class ClienteTeste {
    public static void main(String[] args) {
        try {
            System.out.println("=== CLIENTE DE TESTE ===");
            
            // Criar serviço cliente com host e porta
            ServicoClienteImpl servicoCliente = new ServicoClienteImpl("localhost", 9090);
            
            // Verificar se a conexão está ativa
            if (servicoCliente.isConexaoAtiva()) {
                System.out.println("Conectado ao orquestrador");
                
                // Enviar uma atividade de teste
                System.out.println("Enviando atividade de teste...");
                boolean sucesso = servicoCliente.enviarAtividade("Tarefa de Teste", "Esta é uma tarefa de teste enviada automaticamente");
                
                if (sucesso) {
                    System.out.println("Atividade enviada com sucesso!");
                } else {
                    System.out.println("Falha ao enviar atividade!");
                }
                
                // Aguardar um pouco antes de desconectar
                Thread.sleep(2000);
                
                // Encerrar conexão
                servicoCliente.encerrar();
                System.out.println("Desconectado do orquestrador");
            } else {
                System.out.println("Falha ao conectar ao orquestrador");
            }
            
        } catch (Exception e) {
            System.err.println("Erro no cliente de teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
}