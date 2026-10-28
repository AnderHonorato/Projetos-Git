"""Executa os testes reais de interface no emulador descartável do CI."""
from pathlib import Path
import subprocess
RAIZ=Path(__file__).resolve().parents[1]
PASTA=RAIZ/'entrega'/'capturas'
PASTA.mkdir(parents=True,exist_ok=True)
def adb(*args,check=True):
    return subprocess.run(['adb',*args],capture_output=True,check=check,timeout=240)
adb('install','-r',str(RAIZ/'entrega'/'app-debug.apk'))
adb('install','-r',str(RAIZ/'entrega'/'app-debug-androidTest.apk'))
adb('shell','settings','put','secure','location_mode','3')
adb('emu','geo','fix','-46.625','-23.533')
r=adb('shell','am','instrument','-w','br.com.ander.rotas.test/androidx.test.runner.AndroidJUnitRunner',check=False)
saida=(r.stdout+r.stderr).decode(errors='replace')
(PASTA/'testes-interface.txt').write_text(saida)
adb('pull','/sdcard/Android/data/br.com.ander.rotas/files/qa',str(PASTA/'interface'),check=False)
print(saida)
if r.returncode or 'FAILURES!!!' in saida or 'OK (' not in saida:
    raise RuntimeError('Os testes de interface falharam; consulte testes-interface.txt')
