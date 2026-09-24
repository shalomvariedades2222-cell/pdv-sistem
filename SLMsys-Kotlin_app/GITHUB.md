# Como colocar o app no seu GitHub

Repositório: **https://github.com/shalomvariedades2222-cell/pdv-sistem**

O site (HTML) já está na pasta raiz. O app Android vai numa pasta `android/` no mesmo repo.

---

## Opção A — Pelo site do GitHub (mais fácil)

### 1. Subir a pasta do app
1. Abra: https://github.com/shalomvariedades2222-cell/pdv-sistem
2. Clique em **Add file → Upload files**
3. Arraste a pasta **SLMsys-Kotlin** inteira (ou renomeie para `android` antes)
4. Commit message: `App Android SLMsys 1.1.0`
5. **Commit changes**

### 2. Publicar o APK (atualização no celular)
1. No Android Studio: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Pegue o arquivo: `app/build/outputs/apk/debug/app-debug.apk`
3. No GitHub: **Releases** (menu da direita) → **Create a new release**
4. **Choose a tag**: digite `v1.1.0` → Create new tag
5. Release title: `SLMsys Android 1.1.0`
6. Em **Attach binaries**: arraste o `app-debug.apk`
7. **Publish release**

### 3. No celular
**Ajustes → Procurar atualização no GitHub**  
Se a versão do Release for maior que a instalada, aparece **Baixar versão …**

---

## Opção B — Pelo terminal (git)

```bash
# clone o repo (se ainda não tiver)
git clone https://github.com/shalomvariedades2222-cell/pdv-sistem.git
cd pdv-sistem

# copie o projeto Android para a pasta android/
# (no Windows: copie a pasta SLMsys-Kotlin para dentro de pdv-sistem e renomeie para android)

git add android
git commit -m "App Android SLMsys 1.1.0"
git push origin main
```

Depois faça o **Release** com o APK (passo 2 da Opção A).

---

## Próximas versões

1. Em `android/app/build.gradle.kts` aumente:
   - `versionCode` (ex.: 4)
   - `versionName` (ex.: `"1.2.0"`)
2. Commit + push
3. Novo Release no GitHub: tag `v1.2.0` + APK novo
4. Nos aparelhos: Ajustes → Procurar atualização

O app já está configurado com:
`GITHUB_REPO = "shalomvariedades2222-cell/pdv-sistem"`
