// Condomínio Caiapó – Prestação de contas no Google Drive
//
// Este script recebe do app os balancetes (imagem) e as fotos dos
// comprovantes de despesa e grava tudo numa pasta do Drive desta conta,
// organizada por mês. Os moradores recebem o link da pasta só para ver.
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
      return responder({ ok: true, pasta: raiz.getUrl() });
    }

    if (d.acao === 'balancete') {
      const mes = pastaDe(raiz, d.rotulo);
      apagarPorNome(mes, d.nome);
      const f = mes.createFile(criarArquivo(d.b64, 'image/png', d.nome));
      return responder({ ok: true, id: f.getId(), url: f.getUrl() });
    }

    if (d.acao === 'comprovante') {
      const pasta = pastaDe(pastaDe(raiz, d.rotulo), 'Comprovantes');
      if (d.anterior) apagarPorId(d.anterior, raiz);
      apagarPorNome(pasta, d.nome);
      const f = pasta.createFile(criarArquivo(d.b64, 'image/jpeg', d.nome));
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

function pastaDe(pai, nome) {
  const it = pai.getFoldersByName(nome);
  return it.hasNext() ? it.next() : pai.createFolder(nome);
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
