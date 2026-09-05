# Estado atual do LanCam

## Atualização mais recente — FPS v3 no aparelho

Encerramento dos testes: v3 mantida no S23 após descartar experimento v4 de Wi-Fi sem ganho consistente. Com cliente Windows fechado para evitar receptor duplicado, HD/70% recebeu 21,15 e 21,87 FPS no receptor de diagnóstico; Android produziu ~30 FPS. A tabela anterior de 15,36 FPS envolvia outro cliente ativo e não representa um receptor isolado. Branch final `ae9534d7b60176ade8ed8d00f088b9bdf4236dc4`, árvore idêntica à v3. Relatório detalhado em OTIMIZACAO-FPS.md.

README público refeito conforme solicitação: apresentação simples e aviso destacado de projeto completamente vibe codado por pessoa leiga; seções de assinatura/build removidas. Main remota agora contém commit `7c1897d5ec0c91ffaf36ea474830c8e17bb04afc` (somente README). A pasta local continua com HEAD anterior e alterações locais: não sincronizar com comandos destrutivos.

APK FPS v3 assinado instalado no S23 Android 16. Transformação NV21 com compressão única e limitador ajustado: aproximadamente 30 FPS produzidos em HD/70%; recepção de 29,69 FPS por cabo de diagnóstico, 15,36 FPS pela rede em HD e 29,26 FPS pela rede em VGA/50%. Veja [relatório completo e limites](OTIMIZACAO-FPS.md). Código remoto na branch de teste, commit `19062b95ce99c061a27b25db32b25c6e0d293677`. Nenhuma release oficial publicada. Os registros abaixo sobre a primeira compilação FPS descrevem etapas anteriores.

Atualizado em 05/09/2026. Base consultada: commit `4fc5e51` da branch `main`.

## Objetivo

Usar o celular Android como câmera na rede local, com preview no Windows. Priorizar soluções gratuitas e simples para uso pessoal.

## Versões e ambiente

| Item | Situação verificada |
| --- | --- |
| Repositório | https://github.com/viiiktooor/LanCam |
| Código local | `C:\LanCam\codigo` |
| Android no código | 1.2.1, versionCode 4; versão instalada no celular ainda não confirmada |
| Windows no código | 0.2.1 |
| Versão de release no código | 1.2.1; publicação remota não revalidada nesta sessão |
| Git local | Instalado e cópia do repositório concluída |
| Cliente Windows 0.2.1 | Usuário confirmou que abre normalmente |
| Transmissão celular → PC | Confirmada pelo usuário com EXE de teste; desempenho abaixo do alvo |

## Recursos presentes no código, aguardando validação prática

- Android: câmeras frontal/traseira, resolução, FPS, qualidade JPEG, flash quando suportado e espelhamento frontal.
- Vídeo MJPEG na porta 4747; página de preview, `/stream`, `/video`, `/shot.jpg` e `/api/status`.
- Windows: preview, indicadores de recepção, reconexão e último endereço salvo.
- Verificação e instalação de atualizações via GitHub Releases.

A presença de um recurso no código não significa que ele passou nos testes com o aparelho do usuário.

## Problemas e limites

- Retorno na Principal: Samsung S23 LATAM, PC por cabo, alvo 30 FPS. HD/70% chega a aproximadamente 12 FPS; 640×480/50% chega a aproximadamente 20 FPS e reduz bastante o atraso. Conectar/desconectar funciona. Versão numérica instalada e Android do aparelho não confirmados.

- A falha de abertura do Windows 0.2.0 é descrita no README como corrigida no 0.2.1; a abertura do 0.2.1 foi confirmada pelo usuário.
- Nenhum outro bug foi confirmado nesta etapa. Os testes pendentes não são bugs conhecidos.
- Ainda não há webcam virtual, áudio ou conexão USB.
- Android: compilação debug e lint validados localmente com Java 17, Gradle 8.9 e SDK 35. Seis testes JVM da captura passaram. Nenhum celular/emulador conectado para teste da alteração Android.

## Próximo passo

O usuário já testou o EXE de teste com as correções Windows. A alteração Android de FPS foi implementada: configuração da câmera reaplicada ao trocar FPS, limitador que tolera pequenas variações de tempo, JPEG fora da interface, três buffers reutilizáveis e no máximo um quadro pendente substituído pelo mais recente. Indicadores de FPS de captura, FPS de JPEG e tempo de conversão adicionados. Ganho real ainda não medido.

Build de teste assinado concluído com sucesso na branch remota `android/fps-test-20260905`, commit `0a5150998f67450e026b319ddae685959d08f129`: https://github.com/viiiktooor/LanCam/actions/runs/33975322275. A main, a versão 1.2.1/versionCode 4 e `release/version.txt` não foram alterados no GitHub; nenhuma release nova foi publicada. A pasta compartilhada continua na main com alterações locais, incluindo as anteriores de Windows/docs.

APK entregue em `C:\LanCam\testes\android-fps\LanCam-Android-1.2.1-fps-teste.apk`. Certificado verificado contra o oficial 1.2.1 no workflow; assinatura e SHA-256 do download também verificados localmente. SHA-256 do APK: `5dd3a21bd23cba0579e52f1fbb4949a8537d929b962959d1e62b2a6199030772`. Pacote `com.example.lancam`, versionCode 4, versão 1.2.1 preservados. Próximo passo: instalar por cima e comparar no S23 conforme [TESTES.md](TESTES.md). Instalação e ganho de FPS ainda não testados no aparelho. O APK debug local não serve para atualizar por cima do oficial.

## Organização das conversas

| Conversa | Responsabilidade |
| --- | --- |
| LanCam — Principal | Decisões, prioridades e coordenação |
| LanCam — Android | Aplicativo e câmera do celular |
| LanCam — Windows | Cliente e preview do PC |
| LanCam — Testes e bugs | Resultados e diagnóstico |
| LanCam — Releases | Builds, assinatura e distribuição |

Todas usam a mesma pasta de código. Consultar este arquivo e o estado dos arquivos antes de trabalhar; não presumir acesso ao histórico completo das outras conversas. Concluir uma alteração e sua validação antes de iniciar outra na mesma área.

Ao encerrar uma mudança, registrar aqui o resultado relevante e atualizar [TESTES.md](TESTES.md). Manter propostas em [IDEIAS.md](IDEIAS.md).

## Cuidados de continuidade

- Preservar a mesma chave de assinatura Android e a compatibilidade de atualização. Nunca guardar chaves ou senhas no repositório.
- Alterar `release/version.txt` na `main` dispara o fluxo de publicação por push; não usar esse arquivo para anotações.
- Não considerar uma release pronta apenas porque compila: registrar os testes realizados e os pendentes.

## Registro desta preparação

- Criados os documentos de estado, testes e ideias; adicionados links no README.
- Nenhuma alteração funcional, nova versão ou publicação faz parte desta preparação.

Depois da preparação, o usuário autorizou revisão e otimização: foram alterados o cliente Windows e os workflows para executar testes. Nenhuma versão foi publicada. O registro acima descreve apenas a etapa inicial de organização.
