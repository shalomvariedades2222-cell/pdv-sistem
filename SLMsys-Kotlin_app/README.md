# SLMsys Android (Kotlin)

App nativo da loja Shalom Variedades — estoque, etiqueta e impressora BLE.

## Editor de etiqueta (só no aparelho)

1. **Impressora → Editar etiqueta neste aparelho**
2. Ajuste posição/tamanho/fonte dos elementos (mesmo formato do designer do site)
3. **Salvar no app** — grava só neste celular, **não sobe pro site**
4. **Atualizar modelo do servidor** — baixa de novo o layout do site e substitui a cópia local

Impressão usa o layout local se você salvou; senão usa o do servidor.

## Publicar no GitHub + atualização no app

### 1. Criar repositório
```bash
cd SLMsys-Kotlin
git init
git add .
git commit -m "SLMsys Android 1.1.0"
# no GitHub: New repository → copie a URL
git remote add origin https://github.com/SUA-CONTA/slmsys-android.git
git branch -M main
git push -u origin main
```

### 2. Configurar o app
Em `app/build.gradle.kts`, troque:
```kotlin
buildConfigField("String", "GITHUB_REPO", "\"SUA-CONTA/slmsys-android\"")
```
(substitua `SUA-CONTA/slmsys-android` pelo seu repo)

### 3. Gerar APK
Android Studio → **Build → Build APK(s)**  
Arquivo: `app/build/outputs/apk/debug/app-debug.apk`

### 4. Criar Release no GitHub
1. GitHub → **Releases → Draft a new release**
2. Tag: `v1.1.0` (igual ao `versionName`)
3. Anexe o arquivo `.apk`
4. Publish release

### 5. No app
**Ajustes → Procurar atualização no GitHub**  
Se houver versão mais nova com APK, aparece **Baixar versão X**.

A cada abertura (com login), o app também verifica atualização em segundo plano.

### 6. Novas versões
1. Suba `versionCode` e `versionName` em `app/build.gradle.kts`
2. Commit + push
3. Novo Release no GitHub com o APK novo
4. Nos celulares: Ajustes → Procurar atualização → Baixar

## Build local
- JDK 17
- Android Studio Hedgehog+
- minSdk 26 / targetSdk 34

