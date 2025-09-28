package br.edu.ifba.client;

import java.util.Scanner;

public class Cliente1 {
    private final ServicoClienteImpl servicoCliente;
    private final Scanner scanner;
    private final String host;
    private final int port;

    public Cliente1(String host, int port) {
        this.host = host;
        this.port = port;
        this.servicoCliente = new ServicoClienteImpl(host, port);
        this.scanner = new Scanner(System.in);
        GerenciadorUsuarios.inicializarUsuariosPadrao();
    }

    public void encerrar() throws InterruptedException {
        servicoCliente.encerrar();
        scanner.close();
    }

    public void mostrarMenu() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("CLIENTE 1 - Sistema de Atividades");
        System.out.println("=".repeat(50));
        System.out.println("1. Enviar nova atividade");
        System.out.println("2. Listar atividades");
        System.out.println("3. Sair");
        System.out.println("=".repeat(50));
        System.out.print("Escolha uma opção: ");
    }

    public void executar() {
        System.out.println("Conectando ao orquestrador em " + host + ":" + port + "...");
        
        GerenciadorUsuarios.inicializarUsuariosPadrao();
        
        GerenciadorUsuarios gerenciador = new GerenciadorUsuarios(scanner);
        if (!gerenciador.autenticar()) {
            System.out.println("Falha na autenticação. Encerrando...");
            return;
        }
        
        while (true) {
            mostrarMenu();
            
            try {
                int opcao = Integer.parseInt(scanner.nextLine().trim());
                
                switch (opcao) {
                    case 1:
                        System.out.println("\nNova Atividade");
                        System.out.println("-".repeat(30));
                        
                        System.out.print("Título: ");
                        String titulo = scanner.nextLine();
                        
                        System.out.print("Descrição: ");
                        String descricao = scanner.nextLine();
                        
                        servicoCliente.enviarAtividade(titulo, descricao);
                        break;
                        
                    case 2:
                        servicoCliente.listarAtividades();
                        break;
                        
                    case 3:
                        System.out.println("Encerrando Cliente 1...");
                        return;
                        
                    default:
                        System.out.println("Opção inválida! Tente novamente.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Por favor, digite um número válido!");
            }
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 9090;
        
        if (args.length >= 2) {
            host = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida: " + args[1] + ". Usando porta padrão 9090.");
                port = 9090;
            }
        }
        
        Cliente1 cliente = new Cliente1(host, port);
        
        try {
            cliente.executar();
        } finally {
            try {
                cliente.encerrar();
            } catch (InterruptedException e) {
                System.err.println("Erro ao encerrar cliente: " + e.getMessage());
            }
        }
    }
}