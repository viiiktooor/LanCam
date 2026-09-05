# LanCam

LanCam transforma um celular Android em uma câmera IP local e inclui um cliente leve para Windows. O projeto é pensado como um quebra-galho local: sem servidor próprio, sem conta, sem assinatura e sem serviço pago.

## Android 1.2.0

- Câmera traseira ou frontal.
- Preview no aparelho.
- Stream MJPEG pela rede local.
- Resolução selecionável: 640×480, 1280×720 ou 1920×1080 (o aparelho usa o tamanho suportado mais próximo).
- FPS selecionável: 5, 10, 15, 20 ou 30 fps.
- Qualidade JPEG selecionável: 50%, 70%, 85% ou 95%.
- Flash/torch quando suportado.
- Espelhamento opcional da câmera frontal.
- Correção de orientação do stream.
- `/stream`, `/video`, `/shot.jpg` e `/api/status`.
- Verificação automática de atualização usando a release pública mais recente deste repositório.
- Quando há APK novo, o LanCam baixa o arquivo e abre o instalador do Android. O Android ainda exige confirmação do usuário e, no Android 8+, a permissão "Instalar apps desconhecidos" para o LanCam.

## Windows Client 0.2.0

O cliente 0.2 foi redesenhado para testes sem OBS e sem câmera virtual. Ele possui interface gráfica, conecta ao `/stream`, mostra preview, FPS recebido, taxa aproximada de rede, reconecta quando o stream cai e salva o último endereço usado.

Fluxo de teste atual:

`Android LanCam -> Wi-Fi/LAN -> LanCamClient.exe -> preview no PC`

Ele também verifica a release pública mais recente. Quando encontra um `LanCamClient-X.Y.Z.exe` mais novo, oferece baixar, substitui o executável atual depois de fechá-lo e reinicia.

## Atualizações e assinatura Android

O atualizador usa GitHub Releases, que funciona sem token porque este repositório é público. Para o Android aceitar uma versão nova por cima da anterior, todos os APKs de release precisam ser assinados para sempre com a mesma chave.

A chave privada **não deve ser adicionada ao repositório**. O workflow `Publish LanCam Release` espera dois GitHub Actions Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`

O workflow só publica uma release quando `release/version.txt` é alterado. Isso evita criar releases a cada commit de desenvolvimento.

## Build de desenvolvimento

- `Build Android APK` gera APK debug para testar compilação e funções.
- `Build Windows Client` gera o cliente Windows de teste.
- `Publish LanCam Release` gera os arquivos de atualização reais e exige a chave Android estável.

## Limites atuais

- O cliente 0.2 ainda não cria uma webcam virtual no Windows.
- Ainda não há áudio/microfone.
- Ainda não há USB/ADB.
- O executável Windows não possui assinatura de código comercial; isso pode causar avisos de reputação do Windows.

## Privacidade

O vídeo é servido diretamente pelo celular na rede local. O LanCam não envia o stream para servidor externo. O acesso ao GitHub é usado apenas para verificar e baixar novas versões publicadas.
