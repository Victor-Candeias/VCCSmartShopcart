---
maestru: "0.4"
type: work-spec
id: sc-09-pin-biometric-lock-spec
title: "Especificação: bloqueio com PIN e desbloqueio biométrico"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-09-pin-biometric-lock
owner: developer
created: 2026-10-22
---

# Especificação: bloqueio com PIN e desbloqueio biométrico

## Overview

Bloqueio local da app com PIN e ativação opcional do desbloqueio biométrico (work-item
`sc-09-pin-biometric-lock`). Tudo fica no dispositivo; não há recuperação do PIN (esquecê-lo obriga a limpar
os dados da app, o que é dito nas Definições).

Decisões:

- **PIN**: 4 a 6 dígitos (`PinPolicy`). Guardado como `pbkdf2-sha256$<iterações>$<sal>$<hash>`
  (PBKDF2-HMAC-SHA256, 120 000 iterações, sal aleatório de 16 bytes, comparação em tempo constante) num
  DataStore próprio `app_lock` (qualificador `@AppLockPreferences`), separado das definições. O comprimento do
  PIN é guardado para mostrar os pontos e validar automaticamente ao atingir o comprimento.
  `allowBackup=false` já impede que o hash saia do dispositivo.
- **Tentativas**: contador persistente de falhas seguidas; a cada 5 falhas há bloqueio de
  `30 s × 2^(n-1)` (máx. 10 min) guardado em `locked_out_until`. Um PIN certo repõe o contador.
- **Quando bloqueia** (`AppLockSession`, singleton): ao arrancar o processo e ao voltar ao primeiro plano
  depois de ≥ 30 s em `onStop`. Retornos rápidos (partilha, seletor de ficheiros) não bloqueiam.
- **Ecrã de bloqueio**: sobreposto ao `AppNavHost` em `MainActivity` (o estado de navegação mantém-se);
  o conteúdo por baixo fica sem semântica de acessibilidade, o teclado é escondido e o "Voltar" envia a app
  para segundo plano. Enquanto o estado é desconhecido (`LOADING`) mostra-se só o fundo. Em Android 13+ a
  miniatura dos recentes é desativada quando há PIN.
- **Biometria**: `androidx.biometric:1.1.0` com `BIOMETRIC_STRONG` (sem criptografia — serve para desbloqueio
  da app). `MainActivity` passa a `FragmentActivity` (`fragment-ktx` explícito). Só se ativa com PIN ativo,
  biometria disponível e registada e após uma confirmação biométrica bem-sucedida. No ecrã de bloqueio a
  biometria é pedida automaticamente uma vez por bloqueio e há botão para repetir; "Usar PIN" fecha o
  pedido. Desativar o PIN desativa a biometria.
- **Definições → Segurança**: interruptor "Bloqueio com PIN" (abre criação ou desativação), "Alterar PIN" e
  interruptor "Desbloqueio biométrico" com o motivo quando indisponível (estado reavaliado ao voltar à app).
- **Ecrã de PIN** (`PinSetupRoute(mode)`): `CREATE` (novo → confirmar), `CHANGE` (atual → novo → confirmar),
  `DISABLE` (atual). O PIN atual respeita o limite de tentativas.

## Implementation

### Phase 1: Domínio, segurança e dados (TDD)

| Action | File | Details |
|---|---|---|
| Create | `domain/security/AppLock.kt` | `PinPolicy`, `AppLockSettings`, `PinCheckResult`, `AppLockRepository` |
| Create | `core/security/PinHasher.kt` | PBKDF2 com sal, formato versionado, `verify` em tempo constante |
| Create | `core/datastore/AppLockDataStore.kt` | `DataStoreAppLockRepository` + módulo Hilt (`@AppLockPreferences`) |
| Create | `core/security/AppLockSession.kt` | Estado desbloqueado em memória e regra dos 30 s |
| Create | `app/src/test/.../core/security/PinSecurityTest.kt` (PinHasher, PinPolicy, AppLockSession), `AppLockRepositoryTest.kt` (DataStore em memória — o `File.renameTo` da JVM no Windows não substitui ficheiros) | Testes |

- [x] Testes unitários passam

### Phase 2: Interface

| Action | File | Details |
|---|---|---|
| Modify | `gradle/libs.versions.toml`, `app/build.gradle.kts` | `androidx.biometric:biometric:1.1.0`, `androidx.fragment:fragment-ktx:1.8.9` |
| Create | `core/security/Biometrics.kt` | Disponibilidade e `BiometricPrompt` sobre `FragmentActivity` |
| Create | `feature/security/PinComponents.kt` | Pontos do PIN e teclado de PIN |
| Create | `feature/security/AppLockViewModel.kt`, `AppLockScreen.kt` | Portão de bloqueio e ecrã de desbloqueio |
| Create | `feature/security/PinSetupViewModel.kt`, `PinSetupScreen.kt` | Criar, alterar e desativar PIN |
| Modify | `MainActivity.kt` | `FragmentActivity`, sobreposição de bloqueio, `onStart`/`onStop` |
| Modify | `core/navigation/Routes.kt`, `AppNavHost.kt` | `PinSetupRoute` |
| Modify | `feature/settings/SettingsScreen.kt` | Secção Segurança |
| Modify | `res/values/strings.xml` | Textos |
| Create | `app/src/test/.../feature/security/AppLockViewModelTest.kt` | Testes dos fluxos (`AppLockViewModelTest`, `PinSetupViewModelTest`) |

- [x] `:app:assembleDebug` e `:app:testDebugUnitTest` passam

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/security/AppLock.kt` | Create | Regras e contrato do bloqueio |
| `app/src/main/java/pt/vcc/smartshopcart/core/security/PinHasher.kt` | Create | Hash do PIN |
| `app/src/main/java/pt/vcc/smartshopcart/core/datastore/AppLockDataStore.kt` | Create | Persistência e tentativas |
| `app/src/main/java/pt/vcc/smartshopcart/core/security/AppLockSession.kt` | Create | Sessão desbloqueada |
| `app/src/main/java/pt/vcc/smartshopcart/core/security/Biometrics.kt` | Create | BiometricPrompt |
| `app/src/main/java/pt/vcc/smartshopcart/feature/security/PinComponents.kt` | Create | Componentes de PIN |
| `app/src/main/java/pt/vcc/smartshopcart/feature/security/AppLockViewModel.kt` | Create | Estado do ecrã de bloqueio |
| `app/src/main/java/pt/vcc/smartshopcart/feature/security/AppLockScreen.kt` | Create | Ecrã de bloqueio |
| `app/src/main/java/pt/vcc/smartshopcart/feature/security/PinSetupViewModel.kt` | Create | Fluxos de PIN |
| `app/src/main/java/pt/vcc/smartshopcart/feature/security/PinSetupScreen.kt` | Create | Ecrã de PIN |
| `app/src/main/java/pt/vcc/smartshopcart/MainActivity.kt` | Modify | Integração do bloqueio |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/Routes.kt` | Modify | Rota do PIN |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/AppNavHost.kt` | Modify | Destino do PIN |
| `app/src/main/java/pt/vcc/smartshopcart/feature/settings/SettingsScreen.kt` | Modify | Secção Segurança |
| `app/src/main/res/values/strings.xml` | Modify | Textos |
| `gradle/libs.versions.toml` | Modify | Dependências |
| `app/build.gradle.kts` | Modify | Dependências |
| `README.md` | Modify | Funcionalidade descrita |
