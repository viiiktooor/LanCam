## LanCam 1.2.2

Versão que reúne o estado final desta etapa do projeto: **Android 1.2.2** e **Windows Client 0.2.2**.

### Downloads

- **LanCam-Android-1.2.2.apk** — instale por cima do LanCam atual no celular.
- **LanCamClient-0.2.2.exe** — cliente para Windows.
- **SHA256SUMS.txt** — códigos de integridade dos arquivos.
- O GitHub também disponibiliza o código-fonte desta versão em ZIP e TAR.GZ.

### O que mudou

- Android: conversão de vídeo fora da interface, reaproveitamento de memória e descarte de quadros antigos.
- Rotação e espelho antes de uma única compressão JPEG.
- Correções no ajuste e controle de FPS, com indicadores de captura e conversão no celular.
- Windows: melhorias na leitura do vídeo, na fila de imagens e no isolamento entre conexões.
- Testes automatizados, documentação e resultados de diagnóstico preservados no repositório.

### Resultados e limites

No S23 usado nos testes, o Android passou a produzir aproximadamente 30 FPS em HD. Pela rede local, um receptor de diagnóstico no PC recebeu cerca de 21–22 FPS em HD/70% e 29 FPS em 640×480/50%. Não há garantia de 30 FPS em HD em qualquer rede ou aparelho.

A assinatura Android permanece a mesma. A nova versão pode atualizar a oficial 1.2.1 e os APKs de teste assinados sem desinstalação. A configuração continua sendo transmissão de vídeo pela rede local; não inclui áudio nem webcam virtual.

**Projeto pessoal, completamente vibe codado por uma pessoa leiga com ajuda de IA.** Pode conter bugs e limitações. Os testes e as pendências estão documentados em `docs/`.
