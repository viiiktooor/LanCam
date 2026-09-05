# Estado final desta etapa do LanCam

## Publicação

Release de encerramento **1.2.2** preparada, aguardando conclusão do workflow de publicação. Inclui Android **1.2.2 / versionCode 5** e Windows Client **0.2.2**.

- Repositório: https://github.com/viiiktooor/LanCam
- Release: https://github.com/viiiktooor/LanCam/releases/tag/v1.2.2
- Código, testes, documentação e evidências numéricas estão na main.
- O usuário autorizou publicar esta release. Novas alterações/publicações futuras dependem de nova solicitação.

## O que ficou no código

- Android: processamento JPEG fora da interface, um único quadro pendente, buffers reutilizados, transformação NV21 e uma única compressão JPEG.
- Ajuste da faixa de FPS da câmera e limitador com tolerância à variação de entrega de quadros.
- Proteção contra resultados da câmera anterior após troca/pausa; limpeza da última imagem ao pausar.
- Windows: quadro mais recente na interface, isolamento entre conexões e leitura de rede melhorada.
- O experimento de WifiLock foi descartado por não apresentar ganho consistente.
- README apresenta o app e declara explicitamente que é um projeto completamente vibe codado por pessoa leiga com IA.

## Validação e resultados

- Seis testes Windows passaram; testes JVM de cadência/fila e transformação NV21 passaram; compilação e lint Android passaram na versão funcional de teste.
- S23 SM-S911B / Android 16: APK de teste com assinatura oficial instalado e testado por cima.
- Android produz ~30 FPS em HD/70%. Um receptor no PC recebeu 21–22 FPS nessa configuração pela rede; VGA/50% recebeu ~29 FPS. Diagnóstico temporário por cabo recebeu ~29,7 FPS em HD.
- Instalação por cima, frontal/traseira, espelho, resolução, qualidade, 5 → 30 FPS e pausa/retomada verificados. Ensaio de rede de 60 segundos sem erro de JPEG; não equivale a teste prolongado de horas.
- A release executa novamente testes Windows, testes JVM e lintRelease, além de verificar o certificado contra o APK oficial 1.2.1.

Relatório e limites: [OTIMIZACAO-FPS.md](OTIMIZACAO-FPS.md). Evidências: [evidence/](evidence/). Utilitários: [tools/diagnostics](../tools/diagnostics/README.md). Roteiro histórico: [TESTES.md](TESTES.md).

## Pendências conhecidas

- 30 FPS em HD pela rede atual não estão garantidos; latência ponta a ponta não foi medida.
- Atualizador Windows ainda precisa de recuperação/rollback e teste de falhas.
- Servidor Android ainda precisa limitar conexões, aplicar timeout de cabeçalhos e fechar sockets ativos ao encerrar.
- Atualizador Android ainda precisa de proteção adicional ao terminar download após saída da tela.
- Não há áudio, webcam virtual nem conexão USB na interface do produto.

## Continuidade

Preservar a mesma assinatura Android. Não colocar chaves/senhas no repositório. Não desinstalar para contornar falha de assinatura. Alterar release/version.txt e enviar para main aciona a publicação. Fotos locais de diagnóstico não foram enviadas ao GitHub.

A implementação final funcional corresponde à versão nv21-v3, acrescida dos números oficiais de versão. As branches de testes permanecem como histórico; a main reúne o estado entregue.