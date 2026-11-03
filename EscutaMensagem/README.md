# Escuta Mensagem

Aplicativo Android que adiciona um botão de leitura sob demanda enquanto o WhatsApp está aberto. O botão lê em voz alta o texto disponível na tela e inclui um controle para parar.

## Como usar

1. Instale e abra o aplicativo.
2. Toque em **Ativar botão de leitura**.
3. Nas configurações de Acessibilidade, ative **Escuta Mensagem — botão de leitura** e confirme.
4. Volte ao WhatsApp e abra a conversa desejada.
5. Toque em **OUVIR**. Toque em **PARAR** para interromper.
6. Na tela principal do app, use **Testar a voz**, o controle de velocidade e **Configurar voz do celular** para escolher/baixar uma voz em português.

## Voz

O app seleciona a melhor voz em português instalada no mecanismo de fala do Android e usa uma velocidade levemente reduzida. A naturalidade depende do mecanismo e das vozes disponíveis no aparelho. Para uma voz mais natural, instale/baixe uma voz aprimorada em Português (Brasil) nas configurações de conversão de texto em voz do celular.

## Privacidade

- Não solicita permissão de internet, contatos ou armazenamento.
- O serviço de acessibilidade é necessário para localizar o texto que já está visível no WhatsApp.
- A leitura só começa quando alguém toca em **OUVIR**; não há leitura automática.
- O texto lido fica apenas em memória para permitir repetição pelo mecanismo de fala enquanto a leitura está em curso; o app não o envia para rede.

## Limites

- Lê o texto que o WhatsApp disponibiliza ao serviço de acessibilidade na tela atual. A quantidade e a ordem do texto podem variar com a versão do WhatsApp e com o aparelho.
- Não recupera mensagens antigas ocultas, não abre conversas por conta própria e não interpreta áudio.
- Em uma conversa aberta, pode ler outros rótulos visíveis junto com as mensagens. O ideal é testar com as conversas mais usadas antes de entregar o telefone para uso diário.

## Compilar

Abra esta pasta no Android Studio recente ou use `gradle :app:assembleDebug` com Android SDK 35 instalado. O APK de depuração ficará em `app/build/outputs/apk/debug/app-debug.apk`.

O workflow `.github/workflows/build-apk.yml` também compila o APK automaticamente a cada envio ao GitHub e disponibiliza `escuta-mensagem-apk` como artefato da execução por 30 dias.
