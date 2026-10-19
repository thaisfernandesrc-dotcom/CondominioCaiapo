// Condomínio Caiapó – Balancetes e comprovantes
//
// Este script faz duas coisas para o app do condomínio:
// 1. mostra o site dos balancetes para os moradores (este mesmo endereço);
// 2. guarda as fotos dos comprovantes de despesa numa pasta do Drive desta
//    conta, organizada por mês, com link só para ver.
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
    const props = PropertiesService.getScriptProperties();

    if (d.acao === 'ping') {
      raiz.setSharing(DriveApp.Access['ANYONE\x5FWITH\x5FLINK'], DriveApp.Permission.VIEW); // qualquer pessoa com o link pode ver
      return responder({ ok: true, pasta: raiz.getUrl(), site: true });
    }

    if (d.acao === 'folha') {
      props.setProperty('mes:' + d.mes, JSON.stringify(d.folha));
      props.setProperty('info', JSON.stringify({ atualizado: new Date().toISOString(), pasta: raiz.getUrl() }));
      // tira do site os meses antes do período que os moradores veem
      const todas = props.getProperties();
      Object.keys(todas).forEach(function (k) {
        if (k.indexOf('mes:') === 0 && d.desde && k.slice(4) < d.desde) props.deleteProperty(k);
      });
      // apaga do Drive a imagem antiga do balancete deste mês, se existir
      const it = raiz.getFoldersByName(d.rotulo);
      if (it.hasNext()) apagarPorNome(it.next(), 'Balancete ' + d.rotulo + '.png');
      return responder({ ok: true });
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

// O site dos balancetes
function doGet() {
  const json = JSON.stringify(dados()).split('<').join('\\u003c');
  return HtmlService.createHtmlOutput(PAGINA.split('DADOSAQUI').join(json))
    .setTitle('Condomínio Caiapó – Balancetes')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1');
}

// Chamado pelo site a cada minuto para mostrar as mudanças sem recarregar
function dados() {
  const todas = PropertiesService.getScriptProperties().getProperties();
  const out = { meses: {}, info: JSON.parse(todas.info || '{}') };
  Object.keys(todas).forEach(function (k) {
    if (k.indexOf('mes:') === 0) out.meses[k.slice(4)] = JSON.parse(todas[k]);
  });
  return out;
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

const PAGINA = `<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<style>
body{margin:0;background:#eef1f5;color:#26303d;font:15px/1.45 Roboto,"Segoe UI",system-ui,sans-serif}
.wrap{max-width:560px;margin:0 auto;padding:16px 16px 40px}
.cab{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:12px}
.cab button{width:44px;height:40px;border-radius:10px;border:1px solid #d8dee7;background:#fff;color:#26303d;font-size:20px;cursor:pointer}
.cab button:disabled{opacity:.35;cursor:default}
.cab b{font:600 18px "Roboto Condensed",Roboto,system-ui,sans-serif;color:#5d7391}
.sheet{background:#fff;border:1px solid #d8dee7;border-radius:6px;padding:18px 16px 20px}
.top{display:flex;gap:12px;align-items:center}
.logo{flex:none;width:72px;height:48px;border-radius:7px;background:#5d7391;display:grid;place-items:center}
.tt{font:600 15px/1.3 "Roboto Condensed",Roboto,system-ui,sans-serif;color:#5d7391}
h3{text-align:center;font:600 18px "Roboto Condensed",Roboto,system-ui,sans-serif;color:#5d7391;margin:16px 0 6px}
h4{font:600 15px "Roboto Condensed",Roboto,system-ui,sans-serif;color:#5d7391;margin:16px 0 4px}
.ln{display:flex;justify-content:space-between;gap:12px;padding:3px 0;color:#6a7789}
.ln span:first-child{min-width:0}
.v{font-variant-numeric:tabular-nums;font-weight:600;white-space:nowrap}
.v.in{color:#2f8a3e}.v.out{color:#b8322c}
.ln.open .v{color:#6a7789;font-weight:400}
.ln.big{color:#26303d;font-weight:600;font-size:16px;border-top:1px solid #d8dee7;margin-top:6px;padding-top:8px}
.ln.sub{font-size:13px}
.note{font-size:12px;color:#6a7789;margin-top:12px}
.rodape{font-size:13px;color:#6a7789;margin-top:14px;display:grid;gap:6px}
.rodape a{color:#5d7391;font-weight:600}
.vazio{background:#fff;border:1px solid #d8dee7;border-radius:6px;padding:24px 16px;text-align:center;color:#6a7789}
</style></head><body>
<div class="wrap">
  <div class="cab"><button id="ant" aria-label="Mês anterior">‹</button><b id="titulo">Balancetes</b><button id="prox" aria-label="Próximo mês">›</button></div>
  <div id="folha"></div>
  <div class="rodape"><span id="atual"></span><span id="comp"></span></div>
</div>
<script>
var D = DADOSAQUI;
var LOGO = '<svg width="58" height="40" viewBox="0 0 58 40" aria-hidden="true"><g fill="#fff"><path d="M4 13 12 6l8 7v11H4z"/><path d="M19 12l10-8 10 8v12H19z"/><path d="M38 13l8-7 8 7v11H38z"/></g><g fill="#5d7391"><path d="M10 24v-5a2 2 0 0 1 4 0v5z"/><path d="M27 24v-6a2 2 0 0 1 4 0v6z"/><path d="M44 24v-5a2 2 0 0 1 4 0v5z"/></g><text x="29" y="31.5" text-anchor="middle" font-size="5.2" font-weight="600" fill="#fff" font-family="Roboto,sans-serif">Condomínio Caiapó</text><text x="29" y="37.5" text-anchor="middle" font-size="4.6" fill="#fff" font-family="Roboto,sans-serif">83 | 85 | 87</text></svg>';
var MESES = ["Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro"];
var mes = null;
function z(n){ return String(n).padStart(2, "0"); }
function hoje(){ var d = new Date(); return d.getFullYear() + "-" + z(d.getMonth() + 1) + "-" + z(d.getDate()); }
function lista(){ return Object.keys(D.meses).sort(); }
function el(tag, cls, txt){ var e = document.createElement(tag); if (cls) e.className = cls; if (txt != null) e.textContent = txt; return e; }
function linha(a, b, c, x){ var d = el("div", "ln" + (x ? " " + x : "")); d.appendChild(el("span", "", a)); d.appendChild(el("span", "v " + (c || ""), b)); return d; }
function desenhar(){
  var ms = lista(), box = document.getElementById("folha");
  box.textContent = "";
  if (!ms.length){ box.appendChild(el("div", "vazio", "Nenhum balancete publicado ainda.")); return; }
  if (!mes || ms.indexOf(mes) < 0){ var agora = hoje().slice(0, 7), ok = ms.filter(function(m){ return m <= agora; }); mes = ok.length ? ok[ok.length - 1] : ms[ms.length - 1]; }
  var i = ms.indexOf(mes), F = D.meses[mes], atr = hoje() > F.venc, partes = mes.split("-");
  document.getElementById("titulo").textContent = MESES[Number(partes[1]) - 1] + " " + partes[0];
  document.getElementById("ant").disabled = i <= 0;
  document.getElementById("prox").disabled = i >= ms.length - 1;
  var sh = el("div", "sheet"), top = el("div", "top"), lg = el("div", "logo"), tt = el("div", "tt");
  lg.innerHTML = LOGO; tt.appendChild(document.createTextNode("Condomínio do Edifício Caiapó 83, 85 e 87")); tt.appendChild(el("br")); tt.appendChild(document.createTextNode("Balancete Mensal | Exercício " + F.ano));
  top.appendChild(lg); top.appendChild(tt); sh.appendChild(top); sh.appendChild(el("h3", "", F.nome));
  F.itens.forEach(function(it){
    if (it.t === "h4") sh.appendChild(el("h4", "", it.x));
    else if (it.p) sh.appendChild(atr ? linha(it.a, "Em atraso", "out") : linha(it.a, "A vencer", "", "open"));
    else sh.appendChild(linha(it.a, it.b, it.c, it.e));
  });
  var vd = F.venc.split("-");
  var nota = (!F.ab ? "Todas as cotas pagas. " : F.ab + (F.ab > 1 ? " unidades " : " unidade ") + (atr ? "Em atraso" : "A vencer") + " (vencimento " + vd[2] + "/" + vd[1] + "). ") + F.resto;
  sh.appendChild(el("p", "note", nota));
  box.appendChild(sh);
  var at = D.info && D.info.atualizado ? new Date(D.info.atualizado) : null;
  document.getElementById("atual").textContent = at ? "Atualizado em " + z(at.getDate()) + "/" + z(at.getMonth() + 1) + "/" + at.getFullYear() + " às " + z(at.getHours()) + ":" + z(at.getMinutes()) + "." : "";
  var cp = document.getElementById("comp"); cp.textContent = "";
  if (D.info && D.info.pasta){ cp.appendChild(document.createTextNode("Comprovantes das despesas: ")); var a = el("a", "", "pasta no Google Drive"); a.href = D.info.pasta; a.target = "\x5Fblank"; a.rel = "noopener"; cp.appendChild(a); }
}
document.getElementById("ant").onclick = function(){ var ms = lista(), i = ms.indexOf(mes); if (i > 0){ mes = ms[i - 1]; desenhar(); } };
document.getElementById("prox").onclick = function(){ var ms = lista(), i = ms.indexOf(mes); if (i < ms.length - 1){ mes = ms[i + 1]; desenhar(); } };
desenhar();
setInterval(function(){ google.script.run.withSuccessHandler(function(novo){ D = novo; desenhar(); }).dados(); }, 60000);
</script></body></html>`;
