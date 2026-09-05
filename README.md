# LanCam

LanCam é um app Android simples que transforma o celular em uma câmera IP local.

## Versão 1.1.0

- Câmera traseira ou frontal.
- Preview no aparelho.
- Stream MJPEG pela rede local.
- Resolução selecionável: 640×480, 1280×720 ou 1920×1080. O app escolhe o tamanho suportado mais próximo pela câmera.
- FPS selecionável: 5, 10, 15, 20 ou 30 fps.
- Qualidade JPEG selecionável: 50%, 70%, 85% ou 95%.
- Flash/torch quando a câmera suporta.
- Espelhamento opcional da câmera frontal.
- Correção de orientação do stream.
- Endpoint JSON de status para integração futura com cliente de PC.
- CORS habilitado nos endpoints HTTP para facilitar integração local.

## Endereços

Com celular e PC na mesma rede Wi‑Fi, abra o endereço mostrado no app, por exemplo:

`http://192.168.0.25:4747/`

Endpoints:

- `/stream` ou `/video` — MJPEG contínuo.
- `/shot.jpg` — JPEG atual.
- `/api/status` — estado atual em JSON.

## Compilar

O projeto usa Android Gradle Plugin 8.7.3, `compileSdk 35` e Java 17 no ambiente de CI.

No Android Studio, abra a raiz do projeto e use **Build > Build APK(s)**.

No GitHub, o workflow **Build Android APK** compila automaticamente um APK de debug e publica o arquivo como artefato `LanCam-debug-apk`.

## Limites atuais

O LanCam ainda é uma câmera IP. Ele não instala um dispositivo de webcam virtual no Windows e ainda não transmite áudio do microfone. O próximo estágio previsto é um cliente de PC e, depois, integração com webcam virtual.

## Privacidade

O app foi projetado para permanecer aberto em primeiro plano durante o uso. Ele não tenta esconder o uso da câmera e não transmite para servidores externos.
