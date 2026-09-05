import argparse
import json
import sys
import time
import urllib.parse
import urllib.request

import cv2
import pyvirtualcam

APP_VERSION = "0.1.0"
DEFAULT_PORT = 4747


def normalize_base(value: str) -> str:
    value = value.strip()
    if not value:
        raise ValueError("Endereço vazio")
    if "://" not in value:
        value = "http://" + value
    parsed = urllib.parse.urlsplit(value)
    host = parsed.hostname
    if not host:
        raise ValueError("Endereço inválido")
    port = parsed.port or DEFAULT_PORT
    return f"http://{host}:{port}"


def get_status(base: str) -> dict:
    try:
        with urllib.request.urlopen(base + "/api/status", timeout=3) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception:
        return {}


def open_stream(url: str):
    cap = cv2.VideoCapture(url, cv2.CAP_FFMPEG)
    try:
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
    except Exception:
        pass
    return cap


def wait_first_frame(cap, timeout_seconds=10):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        ok, frame = cap.read()
        if ok and frame is not None and frame.size:
            return frame
        time.sleep(0.05)
    return None


def run(address: str, preview: bool, backend: str | None):
    base = normalize_base(address)
    stream_url = base + "/stream"
    status = get_status(base)
    fps = float(status.get("fps", 10) or 10)
    fps = min(max(fps, 1.0), 60.0)

    print(f"LanCam Client {APP_VERSION}")
    print(f"Celular: {base}")
    if status:
        print(
            "Stream: "
            f"{status.get('width', '?')}x{status.get('height', '?')} @ "
            f"{status.get('fps', '?')} fps"
        )

    while True:
        print("Conectando ao stream...")
        cap = open_stream(stream_url)
        frame = wait_first_frame(cap)
        if frame is None:
            cap.release()
            print("Não foi possível receber vídeo. Tentando novamente em 2 s...")
            time.sleep(2)
            continue

        height, width = frame.shape[:2]
        camera_kwargs = dict(
            width=width,
            height=height,
            fps=fps,
            fmt=pyvirtualcam.PixelFormat.BGR,
            print_fps=False,
        )
        if backend:
            camera_kwargs["backend"] = backend

        try:
            cam = pyvirtualcam.Camera(**camera_kwargs)
        except Exception as exc:
            cap.release()
            print("\nFalha ao abrir uma câmera virtual no Windows.")
            print("Instale o OBS Studio para registrar o dispositivo OBS Virtual Camera.")
            print(f"Detalhe: {exc}")
            return 2

        print(f"Câmera virtual: {cam.device}")
        print("Abra Discord/Meet/Zoom e selecione essa câmera.")
        if preview:
            print("Na janela de preview, pressione Q ou ESC para encerrar.")
        else:
            print("Pressione Ctrl+C para encerrar.")

        disconnected = False
        try:
            while True:
                if frame.shape[1] != width or frame.shape[0] != height:
                    frame = cv2.resize(frame, (width, height), interpolation=cv2.INTER_AREA)

                cam.send(frame)

                if preview:
                    cv2.imshow("LanCam Client", frame)
                    key = cv2.waitKey(1) & 0xFF
                    if key in (27, ord("q"), ord("Q")):
                        return 0

                ok, next_frame = cap.read()
                if not ok or next_frame is None or not next_frame.size:
                    disconnected = True
                    break
                frame = next_frame
        finally:
            cam.close()
            cap.release()
            if preview:
                cv2.destroyAllWindows()

        if disconnected:
            print("Stream desconectado. Reconectando em 1 s...")
            time.sleep(1)


def main():
    parser = argparse.ArgumentParser(
        description="Recebe o stream do LanCam e envia para uma câmera virtual do Windows."
    )
    parser.add_argument("address", nargs="?", help="IP ou URL do celular, ex.: 192.168.0.25")
    parser.add_argument("--no-preview", action="store_true", help="Não exibir a janela de preview")
    parser.add_argument("--backend", default=None, help="Backend do pyvirtualcam, ex.: obs")
    args = parser.parse_args()

    address = args.address
    if not address:
        try:
            address = input("IP ou URL exibido no LanCam: ").strip()
        except (EOFError, KeyboardInterrupt):
            return 1

    try:
        return run(address, not args.no_preview, args.backend)
    except KeyboardInterrupt:
        print("\nEncerrado.")
        return 0
    except Exception as exc:
        print(f"Erro: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
