# 📡 Controle Remoto IR – Philco PTV28G50SN e Fita LED

Aplicativo Android que transforma o celular em **controle remoto infravermelho** da **TV Philco PTV28G50SN** (controle AD1484) e de uma **fita LED de 24 teclas**, usando o emissor IR do aparelho.

Os botões seguem o mesmo layout dos controles originais, e os sinais foram capturados direto dos controles de verdade.

> Projeto simples, sem bibliotecas externas: apenas Kotlin e as APIs do Android.

---

## ✨ Funcionalidades

- 📺 **Controle da TV Philco PTV28G50SN** com todos os botões do AD1484: power, mute, números, setas com OK, volume, canal, mídia, atalhos de Netflix, YouTube, Globoplay e Prime Video.
- 💡 **Controle de fita LED de 24 teclas**: brilho, ON/OFF, R/G/B/W, 16 cores e os efeitos FLASH, STROBE, FADE e SMOOTH.
- 🔁 **Segurar para repetir** em setas, volume, canal e brilho.
- 📳 **Vibração**, 🔊 **som de clique** e 🔴 **LED vermelho** no topo do controle acendendo a cada toque.
- 🗂️ **Abas** para alternar entre os controles (rolam para o lado quando há muitos).
- 🛠️ **Criador de controles**: monte controles novos dentro do app, de forma visual (veja abaixo).

## 📱 Requisitos

- Celular Android 7.0 (API 24) ou superior.
- **Emissor infravermelho (IR blaster)** no aparelho. Sem ele, o app abre, mas avisa que não consegue transmitir.
- Testado em um **Redmi 14 Pro**.

## 🛠️ Como compilar

1. Instale o [Android Studio](https://developer.android.com/studio) (com JDK 17).
2. Clone o repositório:
   ```bash
   git clone https://github.com/gabriel2kpassos-ship-it/controle-ir-philco-ptv28g50sn-mais-fita-led.git
   ```
3. Abra a pasta no Android Studio e espere o Gradle sincronizar.
4. Ative a **Depuração USB** no celular e conecte o cabo.
   - Em celulares Xiaomi, ative também **Instalar via USB** nas Opções do desenvolvedor.
5. Selecione seu celular e clique em **Run ▶**.

Para gerar um APK: **Build > Build APK(s)**.

## 🎯 Como usar

1. Abra o app e escolha a aba **TV Philco** ou **Fita LED**.
2. Aponte o **topo do celular** (onde fica o emissor IR) para o aparelho.
3. Toque nos botões. Segure setas, volume, canal ou brilho para repetir o comando.

## 🛠️ Criar seu próprio controle

Toque em **＋ Novo** na barra de abas para abrir o assistente, em 2 passos:

1. **Nome e aparência**: escolha o nome, a cor, o ponto de partida (*Em branco* ou *TV básico*) e o **endereço** do aparelho (ex.: `BF00`).
2. **Monte os botões**: o controle aparece numa grade de 4 colunas. Toque num espaço com **＋** para adicionar um botão; toque num botão existente para editar ou apagar.

Em cada botão você define o **nome** (aceita emoji), a **cor**, o **tamanho** (normal ou largo), o **código do comando** (2 dígitos hexadecimais) e se ele **repete ao segurar**. O botão **▶ Testar agora** envia o sinal na hora, sem sair da tela.

- **Editar ou excluir**: segure o dedo sobre a aba do controle.
- Os controles ficam salvos no próprio celular.
- O criador usa o protocolo **NEC** (38 kHz), o mesmo dos controles que já vêm no app. O celular só transmite, então os códigos precisam vir do manual, de uma lista na internet ou de uma captura feita por você.

## 🔬 Como funciona

Os dois controles usam o protocolo **NEC** com portadora de **38 kHz**. Cada comando é um quadro de 32 bits enviado pela API `ConsumerIrManager`:

| Parte | Tempo |
|-------|-------|
| Cabeçalho | 9000 µs ligado + 4500 µs desligado |
| Bit 0 | 560 µs ligado + 560 µs desligado |
| Bit 1 | 560 µs ligado + 1690 µs desligado |
| Final | 560 µs ligado |

Os 32 bits são enviados do menos significativo para o mais significativo: endereço (16 bits), comando (8 bits) e comando invertido (8 bits).

| Controle | Endereço NEC |
|----------|--------------|
| TV Philco PTV28G50SN | `0xBF00` |
| Fita LED 24 teclas | `0xEF00` |

### Comandos da TV (`0xBF00`)

| Botão | Cmd | Botão | Cmd | Botão | Cmd |
|-------|-----|-------|-----|-------|-----|
| POWER | `0x03` | 1 | `0x00` | VOL + | `0x16` |
| MUTE | `0x15` | 2 | `0x01` | VOL − | `0x1A` |
| INFO | `0x46` | 3 | `0x02` | CH + | `0x14` |
| ● (ponto) | `0x0C` | 4 | `0x04` | CH − | `0x18` |
| Vermelho | `0x48` | 5 | `0x05` | SOURCE | `0x1E` |
| Verde | `0x49` | 6 | `0x06` | LIST | `0x57` |
| Amarelo | `0x4A` | 7 | `0x08` | PLAY | `0x52` |
| Azul | `0x4C` | 8 | `0x09` | PAUSE | `0x4F` |
| QMENU | `0x60` | 9 | `0x0A` | STOP | `0x53` |
| BACK | `0x0E` | 0 | `0x0D` | REC | `0x5A` |
| ▲ | `0x1D` | MENU | `0x1C` | ⏪ | `0x4D` |
| ▼ | `0x45` | GUIDE | `0x59` | ⏮ | `0x50` |
| ◀ | `0x40` | USB | `0x56` | ⏭ | `0x51` |
| ▶ | `0x42` | EXIT | `0x44` | ⏩ | `0x4E` |
| OK | `0x41` | NETFLIX | `0x43` | YouTube | `0x65` |
| GLOBOPLAY | `0x25` | Prime Video | `0x24` | | |

### Comandos da fita LED (`0xEF00`)

| Cmd | Botão | Cmd | Botão | Cmd | Botão | Cmd | Botão |
|-----|-------|-----|-------|-----|-------|-----|-------|
| `0x00` | Brilho + | `0x01` | Brilho − | `0x02` | OFF | `0x03` | ON |
| `0x04` | R | `0x05` | G | `0x06` | B | `0x07` | W |
| `0x08` | Laranja | `0x09` | Verde claro | `0x0A` | Azul royal | `0x0B` | FLASH |
| `0x0C` | Âmbar | `0x0D` | Ciano | `0x0E` | Roxo | `0x0F` | STROBE |
| `0x10` | Dourado | `0x11` | Azul claro | `0x12` | Magenta | `0x13` | FADE |
| `0x14` | Amarelo | `0x15` | Verde-água | `0x16` | Rosa | `0x17` | SMOOTH |

## 🧩 Adaptando para outro controle

Se o seu controle também usa NEC:

1. Capture os sinais com um receptor IR (por exemplo, Arduino com a biblioteca IRremote).
2. Em `MainActivity.kt`, troque o endereço e os comandos.
3. Ajuste o layout dos botões.

Para outros protocolos (RC5, Sony, Samsung...), altere a função `necFrame` para gerar a sequência de tempos correspondente.

## 📂 Estrutura

```
controle-ir-philco-ptv28g50sn-mais-fita-led/
├── app/
│   └── src/main/
│       ├── java/com/philco/controle/MainActivity.kt   # interface, abas e envio IR
│       ├── java/com/philco/controle/RemoteEditor.kt   # assistente visual de criação de controles
│       ├── java/com/philco/controle/CustomRemotes.kt  # modelo e armazenamento dos controles criados
│       ├── res/                                       # ícone do app
│       └── AndroidManifest.xml                        # permissões (TRANSMIT_IR, VIBRATE)
├── build.gradle.kts
└── settings.gradle.kts
```

## ⚠️ Aviso

Projeto independente e sem fins comerciais. Philco, Netflix, YouTube, Globoplay e Prime Video são marcas de seus respectivos donos, e este projeto não tem ligação com elas.

## 📄 Licença

Escolha a licença que preferir ao publicar. A [MIT](https://choosealicense.com/licenses/mit/) é uma opção comum para projetos pessoais.
