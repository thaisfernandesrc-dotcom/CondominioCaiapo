// Condomínio Caiapó – Prestação de contas no Google Drive
//
// Recebe do app os balancetes (imagem) e as fotos dos comprovantes e grava
// tudo numa pasta do Drive desta conta, com link só para ver:
//   Condomínio Caiapó - Prestação de Contas
//     Balancetes            (um balancete por mês)
//     2026-06 Junho, ...    (os comprovantes de cada mês)
//
// Instalação: veja as instruções no próprio app, em "Nuvem (Google Drive)".

const CODIGO = '__CODIGO__';
const PASTA = 'Condomínio Caiapó - Prestação de Contas';

function doPost(e) {
  const trava = LockService.getScriptLock();
  try {
    trava.waitLock(30000);
    const d = JSON.parse(e.postData.contents);
    if (d.token !== CODIGO) return responder({ ok: false, erro: 'codigo' });
    const raiz = pastaDe(DriveApp.getRootFolder(), PASTA);

    if (d.acao === 'ping') {
      raiz.setSharing(DriveApp.Access['ANYONE\x5FWITH\x5FLINK'], DriveApp.Permission.VIEW); // qualquer pessoa com o link pode ver
      return responder({ ok: true, pasta: raiz.getUrl(), estrutura: 2 });
    }

    if (d.acao === 'balancete2') {
      const pasta = pastaDe(raiz, 'Balancetes');
      apagarPorNome(pasta, d.nome);
      const f = pasta.createFile(criarArquivo(d.b64, 'image/png', d.nome));
      // arruma o jeito antigo: balancete dentro da pasta do mês
      const mes = existente(raiz, d.rotulo);
      if (mes) apagarPorNome(mes, d.nome);
      return responder({ ok: true, id: f.getId(), url: f.getUrl() });
    }

    if (d.acao === 'comprovante2') {
      const mes = pastaDe(raiz, d.rotulo);
      if (d.anterior) apagarPorId(d.anterior, raiz);
      apagarPorNome(mes, d.nome);
      const f = mes.createFile(criarArquivo(d.b64, 'image/jpeg', d.nome));
      // arruma o jeito antigo: subpasta Comprovantes que ficou vazia
      const velha = existente(mes, 'Comprovantes');
      if (velha && !velha.getFiles().hasNext() && !velha.getFolders().hasNext()) velha.setTrashed(true);
      return responder({ ok: true, id: f.getId(), url: f.getUrl() });
    }

    if (d.acao === 'excluir') {
      apagarPorId(d.id, raiz);
      return responder({ ok: true });
    }

    return responder({ ok: false, erro: 'acao' });
  } catch (err) {
    return responder({ ok: false, erro: String(err) });
  } finally {
    trava.releaseLock();
  }
}

function doGet() {
  return responder({ ok: true, app: 'condominio-caiapo' });
}

function responder(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function existente(pai, nome) {
  const it = pai.getFoldersByName(nome);
  return it.hasNext() ? it.next() : null;
}

function pastaDe(pai, nome) {
  return existente(pai, nome) || pai.createFolder(nome);
}

function criarArquivo(b64, tipo, nome) {
  return Utilities.newBlob(Utilities.base64Decode(b64), tipo, nome);
}

function apagarPorNome(pasta, nome) {
  const it = pasta.getFilesByName(nome);
  while (it.hasNext()) it.next().setTrashed(true);
}

// Só apaga arquivos que estejam dentro da pasta de prestação de contas.
function apagarPorId(id, raiz) {
  try {
    const f = DriveApp.getFileById(id);
    let pais = f.getParents();
    for (let nivel = 0; nivel < 4 && pais.hasNext(); nivel++) {
      const p = pais.next();
      if (p.getId() === raiz.getId()) { f.setTrashed(true); return; }
      pais = p.getParents();
    }
  } catch (err) {
    // arquivo já apagado ou inacessível: nada a fazer
  }
}
