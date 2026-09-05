# LanCam

LanCam transforma um celular Android em uma câmera IP local e inclui um cliente experimental para Windows.

## Android 1.1.0

- Câmera traseira ou frontal.
- Preview no aparelho.
- Stream MJPEG pela rede local.
- Resolução selecionável: 640×480, 1280×720 ou 1920×1080. O app escolhe o tamanho suportado mais próximo pela câmera.
- FPS selecionável: 5, 10, 15, 20 ou 30 fps.
- Qualidade JPEG selecionável: 50%, 70%, 85% ou 95%.
- Flash/torch quando a câmera suporta.
- Espelhamento opcional da câmera frontal.
- Correção de orientação do stream.
- Endpoint JSON de status para integração com clientes.
- CORS habilitado nos endpoints HTTP para facilitar integração local.

## Windows Client 0.1.0

O cliente em `desktop/LanCamClient.py` recebe o MJPEG do Android, mostra um preview e envia os frames para uma câmera virtual disponível no Windows via `pyvirtualcam`.

Na implementação atual, o backend mais simples no Windows é a **OBS Virtual Camera**. Instalar o OBS Studio registra esse dispositivo. O cliente então envia o vídeo recebido do LanCam para ele; em Discord, Meet, Zoom ou aplicativos semelhantes, selecione **OBS Virtual Camera** como câmera.

Fluxo atual:

`Android LanCam -> Wi-Fi/LAN -> LanCamClient.exe -> OBS Virtual Camera -> aplicativo de vídeo`

O cliente reconecta automaticamente se o stream cair e aceita um IP simples, por exemplo `192.168.0.25`, ou uma URL completa.

## Endereços Android

Com celular e PC na mesma rede Wi‑Fi, abra o endereço mostrado no app, por exemplo:

`http://192.168.0.25:4747/`

Endpoints:

- `/stream` ou `/video` — MJPEG contínuo.
- `/shot.jpg` — JPEG atual.
- `/api/status` — estado atual em JSON.

## Compilar Android

O projeto usa Android Gradle Plugin 8.7.3, `compileSdk 35` e Java 17 no ambiente de CI.

No GitHub, o workflow **Build Android APK** compila automaticamente um APK de debug e publica o artefato `LanCam-debug-apk`.

## Compilar Windows

O workflow **Build Windows Client** usa Python 3.12, OpenCV, `pyvirtualcam` e PyInstaller para gerar `LanCamClient.exe` como artefato `LanCam-Windows-Client`.

## Limites atuais

- A câmera virtual ainda aparece como **OBS Virtual Camera**, não como **LanCam**. Um dispositivo chamado LanCam exigirá uma etapa própria de driver/câmera virtual do Windows.
- Ainda não há transmissão de áudio/microfone.
- Ainda não há conexão USB/ADB.
- O APK e o cliente Windows atuais são builds de desenvolvimento, não releases assinados para distribuição pública.

## Privacidade

O app Android foi projetado para permanecer aberto em primeiro plano durante o uso. Ele não tenta esconder o uso da câmera e não transmite para servidores externos; o tráfego de vídeo é servido pela rede local configurada pelo usuário.
