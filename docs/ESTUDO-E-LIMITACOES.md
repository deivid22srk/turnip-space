# Estudo técnico e limitações reais — Turnip Space

> Relatório de estudo exigido antes da implementação. Conclusões verificadas
> contra os projetos de referência citados. Este documento define o que o app
> promete (e o que ele NUNCA deve prometer).

## 1. Como a AdrenoTools realmente funciona

A [AdrenoTools](https://github.com/K11MCH1/AdrenoTools) é uma **biblioteca
nativa (C, compilada para `libadrenotools.so`) que o app hospedeiro linka e
chama em tempo de execução**. A API principal é:

```c
adrenotools_open_libvulkan(void **outHandle, int flags, const char *schemaDir,
                           const char *cacheDir, const char *defaultLibDir,
                           const char *driverLibraryPath, void **vulkanHandle);
```

Ela intercepta o caminho de carregamento do Vulkan **dentro do processo do
app que a integra** (hooking de `dlopen`/`android_dlopen_ext` e do namespace
do linker), de modo que quando o motor do app pede `libvulkan.so`, recebe o
`libvulkan_freedreno.so` (Turnip) extraído do `.zip`.

**Provas de integração no próprio código-fonte** (não é injetor externo):

| Projeto | Como integra |
|---|---|
| Skyline / Strato (emuladores Switch) | chamam `adrenotools_open_libvulkan` no backend Vulkan próprio |
| Dolphin / PPSSPP / Vita3K | idem — integração direta no backend gráfico |
| UnleashedRecomp-Android | integração direta |
| Winlator (Windows→Android) | carrega o driver dentro do próprio processo Wine/Box64 |

**Conclusão crítica nº 1:** a AdrenoTools **não é um injetor universal**. Não
existe caminho suportado, sem root, para forçar um APK de terceiros não
cooperativo a usar outro driver. Por isso este projeto adota a
**Abordagem A**: o app controla o processo em que o APK hospedado roda
(espaço virtual estilo VirtualApp) e aplica o driver nesse processo. Para
APKs fora do espaço, a limitação é intransponível sem root — e o onboarding
do app declara isso explicitamente.

## 2. Virtualização de apps sem root (padrão VirtualApp)

Técnicas estudadas nos projetos de referência
([VirtualApp](https://github.com/asLody/VirtualApp) e derivados
(DualSpace/Parallel Space clones), VirtualXposed, Shizuku):

- **Stub components**: o host declara activities/serviços "curinga" no
  manifesto (o sistema só inicia componentes instalados); o engine troca o
  componente real na hora da criação.
- **Hook de `Instrumentation`** (`ActivityThread.mInstrumentation`):
  intercepta `newActivity()` para instanciar a activity do plugin com o
  `ClassLoader` do plugin. É a técnica usada neste projeto (com degradação
  graciosa).
- **Hook do `Handler.Callback` de `ActivityThread.mH`** e proxies de
  `IActivityManager`/`IAppTask`: necessários para redirecionar intents na
  borda do processo. Em Android 28+ o hook clássico de `LAUNCH_ACTIVITY`
  quebrou (`EXECUTE_TRANSACTION`/`ClientTransaction`); deixado como gap
  documentado — lançamos o stub diretamente, o que dispensa o spoof de AMS.
- **ClassLoader dinâmico**: `DexClassLoader` sobre o APK do plugin, com
  parent = classloader do host; `nativeLibraryDir` aponta para os `.so`
  extraídos no sandbox — é o ponto onde o driver Turnip já está pré-carregado.
- **Recursos**: API 30+ usa `ResourcesProvider`/`ResourcesLoader` (API
  pública); versões antigas caem para `AssetManager.addAssetPath` via
  reflexão (greylist — pode falhar; tratado sem crash).
- **Shizuku**: necessário apenas para operações que exigem permissão de
  `shell`/`system` (ex.: diagnósticos privilegiados). Nada essencial deste
  app depende dele — modo degradado é suportado por design.

### Restrições do Android moderno (matriz de compatibilidade)

| Restrição | Impacto | Versões afetadas |
|---|---|---|
| Hidden API enforcement (greylist) | hooks reflexivos podem falhar; mitigação: try/catch + modo degradado | Android 9+ (mais rígido em 12/13/14) |
| Linker namespaces (`dlopen` de libs de fora do app bloqueado) | driver Turnip precisa estar **dentro do sandbox do app** — é o que fazemos (extração do zip para `files/`) | Android 7+ |
| Scoped Storage | impossibilita escrever fora do sandbox; usamos apenas `filesDir`/`getExternalFilesDir` | Android 10+ |
| `dlclose` de ICD Vulkan registrado | inseguro; mantemos o handle aberto (mesma política dos apps que usam AdrenoTools) | — |
| Engines nativas pesadas (Unity/UE) | carregam Vulkan via JNI próprio no processo do plugin; o driver pré-carregado ajuda somente nos caminhos controlados por nós; compatibilidade universal NÃO garantida | — |

**Compatibilidade mínima/recomendada:** minSdk 26 (Android 8.0 — primeira
versão com ícones adaptativos e base estável para o engine); melhor
experiência no Android 9–13; no Android 14+ os hooks podem não instalar
(o app continua abrindo e explicando o porquê). Turnip requer GPU Adreno
6xx/7xx/8xx; Adreno 5xx ou anterior deve manter o driver proprietário.

## 3. Formato do zip de driver (AdrenoTools)

Estrutura observada nos releases de `K11MCH1/AdrenoToolsDrivers`,
`whitebelyash/freedreno_turnip-CI` e `The412Banner/Banners-Turnip`:

```
driver.zip
├── meta.json
└── libvulkan_freedreno.so   (+ libs auxiliares)
```

`meta.json` (parser tolerante implementado em `DriverZipParser`):

```json
{
  "name": "Turnip",
  "author": "...",
  "package_name": "...",
  "description": "...",
  "api": 5,
  "vendor": "freedreno",
  "module_id": "turnip",
  "library": "libvulkan_freedreno.so",
  "version": "25.x.x"
}
```

Validação aplicada na importação (crash-safe, sem `dlopen`):
1. zip legível e `meta.json` presente;
2. existência do `.so` declarado (ou varredura por `libvulkan*.so`);
3. parse ELF puro (Kotlin) de `.dynsym`: exige `vk_icdGetInstanceProcAddr`
   e `vk_icdNegotiateLoaderICDInterfaceVersion`;
4. proteção zip-slip na extração.

O `dlopen` real só acontece quando o usuário executa o espaço (ponte JNI
`NativeDriverLoader`), e o resultado (sucesso/erro) é sempre reportado.

## 4. Gaps conhecidos assumidos (honestidade de engenharia)

1. **Troca transparente de `LoadedApk`/`Resources` no attach**: a activity do
   plugin roda com o `Context` do host (recursos do plugin acessíveis via
   `PluginApk.resources`, mas não injetados no `ContextImpl`). Apps que
   dependem pesadamente de recursos próprios no `attachBaseContext` podem não
   iniciar. Gap herdado do escopo v1; correção documentada no ARCHITECTURE.
2. **Redirecionamento global de `dlopen("libvulkan.so")`**: exigiria PLT/inline
   hooking (ex.: bhook/Dobby). v1 pré-carrega o driver e expõe a ponte; a
   integração da `libadrenotools.so` oficial é o caminho de produção (a ponte
   C mantém a mesma forma de uso).
3. **Não há spoof de AMS/IActivityManager** nesta versão — dispensado porque
   iniciamos stubs reais instalados.
4. **Drivers só afetam apps rodando DENTRO do espaço virtual.** Nunca no
   sistema, nunca em apps instalados fora do espaço.

## 5. Referências

- K11MCH1/AdrenoTools (biblioteca) e K11MCH1/AdrenoToolsDrivers (drivers)
- whitebelyash/freedreno_turnip-CI, The412Banner/Banners-Turnip (CI de Turnip)
- asLody/VirtualApp e derivados comerciais (Parallel Space/DualSpace)
- rikkaapps/Shizuku
- Mesa3D freedreno (turnip) — suporte a6xx/a7xx
