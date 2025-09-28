package br.edu.ifba.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class GerenciadorUsuarios {
    private static final Map<String, String> usuarios = new HashMap<>();
    private Scanner scanner;
    
    public GerenciadorUsuarios(Scanner scanner) {
        this.scanner = scanner;
    }
    
    public boolean autenticar() {
        System.out.println("\n=== Sistema de Autenticação ===");
        System.out.println("1. Login");
        System.out.println("2. Cadastrar novo usuário");
        System.out.print("Escolha uma opção: ");
        
        int opcao = scanner.nextInt();
        scanner.nextLine(); // Consumir quebra de linha
        
        switch (opcao) {
            case 1:
                return realizarLogin();
            case 2:
                return cadastrarUsuario();
            default:
                System.out.println("Opção inválida!");
                return false;
        }
    }
    
    private boolean realizarLogin() {
        while (true) {
            System.out.println("\n" + "-".repeat(30));
            System.out.println("LOGIN");
            System.out.println("-".repeat(30));
            
            System.out.print("Login (ou 'voltar' para retornar): ");
            String login = scanner.nextLine().trim();
            
            if (login.equalsIgnoreCase("voltar")) {
                return false;
            }
            
            System.out.print("Senha: ");
            String senha = scanner.nextLine().trim();
            
            if (usuarios.containsKey(login) && usuarios.get(login).equals(senha)) {
                System.out.println("\nLogin realizado com sucesso! Bem-vindo, " + login + "!");
                return true;
            } else {
                System.out.println("\nLogin ou senha incorretos! Tente novamente.");
            }
        }
    }
    
    private boolean cadastrarUsuario() {
        System.out.println("\n" + "-".repeat(30));
        System.out.println("CADASTRO DE USUÁRIO");
        System.out.println("-".repeat(30));
        
        while (true) {
            System.out.print("Digite o login desejado: ");
            String login = scanner.nextLine().trim();
            
            if (login.isEmpty()) {
                System.out.println("Login não pode estar vazio! Tente novamente.");
                continue;
            }
            
            if (usuarios.containsKey(login)) {
                System.out.println("Login já existe! Escolha outro.");
                continue;
            }
            
            System.out.print("Digite a senha: ");
            String senha = scanner.nextLine().trim();
            
            if (senha.isEmpty()) {
                System.out.println("Senha não pode estar vazia! Tente novamente.");
                continue;
            }
            
            System.out.print("Confirme a senha: ");
            String confirmaSenha = scanner.nextLine().trim();
            
            if (!senha.equals(confirmaSenha)) {
                System.out.println("Senhas não coincidem! Tente novamente.");
                continue;
            }
            
            usuarios.put(login, senha);
            System.out.println("\nUsuário cadastrado com sucesso!");
            return true;
        }
    }
    
    public static void inicializarUsuariosPadrao() {
        usuarios.put("admin", "admin123");
        usuarios.put("cliente1", "senha123");
        usuarios.put("cliente2", "senha456");
    }
}