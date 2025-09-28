# Sistema Distribuído - Processamento de Tarefas

## Relatório Técnico e Guia de Uso

**Projeto final da disciplina de Sistemas Distribuídos 2025.1**

---

## 🚀 Guia de Instalação e Execução

### Pré-requisitos

Antes de executar o sistema, certifique-se de ter instalado:

- **Java 21** ou superior
- **Maven 3.8+** para gerenciamento de dependências
- **Git** para clonagem do repositório
- **Sistema Operacional**: Windows, Linux ou macOS

### Verificação dos Pré-requisitos

```bash
# Verificar versão do Java
java -version

# Verificar versão do Maven
mvn -version

# Verificar versão do Git
git --version
```

### 1. Clonagem e Preparação

```bash
# Clonar o repositório
git clone https://github.com/seu-usuario/sd-processamento-tarefas-projeto-final.git

# Navegar para o diretório do projeto
cd sd-processamento-tarefas-projeto-final

# Verificar estrutura do projeto
ls -la
```

### 2. Compilação do Projeto

```bash
# Limpar e compilar todos os módulos
mvn clean install

# Verificar se a compilação foi bem-sucedida
# Deve exibir "BUILD SUCCESS" para todos os módulos
```

**Saída esperada:**

```
[INFO] Reactor Summary for Sistema Distribuído - Processamento de Tarefas 1.0-SNAPSHOT:
[INFO]
[INFO] orchestrator ....................................... SUCCESS
[INFO] client ............................................. SUCCESS
[INFO] worker ............................................. SUCCESS
[INFO] Sistema Distribuído - Processamento de Tarefas ..... SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

### 3. Execução dos Componentes

#### 3.1 Executando o Orquestrador Principal

```bash
# Navegar para o módulo orchestrator
cd orchestrator

# Executar o orquestrador principal
java -cp "target/classes:target/dependency/*" br.edu.ifba.Main

# Ou no Windows:
java -cp "target/classes;target/dependency/*" br.edu.ifba.Main
```

**Saída esperada:**

```
[INFO] OrquestradorPrincipal iniciado na porta 8080
[INFO] Sistema de heartbeat ativado - intervalo: 5s
[INFO] Aguardando conexões de workers e clientes...
```

#### 3.2 Executando Workers

Abra novos terminais para cada worker:

```bash
# Terminal 2 - Worker 1
cd worker
java -cp "target/classes;target/dependency/*" br.edu.ifba.worker.Worker1

# Terminal 3 - Worker 2
java -cp "target/classes;target/dependency/*" br.edu.ifba.worker.Worker2

# Terminal 4 - Worker 3
java -cp "target/classes;target/dependency/*" br.edu.ifba.worker.Worker3
```

**Saída esperada para cada worker:**

```
[INFO] Worker-001 iniciado
[INFO] Conectando ao orquestrador em localhost:8080
[INFO] Conexão estabelecida com sucesso
[INFO] Worker pronto para receber tarefas
```

#### 3.3 Executando Clientes

```bash
# Terminal 5 - Cliente
cd client
java -cp "target/classes;target/dependency/*" br.edu.ifba.Main

# Ou executar clientes específicos
java -cp "target/classes;target/dependency/*" br.edu.ifba.client.Cliente1
java -cp "target/classes;target/dependency/*" br.edu.ifba.client.Cliente2
```

### 4. Configuração de Rede

#### 4.1 Configuração de Portas

O sistema utiliza as seguintes portas por padrão:

- **Orquestrador gRPC**: 8080
- **Orquestrador TCP**: 8081
- **Multicast UDP**: 224.0.0.1:8082
- **Workers TCP**: 9001, 9002, 9003

#### 4.2 Configuração de Firewall

Certifique-se de que as portas estejam liberadas:

```bash
# Windows (executar como administrador)
netsh advfirewall firewall add rule name="SD-Sistema" dir=in action=allow protocol=TCP localport=8080-8082,9001-9003

# Linux (Ubuntu/Debian)
sudo ufw allow 8080:8082/tcp
sudo ufw allow 9001:9003/tcp
sudo ufw allow 8082/udp
```

### 5. Verificação da Instalação

#### 5.1 Teste de Conectividade

```bash
# Verificar se o orquestrador está rodando
netstat -an | grep 8080

# Verificar se os workers estão conectados
netstat -an | grep 9001
```

#### 5.2 Teste Básico do Sistema

```bash
# Executar teste simples
cd client
java -cp "target/classes;target/dependency/*" br.edu.ifba.client.ClienteTeste
```

---

## 💡 Exemplos de Uso

### Exemplo 1: Submissão Simples de Tarefa

#### Código do Cliente:

```java
// Exemplo de submissão de tarefa
public class ExemploCliente {
    public static void main(String[] args) {
        GerenciadorUsuarios gerenciador = new GerenciadorUsuarios();

        // Criar uma tarefa
        Tarefa tarefa = new Tarefa();
        tarefa.setId("TASK-001");
        tarefa.setDescricao("Processar dados de vendas");
        tarefa.setDados("dados_vendas.csv");
        tarefa.setPrioridade(1);

        // Submeter tarefa
        String resultado = gerenciador.submeterTarefa(tarefa);
        System.out.println("Resultado: " + resultado);
    }
}
```

#### Execução:

```bash
cd client
javac -cp "target/classes;target/dependency/*" ExemploCliente.java
java -cp ".:target/classes;target/dependency/*" ExemploCliente
```

#### Saída esperada:

```
[INFO] Conectando ao orquestrador...
[INFO] Tarefa TASK-001 submetida com sucesso
[INFO] Tarefa atribuída ao Worker-002
[INFO] Processamento concluído em 2.3s
Resultado: Dados processados com sucesso - 1250 registros analisados
```

### Exemplo 2: Processamento em Lote

#### Código para múltiplas tarefas:

```java
public class ProcessamentoLote {
    public static void main(String[] args) {
        GerenciadorUsuarios gerenciador = new GerenciadorUsuarios();
        List<String> resultados = new ArrayList<>();

        // Submeter 10 tarefas em paralelo
        for (int i = 1; i <= 10; i++) {
            Tarefa tarefa = new Tarefa();
            tarefa.setId("BATCH-" + String.format("%03d", i));
            tarefa.setDescricao("Processamento lote item " + i);
            tarefa.setDados("dataset_" + i + ".json");

            String resultado = gerenciador.submeterTarefa(tarefa);
            resultados.add(resultado);

            System.out.println("Tarefa " + i + " submetida");
        }

        // Aguardar conclusão de todas as tarefas
        System.out.println("Todas as tarefas foram processadas:");
        resultados.forEach(System.out::println);
    }
}
```

### Exemplo 3: Monitoramento de Sistema

#### Código para monitorar workers:

```java
public class MonitorSistema {
    public static void main(String[] args) {
        // Conectar ao orquestrador para monitoramento
        OrquestradorClient client = new OrquestradorClient();

        while (true) {
            // Obter status dos workers
            List<StatusWorker> workers = client.obterStatusWorkers();

            System.out.println("=== Status do Sistema ===");
            for (StatusWorker worker : workers) {
                System.out.printf("Worker %s: %s - Carga: %d/%d%n",
                    worker.getId(),
                    worker.getStatus(),
                    worker.getTarefasAtivas(),
                    worker.getCapacidadeMaxima());
            }

            // Aguardar 10 segundos
            Thread.sleep(10000);
        }
    }
}
```

### Exemplo 4: Simulação de Falhas

#### Teste de tolerância a falhas:

```bash
# Terminal 1 - Executar sistema normalmente
cd orchestrator && java -cp "target/classes;target/dependency/*" br.edu.ifba.Main

# Terminal 2 - Executar workers
cd worker && java -cp "target/classes;target/dependency/*" br.edu.ifba.worker.Worker1

# Terminal 3 - Submeter tarefas
cd client && java -cp "target/classes;target/dependency/*" br.edu.ifba.client.ClienteTeste

# Terminal 4 - Simular falha (matar worker)
# Pressionar Ctrl+C no Terminal 2 para simular falha do Worker1

# Observar logs do orquestrador detectando a falha e redistribuindo tarefas
```

### Exemplo 5: Snapshot Global

#### Executar captura de snapshot:

```java
public class ExemploSnapshot {
    public static void main(String[] args) {
        ChandyLamportSnapshot snapshot = new ChandyLamportSnapshot();

        // Iniciar snapshot global
        String snapshotId = snapshot.iniciarSnapshot();
        System.out.println("Snapshot iniciado: " + snapshotId);

        // Aguardar conclusão
        while (!snapshot.isSnapshotConcluido(snapshotId)) {
            Thread.sleep(1000);
        }

        // Obter resultado do snapshot
        EstadoGlobal estado = snapshot.obterEstadoGlobal(snapshotId);
        System.out.println("Estado capturado:");
        System.out.println("- Tarefas ativas: " + estado.getTarefasAtivas());
        System.out.println("- Workers ativos: " + estado.getWorkersAtivos());
        System.out.println("- Timestamp Lamport: " + estado.getTimestampLamport());
    }
}
```

### Exemplo 6: Configuração Personalizada

#### Arquivo de configuração (config.properties):

```properties
# Configurações do Orquestrador
orchestrator.port=8080
orchestrator.backup.port=8081
heartbeat.interval=5000
heartbeat.timeout=15000

# Configurações de Workers
worker.capacity.default=10
worker.timeout=30000

# Configurações de Rede
multicast.address=224.0.0.1
multicast.port=8082

# Configurações de Log
log.level=INFO
log.file=sistema-distribuido.log
```

#### Carregamento da configuração:

```java
public class SistemaComConfiguracao {
    public static void main(String[] args) {
        // Carregar configurações
        Properties config = new Properties();
        config.load(new FileInputStream("config.properties"));

        // Inicializar sistema com configurações personalizadas
        OrquestradorPrincipal orchestrator = new OrquestradorPrincipal(config);
        orchestrator.iniciar();
    }
}
```

---

## 🔧 Solução de Problemas

### Problemas Comuns

#### 1. Erro de Compilação

```bash
# Limpar cache do Maven
mvn clean

# Recompilar com debug
mvn clean install -X
```

#### 2. Porta em Uso

```bash
# Verificar processos usando a porta
netstat -ano | findstr :8080

# Matar processo (Windows)
taskkill /PID <PID> /F

# Matar processo (Linux/Mac)
kill -9 <PID>
```

#### 3. Problemas de Conectividade

```bash
# Testar conectividade
telnet localhost 8080

# Verificar firewall
ping localhost
```

#### 4. Problemas de Multicast

```bash
# Verificar suporte a multicast
ping 224.0.0.1

# Configurar interface de rede (Linux)
sudo route add -net 224.0.0.0 netmask 240.0.0.0 dev eth0
```

---

## 1. Introdução e Fundamentação Teórica

### 1.1 Introdução

Este projeto implementa um sistema distribuído para processamento de tarefas baseado em uma arquitetura de microserviços com orquestração centralizada. O sistema foi projetado para demonstrar conceitos fundamentais de sistemas distribuídos, incluindo coordenação de processos, tolerância a falhas, balanceamento de carga e sincronização distribuída.

### 1.2 Workflows em Sistemas Distribuídos

Um workflow em sistemas distribuídos representa uma sequência coordenada de tarefas que são executadas em diferentes nós da rede. No contexto deste projeto, implementamos um workflow de processamento de tarefas que envolve:

- **Submissão de Tarefas**: Clientes submetem tarefas ao orquestrador
- **Distribuição**: O orquestrador distribui tarefas para workers disponíveis
- **Execução**: Workers processam as tarefas de forma paralela
- **Coleta de Resultados**: O orquestrador coleta e consolida os resultados
- **Resposta**: Os resultados são retornados aos clientes

### 1.3 Balanceamento de Carga

O balanceamento de carga é crucial para garantir utilização eficiente dos recursos e evitar sobrecarga de nós específicos. Implementamos estratégias de:

- **Distribuição Round-Robin**: Tarefas são distribuídas sequencialmente entre workers
- **Monitoramento de Carga**: Acompanhamento da capacidade de processamento de cada worker
- **Redistribuição Dinâmica**: Capacidade de realocar tarefas em caso de falhas

### 1.4 Tolerância a Falhas

A tolerância a falhas é implementada através de múltiplos mecanismos:

- **Detecção de Falhas**: Sistema de heartbeat para monitorar a saúde dos nós
- **Recuperação Automática**: Mecanismos de failover para orquestradores backup
- **Replicação de Estado**: Sincronização de estado entre nós para garantir consistência
- **Timeouts e Retries**: Políticas de timeout e reenvio para operações críticas

---

## 2. Arquitetura do Sistema

### 2.1 Visão Geral da Arquitetura

O sistema adota uma arquitetura híbrida que combina elementos de orquestração centralizada com capacidades de auto-organização distribuída:

```
┌─────────────┐    ┌─────────────────────┐    ┌─────────────┐
│   Cliente   │◄──►│   Orquestrador      │◄──►│   Worker    │
│             │    │   Principal         │    │             │
└─────────────┘    └─────────────────────┘    └─────────────┘
                            │                         │
                            ▼                         │
                   ┌─────────────────────┐            │
                   │   Orquestrador      │            │
                   │   Backup            │            │
                   └─────────────────────┘            │
                            │                         │
                            ▼                         ▼
                   ┌─────────────────────┐    ┌─────────────┐
                   │   Sistema de        │    │   Worker    │
                   │   Snapshots         │    │   Adicional │
                   └─────────────────────┘    └─────────────┘
```

### 2.2 Componentes Principais

#### 2.2.1 Módulo Cliente

- **Responsabilidade**: Interface para submissão de tarefas e recebimento de resultados
- **Tecnologias**: gRPC para comunicação, Lamport Clock para ordenação
- **Funcionalidades**:
  - Submissão de tarefas via gRPC
  - Gerenciamento de usuários
  - Monitoramento de status de tarefas

#### 2.2.2 Módulo Orquestrador

- **Responsabilidade**: Coordenação central do sistema e distribuição de tarefas
- **Tecnologias**: gRPC, Multicast UDP, Chandy-Lamport Algorithm
- **Funcionalidades**:
  - Distribuição inteligente de tarefas
  - Monitoramento de workers
  - Implementação de snapshots globais
  - Sistema de heartbeat para failover

#### 2.2.3 Módulo Worker

- **Responsabilidade**: Execução de tarefas distribuídas
- **Tecnologias**: TCP para comunicação, Lamport Clock
- **Funcionalidades**:
  - Processamento paralelo de tarefas
  - Comunicação com orquestrador
  - Relatório de status e capacidade

### 2.3 Justificativas Arquiteturais

#### 2.3.1 Escolha da Arquitetura Híbrida

A arquitetura híbrida foi escolhida para combinar as vantagens de:

- **Orquestração Centralizada**: Simplifica a coordenação e garante consistência
- **Elementos Distribuídos**: Aumenta a tolerância a falhas e escalabilidade

#### 2.3.2 Separação em Módulos

A modularização permite:

- **Desenvolvimento Independente**: Equipes podem trabalhar em paralelo
- **Escalabilidade Horizontal**: Cada tipo de nó pode ser escalado independentemente
- **Manutenibilidade**: Facilita atualizações e correções

---

## 3. Protocolos, Algoritmos e Políticas

### 3.1 Protocolos de Comunicação

#### 3.1.1 gRPC (Google Remote Procedure Call)

**Escolha**: Protocolo principal para comunicação cliente-orquestrador
**Justificativa**:

- Performance superior ao REST para comunicação interna
- Suporte nativo a streaming bidirecional
- Tipagem forte com Protocol Buffers
- Suporte multiplataforma

#### 3.1.2 TCP (Transmission Control Protocol)

**Escolha**: Comunicação orquestrador-worker
**Justificativa**:

- Garantia de entrega e ordem das mensagens
- Controle de fluxo integrado
- Adequado para transferência de dados críticos

#### 3.1.3 UDP Multicast

**Escolha**: Comunicação para snapshots e sincronização
**Justificativa**:

- Eficiência para comunicação um-para-muitos
- Baixa latência para mensagens de coordenação
- Adequado para algoritmos de consenso distribuído

### 3.2 Algoritmos Implementados

#### 3.2.1 Algoritmo de Chandy-Lamport

**Propósito**: Captura de snapshots globais consistentes
**Implementação**:

```java
// Início do snapshot
public void iniciarSnapshot() {
    salvarEstadoLocal();
    enviarMarcadores();
    iniciarGravacaoCanais();
}

// Processamento de marcadores
public void processarMarcador(String origem) {
    if (!snapshotIniciado) {
        iniciarSnapshot();
    }
    pararGravacaoCanal(origem);
}
```

#### 3.2.2 Relógio de Lamport

**Propósito**: Ordenação causal de eventos distribuídos
**Implementação**:

```java
public synchronized long incrementarTempo() {
    return ++tempoLogico;
}

public synchronized void atualizarTempo(long tempoRecebido) {
    this.tempoLogico = Math.max(this.tempoLogico, tempoRecebido) + 1;
}
```

