# PediuPatrão

Sistema de pedidos para pizzaria, evoluído a partir do projeto base fornecido na disciplina. Esta versão adiciona controle de acesso por perfil, fluxo controlado de pedidos, descontos e cancelamentos exclusivos do gerente e auditoria persistente das alterações de pedidos e clientes.

Tecnologias: Java 21, Spring Boot 3.4, Spring Security 6, Spring Data MongoDB, Thymeleaf, Lombok, Docker Compose.

| | |
|---|---|
| Autor | Leonardo Valdir Silva |
| RGM | 11232100617 |
| Turma | 8B, Engenharia de Software, Universidade de Mogi das Cruzes |



## Demonstração em vídeo
 
Vídeo com a execução das funcionalidades: [assistir no YouTube](https://youtu.be/3HIMAxmc-Io)
 
O vídeo apresenta: autenticação dos três perfis, bloqueio de operações não autorizadas (inclusive pela API), entrada, transições e saída de pedidos, cancelamento e desconto pelo GERENTE, alteração de dados de cliente e consulta aos registros de auditoria resultantes.
 
## Sumário
 
- [Demonstração em vídeo](#demonstração-em-vídeo)
- [Como executar](#como-executar)
- [Usuários de demonstração](#usuários-de-demonstração)
- [Requisitos implementados](#requisitos-implementados)
- [Matriz de permissões](#matriz-de-permissões)
- [API REST](#api-rest)
- [Testes](#testes)
- [Principais alterações em relação ao projeto base](#principais-alterações-em-relação-ao-projeto-base)
- [Estrutura do projeto](#estrutura-do-projeto)
## Como executar
 
### Pré-requisitos
 
- Docker Desktop (ou Docker Engine com o plugin Compose)
- Git
Não é necessário ter Java, Maven ou MongoDB instalados: o build e a execução acontecem nos containers.
 
### Passo a passo
 
1. Clone o repositório e entre na pasta:
```bash
   git clone https://github.com/leo-vasi/pizzaria_pediupatrao.git
   cd pizzaria_pediupatrao
```
 
2. Crie o arquivo `.env` a partir do modelo e, se quiser, altere as senhas:
```bash
   cp .env.example .env
```
 
3. Suba os containers:
```bash
   docker compose up --build
```
 
   O primeiro build leva alguns minutos, pois baixa as dependências do Maven. A aplicação está pronta quando o log exibir `Started PizzariaPedidosApplication`.
 
4. Acesse:
   | Serviço | Endereço | Observação |
   |---|---|---|
   | Aplicação | http://localhost:8080 | Login com os usuários abaixo |
   | mongo-express | http://localhost:8081 | Login definido no `.env` (`MONGO_EXPRESS_USER` e `MONGO_EXPRESS_PASSWORD`) |
5. Para parar: `Ctrl+C` e depois `docker compose down`. Para apagar também os dados do banco: `docker compose down -v`.
### Serviços do Docker Compose
 
| Serviço | Imagem | Porta | Função |
|---|---|---|---|
| `mongo` | mongo:7 | 27017 | Banco de dados, com volume persistente e healthcheck |
| `app` | build local (Temurin 21) | 8080 | Aplicação Spring Boot, iniciada somente após o MongoDB responder |
| `mongo-express` | mongo-express:1 | 8081 | Interface web para consultar as coleções, inclusive `audit_logs` |
 
### Variáveis de ambiente
 
| Variável | Uso |
|---|---|
| `MONGO_ROOT_USER` / `MONGO_ROOT_PASSWORD` | Credenciais do MongoDB, usadas pelo banco, pela aplicação e pelo mongo-express |
| `MONGO_DATABASE` | Nome do banco usado pela aplicação (padrão: `pizzaria`) |
| `MONGO_EXPRESS_USER` / `MONGO_EXPRESS_PASSWORD` | Login da interface do mongo-express |
| `LOG_LEVEL` | Nível de log da aplicação (padrão: `INFO`) |
 
A aplicação lê a conexão de `MONGODB_URI` e `MONGODB_DATABASE`, que o Compose monta a partir das variáveis acima. Fora do Docker, o padrão é `mongodb://localhost:27017`. O arquivo `.env` não é versionado.
 
## Usuários de demonstração
 
Criados automaticamente na inicialização, caso ainda não existam (`DataInitializer`):
 
| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `teste123` | ADMIN |
| `gerente01` | `gerente123` | GERENTE |
| `atendente01` | `atendente123` | ATENDENTE |
 
Na primeira inicialização também é cadastrado um cardápio com 5 produtos, para que pedidos possam ser registrados imediatamente.
 
## Requisitos implementados
 
### RF01: controle de usuários e permissões
 
- Perfis definidos no enum `Role` (`ADMIN`, `GERENTE`, `ATENDENTE`) e lidos do banco pelo `UsuarioDetailsService`. Usuário sem perfil não consegue autenticar.
- Autorização aplicada no backend em duas camadas:
  1. regras de URL no `SecurityConfig`, cobrindo páginas e API;
  2. `@PreAuthorize` nos métodos dos services, protegendo a operação de negócio independentemente de qual controller a chama.
- Aplicar desconto e cancelar pedido são operações exclusivas do GERENTE, inclusive em relação ao ADMIN.
- Os menus são ocultados conforme o perfil apenas por usabilidade: acessar a URL ou chamar a API diretamente resulta em `403 Forbidden`.
- Proteção contra atribuição indevida de privilégios:
  - a API `/api/usuarios`, que antes era pública e permitia criar um ADMIN sem login, agora é restrita ao ADMIN;
  - o usuário não pode alterar o próprio perfil nem excluir a si mesmo;
  - não é possível remover o perfil ou excluir o último ADMIN;
  - na criação, o `id` enviado é descartado, impedindo sobrescrever um usuário existente;
  - o username é único (índice no MongoDB) e a senha nunca é devolvida nas respostas da API.
- CSRF reativado para todas as operações que alteram dados. Os formulários Thymeleaf recebem o token automaticamente, e as chamadas `fetch()` usam a função `csrfHeaders()` definida no layout.
- Requisições não autenticadas à API recebem `401`, em vez de um redirecionamento para a tela de login.
### RF02: evolução do cadastro de pedidos
 
- A entidade `Pedido` registra itens (`ItemPedido`: produto, quantidade, preço unitário e subtotal), subtotal, percentual e valor do desconto, valor total, responsável e data/hora de entrada.
- O nome e o preço do produto são copiados no momento do pedido, para que alterações futuras no cardápio não mudem o histórico.
- O valor final é calculado no servidor (`PedidoService.recalcular`). A requisição de criação (`CriarPedidoRequest`) aceita apenas cliente, produto e quantidade: um campo `total` ou `preco` enviado é ignorado.
- Validações: cliente existente, pelo menos um item, quantidade entre 1 e 100, produto existente, ativo e com preço válido.
### RF03: controle de entrada, saída e transições
 
Fluxo implementado no enum `StatusPedido`:
 
```
RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA ou RETIRADO -> FINALIZADO
```
 
- Entrada (`RECEBIDO`): grava data/hora e usuário responsável.
- Saída (`SAIU_PARA_ENTREGA` ou `RETIRADO`): grava obrigatoriamente data/hora e responsável.
- Conclusão (`FINALIZADO`): grava data/hora e responsável.
- Transições fora do fluxo (por exemplo, finalizar um pedido em preparação) são recusadas com mensagem indicando os próximos status permitidos.
- `CANCELADO` só é alcançado pela operação de cancelamento. Depois do cancelamento, nenhuma alteração de status é aceita.
- Todo o andamento fica registrado em `historicoStatus` e é exibido na tela de detalhe do pedido.
### RF04: cancelamentos e descontos
 
- Exclusivos do GERENTE (URL, `@PreAuthorize` e tela).
- Cancelamento: exige justificativa (mínimo de 5 caracteres), só é permitido antes da saída para entrega ou retirada, preserva itens e valores e bloqueia alterações posteriores.
- Desconto: percentual maior que 0 e no máximo 20%, permitido somente antes da saída. Um novo percentual substitui o anterior (não acumula), garantindo que o desconto nunca ultrapasse 20% do valor do pedido. O total é recalculado no servidor.
- As duas operações são auditadas com usuário, perfil, data/hora e valores anteriores e novos. O cancelamento registra também a justificativa.
### RF05: auditoria
 
- Entidade `RegistroAuditoria`, coleção `audit_logs`, com repositório (`RegistroAuditoriaRepository`) e serviço (`AuditoriaService`) próprios.
- Cada evento registra: usuário autenticado e perfil, data e hora (fuso America/Sao_Paulo), tipo de operação, entidade afetada e identificador, campos modificados com valores anteriores e novos, e justificativa quando exigida.
- Operações auditadas:
  | Operação | Quando |
  |---|---|
  | `PEDIDO_CRIADO` | Inclusão de pedido |
  | `PEDIDO_STATUS_ALTERADO` | Cada mudança de status, incluindo dados de saída |
  | `PEDIDO_DESCONTO_APLICADO` | Aplicação de desconto |
  | `PEDIDO_CANCELADO` | Cancelamento, com justificativa |
  | `CLIENTE_CRIADO` | Inclusão de cliente |
  | `CLIENTE_ATUALIZADO` | Alteração de cliente, somente com os campos que mudaram (endereço, telefone etc.) |
  | `CLIENTE_EXCLUIDO` | Exclusão de cliente, com os últimos dados conhecidos |
- Consulta exclusiva para ADMIN e GERENTE, pela tela **Auditoria** (com filtro por entidade) ou por `GET /api/auditoria`.
- Integridade:
  - `RegistroAuditoriaRepository` estende `Repository`, e não `MongoRepository`, portanto não existe método de atualização ou exclusão;
  - `RegistroAuditoria` não possui setters e é criado uma única vez, pelo builder;
  - a API de auditoria mapeia somente `GET`; outros métodos respondem `405` (requisições sem token CSRF são recusadas antes, com `403`).
- Proteção de dados:
  - campos `password`, `senha`, `token` e `secret` são descartados antes da gravação;
  - telefones são exibidos mascarados na consulta, mantendo apenas os 4 últimos dígitos.
## Matriz de permissões
 
| Funcionalidade | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Administrar usuários e perfis | Sim | Não | Não |
| Consultar pedidos | Sim | Sim | Sim |
| Registrar pedidos e avançar status | Não | Sim | Sim |
| Aplicar desconto | Não | Sim | Não |
| Cancelar pedido | Não | Sim | Não |
| Consultar clientes | Não | Sim | Sim |
| Cadastrar e alterar clientes | Não | Não | Sim |
| Excluir clientes | Não | Sim | Não |
| Manter produtos (cardápio) | Não | Sim | Não |
| Visualizar auditoria | Sim | Sim | Não |
 
## API REST
 
Todas as rotas exigem autenticação (sessão do login). Requisições `POST`, `PUT` e `DELETE` exigem o token CSRF no cabeçalho `X-CSRF-TOKEN`; no navegador, use `csrfHeaders()`.
 
| Método | Rota | Perfis | Descrição |
|---|---|---|---|
| GET | `/api/pedidos` | ADMIN, GERENTE, ATENDENTE | Lista pedidos |
| GET | `/api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE | Detalha um pedido |
| POST | `/api/pedidos` | GERENTE, ATENDENTE | Cria pedido: `{"clienteId": "...", "itens": [{"produtoId": "...", "quantidade": 2}]}` |
| PUT | `/api/pedidos/{id}/status?status=EM_PREPARACAO` | GERENTE, ATENDENTE | Avança o status |
| POST | `/api/pedidos/{id}/desconto` | GERENTE | `{"percentual": 10}` |
| POST | `/api/pedidos/{id}/cancelar` | GERENTE | `{"justificativa": "..."}` |
| GET | `/api/clientes` | GERENTE, ATENDENTE | Lista clientes |
| POST | `/api/clientes` | ATENDENTE | Cria cliente |
| PUT | `/api/clientes/{id}` | ATENDENTE | Altera cliente |
| DELETE | `/api/clientes/{id}` | GERENTE | Exclui cliente |
| GET | `/api/auditoria?entityType=Pedido&entityId=...` | ADMIN, GERENTE | Consulta a auditoria |
| GET, POST, PUT, DELETE | `/api/usuarios` | ADMIN | Administração de usuários |
 
Exemplo de teste de bloqueio pelo console do navegador (F12), logado como ATENDENTE ou ADMIN:
 
```js
fetch('/api/pedidos/ID_DO_PEDIDO/desconto', {
  method: 'POST',
  headers: csrfHeaders({'Content-Type': 'application/json'}),
  body: JSON.stringify({percentual: 10})
}).then(r => console.log(r.status)); // 403
```
 
## Testes
 
Os testes não dependem de banco de dados nem dos containers do Compose. Para executá-los com o mesmo JDK do container, a partir da raiz do projeto:
 
```bash
docker run --rm -v "$PWD":/app -v pediupatrao-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -B test
```
 
- O volume `pediupatrao-m2` guarda as dependências do Maven, então apenas a primeira execução é demorada.
- No Git Bash (Windows), adicione `MSYS_NO_PATHCONV=1` antes do comando, para evitar que os caminhos sejam convertidos: `MSYS_NO_PATHCONV=1 docker run ...`.
- No PowerShell, troque `"$PWD"` por `"${PWD}"`.
- Com Java 21 instalado localmente, também é possível usar `./mvnw test`.
Resultado esperado: `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`.
 
| Classe | O que verifica |
|---|---|
| `StatusPedidoTest` | Transições válidas e inválidas, estados finais, janela de cancelamento e desconto |
| `PedidoServiceTest` | Cálculo do total com preços do banco, transição inválida, registro de saída, bloqueio após cancelamento, limite de 20%, justificativa obrigatória e auditoria das operações |
| `AuditoriaServiceTest` | Diferença entre valores anteriores e novos, usuário/perfil/data no evento, descarte de credenciais e máscara de telefone |
| `PedidoApiAutorizacaoTest` | API protegida no backend: anônimo recebe 401, ATENDENTE e ADMIN recebem 403 em desconto e cancelamento, GERENTE recebe 200, e requisição sem CSRF é recusada |
 
## Principais alterações em relação ao projeto base
 
| Área | Projeto base | Esta versão |
|---|---|---|
| Perfis | Todos autenticados recebiam `USER` | `ADMIN`, `GERENTE` e `ATENDENTE` lidos do banco |
| `/api/usuarios` | Pública, permitia criar ADMIN sem login | Restrita ao ADMIN, com proteção contra autoelevação |
| CSRF | Desativado em todo o sistema | Ativo em formulários e API |
| Pedido | `tipoPizza`, `sabor`, `quantidade` e status em texto livre | Itens, valores calculados no servidor, entrada, saída e histórico |
| Status | Qualquer valor era aceito | Fluxo validado pelo enum `StatusPedido` |
| Desconto e cancelamento | Inexistentes | Exclusivos do GERENTE, com regras e auditoria |
| Auditoria | Inexistente | Coleção `audit_logs` somente de inclusão |
| Produtos | Tela não renderizava e o formulário enviava dados para uma API JSON | Tela corrigida, cadastro e exclusão pelo GERENTE |
| Configuração | Credenciais do MongoDB Atlas fixas no `application.properties` | Variáveis de ambiente e Docker Compose |
| Testes | Nenhum | Testes unitários e de autorização |
 
Convenção adotada: o código novo segue o padrão de nomenclatura em português do projeto base (por exemplo, `StatusPedido`, `ItemPedido`, `AuditoriaService`), para manter a consistência com as classes existentes.
 
## Estrutura do projeto
 
```
src/main/java/com/umc/pediupatrao
├── config        SecurityConfig, DataInitializer, MongoInitConfig
├── controller    Páginas Thymeleaf, API REST e tratamento de erros
├── dto           Entradas da API de pedidos (sem campos de valor)
├── entity        Entidades e enums (Pedido, StatusPedido, RegistroAuditoria, Role...)
├── repository    Repositórios MongoDB
├── security      UsuarioLogado (usuário autenticado e perfil)
└── service       Regras de negócio e autorização (@PreAuthorize)
```
 
