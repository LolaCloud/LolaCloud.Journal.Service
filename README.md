<p align="center">
  <img src="./docs/assets/logo.png" alt="Lola Cloud Logo" width="150" />
</p>

<h1 align="center">Lola Cloud • Journal</h1>

<p align="center">
  <strong>Hub Central de Observabilidade, Live Tail de Logs e Telemetria (Equivalente ao AWS CloudWatch)</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 25" />
  <img src="https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 4.1.1" />
  <img src="https://img.shields.io/badge/Server--Sent_Events-SSE-008080?style=flat-square" alt="SSE" />
  <img src="https://img.shields.io/badge/Docker_Daemon-Streaming-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Daemon" />
  <img src="https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Port-8087-blue?style=flat-square" alt="Port 8087" />
</p>

---

## 📌 Visão Geral

O **Lola Journal** é o microsserviço de **telemetria e observabilidade centralizada** da plataforma **Lola Cloud**. Ele unifica a coleta, visualização e armazenamento de eventos, logs de contêineres e métricas de consumo de recursos de todo o ecossistema.

Destaca-se pelo recurso de **Live Tail** em tempo real via **Server-Sent Events (SSE)**, que consome fluxos de saída padrão (`stdout`/`stderr`) diretamente do Docker Daemon e os transmite de forma reativa para o **Lola Console**.

---

## 📊 Principais Responsabilidades

1. **Gestão de Grupos e Fluxos de Log (Log Groups & Streams)**: Organização hierárquica de logs inspirada no CloudWatch Logs.
2. **Live Tail em Tempo Real (SSE)**:
   - Endpoint reativo `GET /api/v1/journal/logs/tail/{streamId}` com Spring `SseEmitter`.
   - Streaming não-bloqueante direto da Docker CLI/Daemon.
3. **Métricas de Infraestrutura**:
   - Monitoramento contínuo de consumo de CPU, memória RAM e uso de disco dos contêineres provisionados pelo Relate.
4. **Histórico e Auditoria**:
   - Registro de logs de auditoria de chamadas de API e operações críticas realizadas pelos operadores da nuvem.

---

## 📡 Principais Endpoints da API REST

| Método | Endpoint | Descrição |
| :--- | :--- | :--- |
| `GET` | `/api/v1/journal/groups` | Lista todos os grupos de log cadastrados |
| `POST` | `/api/v1/journal/groups` | Cria novo grupo de logs |
| `GET` | `/api/v1/journal/groups/{id}/streams` | Lista fluxos de logs de um grupo |
| `GET` | `/api/v1/journal/streams/{streamId}/events` | Consulta eventos históricos de log com paginação |
| `GET` | `/api/v1/journal/logs/tail/{streamId}` | Stream de logs ao vivo via Server-Sent Events (SSE) |
| `GET` | `/api/v1/journal/metrics` | Consulta métricas consolidadas de consumo de contêineres |

---

## 🛠️ Stack Tecnológica

- **Linguagem**: Java 25 (OpenJDK)
- **Framework**: Spring Boot 4.1.1
- **Streaming Reativo**: Spring WebMVC `SseEmitter` (Server-Sent Events)
- **Interação com Sistema**: Java `ProcessBuilder` (Docker CLI streaming)
- **Segurança**: Filtro de introspecção LCI (`LciAuthFilter`)
- **Banco de Dados**: PostgreSQL 17 (`jdbc:postgresql://localhost:5433/lola_journal`)

---

## 🚀 Como Executar

```bash
# Executar a aplicação
./mvnw spring-boot:run
```
Porta padrão: **`8087`**
