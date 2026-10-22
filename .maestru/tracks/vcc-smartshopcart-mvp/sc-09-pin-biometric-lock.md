---
maestru: "0.4"
type: work-item
id: sc-09-pin-biometric-lock
title: Bloqueio da app com PIN e ativação de desbloqueio biométrico
created: 2026-10-22
owner: developer
completed: 2026-10-22
priority: high
status: done
specs:
  - sc-09-pin-biometric-lock-spec
track: vcc-smartshopcart-mvp
---

# sc-09-pin-biometric-lock: Bloqueio da app com PIN e ativação de desbloqueio biométrico

Proteger o acesso à app com um PIN local e permitir ativar o desbloqueio biométrico (impressão digital/rosto).

## Âmbito

- Definições → Segurança: ativar/desativar o bloqueio com PIN (4 a 6 dígitos, introduzido duas vezes),
  alterar o PIN (pede o atual) e desativar (pede o atual).
- Ecrã de bloqueio ao abrir a app e ao voltar após 30 s em segundo plano, com teclado de PIN.
- Limite de tentativas: após 5 PIN errados seguidos a introdução fica bloqueada 30 s (duplica a cada novo
  bloqueio, até 10 min), mesmo depois de reiniciar a app.
- Desbloqueio biométrico (classe forte) ativável só com PIN ativo, biometria registada no Android e
  confirmação biométrica; o PIN continua sempre disponível como alternativa.
- O PIN nunca é guardado em claro (PBKDF2-HMAC-SHA256 com sal aleatório).

## Saída verificável

`:app:assembleDebug` e `:app:testDebugUnitTest` passam; testes unitários do hash, política de tentativas,
repositório, sessão de bloqueio e ViewModels de PIN.
