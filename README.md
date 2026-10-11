# Condomínio Caiapó

App Android de controle financeiro do Condomínio do Edifício Caiapó 83, 85 e 87.

- **Lançar:** cotas das 14 unidades, outras receitas e despesas, por mês; exclusão de lançamentos.
- **Balancete:** balancete mensal com saldo encadeado; compartilhamento como imagem ou texto.
- **Backup:** os dados ficam só no celular. Use "Salvar backup" ou "Enviar backup" com frequência e "Restaurar de um backup" ao trocar de aparelho.

## Como obter o APK

Cada envio para a branch `main` gera o APK automaticamente (aba **Actions**) e publica em **Releases**. Baixe `condominio-caiapo.apk` da versão mais recente e instale no celular.

O APK é sempre assinado com a mesma chave (`app/caiapo.keystore`), então as atualizações instalam por cima sem apagar os dados. Mantenha este repositório privado.

## Nuvem (Google Drive)

O app envia sozinho, a cada mudança, os balancetes (imagem) e as fotos dos comprovantes de despesa para uma pasta no Google Drive do condomínio, organizada por mês. Quando um lançamento muda um mês, os balancetes daquele mês e dos seguintes são reenviados, porque o saldo muda em cadeia. Sem internet, os envios ficam na fila e saem quando a conexão volta.

O lado do Google é o script `nuvem/Codigo.gs`, instalado uma vez na conta do condomínio (o passo a passo está no app, em "Nuvem (Google Drive)"). O app gera um código próprio e já copia o script com ele preenchido.
