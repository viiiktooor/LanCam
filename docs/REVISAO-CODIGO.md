# Revisão do código — 05/09/2026

Atualização: a etapa v3 removeu a dupla compressão JPEG, ajustou a tolerância do limitador e foi instalada/testada no S23. Resultados, commits e limitações em [OTIMIZACAO-FPS.md](OTIMIZACAO-FPS.md). A descrição abaixo da dupla conversão mantida refere-se à primeira etapa, anterior à v3.

## Continuação Android — otimização de FPS

Implementada na conversa Android em 05/09/2026, após retorno do S23 na Principal (HD/70%: ~12 FPS; VGA/50%: ~20 FPS; alvo 30; PC por cabo).

- Corrigido o seletor de FPS: agora reabre/configura a câmera com a faixa solicitada. Antes alterava apenas o limite de processamento, mantendo a faixa escolhida na abertura.
- Limitador usa relógio monotônico em nanossegundos, mantém a cadência e tolera jitter de até 2 ms. Simulação de 300 callbacks a 30 FPS com variação de ±1,5 ms aceita os 300; alvos 5/10/15/20/30 mantêm seus limites; pausa longa não causa rajada.
- Compressão/transformação JPEG movida para trabalhador único. Um quadro em processamento e no máximo um pendente; entrada mais recente substitui o pendente. Três buffers NV21 reutilizados; devolução à câmera ocorre na thread da interface.
- Troca/pausa invalida a geração, descarta pendência e limpa `latestFrame`. Resultados de uma câmera anterior não podem ser publicados nem devolvidos à câmera nova. Essa proteção foi revisada no código; ciclo completo ainda precisa de aparelho.
- Seis testes JVM passaram, incluindo substituição de 999 entradas enquanto o trabalhador está ocupado, descarte ao limpar/fechar e rejeição após fechamento.
- `verifyFramePipeline assembleDebug lintDebug` passou localmente com Java 17/Gradle 8.9/SDK 35. Lint sem erros, com 21 avisos de recursos de texto, manifesto e dependência existente. `verifyFramePipeline lintRelease assembleRelease` passou no GitHub pelo workflow isolado `android-fps-test.yml` (run 33975322275). Certificado corresponde ao APK oficial 1.2.1; assinatura e hash do APK baixado verificados localmente.
- A dupla conversão JPEG/bitmap foi mantida nesta etapa. CPU, orientação, espelho, FPS efetivo, temperatura e latência precisam ser medidos no S23 antes de afirmar melhora. Não houve teste em aparelho conectado.
- `captureFps` mede callbacks de câmera; `encodedFps` mede JPEGs concluídos/publicados; `encodeMs` é a média de conversão; `replacedFrames` conta quadros pendentes substituídos na janela de aproximadamente 1 segundo. Esses indicadores não medem recepção no PC nem latência de rede.

O texto abaixo registra a revisão anterior à implementação; pendências sobre processamento na interface e limpeza de `latestFrame` foram tratadas no código, com teste prático ainda pendente.

## Avaliação

O projeto tem uma base compacta e compreensível para um protótipo pessoal: transmissão local, configuração simples e atualização por Releases. O modo de conversa usado para criá-lo não é evidência de qualidade; o que importa é revisar comportamentos e validar com testes.

Foram lidos o cliente Windows, a captura e o servidor Android, o atualizador Android, o manifesto e os fluxos de build/publicação. Esta revisão não equivale a uma auditoria completa nem a um teste em aparelho.

## Corrigido no código local

| Problema | Mudança | Evidência |
| --- | --- | --- |
| Um callback por quadro podia acumular imagens quando a interface demorasse a renderizar | Apenas o quadro mais recente aguarda exibição | Teste envia 1.000 quadros e recebe apenas o último na interface |
| Uma conexão antiga podia alterar status ou imagem após desconectar/reconectar | Eventos e quadros são vinculados ao trabalhador da conexão atual | Teste descarta status e imagem da conexão anterior |
| Trabalhadores de rede chamavam o agendador Tk diretamente | Fila de eventos drenada pela linha da interface; encerramento descarta novas notificações | Testes de notificação em segundo plano e encerramento |
| Um JPEG iniciado e nunca encerrado podia crescer sem limite | Limite de 16 MiB por quadro; conexão inválida entra no tratamento de erro existente | Teste de quadro incompleto acima do limite |
| Leitura de 8 KiB podia esperar preencher o bloco, acrescentando espera a imagens pequenas | Uso de `read1` para consumir os bytes disponíveis da resposta HTTP | Separação de marcadores e múltiplos quadros validada com resposta simulada; latência real não medida |
| Estatística de bytes podia carregar uma amostra da conexão anterior | Contador reiniciado ao abrir cada leitura de stream | Conferência do código; não houve medição de rede |
| Artefato de build Windows ainda era rotulado como 0.2.0 | Nome sem versão fixa | Conferência do workflow |
| Builds não executavam os testes do cliente | Etapa de testes antes do empacotamento, inclusive no fluxo de release | Comando executado localmente; workflows remotos ainda não executados |

## Pendências por prioridade

### Alta — desempenho Android precisa ser medido

`MainActivity.onPreviewFrame` comprime JPEG e, para rotacionar/espelhar, decodifica um bitmap e comprime novamente. A câmera é aberta pela interface, cujo fluxo recebe os callbacks. Esse trabalho pode tornar os controles lentos e elevar uso de CPU, especialmente em resoluções altas. Não foi medido neste aparelho.

Próxima abordagem: medir primeiro e mover a transformação para um trabalhador com no máximo um quadro pendente, cuidando do ciclo de vida e da reutilização dos buffers da câmera. Não foi feita essa alteração sem uma compilação Android e testes adequados.

### Alta — atualização Windows precisa de teste de recuperação

`install_self_update` usa cópia sobre o executável atual, sem backup/rollback. Se a cópia falhar após modificar o destino, não há recuperação implementada. O arquivo de script tem nome fixo e não há bloqueio durante todo o download, permitindo concorrência entre tentativas. A próxima mudança deve tornar download e substituição exclusivos, preservar uma cópia anterior e testar falhas e caminhos com caracteres especiais. Não houve substituição de executável nesta revisão.

### Média — encerramento do servidor Android

`MjpegServer` cria uma thread por conexão, lê cabeçalhos sem timeout e fecha somente o socket de escuta no encerramento. Uma conexão parada pode reter recursos, e sockets já aceitos não são fechados explicitamente por `shutdown`. Próxima correção: limitar conexões, definir tempo de leitura e rastrear/fechar sockets ativos, com teste de reconexão e saída do app.

### Média — imagem antiga após pausar a câmera

`releaseCamera` não limpa `latestFrame`. A foto em `/shot.jpg` pode continuar expondo a última imagem quando a câmera já foi liberada. O stream não envia novos quadros e o cliente acaba entrando em timeout. Definir e testar um estado claro de câmera pausada.

### Média — ciclo de vida do download Android

O botão “Abrir LanCam agora” continua acessível durante o download. O término do download chama `requestInstall` sem verificar se a tela já foi encerrada; erros também podem tentar abrir um diálogo nessa tela. Guardar a atualização pendente e verificar o ciclo de vida antes de abrir instalador/diálogo são os próximos ajustes.

## Validação e limites

- Seis testes automatizados do Windows passaram com Python 3.12 e Pillow 11.3.0.
- Não houve teste visual do cliente alterado, transmissão de celular, medição de CPU/FPS, compilação Android ou geração de novo EXE/APK.
- O Java e o Gradle não foram encontrados no PATH consultado. O ambiente Android local ainda precisa ser preparado/verificado.
- A abertura do EXE 0.2.1 confirmada pelo usuário refere-se ao binário anterior, não a estas alterações.
- Versões e assinatura não foram alteradas. Arquivos ainda locais, sem commit/push ou release.

## Próximo teste

Executar o cliente alterado com a câmera real, repetir T04/T05/T11 e alternar conectar/desconectar rapidamente. Medir antes de afirmar redução de CPU ou latência. Depois, tratar o processamento Android e a recuperação do atualizador em mudanças separadas.
