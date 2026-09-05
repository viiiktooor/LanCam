# Otimização Android e testes no S23 — 05/09/2026

## Resultado atual

Encerramento: experimento v4 com Wi-Fi low-latency descartado por não apresentar ganho consistente. APK v3 reinstalado com sucesso e confirmado por `pipeline: nv21-v3`; configuração frontal/HD/70%/30 FPS restaurada. Branch de teste final em `ae9534d7b60176ade8ed8d00f088b9bdf4236dc4`, cuja árvore é idêntica à v3 validada (`2292d3af1400908e1ab4a5a1d1021b84604c1609`).

APK de teste v3 instalado por cima no Samsung S23 SM-S911B, Android 16, sem desinstalação. Pacote `com.example.lancam`, versão 1.2.1/versionCode 4 e certificado oficial mantidos. APK local: `C:\LanCam\testes\android-fps\v3\LanCam-Android-1.2.1-fps-teste-v3.apk`.

Código Android na branch de teste `android/fps-test-20260905`, commit `19062b95ce99c061a27b25db32b25c6e0d293677`. Build assinado: https://github.com/viiiktooor/LanCam/actions/runs/33977597404. Nenhuma release oficial publicada. A alteração pública da main foi somente o README solicitado pelo usuário, commit `7c1897d5ec0c91ffaf36ea474830c8e17bb04afc`.

## Alterações

- Rotação e espelho são aplicados diretamente em NV21, preservando os pares de cor VU. O JPEG é comprimido uma única vez; removidos decode/transformação de bitmap e segunda compressão.
- Buffer de transformação e saída de compressão reutilizados no trabalhador único.
- Mantida fila com no máximo um quadro pendente e isolamento de gerações de câmera.
- Limitador arredonda para o intervalo mais próximo (tolerância de meio intervalo), preservando a cadência média. A tolerância anterior de 2 ms ainda descartava quadros no S23 mesmo com conversão rápida.
- Campo de diagnóstico `pipeline: nv21-v3` identifica o APK instalado.

## Medições

**Correção de interpretação:** após os ensaios abaixo, foi identificado um segundo receptor ativo: `LanCamClient-0.2.1-teste.exe`, PID 8628, com conexão TCP à porta 4747. Portanto, os ensaios pela rede abaixo não são medições de um receptor isolado e não permitem concluir a capacidade do Wi-Fi para uso normal. A janela desse cliente foi encerrada normalmente para repetir com receptor único. O teste por cabo também pode ter coexistido com esse receptor LAN.

Receptor de diagnóstico no PC recebe MJPEG e decodifica cada JPEG com Pillow. A medição não representa o tempo de renderização da janela do EXE nem mede latência ponta a ponta. Consultas de `/api/status` em paralelo, cerca de uma por segundo. Ensaios sequenciais e curtos; cena, temperatura e condições da rede podem variar. Wi-Fi reportou 2,4 GHz/802.11n; PC por Ethernet 100 Mbps. Temperatura de bateria observada entre 42,1 e 43,1 °C durante parte da sessão.

| Ensaio | Duração | FPS recebido no PC | FPS JPEG no celular (média) | Conversão média |
| --- | --- | --- | --- | --- |
| v1 frontal HD/70%, rede, antes desta mudança | 20 s | 16,09 | 16,02 | 62,26 ms |
| v2 frontal HD/70%, rede | 20 s | 18,57 | 24,34 | 13,44 ms |
| v3 frontal HD/70%, rede | 20 s | 15,36 | 29,78 | 10,51 ms |
| v3 frontal HD/70%, cabo de diagnóstico | 20 s | 29,69 | 29,96 | 13,22 ms |
| v3 frontal VGA/50%, rede | 20 s | 29,26 | 29,61 | 6,54 ms |
| v3 frontal VGA/50%, alvo 5 FPS | 8 s | 5,08 | 4,76 | 5,57 ms |
| v3 traseira HD/70%, rede | 10 s | 21,70 | 29,98 | 9,37 ms |
| v3 frontal HD/70%, rede, estabilidade com outro cliente possivelmente ativo | 60 s | 10,41 | 30,01 | 12,82 ms |
| v3 frontal HD/70%, rede, cliente Windows fechado e receptor único | 20 s | 21,15 | 30,00 | 14,22 ms |
| v3 frontal HD/70%, receptor único, repetição | 20 s | 21,87 | 29,70 | 13,17 ms |
| v4 experimental com WifiLock, receptor único | 20 s | 23,31 | 29,96 | 12,90 ms |
| v4 experimental após pausa/retomada, receptor único | 20 s | 21,70 | 30,00 | 12,27 ms |

O experimento v4 usou o mecanismo documentado de [WifiLock](https://developer.android.com/reference/android/net/wifi/WifiManager.WifiLock), solicitado somente com câmera ativa. O sistema confirmou a solicitação e zero locks do LanCam durante pausa. Como o resultado repetido sobrepôs a faixa sem lock, a alteração e a permissão WAKE_LOCK foram removidas; não fazem parte da versão entregue.

O teste de 60 segundos decodificou 624 JPEGs sem erro ou interrupção. Não houve mensagem nos filtros `LanCam:W`/`AndroidRuntime:E` do processo 29880 ao consultar posteriormente; isso não equivale a uma auditoria completa de logs.

O primeiro ensaio v2 (`v2-hd-front`) é inválido para comparação: configurações mudaram durante a coleta. Foi preservado, mas excluído da tabela.

O Android passou a produzir perto de 30 FPS em HD. A diferença entre recepção pela rede e cabo foi observada sob possível tráfego duplicado; não identifica sozinha um defeito de rede. O encaminhamento ADB foi temporário e removido ao concluir. Não foi implementada uma função USB na interface do app.

## Verificações funcionais

- Instalações v2 e v3 por cima: sucesso. Workflow verificou certificado contra APK oficial; SHA-256 dos downloads verificado antes de instalar.
- Compilação debug/release, análise lint e testes JVM passaram. Casos conhecidos para oito combinações de rotação/espelho, inversão de transformação com crominância em imagem não quadrada, rejeição de buffers inválidos; seis testes da cadência/fila incluindo jitter de ±10 ms.
- Espelho frontal ligado/desligado: duas imagens inspecionadas, inversão horizontal correta, cores preservadas (v2; transformação idêntica à v3).
- Frontal/traseira, resolução, qualidade e 5 → 30 FPS: retorno de imagem confirmado na v3.
- Full HD/95%: JPEG 1920×1080 recebido e decodificado; status momentâneo de 30 FPS/19,6 ms na traseira. Verificação curta, não teste sustentado de Full HD.
- Pausa ao ir para a tela inicial: `/shot.jpg` retornou HTTP 503. Volta ao app: frontal HD/70%/30 FPS retomou sem imagem antiga.
- Memória pontual na v2: PSS 113.203 KiB e uma Activity. Não é uma prova de ausência de vazamento.

## Evidências e limites

Arquivos por ensaio em `C:\LanCam\testes\android-fps\`: `summary.json`, `status.json` e primeira imagem recebida. Imagens do ambiente do usuário são somente locais, não enviadas ao GitHub.

Não foram validados nesta etapa: latência visual ponta a ponta, desempenho sustentado de horas, outros aparelhos, orientação física em todas as posições, atualizador Windows ou queda real do Wi-Fi. Não alterar esses resultados para “passou” com base na compilação.

O usuário autorizou continuar edições, builds, instalação com mesma assinatura e testes. Publicação de release oficial depende de consulta ao usuário.

Próxima decisão: para buscar 30 FPS em HD pela rede, isolar condições da rede (por exemplo, comparar uma rede de 5 GHz que o usuário já tenha disponível) ou planejar um formato de vídeo mais eficiente. Não foi prometido 30 FPS em HD via Wi-Fi atual nem alterada a configuração do roteador.
