"""Teste visual em emulador descartável; nunca usa um telefone físico."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

RAIZ = Path(__file__).resolve().parents[1]
PASTA = RAIZ / "entrega" / "capturas"
PASTA.mkdir(parents=True, exist_ok=True)
PACOTE = "br.com.ander.rotas"
resultado = {"ambiente": "Emulador Android API 35 no GitHub Actions", "fisico": False}

def adb(*args, verificar=True):
    return subprocess.run(["adb", *args], check=verificar, capture_output=True, timeout=120)

def arvore():
    adb("shell", "uiautomator", "dump", "/sdcard/ander-ui.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/ander-ui.xml").stdout)

def tocar(rotulo):
    for no in arvore().iter("node"):
        if rotulo in (no.get("text", ""), no.get("content-desc", "")):
            numeros = list(map(int, re.findall(r"\d+", no.get("bounds", ""))))
            if len(numeros) == 4:
                x1, y1, x2, y2 = numeros
                adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                time.sleep(2)
                return True
    return False

def capturar(nome):
    (PASTA / (nome + ".png")).write_bytes(adb("exec-out", "screencap", "-p").stdout)
    (PASTA / (nome + ".xml")).write_bytes(ET.tostring(arvore(), encoding="utf-8"))
    if not adb("shell", "pidof", PACOTE, verificar=False).stdout.strip():
        raise RuntimeError("O processo do aplicativo encerrou durante o teste")

adb("install", "-r", str(RAIZ / "entrega" / "app-debug.apk"))
adb("logcat", "-c")
adb("shell", "am", "start", "-W", "-n", PACOTE + "/.MainActivity")
time.sleep(10)
try:
    capturar("01-permissao-automatica")
    resultado["dialogoLocalizacaoInicial"] = any("permissioncontroller" in n.get("package", "") for n in arvore().iter("node"))
    adb("shell", "input", "keyevent", "4")
    adb("shell", "pm", "grant", PACOTE, "android.permission.ACCESS_COARSE_LOCATION")
    adb("shell", "pm", "grant", PACOTE, "android.permission.ACCESS_FINE_LOCATION")
    adb("shell", "am", "force-stop", PACOTE)
    adb("shell", "am", "start", "-W", "-n", PACOTE + "/.MainActivity")
    adb("emu", "geo", "fix", "-46.625", "-23.533")
    time.sleep(6)
    capturar("02-inicial-sem-destino")
    textos = [n.get("text", "") for n in arvore().iter("node")]
    resultado["semDestinoPadrao"] = "Digite seu destino" in textos and not any("Rua Silveira Tavares" in t or "Rua Seis, 65" in t for t in textos)
    resultado["origemGpsAutomatica"] = "Minha localização" in textos
    assert resultado["semDestinoPadrao"], "Destino predefinido ou campo ausente"
    assert resultado["origemGpsAutomatica"], "GPS não preencheu a origem"
    resultado["buscaDestinoAbriu"] = tocar("Digite seu destino")
    assert resultado["buscaDestinoAbriu"]
    # O campo fica disponível para digitação; foco explícito pela árvore acessível.
    for no in arvore().iter("node"):
        if no.get("class", "").endswith("EditText"):
            x1,y1,x2,y2=map(int,re.findall(r"\d+",no.get("bounds","")))
            adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2));break
    adb("shell","input","text","Luz")
    time.sleep(4)
    capturar("03-busca-ao-digitar")
    textos=[n.get("text","") for n in arvore().iter("node")]
    resultado["buscaSemBotaoPesquisar"] = "Pesquisar endereço" not in textos
    assert resultado["buscaSemBotaoPesquisar"]
    adb("shell", "input", "keyevent", "4")
    adb("shell", "input", "keyevent", "4")
    if tocar("Alternar aparência"): capturar("04-escuro")
    resultado["abriu"]=True
finally:
    adb("shell", "svc", "wifi", "enable", verificar=False)
    adb("shell", "svc", "data", "enable", verificar=False)
    logs = adb("logcat", "-d", "-s", "AndroidRuntime:E", verificar=False).stdout.decode(errors="replace")
    (PASTA / "android-runtime.txt").write_text(logs)
    resultado["excecaoFatal"] = "FATAL EXCEPTION" in logs
    (PASTA / "resultado.json").write_text(json.dumps(resultado, ensure_ascii=False, indent=2))
if resultado["excecaoFatal"]:
    raise RuntimeError("Exceção fatal encontrada no log Android")
print(json.dumps(resultado, ensure_ascii=False))
