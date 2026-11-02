<p align="center">
  <img src="images/EH3D_BANNER.svg" alt="EH3D-ANDROID-CONTROL banner" width="100%">
</p>

# 🤖 EH3D-ANDROID-CONTROL

<p align="center"><a href="README.md">🇺🇸 English</a> | <a href="README_spa.md">🇪🇸 Español</a> | <a href="README_fra.md">🇫🇷 Français</a> | <a href="README_ita.md">🇮🇹 Italiano</a> | <a href="README_deu.md">🇩🇪 Deutsch</a> | <a href="README_zho.md">🇨🇳 中文</a> | <a href="README_jpn.md">🇯🇵 日本語</a> | 🇵🇹 <b>Português</b></p>

### A aplicação Android dos programas EH3D: segue em tempo real as máquinas Bungard da oficina através do EH3D-SERVER, para-as, mostra o trabalho em 2D e 3D e a câmara do cabeçote de furação

<p align="center">
  <img src="https://img.shields.io/badge/License-GPL%203.0--or--later-blue.svg" alt="GPL 3.0 or later">
  <img src="https://img.shields.io/badge/Stack-Kotlin%20%2B%20Jetpack%20Compose-7F52FF.svg" alt="Kotlin, Jetpack Compose">
  <img src="https://img.shields.io/badge/Languages-8-00E5FF.svg" alt="Languages">
  <img src="https://img.shields.io/badge/Version-0.0.8-0f6cbd.svg" alt="Version">
  <img src="https://img.shields.io/badge/Maturity-scaffolding-FFB020.svg" alt="Maturity">
</p>

---

**Verificação de honestidade - o que funciona hoje:** as regras da aplicação (ler o que o EH3D-SERVER diz das máquinas, dos trabalhos e do cabeçote, a posição do cabeçote a partir dos passos feitos, a geometria 2D e isométrica, os idiomas, as verificações de endereço e a conservação da sessão) estão escritas e testadas num computador. Os ecrãs estão escritos para Android no tema Forge dos programas EH3D, mas **a aplicação nunca foi executada num telemóvel contra um servidor real**, a paragem de emergência nunca parou uma máquina real e a câmara nunca mostrou uma placa real.

---

> **Estado de segurança:** esta aplicação não é um sistema de segurança e não substitui a paragem de emergência da máquina. A sua paragem cancela o trabalho em curso através do servidor, que para a máquina e desliga os motores; precisa que a rede e o servidor funcionem. Só os operadores e administradores podem parar ou começar; a palavra-passe só é enviada para um endereço da rede local ou por https, e a sessão é guardada cifrada no telemóvel.


## 📋 Num relance

| | |
|---|---|
| Versão | `0.0.8` |
| Maturidade | Estrutura inicial |
| Testes | testes unitários na JVM |
| Tecnologias | Kotlin, Jetpack Compose |
| Em hardware real | Ainda não: a aplicação não foi executada num telemóvel, contra um servidor real nem com uma máquina; o primeiro a experimentar é entrar no EH3D-SERVER da oficina. |

**Ir para:** [Visão geral](#-visão-geral) · [Arquitetura](#-arquitetura) · [Início rápido](#-início-rápido) · [Testes](#-testes) · [Estrutura](#-estrutura-do-repositório) · [Relacionados](#-projetos-relacionados)

## 🚦 Estado do projeto

Cada linha é uma parte deste repositório e a luz diz até onde chega hoje. É escrita com o que o repositório consegue demonstrar (testes, compilações, execuções), não com o que está previsto.

🟢 Feito e verificado · 🟡 Funciona, com limites conhecidos · 🟠 Feito a meio ou ainda por verificar · 🔴 Por fazer ou a não funcionar

| Área | Estado | O que significa aqui |
|---|---|---|
| Software | 🟠 | Os ecrãs e as regras da aplicação estão escritos para Android; as regras compilam-se e testam-se sem telemóvel. |
| Testes | 🟡 | testes unitários na JVM: o que o servidor diz da frota, dos trabalhos e do cabeçote, a posição do cabeçote, a geometria 2D e isométrica, os idiomas e as verificações de endereço e versão. |
| Monitorização | 🟠 | Cada máquina da oficina com o seu estado, trabalho, progresso, passos feitos e tempo que falta, lido do servidor a cada segundo e meio; um aviso quando uma máquina espera pelo operador ou para. Escrito, não executado contra um servidor real. |
| Paragem de emergência | 🟠 | Um botão vermelho que pergunta duas vezes e cancela o trabalho em curso através do servidor (uma máquina ou todas); a confirmação dos passos que um trabalho espera. Só operadores e administradores. Nunca parou uma máquina real e não substitui a sua paragem física. |
| Preparar trabalhos | 🟠 | Escolher furação, fresagem, ambas ou um programa de G-code e o ficheiro do telemóvel; as ferramentas do ficheiro com a sua contagem (e o diâmetro, quando o ficheiro o declara), desligar ferramentas, começar por uma; pausar, retomar, cancelar e retomar um trabalho parado (de qualquer tipo) de onde parou, com o ficheiro guardado pelo servidor; ler o interruptor das linhas `$` do GRBL da máquina. O servidor não tem lista de ficheiros, por isso o ficheiro é enviado a partir do telemóvel. Escrito, nunca executado contra um servidor ou uma máquina reais. |
| Visor 2D e 3D | 🟠 | A placa, os furos (um anel até serem furados), os trajetos (ténues até serem cortados) e o cabeçote, em 2D e numa vista isométrica que roda com o dedo; a posição do cabeçote é uma estimativa a partir dos passos feitos. Testado como geometria, nunca visto num telemóvel. |
| Câmara do cabeçote | 🟠 | A câmara do cabeçote de furação (EH3D-NODE-BUNGARD-AUX) em MJPEG através do servidor, as suas rotações, temperatura e saída de proteção, e uma medição da mesa com o laser. Escrito, nunca executado com um nó real. |
| Atualizações | 🟠 | Consulta a última release deste projeto no GitHub, descarrega o APK, verifica o pacote, a versão e a assinatura e entrega-o ao instalador do Android; nunca instala sozinha. Escrito e testado; nunca executado contra o GitHub a partir de um telemóvel. |
| Uso real | 🔴 | Ainda não: a aplicação não foi executada num telemóvel, contra um servidor real nem com uma máquina; o primeiro a experimentar é entrar no EH3D-SERVER da oficina. |
| Documentação | 🟡 | README em oito idiomas, a ajuda da aplicação em oito idiomas e as notas da API do servidor que usa. |

## 🎯 Visão geral

* Cada máquina da oficina com o seu estado, trabalho, progresso, passos feitos e tempo que falta, lido do servidor a cada segundo e meio; um aviso quando uma máquina espera pelo operador ou para. Escrito, não executado contra um servidor real.
* Um botão vermelho que pergunta duas vezes e cancela o trabalho em curso através do servidor (uma máquina ou todas); a confirmação dos passos que um trabalho espera. Só operadores e administradores. Nunca parou uma máquina real e não substitui a sua paragem física.
* Iniciar um trabalho a partir da sua página pede mais uma confirmação (verifique que a máquina está preparada e desimpedida), tal como a partir de Preparar, para que um toque por engano não ponha a máquina em marcha.
* Antes de iniciar um trabalho, um resumo mostra o que faz, quanto demora, até onde vai cada eixo (a simulação em seco do plano exato feito pelo servidor) e o que parece errado, com Começar e Cancelar; uma definição desliga-o (ativado por defeito). Escrito, nunca executado com um servidor real.
* A ligação é indicada com honestidade: ligado, dados que envelhecem (com a idade), sem ligação após duas falhas seguidas, ou um endereço que responde a outro serviço, com um botão para tentar de novo; ao carregar em PARAR sem ligação, avisa que a ordem pode não chegar e que se deve usar a paragem física da máquina, e depois de uma paragem a app diz se a máquina a confirmou. Escrito, nunca executado com um servidor real.
* Escolher furação, fresagem, ambas ou um programa de G-code e o ficheiro do telemóvel; as ferramentas do ficheiro com a sua contagem (e o diâmetro, quando o ficheiro o declara), desligar ferramentas, começar por uma; pausar, retomar, cancelar e retomar um trabalho parado (de qualquer tipo) de onde parou, com o ficheiro guardado pelo servidor; ler o interruptor das linhas `$` do GRBL da máquina. O servidor não tem lista de ficheiros, por isso o ficheiro é enviado a partir do telemóvel. Escrito, nunca executado contra um servidor ou uma máquina reais.
* A placa, os furos (um anel até serem furados), os trajetos (ténues até serem cortados) e o cabeçote, em 2D e numa vista isométrica que roda com o dedo; a posição do cabeçote é uma estimativa a partir dos passos feitos. Testado como geometria, nunca visto num telemóvel.
* A câmara do cabeçote de furação (EH3D-NODE-BUNGARD-AUX) em MJPEG através do servidor, as suas rotações, temperatura e saída de proteção, e uma medição da mesa com o laser. Escrito, nunca executado com um nó real.
* Consulta a última release deste projeto no GitHub, descarrega o APK, verifica o pacote, a versão e a assinatura e entrega-o ao instalador do Android; nunca instala sozinha. Escrito e testado; nunca executado contra o GitHub a partir de um telemóvel.

A página de uma máquina também tem o anel de LEDs da cabeça (desligado e três níveis) e um cartão Verificação de qualidade: pede ao servidor que olhe para a placa com a câmara da cabeça de furação, julga os furos e os canais fresados e mostra o veredicto grande como um cartaz (APROVADO, REPROVADO ou não avaliável) com o que se encontrou, o perfil (padrão, passo fino, protótipo) e a calibração da escala. Os primeiros ecrãs (o de arranque com a marca, a espera enquanto a sessão é restaurada e o de entrada com os seus ícones, o olho da palavra-passe e a nota sobre o que fica no telemóvel) seguem o aspeto das outras apps da suite.

A página de uma máquina também mostra o fuso da máquina (o original ou o que leva variador de frequência), com os seus limites e a velocidade máxima a que o nó deve ser regulado; um administrador escolhe-o e escolhe o que o servidor faz a um trabalho que pede mais (recusá-lo, ou deixá-lo passar reduzido ao máximo do fuso). Um trabalho lista os excessos com que foi deixado passar, as ferramentas desativadas para ele e os furos ou percursos já feitos que deixa de fora. A página de uma máquina livre tem também Preparar trabalho: escolhe-se o que se faz (furação, fresagem, ambas ou um programa de G-code livre) e o ficheiro do telemóvel; o servidor planeia-o, e a aplicação lista as ferramentas do ficheiro com a sua contagem, deixa desligar ferramentas ou começar por uma (as anteriores na ordem do trabalho ficam de fora) e inicia o trabalho depois de uma confirmação. Com ambas criam-se dois trabalhos e o segundo fica pronto. Um programa de G-code pode ser pausado, retomado, cancelado e continuado a partir da linha onde parou; o fuso e o refrigerante só voltam a ligar-se se ativar essa opção (desativada por predefinição).

O cartão da cabeça de furação mostra também o que dizem os seus sensores: como correu a última leitura do laser (válida, sinal fraco, demasiada dispersão, fora do intervalo, falha do sensor) com a distância, há quanto tempo foi feita e o desvio com que o laser foi calibrado; se os impulsos do sensor de rotação estão limpos ou com ruído e quantos foram rejeitados como ruído; quantos sensores de temperatura o nó encontrou no fio (e um aviso quando apenas adivinha qual ler); e a versão do nó. Um nó que não o diz não mostra nada disto. As definições da imagem da câmara, a calibração do laser e a escolha do sensor de temperatura fazem-se no painel do nó: o servidor ainda não as transmite.

## 🧩 Papel no EH3D

EH3D-ANDROID-CONTROL é o ecrã de bolso da oficina EH3D: só fala com o EH3D-SERVER, que executa os trabalhos nas máquinas (através do EH3D-NODE-BUNGARD) e retransmite a câmara do cabeçote de furação (EH3D-NODE-BUNGARD-AUX). Os trabalhos preparam-se no EH3D-STUDIO, no BUNGARD-GUI ou nesta aplicação com Preparar trabalho; esta aplicação segue-os, confirma os passos que esperam e para-os.

## 🏛️ Arquitetura

<p align="center">
  <img src="images/architecture.svg" alt="Arquitetura de EH3D-ANDROID-CONTROL: quem o usa, o que contém e de que depende" width="100%">
</p>

Como as partes deste repositório se encaixam: quem o usa, o que contém e de que depende. A imagem é desenhada a partir de `docs/architecture.json` e verificada com o código.

## 🖼️ Capturas de ecrã

<p align="center">
  <img src="images/screenshots/app-home.png" alt="app-home" width="30%">
</p>

<p align="center"><em>As máquinas da oficina: livres, a trabalhar e à espera do operador, com a paragem de todas.</em></p>

<p align="center">
  <img src="images/screenshots/app-machine.png" alt="app-machine" width="30%">
</p>

<p align="center"><em>Uma máquina: a paragem, o trabalho em curso e a vista 2D em direto com a posição do cabeçote.</em></p>

<p align="center">
  <img src="images/screenshots/app-viewer3d.png" alt="app-viewer3d" width="30%">
</p>

<p align="center"><em>A vista isométrica do mesmo trabalho, rodada com o dedo, e as suas ferramentas.</em></p>

<p align="center">
  <img src="images/screenshots/app-head.png" alt="app-head" width="30%">
</p>

<p align="center"><em>O cabeçote de furação: a câmara através do servidor, as rotações, a temperatura, a saída de proteção e a medição da mesa.</em></p>

<p align="center">
  <img src="images/screenshots/app-jobs.png" alt="app-jobs" width="30%">
</p>

<p align="center"><em>Os trabalhos do servidor com os seus estados.</em></p>

<p align="center">
  <img src="images/screenshots/app-more.png" alt="app-more" width="30%">
</p>

<p align="center"><em>Mais: a sessão, a atualização a partir do GitHub, o idioma e a ajuda.</em></p>

<p align="center"><em>Um desenho dos ecrãs feito com as mesmas regras e um trabalho de exemplo, não uma captura de um telemóvel: a aplicação não foi executada em nenhum.</em></p>

## 🚀 Início rápido

Precisa do JDK 21 e do SDK do Android (o wrapper do Gradle faz o resto) e de um EH3D-SERVER na rede.

```bash
build.bat          # tests and debug APK (Windows)  -  ./build.sh on Linux
test.bat           # only the tests
python -I tools/ci_validate.py
```

Abra a aplicação, escreva o endereço do servidor (e a porta 18200), prima Ligar e inicie sessão. Um servidor sem utilizadores pede o código de registo que mostra na sua consola e cria o primeiro administrador.

## 🧪 Testes

`./gradlew test` executa os testes unitários na JVM: os leitores do JSON do servidor (frota, trabalhos, cabeçote, medição), a posição do cabeçote e a geometria das duas vistas, a verificação do endereço, a conservação cifrada da sessão, a versão e a porta da release da atualização, e os idiomas (cada frase do código nas sete tabelas, com os mesmos espaços). Também leem os exemplos de respostas que os testes do servidor produzem, de modo que uma resposta que muda sem que a app a acompanhe falha aqui. As duas apps de telemóvel percorrem também os mesmos ficheiros de casos (o resumo, a ligação, a paragem, os avisos, os endereços e os campos que leem), pelo que uma não pode diferir da outra sem que falhe um teste.

## 🏗️ Compilação e publicação

A versão vive em `eh3d.project.json` e em `app/build.gradle.kts` (a compilação pára se diferirem) e cada versão tem a sua entrada em [CHANGELOG.md](CHANGELOG.md). O APK da release é reduzido com o R8 e assinado depois com a chave própria do mantenedor, guardada fora do repositório; o ficheiro de uma release é `EH3D-ANDROID-CONTROL-release.apk`, o que a atualização a partir do GitHub procura.

## 📂 Estrutura do repositório

<p align="center">
  <img src="images/repository-structure.svg" alt="Estrutura do repositório EH3D-ANDROID-CONTROL" width="100%">
</p>

<details>
<summary>Versão em texto da árvore</summary>

```text
EH3D-ANDROID-CONTROL/
├── app/
│   ├── build.gradle.kts proguard-rules.pro      a compilação e as regras de redução
│   ├── src/main/AndroidManifest.xml             permissões e a única atividade
│   ├── src/main/assets/i18n/                    os textos em oito idiomas
│   ├── src/main/java/es/electrohobby3d/eh3d/    o código da aplicação
│   └── src/test/java/es/electrohobby3d/eh3d/    os testes na JVM
├── tools/
│   ├── ci_validate.py                           manifesto, versões, paridade dos README
│   └── make_architecture.py make_structure.py   desenham as duas imagens do README
├── docs/                                        as fontes das duas imagens
├── gradle/                                      os ficheiros do wrapper do Gradle
├── gradlew                                      o wrapper do Gradle
├── gradlew.bat                                  o wrapper para Windows
├── build.gradle.kts                             os plugins da compilação
├── settings.gradle.kts                          o nome do projeto
├── gradle.properties                            a memória da compilação
├── images/                                      banner e imagens
├── .github/                                     fluxo de CI
├── build.bat build.sh                           compilar e executar os testes
├── eh3d.project.json                            versão, papel, maturidade
├── CHANGELOG.md                                 o que mudou em cada versão
├── SECURITY.md SUPPORT.md                       comunicar uma vulnerabilidade, perguntar
├── CONTRIBUTING.md                              como contribuir
├── CODE_OF_CONDUCT.md                           o código de conduta
└── LICENSE                                      a licença
```

</details>

## 🗺️ Roteiro e limites conhecidos

**Limites conhecidos hoje**

* A aplicação não foi executada num telemóvel, contra um servidor real nem com uma máquina.
* A posição do cabeçote é uma estimativa a partir dos passos feitos; o servidor não informa a posição real da máquina.
* A paragem de emergência passa pelo servidor e pela rede: não é uma paragem física.
* A câmara nunca mostrou uma imagem real e a medição da mesa nunca foi feita com um laser real.

**A seguir**

* Entrar no EH3D-SERVER da oficina e seguir um trabalho real.
* Mostrar a posição verdadeira do cabeçote quando o servidor a informar.
* Os avisos com a app fechada vêm de uma tarefa periódica (de 15 em 15 minutos no melhor caso, o mínimo que o Android permite), não em tempo real; um canal push do servidor tornava-os imediatos e ainda não existe.
* Escolher o ficheiro entre os guardados no servidor em vez do telemóvel (o servidor ainda não tem lista de ficheiros).

## 🌍 Idiomas

O README e os textos existem em inglês, espanhol, francês, italiano, alemão, chinês, japonês e português europeu.

## 🔗 Projetos relacionados

* [EH3D-SERVER](https://github.com/JuanenRac/EH3D-SERVER): o centro da família EH3D: executa os trabalhos nas máquinas e dá a esta aplicação tudo o que mostra
* [EH3D-STUDIO](https://github.com/JuanenRac/EH3D-STUDIO): as páginas onde se juntam as máquinas e se preparam os trabalhos
* [EH3D-BUNGARD-GUI](https://github.com/JuanenRac/EH3D-BUNGARD-GUI): o programa do computador que prepara os trabalhos de furação e fresagem e os executa
* [EH3D-NODE-BUNGARD-AUX](https://github.com/JuanenRac/EH3D-NODE-BUNGARD-AUX): o nó do cabeçote de furação: a câmara, o laser e os sensores que esta aplicação mostra
* [EH3D-IOS-CONTROL](https://github.com/JuanenRac/EH3D-IOS-CONTROL): as aplicações Android e iPhone são gémeas: as mesmas regras e os mesmos ecrãs

## 📚 Documentação

* [A API do servidor](https://github.com/JuanenRac/EH3D-SERVER/blob/main/docs/API.md): o que esta aplicação lê e pede.
* [Registo de alterações](CHANGELOG.md)

## 🤝 Contribuir e comunidade

* Antes de um pull request execute `./gradlew test` e `python -I tools/ci_validate.py`: ambos têm de passar. Veja [CONTRIBUTING.md](CONTRIBUTING.md) e o [código de conduta](CODE_OF_CONDUCT.md).
* Cada texto que a aplicação mostra está nas tabelas de `app/src/main/assets/i18n` nos oito idiomas; um teste mantém-nas completas.
* Comunique uma vulnerabilidade em privado: [SECURITY.md](SECURITY.md). Perguntas, ideias e relatórios: electrohobby3d@gmail.com ([SUPPORT.md](SUPPORT.md)).

## 👤 AUTOR

**JuanenRac (Electro Hobby 3D)** · electrohobby3d@gmail.com

## 📜 LICENÇA

Software: GPL-3.0-or-later. Ver [LICENSE](LICENSE). Os projetos de hardware da suite usam a CERN OHL-S.
