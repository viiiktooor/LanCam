import hashlib
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
import urllib.request
from pathlib import Path
import tkinter as tk
from tkinter import messagebox

from PIL import Image, ImageTk

APP_VERSION = "0.2.1"
DEFAULT_PORT = 4747
RELEASE_API = "https://api.github.com/repos/viiiktooor/LanCam/releases/latest"
ASSET_PATTERN = re.compile(r"^LanCamClient-([0-9]+(?:\.[0-9]+){1,3})\.exe$", re.I)
USER_AGENT = f"LanCamClient/{APP_VERSION}"


def normalize_base(value: str) -> str:
    value = value.strip()
    if not value:
        raise ValueError("Digite o IP ou URL mostrado no LanCam.")
    if "://" not in value:
        value = "http://" + value
    parsed = urllib.parse.urlsplit(value)
    host = parsed.hostname
    if not host:
        raise ValueError("Endereço inválido.")
    port = parsed.port or DEFAULT_PORT
    return f"http://{host}:{port}"


def version_tuple(value: str):
    parts = [int(p) for p in re.findall(r"\d+", value)]
    return tuple((parts + [0, 0, 0, 0])[:4])


def request_json(url: str, timeout=5):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def get_status(base: str) -> dict:
    try:
        req = urllib.request.Request(base + "/api/status", headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=3) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception:
        return {}


class StreamWorker(threading.Thread):
    def __init__(self, app, base: str):
        super().__init__(daemon=True, name="LanCamStream")
        self.app = app
        self.base = base
        self.stop_event = threading.Event()
        self.bytes_received = 0

    def stop(self):
        self.stop_event.set()

    def run(self):
        while not self.stop_event.is_set():
            try:
                status = get_status(self.base)
                self.app.ui(self.app.on_connecting, self.base, status)
                req = urllib.request.Request(
                    self.base + "/stream",
                    headers={"User-Agent": USER_AGENT, "Cache-Control": "no-cache"},
                )
                with urllib.request.urlopen(req, timeout=7) as response:
                    self.app.ui(self.app.on_connected, self.base, status)
                    self.read_mjpeg(response)
            except Exception as exc:
                if self.stop_event.is_set():
                    break
                self.app.ui(self.app.on_stream_error, str(exc))
                for _ in range(10):
                    if self.stop_event.is_set():
                        return
                    time.sleep(0.1)

    def read_mjpeg(self, response):
        buffer = bytearray()
        frame_count = 0
        sample_started = time.monotonic()
        while not self.stop_event.is_set():
            chunk = response.read(8192)
            if not chunk:
                raise ConnectionError("O celular encerrou o stream.")
            self.bytes_received += len(chunk)
            buffer.extend(chunk)

            while True:
                start = buffer.find(b"\xff\xd8")
                if start < 0:
                    if len(buffer) > 2_000_000:
                        del buffer[:-2]
                    break
                end = buffer.find(b"\xff\xd9", start + 2)
                if end < 0:
                    if start > 0:
                        del buffer[:start]
                    break

                jpg = bytes(buffer[start:end + 2])
                del buffer[:end + 2]
                frame_count += 1
                self.app.ui(self.app.on_frame, jpg)

                now = time.monotonic()
                elapsed = now - sample_started
                if elapsed >= 1.0:
                    fps = frame_count / elapsed
                    mbps = (self.bytes_received * 8.0 / elapsed) / 1_000_000.0
                    self.app.ui(self.app.on_stats, fps, mbps)
                    frame_count = 0
                    self.bytes_received = 0
                    sample_started = now


class LanCamApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title(f"LanCam Client {APP_VERSION}")
        self.root.geometry("900x680")
        self.root.minsize(720, 520)
        self.worker = None
        self.photo = None
        self.current_image = None
        self.connected = False
        self.closing = False
        self.build_ui()
        self.load_last_address()
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.root.after(1200, self.check_updates_async)

    def build_ui(self):
        outer = tk.Frame(self.root, padx=14, pady=14)
        outer.pack(fill="both", expand=True)

        top = tk.Frame(outer)
        top.pack(fill="x")
        tk.Label(top, text="IP ou URL do celular:").pack(side="left")
        self.address = tk.Entry(top)
        self.address.pack(side="left", fill="x", expand=True, padx=8)
        self.connect_button = tk.Button(top, text="Conectar", width=12, command=self.toggle_connection)
        self.connect_button.pack(side="left")
        self.update_button = tk.Button(top, text="Atualizações", command=self.check_updates_async)
        self.update_button.pack(side="left", padx=8)

        status_box = tk.Frame(outer, pady=10)
        status_box.pack(fill="x")
        self.connection_label = tk.Label(status_box, text="Celular: desconectado", anchor="w")
        self.connection_label.pack(fill="x")
        self.video_label = tk.Label(status_box, text="Vídeo: aguardando conexão", anchor="w")
        self.video_label.pack(fill="x")
        self.stats_label = tk.Label(status_box, text="Recepção: —", anchor="w")
        self.stats_label.pack(fill="x")
        self.message_label = tk.Label(status_box, text="Pronto.", anchor="w")
        self.message_label.pack(fill="x")

        self.preview_frame = tk.Frame(outer, bd=1, relief="sunken")
        self.preview_frame.pack(fill="both", expand=True)
        self.preview = tk.Label(self.preview_frame, text="O preview aparecerá aqui após conectar.")
        self.preview.pack(fill="both", expand=True)

        bottom = tk.Frame(outer)
        bottom.pack(fill="x", pady=10)
        tk.Label(
            bottom,
            text=("Este cliente 0.2 serve para testar o vídeo no PC sem OBS. "
                  "Ele ainda não cria uma webcam virtual no Windows."),
            anchor="w",
            justify="left",
        ).pack(side="left", fill="x", expand=True)
        tk.Label(bottom, text=f"v{APP_VERSION}").pack(side="right")

    def ui(self, fn, *args):
        if not self.closing:
            self.root.after(0, lambda: fn(*args))

    def settings_path(self):
        base = Path(os.environ.get("APPDATA") or Path.home()) / "LanCam"
        base.mkdir(parents=True, exist_ok=True)
        return base / "settings.json"

    def load_last_address(self):
        try:
            data = json.loads(self.settings_path().read_text(encoding="utf-8"))
            self.address.insert(0, data.get("address", ""))
        except Exception:
            pass

    def save_last_address(self):
        try:
            self.settings_path().write_text(
                json.dumps({"address": self.address.get().strip()}), encoding="utf-8"
            )
        except Exception:
            pass

    def toggle_connection(self):
        if self.worker:
            self.disconnect()
        else:
            self.connect()

    def connect(self):
        try:
            base = normalize_base(self.address.get())
        except ValueError as exc:
            messagebox.showerror("LanCam", str(exc))
            return
        self.save_last_address()
        self.connect_button.configure(text="Desconectar")
        self.message_label.configure(text="Iniciando conexão...")
        self.worker = StreamWorker(self, base)
        self.worker.start()

    def disconnect(self):
        worker = self.worker
        self.worker = None
        if worker:
            worker.stop()
        self.connected = False
        self.connect_button.configure(text="Conectar")
        self.connection_label.configure(text="Celular: desconectado")
        self.video_label.configure(text="Vídeo: aguardando conexão")
        self.stats_label.configure(text="Recepção: —")
        self.message_label.configure(text="Desconectado.")
        self.preview.configure(image="", text="O preview aparecerá aqui após conectar.")
        self.photo = None
        self.current_image = None

    def on_connecting(self, base, status):
        self.connection_label.configure(text=f"Celular: conectando a {base}")
        if status:
            self.video_label.configure(
                text=f"Vídeo anunciado: {status.get('width', '?')}×{status.get('height', '?')} · {status.get('fps', '?')} fps"
            )
        self.message_label.configure(text="Abrindo /stream...")

    def on_connected(self, base, status):
        self.connected = True
        self.connection_label.configure(text=f"Celular: conectado · {base}")
        if status:
            self.video_label.configure(
                text=f"Vídeo: {status.get('width', '?')}×{status.get('height', '?')} · alvo {status.get('fps', '?')} fps · JPEG {status.get('jpegQuality', '?')}%"
            )
        self.message_label.configure(text="Recebendo vídeo.")

    def on_stream_error(self, detail):
        self.connected = False
        self.connection_label.configure(text="Celular: conexão perdida; tentando novamente...")
        self.message_label.configure(text=f"Falha: {detail}")

    def on_stats(self, fps, mbps):
        self.stats_label.configure(text=f"Recepção: {fps:.1f} fps · {mbps:.2f} Mbit/s")

    def set_message(self, text):
        self.message_label.configure(text=text)

    def on_frame(self, jpg: bytes):
        try:
            image = Image.open(io.BytesIO(jpg)).convert("RGB")
            self.current_image = image
            self.render_current_image()
        except Exception:
            pass

    def render_current_image(self):
        if self.current_image is None:
            return
        w = max(self.preview.winfo_width() - 10, 320)
        h = max(self.preview.winfo_height() - 10, 240)
        image = self.current_image.copy()
        image.thumbnail((w, h), Image.Resampling.LANCZOS)
        self.photo = ImageTk.PhotoImage(image)
        self.preview.configure(image=self.photo, text="")

    def check_updates_async(self):
        self.update_button.configure(state="disabled")
        threading.Thread(target=self.check_updates, daemon=True, name="LanCamUpdater").start()

    def check_updates(self):
        try:
            release = request_json(RELEASE_API)
            candidate = None
            for asset in release.get("assets", []):
                match = ASSET_PATTERN.match(asset.get("name", ""))
                if not match:
                    continue
                version = match.group(1)
                if candidate is None or version_tuple(version) > version_tuple(candidate[0]):
                    candidate = (version, asset)
            if candidate and version_tuple(candidate[0]) > version_tuple(APP_VERSION):
                self.ui(self.offer_update, candidate[0], candidate[1])
            else:
                self.ui(self.no_update)
        except Exception:
            self.ui(self.update_check_failed)

    def no_update(self):
        self.update_button.configure(state="normal")
        self.message_label.configure(text=f"LanCam Client {APP_VERSION} está atualizado.")

    def update_check_failed(self):
        self.update_button.configure(state="normal")
        self.message_label.configure(text="Não foi possível verificar atualizações agora.")

    def offer_update(self, version, asset):
        self.update_button.configure(state="normal")
        if not messagebox.askyesno(
            "Atualização do LanCam",
            f"LanCam Client {version} está disponível.\n\nBaixar e atualizar este executável agora?",
        ):
            return
        threading.Thread(
            target=self.download_update, args=(version, asset), daemon=True, name="LanCamUpdateDownload"
        ).start()

    def download_update(self, version, asset):
        try:
            self.ui(self.set_message, f"Baixando LanCam Client {version}...")
            url = asset["browser_download_url"]
            target = Path(tempfile.gettempdir()) / f"LanCamClient-{version}.exe"
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            sha = hashlib.sha256()
            with urllib.request.urlopen(req, timeout=20) as response, target.open("wb") as out:
                while True:
                    chunk = response.read(1024 * 256)
                    if not chunk:
                        break
                    out.write(chunk)
                    sha.update(chunk)

            digest = asset.get("digest") or ""
            if digest.startswith("sha256:") and sha.hexdigest().lower() != digest.split(":", 1)[1].lower():
                target.unlink(missing_ok=True)
                raise RuntimeError("A verificação SHA-256 do arquivo falhou.")

            if getattr(sys, "frozen", False) and os.name == "nt":
                self.ui(self.install_self_update, target, version)
            else:
                self.ui(
                    messagebox.showinfo,
                    "LanCam",
                    f"Atualização {version} baixada em:\n{target}\n\nA troca automática só funciona no .exe compilado.",
                )
        except Exception as exc:
            self.ui(messagebox.showerror, "Atualização do LanCam", f"Não foi possível atualizar.\n\n{exc}")

    def install_self_update(self, new_exe: Path, version: str):
        current = Path(sys.executable).resolve()
        cmd_file = Path(tempfile.gettempdir()) / "LanCam-self-update.cmd"
        script = f'''@echo off\nsetlocal\nset "NEW={new_exe}"\nset "OLD={current}"\nfor /L %%i in (1,1,30) do (\n  copy /Y "%NEW%" "%OLD%" >nul 2>&1 && goto done\n  timeout /t 1 /nobreak >nul\n)\nexit /b 1\n:done\ndel /Q "%NEW%" >nul 2>&1\nstart "" "%OLD%"\ndel "%~f0"\n'''
        cmd_file.write_text(script, encoding="utf-8")
        messagebox.showinfo(
            "Atualização do LanCam",
            f"A versão {version} foi baixada. O LanCam Client será reiniciado para concluir a atualização.",
        )
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        subprocess.Popen(["cmd.exe", "/c", str(cmd_file)], creationflags=creationflags)
        self.close()

    def close(self):
        self.closing = True
        if self.worker:
            self.worker.stop()
            self.worker = None
        self.root.destroy()


def main():
    root = tk.Tk()
    LanCamApp(root)
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
