# Condomínio Caiapó

App Android de controle financeiro do Condomínio do Edifício Caiapó 83, 85 e 87.

- **Lançar:** cotas das 14 unidades, outras receitas e despesas, por mês; exclusão de lançamentos.
- **Balancete:** balancete mensal com saldo encadeado; compartilhamento como imagem ou texto.
- **Backup:** os dados ficam só no celular. Use "Salvar backup" ou "Enviar backup" com frequência e "Restaurar de um backup" ao trocar de aparelho.

## Como obter o APK

Cada envio para a branch `main` gera o APK automaticamente (aba **Actions**) e publica em **Releases**. Baixe `condominio-caiapo.apk` da versão mais recente e instale no celular.

O APK é sempre assinado com a mesma chave (`app/caiapo.keystore`), então as atualizações instalam por cima sem apagar os dados. Mantenha este repositório privado.

## Site dos balancetes e comprovantes no Drive

O script `nuvem/Codigo.gs`, instalado uma vez na conta Google do condomínio, faz duas coisas:

- **Site dos balancetes:** o endereço do script (termina em /exec) é um site que mostra o balancete de cada mês, igual ao do app. O app envia os dados a cada mudança e o site se atualiza sozinho a cada minuto. "A vencer" e "Em atraso" são calculados pelo site na hora, pela data do vencimento.
- **Comprovantes:** as fotos das despesas ficam numa pasta do Drive do condomínio, por mês, com link só de leitura.

O app gera um código próprio e já copia o script com ele preenchido. O passo a passo está no app, em "Nuvem (Google Drive)".
