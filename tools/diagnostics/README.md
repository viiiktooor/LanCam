# Diagnóstico de transmissão

Utilitários usados na medição no S23, com caminhos de computador e identificador do aparelho substituídos por parâmetros. Requerem Python 3 e Pillow (`pip install pillow`). O controle do celular também requer ADB no PATH, ou seu caminho na variável `ADB`, e depuração USB autorizada.

## Medir sem controlar o celular

```text
python tools/diagnostics/measure_stream.py ensaio --url http://IP_DO_CELULAR:4747 --seconds 20
```

Gera `summary.json` e `status.json` em `diagnostics-output/ensaio`. Decodifica cada JPEG para verificar integridade e mede recepção, não renderização da janela Windows nem latência ponta a ponta. Somente `--save-image` salva a primeira imagem recebida. Use um único receptor e mantenha câmera, resolução, qualidade e FPS fixos durante cada ensaio.

## Controlar a tela do LanCam

Defina `LANCAM_DEVICE` com o serial retornado por `adb devices`. Exemplos:

```text
python tools/diagnostics/control_phone.py dump
python tools/diagnostics/control_phone.py tap "30 fps" "5 fps"
```

Os toques usam os limites dos controles encontrados na árvore de interface, não coordenadas fixas. O comando `screenshot CAMINHO.png` salva uma captura somente quando solicitado.

Resultados históricos em `docs/evidence/`; interpretação e limites em `docs/OTIMIZACAO-FPS.md`. O ensaio `v2-hd-front` contém mudança de configurações e não serve como comparação estável. Os primeiros ensaios de rede tiveram outro receptor ativo; consulte o relatório antes de interpretar os números.
