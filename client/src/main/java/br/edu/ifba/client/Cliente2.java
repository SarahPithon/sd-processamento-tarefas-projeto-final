package br.edu.ifba.client;

import java.util.Scanner;

public class Cliente2 {
    private final ServicoClienteImpl servicoCliente;
    private final Scanner scanner;

    public Cliente2(String host, int port) {
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
        System.out.println("CLIENTE 2 - Sistema de Atividades");
        System.out.println("=".repeat(50));
        System.out.println("1. Enviar nova atividade");
        System.out.println("2. Listar atividades");
        System.out.println("3. Sair");
        System.out.println("=".repeat(50));
        System.out.print("Escolha uma opção: ");
    }



    public void executar() {
        System.out.println("=== Cliente 2 ===");
        
        // Autenticação
        GerenciadorUsuarios gerenciador = new GerenciadorUsuarios(scanner);
        if (!gerenciador.autenticar()) {
            System.out.println("Falha na autenticação. Encerrando...");
            return;
        }
        
        while (true) {
            mostrarMenu();
            
            try {
                int opcao = Integer.parseInt(scanner.nextLine());
                
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
                        System.out.println("Encerrando Cliente 2...");
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
        Cliente2 cliente = new Cliente2("localhost", 9090);
        
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