# VCCSmartShopcart

Aplicação Android nativa, offline-first, com dois modos de utilização: **calculadora rápida** e **lista planeada**,
com scanner de código de barras e preço por OCR, identificação de produtos via Open Food Facts, orçamento e histórico local.

## Documentação

- Documento de implementação: [Documents/Implementacao_MVP_App_Compras.md](Documents/Implementacao_MVP_App_Compras.md)
- Decisões técnicas (prevalecem sobre o documento): [.maestru/docs/mvp-decisoes-tecnicas.md](.maestru/docs/mvp-decisoes-tecnicas.md)
- Plano e work-items (Maestru): [.maestru/tracks/vcc-smartshopcart-mvp.md](.maestru/tracks/vcc-smartshopcart-mvp.md)

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
   & "$env:ANDROID_HOME\build-tools\<versão>\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
   ```

O APK fica em `app\build\outputs\apk\release\app-release.apk`. Sem `keystore.properties` o build de release
é gerado sem assinatura. Como as builds debug e release usam chaves diferentes, trocar entre elas exige
desinstalar a app (os dados locais são apagados).
