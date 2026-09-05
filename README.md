# LanCam

LanCam é um app Android simples que transforma o celular em uma câmera IP local.

## O que faz

- Abre a câmera traseira ou frontal.
- Mantém um preview no aparelho.
- Converte o preview em JPEG em aproximadamente 10 fps.
- Abre um servidor HTTP na porta `4747`.
- No computador, na mesma rede Wi‑Fi, abra o endereço mostrado no app, por exemplo `http://192.168.0.25:4747/`.
- `http://IP:4747/stream` fornece MJPEG.
- `http://IP:4747/shot.jpg` fornece um JPEG atual.

## Compilar

O projeto usa Android Gradle Plugin 8.7.3, `compileSdk 35` e Java 17 no ambiente de CI.

No Android Studio, abra a raiz do projeto e use **Build > Build APK(s)**.

No GitHub, o workflow **Build Android APK** compila automaticamente um APK de debug e publica o arquivo como artefato `LanCam-debug-apk`.

## Observações

O app foi projetado para permanecer aberto em primeiro plano durante o uso. Ele não tenta esconder o uso da câmera e não transmite para servidores externos.
