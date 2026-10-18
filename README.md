# VCCSmartShopcart

Aplicação Android nativa, offline-first, com três modos de utilização: **calculadora rápida**, **lista planeada** e
**despesas rápidas**, com scanner de código de barras e preço por OCR, identificação de produtos via Open Food Facts, orçamento e histórico local.
No passo de confirmação do scanner é possível consultar **preços de referência em Portugal** (Open Prices, ODbL), guardados em
cache local (Room v2) e usados apenas após confirmação explícita.

O modo **Despesas Rápidas** regista consumos do dia a dia (cafés, refeições, transportes…) em poucos toques: catálogo
predefinido por categorias e personalizável, seleção rápida de vários produtos com quantidades, total automático,
estabelecimento opcional, rascunho local retomável, histórico (ver, editar, eliminar e repetir) e preço sugerido pelo
último valor pago no mesmo estabelecimento → último valor pago → preço habitual. Funciona totalmente offline (Room v3).

## Documentação

- Documento de implementação: [Documents/Implementacao_MVP_App_Compras.md](Documents/Implementacao_MVP_App_Compras.md)
- Decisões técnicas (prevalecem sobre o documento): [.maestru/docs/mvp-decisoes-tecnicas.md](.maestru/docs/mvp-decisoes-tecnicas.md)
- Plano e work-items (Maestru): [.maestru/tracks/vcc-smartshopcart-mvp.md](.maestru/tracks/vcc-smartshopcart-mvp.md)
- Integração Open Prices: [Documents/Integracao_Open_Prices_MVP_Android.md](Documents/Integracao_Open_Prices_MVP_Android.md),
  decisões [.maestru/docs/open-prices-decisoes.md](.maestru/docs/open-prices-decisoes.md) e
  work-items [.maestru/tracks/vcc-open-prices.md](.maestru/tracks/vcc-open-prices.md)
- Despesas Rápidas: [Documents/CestoSmart_Despesas_Rapidas_Especificacao.md](Documents/CestoSmart_Despesas_Rapidas_Especificacao.md),
  decisões [.maestru/docs/despesas-rapidas-decisoes.md](.maestru/docs/despesas-rapidas-decisoes.md) e
  work-items [.maestru/tracks/vcc-despesas-rapidas.md](.maestru/tracks/vcc-despesas-rapidas.md)

## Compilar e testar

Requer JDK 17 e o Android SDK (API 37).

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-17.0.20.1+1"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Gerar APK assinado (local)

A keystore e as palavras-passe ficam apenas neste PC: `keystore/` e `keystore.properties` são ignorados pelo Git.
Faça cópia de segurança de ambos; sem a mesma keystore não é possível atualizar a app já instalada.

1. Gerar a keystore (uma vez):

   ```powershell
   New-Item -ItemType Directory keystore -Force | Out-Null
   & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -storetype PKCS12 `
     -keystore keystore\vccsmartshopcart-release.jks -alias vccsmartshopcart `
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=VCCSmartShopcart, O=VCC, C=PT"
   ```

2. Criar `keystore.properties` na raiz do repositório:

   ```properties
   storeFile=keystore/vccsmartshopcart-release.jks
   storePassword=<palavra-passe>
   keyAlias=vccsmartshopcart
   keyPassword=<palavra-passe>
   ```

3. Compilar e verificar:

   ```powershell
   .\gradlew.bat :app:assembleRelease
   & "$env:ANDROID_HOME\build-tools\<versão>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\VCCSmartShopcart-<versão>-release.apk
   ```

O APK fica em `app\build\outputs\apk\release\VCCSmartShopcart-<versão>-release.apk`. Sem `keystore.properties` o build de release
é gerado sem assinatura. Como as builds debug e release usam chaves diferentes, trocar entre elas exige
desinstalar a app (os dados locais são apagados).
