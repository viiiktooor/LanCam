# Roteiro de testes do LanCam

## Resultado mais recente

APK FPS v3 instalado e testado no S23 Android 16: instalação por cima, imagens frontal/traseira, ajuste de resolução/qualidade/FPS e pausa/retomada passaram nas verificações descritas no [relatório de FPS](OTIMIZACAO-FPS.md). A captura produz ~30 FPS em HD, mas a recepção pela rede permanece limitada nessa configuração; VGA/50% chegou a 29,26 FPS. Não confundir esses ensaios curtos com aprovação de todos os testes antigos abaixo.

Resultados iniciais de 05/09/2026. Use **Passou**, **Falhou**, **Não testado** ou **Não se aplica**, sempre com uma observação quando necessário.

## Preparação

1. Quando estiver disponível, abra o LanCam no celular e mantenha a tela ligada e o aplicativo em primeiro plano.
2. Conecte celular e PC à mesma rede local; o PC pode estar por cabo no mesmo roteador.
3. Anote o endereço que o celular mostra e confira a versão instalada.
4. Abra `C:\LanCam\LanCamClient-0.2.1.exe` no PC.

Celular/modelo: Samsung S23 LATAM, informado pelo usuário. Android do aparelho: não informado. LanCam: usuário informa o último APK, número ainda não confirmado. Rede usada: não informada.

## Retorno do EXE de teste

Usuário testou `C:\LanCam\testes\LanCamClient-0.2.1-teste.exe`: conectar e desconectar pelo botão funciona. No S23, com PC por cabo e alvo 30 FPS, HD/70% recebe até 12 FPS; 640×480/50% recebe cerca de 20 FPS e reduz bastante o atraso. Duração não informada. Isso não valida a reconexão automática após queda do Wi-Fi (T11).

## Comparação do APK de teste FPS

Build local debug e lint passaram; seis testes JVM passaram com `gradle verifyFramePipeline`. Build release assinado, lintRelease e testes passaram no GitHub. Certificado corresponde ao APK oficial 1.2.1; assinatura e hash do download verificados localmente. APK: `C:\LanCam\testes\android-fps\LanCam-Android-1.2.1-fps-teste.apk`. Não houve instalação nem medição real com o novo código no S23.

1. Instalar o APK assinado por cima do LanCam atual, sem desinstalar. Ele mantém 1.2.1/versionCode 4; a nova linha de FPS no celular identifica a alteração.
2. Usar o mesmo EXE de teste, mesmo Wi-Fi/local e iluminação. Testar HD/70%/30 FPS por 1 minuto, depois 640×480/50%/30 FPS por 1 minuto.
3. Anotar no celular os números de “câmera”, “vídeo” e “ms/quadro”; no PC, o FPS recebido e o atraso percebido. “Vídeo” é JPEG pronto, não confirmação de entrega pela rede.
4. Alternar 5 → 30 FPS, trocar frontal/traseira, testar espelho e mudar resolução. Conferir imagem e resposta dos controles.
5. Sair para a tela inicial e voltar; fechar e reabrir. Confirmar que não reaparece imagem da câmera anterior. Testar 5 minutos em HD se a comparação inicial passar.

| Configuração | APK anterior | APK FPS teste |
| --- | --- | --- |
| HD / 70% / alvo 30 | ~12 FPS no PC | Não testado |
| 640×480 / 50% / alvo 30 | ~20 FPS no PC, menos atraso | Não testado |

Caso a instalação reclame de assinatura, registrar o erro e manter o app atual. Não usar o APK debug local para substituir o oficial.

## Primeiro teste: imagem no PC

| ID | O que fazer | Resultado esperado | Situação |
| --- | --- | --- | --- |
| T01 | Abrir o cliente Windows 0.2.1 | Janela abre normalmente | Passou — confirmado pelo usuário em 05/09/2026 |
| T02 | Abrir o Android e permitir o uso da câmera, se solicitado | Preview local aparece e endereço é exibido | Não testado |
| T03 | No navegador do PC, abrir o endereço mostrado no celular, incluindo a porta 4747 | Página do LanCam com imagem em movimento | Não testado |
| T04 | No cliente Windows, informar o mesmo endereço e clicar em Conectar | Preview em movimento e indicadores de recepção | Não testado |

Se T03 falhar, anote a mensagem e confirme o endereço e a rede. Se T03 passar e T04 falhar, registre essa diferença: ela ajuda a investigar o cliente Windows.

## Depois que a imagem funcionar

| ID | O que fazer | Resultado esperado | Situação |
| --- | --- | --- | --- |
| T05 | Manter a transmissão por 5 minutos e mover a mão diante da câmera | Imagem continua atualizando; anotar travamentos e atraso percebido | Não testado |
| T06 | Usar Trocar câmera no celular | Preview do celular e do PC passam à outra câmera | Não testado |
| T07 | Na frontal, alternar Espelho | Imagem transmitida muda de espelhamento | Não testado |
| T08 | Alternar uma resolução, FPS e qualidade JPEG por vez | Vídeo volta após ajuste; resolução pode ser a mais próxima suportada; FPS recebido pode ficar abaixo do alvo | Não testado |
| T09 | Na traseira, alternar Flash | Luz liga/desliga quando suportado; controle indisponível se não suportado | Não testado |
| T10 | Girar o celular e observar os dois previews | Registrar se a orientação fica correta ou o que aparece errado | Não testado |
| T11 | Desligar o Wi-Fi do celular por alguns segundos e religar | Cliente informa interrupção e tenta recuperar a transmissão; anotar se o endereço mudou | Não testado |
| T12 | Fechar e reabrir o cliente Windows | Último endereço continua preenchido | Não testado |
| T13 | Clicar em Atualizações no Windows, com internet | Informa situação ou oferece versão disponível sem travar | Não testado |

## Atualizações: quando houver uma versão apropriada

| ID | O que fazer | Resultado esperado | Situação |
| --- | --- | --- | --- |
| T14 | Atualizar Android oficial para uma versão oficial posterior, sem desinstalar | Instalação aceita por cima e aplicativo abre | Não testado — depende de versões e assinatura compatíveis |
| T15 | Aceitar uma atualização posterior pelo cliente Windows | Baixa, substitui e reabre na versão nova | Não testado — depende de atualização disponível |

A consulta de atualização (T13) não valida a instalação completa (T15). Não desinstalar o Android para contornar uma falha de assinatura; registrar o erro.

## Como relatar um resultado

Copie e preencha na conversa Testes e bugs:

```text
Teste: T04
Versão Windows:
Versão LanCam Android e modelo do celular:
O que fiz:
O que aconteceu:
Mensagem de erro ou print, se houver:
Resultado: Passou / Falhou / Não testado / Não se aplica
```

## Registro de falhas

Nenhuma nova falha registrada. Para cada falha, anotar ID do teste, data, versões, passos, resultado observado e estado da investigação. Após corrigir, repetir o teste que falhou antes de marcar como resolvido.
Medição S23 SM-S911B, APK FPS teste: frontal/espelho, HD/70%/30 FPS. 15 consultas de status: captura média 27,92 FPS (25,8–29,5), JPEG preparado 19,89 FPS (18–22), conversão 49,33 ms/quadro (45,3–52,2). Evidência: C:\LanCam\testes\android-fps\s23-hd-frontal-status.json. Não mede FPS recebido no Windows ou latência; conversão continua limitando a aproximadamente 20 FPS. Sem alterações funcionais ou instalação nesta medição.
