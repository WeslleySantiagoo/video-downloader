# WesDownloader

Aplicativo Android nativo para baixar, no proprio aparelho, uma copia de videos individuais que voce criou ou tem permissao para usar. Nao existe servidor, conta, CAPTCHA ou armazenamento remoto: a inspecao, o download e o processamento acontecem localmente.

> O uso pessoal e a distribuicao por APK nao alteram os termos da plataforma de origem. Use somente com conteudo para o qual voce tenha os direitos e autorizacoes aplicaveis.

## Instalar

A versao estavel mais recente fica em [GitHub Releases](https://github.com/WeslleySantiagoo/video-downloader/releases/latest). Baixe o arquivo `WesDownloader-vN-universal.apk`, confira opcionalmente o arquivo `.sha256` e permita a instalacao de aplicativos dessa fonte nas configuracoes do Android.

As versoes da branch `dev` aparecem na lista geral de releases com o sufixo `-beta` e sao marcadas como prerelease.

O mesmo certificado assina betas e versoes estaveis. Por isso, uma versao nova pode ser instalada sobre a anterior sem remover os downloads salvos.

## Recursos

- Interface Kotlin/Jetpack Compose com tema escuro.
- Video ou audio, com video selecionado por padrao.
- Qualidades de video ordenadas da menor para a maior.
- MP4/M4A preferidos e WebM como alternativa.
- Audio MP3 com fallback M4A.
- Download em segundo plano com notificacao, progresso e cancelamento.
- Retomada manual de downloads interrompidos.
- Historico local em Room.
- Resultado em `Downloads/WesDownloader` via MediaStore.
- Compartilhamento de links de outros aplicativos para o WesDownloader.
- Atualizacao manual do mecanismo `yt-dlp`.

Playlists, lives, contas, cookies e conteudo privado ou protegido nao fazem parte da primeira versao.

## Desenvolvimento

Requisitos: Android Studio, Android SDK 35 e JDK 17.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

O APK de desenvolvimento sera criado em `app/build/outputs/apk/debug/app-debug.apk`.

O projeto usa quatro ABIs (`armeabi-v7a`, `arm64-v8a`, `x86` e `x86_64`) no mesmo APK universal. Isso facilita a instalacao, mas produz um arquivo consideravelmente maior porque Python, yt-dlp, FFmpeg e aria2c sao embarcados.

## Assinatura das releases

Crie uma unica keystore e guarde-a em local seguro. Perder esse arquivo ou suas senhas impede atualizar APKs ja instalados.

```bash
keytool -genkeypair -v \
  -keystore wesdownloader-release.jks \
  -alias wesdownloader \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Converta a keystore para Base64 no Linux:

```bash
base64 -w 0 wesdownloader-release.jks
```

No macOS:

```bash
base64 -i wesdownloader-release.jks
```

Em **GitHub > Settings > Secrets and variables > Actions**, cadastre:

| Secret | Valor |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Saida completa do comando Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Senha da keystore |
| `ANDROID_KEY_ALIAS` | `wesdownloader`, ou o alias escolhido |
| `ANDROID_KEY_PASSWORD` | Senha da chave |

Nao versione o arquivo `.jks`; ele esta coberto pelo `.gitignore`.

## Publicacao automatica

- Push na `main`: cria `v1`, `v2`, `v3` e assim por diante, marcando a release como **Latest**.
- Push na `dev`: cria `v0.1.1-beta`, `v0.1.2-beta` e assim por diante, sempre como **prerelease**.
- Toda publicacao executa testes, lint e `assembleRelease`, anexa o APK universal e seu SHA-256 e tambem mantem os arquivos como artifact da execucao.
- Reexecutar o mesmo workflow substitui os assets da release correspondente.

O `versionCode` Android usa a quantidade de commits alcancaveis. Para permitir a atualizacao direta de uma beta para a versao estavel, integre a `dev` na `main` sem reescrever o historico.

## Licenca

GPL-3.0. Consulte [LICENSE](LICENSE).
