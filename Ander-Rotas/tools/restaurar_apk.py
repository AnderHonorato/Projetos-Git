"""Recupera o APK assinado original sem armazenar ou expor a chave no GitHub."""
from pathlib import Path
import hashlib
import lzma
import shutil
import tempfile

RAIZ = Path(__file__).resolve().parents[1]
SHA256 = "4077599fec639211afb3fcf39a9a67389b8c091f909eb410358cb2ae233f555b"
TAMANHO = 61411017
destino = RAIZ / "entrega" / "Ander-Rotas-v1.0.0.apk"
destino.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryFile() as compactado:
    for indice in range(55):
        parte = RAIZ / ".distribution" / "1.0.0" / f"apk.part{indice:03d}"
        with parte.open("rb") as entrada:
            shutil.copyfileobj(entrada, compactado)
    compactado.seek(0)
    with lzma.LZMAFile(compactado) as entrada, destino.open("wb") as saida:
        total = 0
        while trecho := entrada.read(1 << 20):
            total += len(trecho)
            if total > TAMANHO:
                raise ValueError("O arquivo excedeu o tamanho aprovado")
            saida.write(trecho)
digest = hashlib.sha256(destino.read_bytes()).hexdigest()
if destino.stat().st_size != TAMANHO or digest != SHA256:
    raise ValueError("APK diferente do artefato assinado e verificado")
(destino.parent / "SHA256SUMS.txt").write_text(f"{digest}  {destino.name}\n")
print(f"APK original recuperado e SHA-256 confirmado: {destino}")
